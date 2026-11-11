"""Exports the App Store screenshots from tools/store/ios-screens.html.

Each <section class="shot"> with id `iphone-N` or `ipad-N` becomes a PNG without alpha in
fastlane/screenshots/ios/vi/ (iPhone 6.9" 1320 x 2868, iPad 13" 2064 x 2752); older numbered files
beyond the current count are removed. The device content comes from real Simulator captures in
tools/store/ios-raw/. Needs Python Playwright with Google Chrome, and ffmpeg.

    python3 tools/store_ios_screens.py
"""
from pathlib import Path
import subprocess

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / 'tools' / 'store' / 'ios-screens.html'
OUT = ROOT / 'fastlane' / 'screenshots' / 'ios' / 'vi'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    written = set()
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='chrome')
        page = browser.new_page(viewport={'width': 2064, 'height': 2868}, device_scale_factor=1)
        page.goto(PAGE.as_uri())
        page.evaluate('document.fonts.ready')
        page.wait_for_load_state('networkidle')
        for shot in page.query_selector_all('section.shot'):
            name = shot.get_attribute('id')
            raw = OUT / f'.{name}.png'
            shot.screenshot(path=str(raw))
            # App Store Connect rejects screenshots with an alpha channel.
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(raw), '-pix_fmt', 'rgb24', str(OUT / f'{name}.png')], check=True)
            raw.unlink()
            written.add(f'{name}.png')
            print(OUT / f'{name}.png')
        browser.close()
    for old in OUT.glob('*.png'):
        if old.name not in written:
            old.unlink()
            print('removed', old)


if __name__ == '__main__':
    main()
