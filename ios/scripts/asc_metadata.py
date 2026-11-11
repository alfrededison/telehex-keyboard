#!/usr/bin/env python3
"""Đẩy thông tin App Store của bản iOS lên App Store Connect bằng khoá API. Chạy lại bao nhiêu lần cũng được.

    python3 ios/scripts/asc_metadata.py            # thông tin, xếp hạng tuổi, giá, ảnh chụp, gắn build
    python3 ios/scripts/asc_metadata.py --no-screenshots

Nguồn:
  fastlane/metadata/ios/<ngôn ngữ>/*.txt   tên, phụ đề, mô tả, từ khoá, văn bản quảng bá, URL (định dạng fastlane deliver)
  fastlane/metadata/ios/*.txt              bản quyền, danh mục; review_information/notes.txt là ghi chú cho App Review
  fastlane/screenshots/ios/<ngôn ngữ>/     iphone-<n>.png (6.9", 1320×2868), ipad-<n>.png (13", 2064×2752)
  ios/Config/Signing.local.xcconfig        ASC_KEY_PATH/ASC_KEY_ID/ASC_ISSUER_ID (hoặc biến môi trường) và thông tin
                                           liên hệ cho App Review: REVIEW_FIRST_NAME, REVIEW_LAST_NAME, REVIEW_EMAIL,
                                           REVIEW_PHONE (không commit)

Script cũng: đặt số phiên bản App Store theo MARKETING_VERSION của project.json, trả lời bảng xếp hạng tuổi là không có
nội dung nào (4+), đặt giá miễn phí và bán ở mọi quốc gia nếu app chưa có giá, rồi gắn build mới nhất cùng phiên bản đã
xử lý xong. Không làm được qua API, phải làm trên web: App Privacy ("Data Not Collected") và Content Rights.
"""
import hashlib, json, os, re, sys, urllib.error, urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from asc_signing import Client, fail  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
METADATA = ROOT / "fastlane" / "metadata" / "ios"
SCREENSHOTS = ROOT / "fastlane" / "screenshots" / "ios"
LOCAL = ROOT / "ios" / "Config" / "Signing.local.xcconfig"
PROJECT = ROOT / "ios" / "project.json"
# Ảnh 6.9" và 13" đi vào hai ô bắt buộc của App Store Connect.
DISPLAY_TYPES = {"iphone": "APP_IPHONE_67", "ipad": "APP_IPAD_PRO_3GEN_129"}
# Bảng xếp hạng tuổi: bàn phím không có nội dung nào trong các nhóm này.
AGE_RATING_LEVELS = ["alcoholTobaccoOrDrugUseOrReferences", "contests", "gamblingSimulated", "gunsOrOtherWeapons",
                     "horrorOrFearThemes", "matureOrSuggestiveThemes", "medicalOrTreatmentInformation",
                     "profanityOrCrudeHumor", "sexualContentGraphicAndNudity", "sexualContentOrNudity",
                     "violenceCartoonOrFantasy", "violenceRealistic", "violenceRealisticProlongedGraphicOrSadistic"]
AGE_RATING_FLAGS = ["advertising", "ageAssurance", "gambling", "healthOrWellnessTopics", "lootBox", "messagingAndChat",
                    "parentalControls", "socialMedia", "unrestrictedWebAccess", "userGeneratedContent"]
EDITABLE = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"}


def local_setting(name):
    if os.environ.get(name):
        return os.environ[name]
    if LOCAL.exists():
        for line in reversed(LOCAL.read_text().splitlines()):
            match = re.match(rf"\s*{name}\s*=\s*(.*?)\s*(?:\s//.*)?$", line)
            if match:
                return match[1]
    return ""


def text(path):
    """Nội dung file, hoặc None khi thiếu file hay file rỗng (không gửi chuỗi rỗng lên App Store Connect)."""
    return (path.read_text().strip() or None) if path.exists() else None


def say(message):
    print("asc_metadata:", message)


def upsert(client, kind, existing, values, parent):
    """Sửa một localization nếu khác, chưa có thì tạo. `parent` là (tên quan hệ, type, id)."""
    values = {k: v for k, v in values.items() if v is not None}
    if existing:
        changed = {k: v for k, v in values.items() if existing["attributes"].get(k) != v}
        if changed:
            client.call("PATCH", f"/v1/{kind}/{existing['id']}",
                        {"data": {"type": kind, "id": existing["id"], "attributes": changed}})
        return sorted(changed)
    relation, kind_parent, parent_id = parent
    client.call("POST", f"/v1/{kind}", {"data": {"type": kind, "attributes": values,
        "relationships": {relation: {"data": {"type": kind_parent, "id": parent_id}}}}})
    return ["(mới)"]


def app_info(client, app_id, locales):
    infos = client.get_all(f"/v1/apps/{app_id}/appInfos")
    info = next((i for i in infos if i["attributes"].get("state") != "READY_FOR_DISTRIBUTION"), None)
    if info is None:
        fail("thông tin app đang ở trạng thái đã phát hành, không sửa được; tạo phiên bản mới trên App Store Connect trước")
    current = {l["attributes"]["locale"]: l for l in client.get_all(f"/v1/appInfos/{info['id']}/appInfoLocalizations")}
    for locale in locales:
        d = METADATA / locale
        changed = upsert(client, "appInfoLocalizations", current.get(locale), {
            "locale": None if locale in current else locale, "name": text(d / "name.txt"),
            "subtitle": text(d / "subtitle.txt"), "privacyPolicyUrl": text(d / "privacy_url.txt")},
            ("appInfo", "appInfos", info["id"]))
        say(f"thông tin app {locale}: {', '.join(changed) or 'không đổi'}")
    relations = {}
    for slot, name in (("primaryCategory", "primary_category.txt"), ("secondaryCategory", "secondary_category.txt")):
        if text(METADATA / name):
            relations[slot] = {"data": {"type": "appCategories", "id": text(METADATA / name)}}
    if relations:
        client.call("PATCH", f"/v1/appInfos/{info['id']}", {"data": {"type": "appInfos", "id": info["id"],
                                                                     "relationships": relations}})
        say("danh mục: " + ", ".join(r["data"]["id"] for r in relations.values()))
    rating = client.call("GET", f"/v1/appInfos/{info['id']}/ageRatingDeclaration")["data"]
    attributes = {k: "NONE" for k in AGE_RATING_LEVELS} | {k: False for k in AGE_RATING_FLAGS}
    client.call("PATCH", f"/v1/ageRatingDeclarations/{rating['id']}",
                {"data": {"type": "ageRatingDeclarations", "id": rating["id"], "attributes": attributes}})
    say("xếp hạng tuổi: không có nội dung nào (4+)")


def version(client, app_id, locales, version_string):
    versions = [v for v in client.get_all(f"/v1/apps/{app_id}/appStoreVersions?filter[platform]=IOS")
                if v["attributes"]["appStoreState"] in EDITABLE]
    if not versions:
        fail("không có phiên bản iOS nào đang sửa được; tạo phiên bản mới trên App Store Connect trước")
    if len(versions) > 1:
        say("có nhiều phiên bản đang sửa được; dùng " + versions[0]["attributes"]["versionString"])
    v = versions[0]
    attributes = {"versionString": version_string, "copyright": text(METADATA / "copyright.txt")}
    changed = {k: val for k, val in attributes.items() if val and v["attributes"].get(k) != val}
    if changed:
        client.call("PATCH", f"/v1/appStoreVersions/{v['id']}",
                    {"data": {"type": "appStoreVersions", "id": v["id"], "attributes": changed}})
    say(f"phiên bản {version_string}: {', '.join(changed) or 'không đổi'}")
    first = len(client.get_all(f"/v1/apps/{app_id}/appStoreVersions?filter[platform]=IOS")) == 1
    current = {l["attributes"]["locale"]: l
               for l in client.get_all(f"/v1/appStoreVersions/{v['id']}/appStoreVersionLocalizations")}
    for locale in locales:
        d = METADATA / locale
        changed = upsert(client, "appStoreVersionLocalizations", current.get(locale), {
            "locale": None if locale in current else locale, "description": text(d / "description.txt"),
            "keywords": text(d / "keywords.txt"), "promotionalText": text(d / "promotional_text.txt"),
            "supportUrl": text(d / "support_url.txt"), "marketingUrl": text(d / "marketing_url.txt"),
            # Bản đầu tiên không có "Có gì mới".
            "whatsNew": None if first else text(d / "release_notes.txt")},
            ("appStoreVersion", "appStoreVersions", v["id"]))
        say(f"mô tả {locale}: {', '.join(changed) or 'không đổi'}")
    return v


def review(client, v):
    values = {"notes": text(METADATA / "review_information" / "notes.txt"), "demoAccountRequired": False,
              "contactFirstName": local_setting("REVIEW_FIRST_NAME") or None,
              "contactLastName": local_setting("REVIEW_LAST_NAME") or None,
              "contactEmail": local_setting("REVIEW_EMAIL") or None,
              "contactPhone": local_setting("REVIEW_PHONE") or None}
    values = {k: val for k, val in values.items() if val is not None}
    detail = client.call("GET", f"/v1/appStoreVersions/{v['id']}/appStoreReviewDetail")["data"]
    missing = [k for k in ("REVIEW_FIRST_NAME", "REVIEW_LAST_NAME", "REVIEW_EMAIL", "REVIEW_PHONE") if not local_setting(k)]
    if detail:
        if all(detail["attributes"].get(k) == val for k, val in values.items()):
            say("ghi chú App Review: không đổi" + (f"; thiếu {', '.join(missing)} trong Signing.local.xcconfig" if missing else ""))
            return
        if missing:  # sau lần tạo đầu, Apple bắt mọi lần sửa phải kèm người liên hệ
            say(f"ghi chú App Review: không cập nhật được, cần {', '.join(missing)} trong Signing.local.xcconfig")
            return
        client.call("PATCH", f"/v1/appStoreReviewDetails/{detail['id']}",
                    {"data": {"type": "appStoreReviewDetails", "id": detail["id"], "attributes": values}})
    else:
        client.call("POST", "/v1/appStoreReviewDetails", {"data": {"type": "appStoreReviewDetails", "attributes": values,
            "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": v["id"]}}}}})
    say("ghi chú App Review đã cập nhật" + (f"; thiếu {', '.join(missing)} trong Signing.local.xcconfig" if missing else ""))


def price_and_availability(client, app_id):
    try:
        schedule = client.call("GET", f"/v1/apps/{app_id}/appPriceSchedule?include=manualPrices", quiet=True)
    except LookupError:
        schedule = {}
    if not (schedule.get("included") or []):
        points = client.get_all(f"/v1/apps/{app_id}/appPricePoints?filter[territory]=USA&limit=200")
        free = next((p for p in points if float(p["attributes"]["customerPrice"]) == 0), None)
        if free is None:
            fail("không tìm thấy mức giá miễn phí cho USA; đặt giá trên App Store Connect")
        client.call("POST", "/v1/appPriceSchedules", {"data": {"type": "appPriceSchedules", "relationships": {
            "app": {"data": {"type": "apps", "id": app_id}},
            "baseTerritory": {"data": {"type": "territories", "id": "USA"}},
            "manualPrices": {"data": [{"type": "appPrices", "id": "${free}"}]}}},
            "included": [{"type": "appPrices", "id": "${free}", "attributes": {"startDate": None},
                          "relationships": {"appPricePoint": {"data": {"type": "appPricePoints", "id": free["id"]}}}}]})
        say("giá: miễn phí")
    else:
        say("giá: đã có, giữ nguyên")
    try:
        client.call("GET", f"/v1/apps/{app_id}/appAvailabilityV2", quiet=True)
        say("quốc gia: đã có, giữ nguyên")
    except LookupError:
        territories = client.get_all("/v1/territories?limit=200")
        refs = [{"type": "territoryAvailabilities", "id": f"${{{t['id']}}}"} for t in territories]
        client.call("POST", "/v2/appAvailabilities", {"data": {"type": "appAvailabilities",
            "attributes": {"availableInNewTerritories": True},
            "relationships": {"app": {"data": {"type": "apps", "id": app_id}},
                              "territoryAvailabilities": {"data": refs}}},
            "included": [{"type": "territoryAvailabilities", "id": r["id"], "attributes": {"available": True},
                          "relationships": {"territory": {"data": {"type": "territories", "id": t["id"]}}}}
                         for r, t in zip(refs, territories)]})
        say(f"quốc gia: bán ở cả {len(territories)} quốc gia và vùng lãnh thổ")


SCREENSHOT_LIMIT = 10  # mỗi bộ ảnh của App Store Connect
SCREENSHOT_OK = {"UPLOAD_COMPLETE", "COMPLETE"}  # đã tải xong, đang hoặc đã xử lý xong


def upload_screenshot(client, set_id, path):
    data = path.read_bytes()
    checksum = hashlib.md5(data).hexdigest()
    shot = client.call("POST", "/v1/appScreenshots", {"data": {"type": "appScreenshots",
        "attributes": {"fileName": path.name, "fileSize": len(data)},
        "relationships": {"appScreenshotSet": {"data": {"type": "appScreenshotSets", "id": set_id}}}}})["data"]
    try:
        for op in shot["attributes"]["uploadOperations"]:
            part = data[op["offset"]:op["offset"] + op["length"]]
            request = urllib.request.Request(op["url"], data=part, method=op["method"],
                                             headers={h["name"]: h["value"] for h in op["requestHeaders"]})
            urllib.request.urlopen(request).read()
    except (urllib.error.URLError, OSError) as error:
        client.call("DELETE", f"/v1/appScreenshots/{shot['id']}")
        fail(f"tải ảnh {path} lỗi: {error}. Chạy lại để thử tiếp")
    client.call("PATCH", f"/v1/appScreenshots/{shot['id']}", {"data": {"type": "appScreenshots",
        "id": shot["id"], "attributes": {"uploaded": True, "sourceFileChecksum": checksum}}})
    return shot["id"]


def screenshot_files(folder, prefix):
    files = []
    for path in folder.glob(f"{prefix}-*.png"):
        number = path.stem.split("-")[1]
        if number.isdigit():
            files.append((int(number), path))
    files = [path for _, path in sorted(files)]
    if len(files) > SCREENSHOT_LIMIT:
        fail(f"{folder} có {len(files)} ảnh {prefix}, App Store Connect nhận tối đa {SCREENSHOT_LIMIT}")
    return files


def screenshots(client, v, locales):
    current = {l["attributes"]["locale"]: l
               for l in client.get_all(f"/v1/appStoreVersions/{v['id']}/appStoreVersionLocalizations")}
    for locale in locales:
        folder = SCREENSHOTS / locale
        if not folder.is_dir() or locale not in current:
            continue
        sets = {s["attributes"]["screenshotDisplayType"]: s
                for s in client.get_all(f"/v1/appStoreVersionLocalizations/{current[locale]['id']}/appScreenshotSets")}
        for prefix, display in DISPLAY_TYPES.items():
            files = screenshot_files(folder, prefix)
            if not files:
                continue
            shot_set = sets.get(display) or client.call("POST", "/v1/appScreenshotSets", {"data": {
                "type": "appScreenshotSets", "attributes": {"screenshotDisplayType": display},
                "relationships": {"appStoreVersionLocalization": {"data": {
                    "type": "appStoreVersionLocalizations", "id": current[locale]["id"]}}}}})["data"]
            existing = client.get_all(f"/v1/appScreenshotSets/{shot_set['id']}/appScreenshots")
            wanted = [hashlib.md5(f.read_bytes()).hexdigest() for f in files]
            healthy = all((e["attributes"].get("assetDeliveryState") or {}).get("state") in SCREENSHOT_OK for e in existing)
            if healthy and [e["attributes"].get("sourceFileChecksum") for e in existing] == wanted:
                say(f"ảnh {locale} {prefix}: {len(files)} ảnh, không đổi")
                continue
            # Tải bộ mới trước rồi mới xoá bộ cũ, để lỗi giữa chừng không làm trang App Store mất ảnh. Bộ cũ cộng
            # bộ mới vượt giới hạn mỗi bộ thì đành xoá trước.
            if len(existing) + len(files) > SCREENSHOT_LIMIT:
                for e in existing:
                    client.call("DELETE", f"/v1/appScreenshots/{e['id']}")
                existing = []
            new_ids = [upload_screenshot(client, shot_set["id"], path) for path in files]
            for e in existing:
                client.call("DELETE", f"/v1/appScreenshots/{e['id']}")
            client.call("PATCH", f"/v1/appScreenshotSets/{shot_set['id']}/relationships/appScreenshots",
                        {"data": [{"type": "appScreenshots", "id": i} for i in new_ids]})
            say(f"ảnh {locale} {prefix}: tải lên {len(files)} ảnh")


def attach_build(client, app_id, v, version_string):
    builds = client.call("GET", f"/v1/builds?filter[app]={app_id}&filter[preReleaseVersion.version]={version_string}"
                         "&filter[processingState]=VALID&filter[expired]=false&sort=-uploadedDate&limit=1")["data"]
    if not builds:
        say(f"chưa có build {version_string} xử lý xong để gắn")
        return
    client.call("PATCH", f"/v1/appStoreVersions/{v['id']}/relationships/build",
                {"data": {"type": "builds", "id": builds[0]["id"]}})
    say(f"gắn build {version_string} ({builds[0]['attributes']['version']})")


def connect():
    """(client, app id, MARKETING_VERSION) từ khoá API trong Signing.local.xcconfig và bundle ID trong project.json."""
    key_path = os.path.expanduser(local_setting("ASC_KEY_PATH"))
    key_id, issuer = local_setting("ASC_KEY_ID"), local_setting("ASC_ISSUER_ID")
    if not (key_path and key_id and issuer):
        fail("thiếu ASC_KEY_PATH, ASC_KEY_ID hoặc ASC_ISSUER_ID (ios/Config/Signing.local.xcconfig hoặc biến môi trường)")
    client = Client(key_path, key_id, issuer)
    spec = json.loads(PROJECT.read_text())
    bundle = spec["targets"]["TeleHex"]["settings"]["base"]["PRODUCT_BUNDLE_IDENTIFIER"]
    apps = [a for a in client.get_all(f"/v1/apps?filter[bundleId]={bundle}") if a["attributes"]["bundleId"] == bundle]
    if not apps:
        fail(f"chưa có app {bundle} trên App Store Connect (Apps › + › New App)")
    return client, apps[0]["id"], spec["settings"]["base"]["MARKETING_VERSION"]


def main():
    client, app_id, version_string = connect()
    locales = sorted(p.name for p in METADATA.iterdir() if p.is_dir() and (p / "name.txt").exists())
    app_info(client, app_id, locales)
    v = version(client, app_id, locales, version_string)
    review(client, v)
    price_and_availability(client, app_id)
    if "--no-screenshots" not in sys.argv:
        screenshots(client, v, locales)
    attach_build(client, app_id, v, version_string)
    say("xong. Còn phải làm trên web: App Privacy (Data Not Collected) và Content Rights")


if __name__ == "__main__":
    main()
