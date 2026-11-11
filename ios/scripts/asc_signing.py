#!/usr/bin/env python3
"""Chuẩn bị ký App Store bằng khoá API App Store Connect, không cần đăng nhập Xcode.

Khoá API không tạo hay gán được App Group, nên ký tự động của xcodebuild bằng khoá API lỗi 401 ở `appGroups`.
Script này làm phần ký thủ công thay nó:
  1. Tìm chứng chỉ Apple Distribution của team mà Keychain máy này có khoá riêng; chưa có thì tạo khoá, gửi CSR
     và nhập chứng chỉ mới vào login keychain (khoá giữ ở ~/.appstoreconnect/signing).
  2. Với app và keyboard extension trong project.json: đăng ký bundle ID nếu thiếu (bật App Groups), rồi tái dùng
     profile App Store tên "<target> App Store" nếu còn hạn, đúng chứng chỉ và có đủ App Group; không thì tạo lại.
  3. Cài profile vào Xcode và in cho release.sh: `identity <SHA-1 chứng chỉ>`, rồi `<target> <bundle ID> <UUID profile>`
     cho mỗi target.

App Group phải do chủ tài khoản tạo và gán cho từng App ID trên developer.apple.com. Profile thiếu group thì dừng
và báo cần gán gì.

Dùng: asc_signing.py <project.json> <team ID> <khoá .p8> <key ID> <issuer ID>
"""
import base64, hashlib, json, os, plistlib, re, secrets, subprocess, sys, tempfile, time
import urllib.error, urllib.parse, urllib.request
from datetime import datetime, timedelta, timezone

API = "https://api.appstoreconnect.apple.com"
SIGNING_DIR = os.path.expanduser("~/.appstoreconnect/signing")
PROFILE_DIR = os.path.expanduser("~/Library/Developer/Xcode/UserData/Provisioning Profiles")
TARGETS = ("TeleHex", "TeleHexKeyboard")  # app và extension được archive; test bundle không cần profile


def fail(message):
    sys.exit(f"asc_signing: {message}")


def run(*args, data=None):
    try:
        return subprocess.run(args, input=data, capture_output=True, check=True).stdout
    except subprocess.CalledProcessError as error:  # không in argv: có thể chứa mật khẩu p12 dùng một lần
        fail(f"{args[0]} {args[1]} lỗi: {error.stderr.decode(errors='replace').strip()[:500]}")


class Client:
    def __init__(self, key_path, key_id, issuer):
        self.key_path, self.key_id, self.issuer = key_path, key_id, issuer

    def token(self):
        b64 = lambda raw: base64.urlsafe_b64encode(raw).rstrip(b"=")
        now = int(time.time())
        head = b64(json.dumps({"alg": "ES256", "kid": self.key_id, "typ": "JWT"}).encode())
        body = b64(json.dumps({"iss": self.issuer, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"}).encode())
        der = run("openssl", "dgst", "-sha256", "-sign", self.key_path, data=head + b"." + body)
        # Chữ ký ECDSA dạng DER (SEQUENCE r, s) → r||s 64 byte như JWS yêu cầu.
        def integer(at):
            length = der[at + 1]
            return der[at + 2:at + 2 + length], at + 2 + length
        r, at = integer(2 + (1 if der[1] & 0x80 else 0))
        s, _ = integer(at)
        sig = r.lstrip(b"\0").rjust(32, b"\0") + s.lstrip(b"\0").rjust(32, b"\0")
        return (head + b"." + body + b"." + b64(sig)).decode()

    def call(self, method, path, body=None, quiet=False):
        """quiet: HTTP 404 thành LookupError thay vì dừng script, để hỏi tài nguyên có thể chưa tồn tại."""
        request = urllib.request.Request(API + path, method=method, data=None if body is None else json.dumps(body).encode(),
                                         headers={"Authorization": "Bearer " + self.token(), "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request) as response:
                raw = response.read()
        except urllib.error.HTTPError as error:
            if quiet and error.code == 404:
                raise LookupError(path) from None
            fail(f"{method} {path}: HTTP {error.code} {error.read().decode(errors='replace')[:500]}")
        return json.loads(raw) if raw else None

    def get_all(self, path):
        items, url = [], path
        while url:
            page = self.call("GET", url)
            items += page["data"]
            url = page.get("links", {}).get("next", "").replace(API, "") or None
        return items


def local_identities(team):
    """{SHA-1: hợp lệ?} của các chứng chỉ phân phối của team có khoá riêng trong Keychain, kể cả chứng chỉ hết hạn hay
    thiếu chứng chỉ trung gian (find-identity không -v vẫn liệt kê, kèm lỗi CSSMERR_… ở cuối dòng)."""
    found = {}
    for line in run("security", "find-identity", "-p", "codesigning").decode().splitlines():
        match = re.match(r'\s*\d+\) ([0-9A-F]{40}) "([^"]*)"(.*)', line)
        if match and match[2].startswith(("Apple Distribution:", "iPhone Distribution:")) and f"({team})" in match[2]:
            found[match[1]] = found.get(match[1], False) or not match[3].strip()
    return found


def import_identity(key, der):
    with tempfile.TemporaryDirectory() as work:
        pem, p12 = os.path.join(work, "c.pem"), os.path.join(work, "c.p12")
        password = secrets.token_hex(16)
        run("openssl", "x509", "-inform", "der", "-out", pem, data=der)
        export = ["openssl", "pkcs12", "-export", "-inkey", key, "-in", pem, "-out", p12, "-passout", "stdin"]
        try:  # OpenSSL 3 mặc định mã hoá AES mà security import không đọc được; LibreSSL không có -legacy
            subprocess.run(export[:3] + ["-legacy"] + export[3:], input=password.encode(), capture_output=True, check=True)
        except subprocess.CalledProcessError:
            run(*export, data=password.encode())
        run("security", "import", p12, "-k", os.path.expanduser("~/Library/Keychains/login.keychain-db"),
            "-P", password, "-T", "/usr/bin/codesign", "-T", "/usr/bin/security")


def distribution_certificate(client, team):
    """(id, DER, SHA-1) của chứng chỉ phân phối dùng để ký. Thứ tự: chứng chỉ có trong Keychain; cặp .key/.cer đã lưu ở
    SIGNING_DIR (lần trước tạo xong mà chưa nhập được); cuối cùng mới tạo mới, vì mỗi team chỉ có vài chứng chỉ."""
    local = local_identities(team)
    remote = {}
    for cert in client.get_all("/v1/certificates?filter[certificateType]=DISTRIBUTION,IOS_DISTRIBUTION&limit=200"):
        der = base64.b64decode(cert["attributes"]["certificateContent"])
        remote[hashlib.sha1(der).hexdigest().upper()] = (cert["id"], der)
    for sha1, valid in local.items():
        if valid and sha1 in remote:
            return (*remote[sha1], sha1)
    broken = [sha1 for sha1 in local if sha1 in remote]
    if broken:
        fail(f"Keychain có chứng chỉ phân phối {', '.join(broken)} nhưng macOS không tin (hết hạn hoặc thiếu chứng chỉ "
             "trung gian Apple WWDR G3). Sửa trong Keychain Access hoặc thu hồi chứng chỉ đó rồi chạy lại; "
             "không tự tạo thêm để khỏi chạm giới hạn chứng chỉ của team.")
    os.makedirs(SIGNING_DIR, mode=0o700, exist_ok=True)
    for name in sorted(os.listdir(SIGNING_DIR)):
        key = os.path.join(SIGNING_DIR, name[:-4] + ".key")
        if name.endswith(".cer") and os.path.exists(key):
            with open(os.path.join(SIGNING_DIR, name), "rb") as handle:
                sha1 = hashlib.sha1(handle.read()).hexdigest().upper()
            if sha1 in remote:
                print(f"asc_signing: nhập lại chứng chỉ đã lưu {name}", file=sys.stderr)
                import_identity(key, remote[sha1][1])
                return (*remote[sha1], sha1)
    print("asc_signing: Keychain chưa có chứng chỉ phân phối của team; tạo chứng chỉ Apple Distribution mới", file=sys.stderr)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    key = os.path.join(SIGNING_DIR, f"distribution-{team}-{stamp}.key")
    old = os.umask(0o077)
    try:
        csr = run("openssl", "req", "-new", "-newkey", "rsa:2048", "-nodes", "-keyout", key,
                  "-subj", "/CN=TeleHex Distribution/C=VN").decode()
    finally:
        os.umask(old)
    content = "".join(line for line in csr.splitlines() if not line.startswith("-----"))
    cert = client.call("POST", "/v1/certificates", {"data": {"type": "certificates", "attributes": {
        "certificateType": "DISTRIBUTION", "csrContent": content}}})["data"]
    der = base64.b64decode(cert["attributes"]["certificateContent"])
    with open(key[:-4] + ".cer", "wb") as handle:  # lưu trước khi nhập: nhập lỗi thì lần sau dùng lại cặp này
        handle.write(der)
    import_identity(key, der)
    return cert["id"], der, hashlib.sha1(der).hexdigest().upper()


def targets(project_path):
    with open(project_path) as handle:
        spec = json.load(handle)
    for name in TARGETS:
        target = spec["targets"][name]
        bundle = target["settings"]["base"]["PRODUCT_BUNDLE_IDENTIFIER"]
        groups = target.get("entitlements", {}).get("properties", {}).get("com.apple.security.application-groups", [])
        yield name, bundle, sorted(groups)


def bundle_id(client, identifier, needs_groups):
    found = [b for b in client.get_all(f"/v1/bundleIds?filter[identifier]={identifier}&limit=200")
             if b["attributes"]["identifier"] == identifier]
    if found:
        return found[0]["id"]
    print(f"asc_signing: đăng ký bundle ID {identifier}", file=sys.stderr)
    created = client.call("POST", "/v1/bundleIds", {"data": {"type": "bundleIds", "attributes": {
        "name": identifier, "identifier": identifier, "platform": "IOS"}}})["data"]
    if needs_groups:
        client.call("POST", "/v1/bundleIdCapabilities", {"data": {"type": "bundleIdCapabilities",
            "attributes": {"capabilityType": "APP_GROUPS"},
            "relationships": {"bundleId": {"data": {"type": "bundleIds", "id": created["id"]}}}}})
    return created["id"]


def decode(profile):
    raw = base64.b64decode(profile["attributes"]["profileContent"])
    with tempfile.NamedTemporaryFile(suffix=".mobileprovision") as handle:
        handle.write(raw); handle.flush()
        return raw, plistlib.loads(run("security", "cms", "-D", "-i", handle.name))


def problems(info, cert_der, groups):
    issues = []
    if info["ExpirationDate"].replace(tzinfo=timezone.utc) < datetime.now(timezone.utc) + timedelta(days=7):
        issues.append("sắp hết hạn")
    if cert_der not in info.get("DeveloperCertificates", []):
        issues.append("không chứa chứng chỉ trong Keychain")
    missing = set(groups) - set(info["Entitlements"].get("com.apple.security.application-groups", []))
    if missing:
        issues.append("thiếu App Group " + ", ".join(sorted(missing)))
    return issues


def main():
    if len(sys.argv) != 6:
        fail(__doc__.strip().splitlines()[-1])
    project, team, key_path, key_id, issuer = sys.argv[1:]
    client = Client(os.path.expanduser(key_path), key_id, issuer)
    cert_id, cert_der, cert_sha1 = distribution_certificate(client, team)
    print("identity", cert_sha1)
    os.makedirs(PROFILE_DIR, exist_ok=True)
    for target, identifier, groups in targets(project):
        bid = bundle_id(client, identifier, bool(groups))
        name = f"{target} App Store"
        existing = [p for p in client.get_all(f"/v1/profiles?filter[name]={urllib.parse.quote(name)}&limit=200")
                    if p["attributes"]["profileType"] == "IOS_APP_STORE" and p["attributes"]["name"] == name]
        chosen = None
        for profile in existing:
            raw, info = decode(profile)
            if profile["attributes"]["profileState"] == "ACTIVE" and not problems(info, cert_der, groups) and chosen is None:
                chosen = (raw, info)
            else:
                client.call("DELETE", f"/v1/profiles/{profile['id']}")
        if chosen is None:
            print(f"asc_signing: tạo profile {name}", file=sys.stderr)
            created = client.call("POST", "/v1/profiles", {"data": {"type": "profiles",
                "attributes": {"name": name, "profileType": "IOS_APP_STORE"},
                "relationships": {"bundleId": {"data": {"type": "bundleIds", "id": bid}},
                                  "certificates": {"data": [{"type": "certificates", "id": cert_id}]}}}})["data"]
            chosen = decode(created)
            issues = problems(chosen[1], cert_der, groups)
            if issues:
                client.call("DELETE", f"/v1/profiles/{created['id']}")
                fail(f"profile {name} {'; '.join(issues)}. Chủ tài khoản vào developer.apple.com › Certificates, Identifiers "
                     f"& Profiles › Identifiers › {identifier} › App Groups › Configure, chọn {', '.join(groups)}, Save; "
                     "rồi chạy lại.")
        raw, info = chosen
        with open(os.path.join(PROFILE_DIR, info["UUID"] + ".mobileprovision"), "wb") as handle:
            handle.write(raw)
        print(target, identifier, info["UUID"])


if __name__ == "__main__":
    main()
