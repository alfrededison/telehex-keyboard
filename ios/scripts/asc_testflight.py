#!/usr/bin/env python3
"""Đưa build iOS mới nhất ra TestFlight công khai (nhóm External có public link). Chạy lại bao nhiêu lần cũng được.

    python3 ios/scripts/asc_testflight.py

Làm lần lượt:
  1. Test Information: mô tả bản beta, email góp ý, Privacy Policy URL (fastlane/metadata/ios/<ngôn ngữ>/
     testflight_description.txt, privacy_url.txt, marketing_url.txt) và người liên hệ cho Beta App Review, lấy từ
     phần App Review của phiên bản App Store (đã điền trên web) hoặc REVIEW_* trong Signing.local.xcconfig.
  2. "What to Test" của build mới nhất đã xử lý xong: <ngôn ngữ>/testflight_what_to_test.txt.
  3. Nhóm External "Công khai": thêm build, bật public link (TESTFLIGHT_PUBLIC_LIMIT trong Signing.local.xcconfig
     để giới hạn số người, bỏ trống là không giới hạn).
  4. Gửi Beta App Review nếu build chưa gửi; in public link.

Email góp ý là TESTFLIGHT_FEEDBACK_EMAIL trong Signing.local.xcconfig, mặc định là email liên hệ App Review. Người thử
thấy email này.
"""
from asc_metadata import EDITABLE, METADATA, connect, local_setting, text, upsert
from asc_signing import fail

GROUP = "Công khai"
# externalBuildState không thể gửi lại: build hỏng hay bị từ chối cần build mới.
DEAD = {"BETA_REJECTED", "EXPIRED", "PROCESSING_EXCEPTION", "MISSING_EXPORT_COMPLIANCE"}


def say(message):
    print("asc_testflight:", message)


def contact(client, app_id):
    """Người liên hệ cho Beta App Review: phần App Review của phiên bản App Store, thiếu thì REVIEW_*."""
    fields = {"contactFirstName": "REVIEW_FIRST_NAME", "contactLastName": "REVIEW_LAST_NAME",
              "contactEmail": "REVIEW_EMAIL", "contactPhone": "REVIEW_PHONE"}
    local = {k: local_setting(setting) for k, setting in fields.items()}
    if all(local.values()):
        return local
    # Phiên bản đang sửa trước, rồi mới nhất: không lấy người liên hệ cũ của một bản đã phát hành từ lâu.
    versions = client.get_all(f"/v1/apps/{app_id}/appStoreVersions?filter[platform]=IOS&sort=-createdDate")
    versions.sort(key=lambda v: v["attributes"]["appStoreState"] not in EDITABLE)
    found = {}
    if versions:
        detail = client.call("GET", f"/v1/appStoreVersions/{versions[0]['id']}/appStoreReviewDetail")["data"]
        found = {k: detail["attributes"].get(k) for k in fields} if detail else {}
    values = {k: local[k] or found.get(k) or None for k in fields}
    missing = [k for k, val in values.items() if not val]
    if missing:
        fail(f"thiếu người liên hệ ({', '.join(missing)}): điền App Review trên App Store Connect hoặc REVIEW_* "
             "trong ios/Config/Signing.local.xcconfig")
    return values


def test_information(client, app_id, locales, person):
    feedback = local_setting("TESTFLIGHT_FEEDBACK_EMAIL") or person["contactEmail"]
    current = {l["attributes"]["locale"]: l for l in client.get_all(f"/v1/apps/{app_id}/betaAppLocalizations")}
    for locale in locales:
        d = METADATA / locale
        changed = upsert(client, "betaAppLocalizations", current.get(locale), {
            "locale": None if locale in current else locale, "description": text(d / "testflight_description.txt"),
            "feedbackEmail": feedback, "privacyPolicyUrl": text(d / "privacy_url.txt"),
            "marketingUrl": text(d / "marketing_url.txt")}, ("app", "apps", app_id))
        say(f"Test Information {locale}: {', '.join(changed) or 'không đổi'}")
    detail = client.call("GET", f"/v1/apps/{app_id}/betaAppReviewDetail")["data"]
    values = person | {"demoAccountRequired": False, "notes": text(METADATA / "review_information" / "notes.txt")}
    if any(detail["attributes"].get(k) != v for k, v in values.items()):
        client.call("PATCH", f"/v1/betaAppReviewDetails/{detail['id']}",
                    {"data": {"type": "betaAppReviewDetails", "id": detail["id"], "attributes": values}})
        say("Beta App Review: cập nhật người liên hệ và ghi chú")
    else:
        say("Beta App Review: không đổi")


def latest_build(client, app_id, version_string):
    builds = client.call("GET", f"/v1/builds?filter[app]={app_id}&filter[preReleaseVersion.version]={version_string}"
                         "&filter[processingState]=VALID&filter[expired]=false&sort=-uploadedDate&limit=1")["data"]
    if not builds:
        fail(f"chưa có build {version_string} nào xử lý xong; chạy ios/scripts/release.sh rồi đợi Apple xử lý")
    return builds[0]


def what_to_test(client, build, locales):
    current = {l["attributes"]["locale"]: l for l in client.get_all(f"/v1/builds/{build['id']}/betaBuildLocalizations")}
    for locale in locales:
        changed = upsert(client, "betaBuildLocalizations", current.get(locale), {
            "locale": None if locale in current else locale,
            "whatsNew": text(METADATA / locale / "testflight_what_to_test.txt")}, ("build", "builds", build["id"]))
        say(f"What to Test {locale}: {', '.join(changed) or 'không đổi'}")


def public_group(client, app_id, build):
    groups = [g for g in client.get_all(f"/v1/apps/{app_id}/betaGroups")
              if g["attributes"]["name"] == GROUP and not g["attributes"]["isInternalGroup"]]
    group = groups[0] if groups else client.call("POST", "/v1/betaGroups", {"data": {"type": "betaGroups",
        "attributes": {"name": GROUP}, "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})["data"]
    builds = {b["id"] for b in client.get_all(f"/v1/betaGroups/{group['id']}/builds")}
    if build["id"] not in builds:
        client.call("POST", f"/v1/betaGroups/{group['id']}/relationships/builds",
                    {"data": [{"type": "builds", "id": build["id"]}]})
        say(f"nhóm {GROUP}: thêm build {build['attributes']['version']}")
    limit = local_setting("TESTFLIGHT_PUBLIC_LIMIT")
    if limit and not (limit.isdigit() and 1 <= int(limit) <= 10000):
        fail(f"TESTFLIGHT_PUBLIC_LIMIT phải là số từ 1 tới 10000, đang là {limit!r}")
    wanted = {"publicLinkEnabled": True, "publicLinkLimitEnabled": bool(limit)}
    if limit:
        wanted["publicLinkLimit"] = int(limit)
    if any(group["attributes"].get(k) != v for k, v in wanted.items()):
        group = client.call("PATCH", f"/v1/betaGroups/{group['id']}",
                            {"data": {"type": "betaGroups", "id": group["id"], "attributes": wanted}})["data"]
    return group


def beta_state(client, build):
    return client.call("GET", f"/v1/builds/{build['id']}/buildBetaDetail")["data"]["attributes"]["externalBuildState"]


def submit(client, build, state):
    """Chỉ gửi khi build đang chờ gửi. Build sau của một phiên bản đã duyệt thường được duyệt tự động
    (READY_FOR_BETA_TESTING); các trạng thái đang duyệt hay đã duyệt thì chỉ báo."""
    if state != "READY_FOR_BETA_SUBMISSION":
        say(f"Beta App Review: {state}")
        return
    client.call("POST", "/v1/betaAppReviewSubmissions", {"data": {"type": "betaAppReviewSubmissions",
        "relationships": {"build": {"data": {"type": "builds", "id": build["id"]}}}}})
    say("Beta App Review: đã gửi, đợi Apple duyệt (thường dưới 1 ngày)")


def main():
    client, app_id, version_string = connect()
    locales = sorted(p.name for p in METADATA.iterdir() if (p / "testflight_description.txt").exists())
    test_information(client, app_id, locales, contact(client, app_id))
    build = latest_build(client, app_id, version_string)
    state = beta_state(client, build)
    if state in DEAD:
        fail(f"build {build['attributes']['version']} ở trạng thái {state}, không gửi TestFlight được; sửa rồi tải "
             "build mới bằng ios/scripts/release.sh")
    what_to_test(client, build, locales)
    group = public_group(client, app_id, build)
    submit(client, build, state)
    link = group["attributes"].get("publicLink")
    say(f"public link: {link}" if link else "public link: chưa có, Apple cấp khi duyệt xong; chạy lại để xem")


if __name__ == "__main__":
    main()
