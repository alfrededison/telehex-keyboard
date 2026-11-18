#!/usr/bin/env python3
"""Gửi phiên bản App Store theo MARKETING_VERSION đi duyệt, hoặc phát hành khi đã duyệt xong. Chạy lại bao nhiêu lần cũng được.

    python3 ios/scripts/asc_submit.py              # gửi App Review; đang duyệt hay đã duyệt thì chỉ báo trạng thái
    python3 ios/scripts/asc_submit.py --release    # phiên bản đã duyệt (chờ phát hành thủ công) thì phát hành luôn

Chạy sau ios/scripts/asc_metadata.py: phiên bản phải đang sửa được và đã gắn build.
"""
import sys

from asc_metadata import EDITABLE, connect
from asc_signing import fail

# Phiên bản bị từ chối được gửi lại qua lần gửi cũ đang có vấn đề cần giải quyết.
OPEN_SUBMISSIONS = ("READY_FOR_REVIEW", "UNRESOLVED_ISSUES")


def say(message):
    print("asc_submit:", message)


def submission(client, app_id):
    """Lần gửi duyệt đang mở của app (chưa gửi hoặc bị từ chối), không có thì tạo."""
    for state in OPEN_SUBMISSIONS:
        found = client.call("GET", f"/v1/reviewSubmissions?filter[app]={app_id}&filter[platform]=IOS"
                                   f"&filter[state]={state}&limit=1")["data"]
        if found:
            return found[0]
    return client.call("POST", "/v1/reviewSubmissions", {"data": {"type": "reviewSubmissions",
        "attributes": {"platform": "IOS"}, "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})["data"]


def submit(client, app_id, v):
    build = client.call("GET", f"/v1/appStoreVersions/{v['id']}/build")["data"]
    if not build:
        fail(f"phiên bản {v['attributes']['versionString']} chưa gắn build; chạy ios/scripts/asc_metadata.py khi build "
             "đã xử lý xong")
    sub = submission(client, app_id)
    items = client.get_all(f"/v1/reviewSubmissions/{sub['id']}/items?include=appStoreVersion")
    if not any((i["relationships"].get("appStoreVersion", {}).get("data") or {}).get("id") == v["id"] for i in items):
        client.call("POST", "/v1/reviewSubmissionItems", {"data": {"type": "reviewSubmissionItems", "relationships": {
            "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": sub["id"]}},
            "appStoreVersion": {"data": {"type": "appStoreVersions", "id": v["id"]}}}}})
    client.call("PATCH", f"/v1/reviewSubmissions/{sub['id']}",
                {"data": {"type": "reviewSubmissions", "id": sub["id"], "attributes": {"submitted": True}}})
    say(f"đã gửi App Review phiên bản {v['attributes']['versionString']} (build {build['attributes']['version']}); "
        "duyệt xong chạy lại với --release")


def main():
    client, app_id, version_string = connect()
    found = client.call("GET", f"/v1/apps/{app_id}/appStoreVersions?filter[platform]=IOS"
                               f"&filter[versionString]={version_string}")["data"]
    if not found:
        fail(f"chưa có phiên bản {version_string}; chạy ios/scripts/asc_metadata.py trước")
    v = found[0]
    state = v["attributes"]["appStoreState"]
    if state in EDITABLE:
        submit(client, app_id, v)
    elif state == "PENDING_DEVELOPER_RELEASE" and "--release" in sys.argv:
        client.call("POST", "/v1/appStoreVersionReleaseRequests", {"data": {"type": "appStoreVersionReleaseRequests",
            "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": v["id"]}}}}})
        say(f"đã phát hành phiên bản {version_string}; App Store cập nhật trong vài giờ")
    elif state == "PENDING_DEVELOPER_RELEASE":
        say(f"phiên bản {version_string} đã duyệt, chờ phát hành; chạy lại với --release để phát hành")
    else:
        say(f"phiên bản {version_string}: {state}")


if __name__ == "__main__":
    main()
