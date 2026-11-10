"""Exports the Google Play phone screenshots from tools/store/screens.html.

Each <section class="shot"> becomes a 1080 x 1920 PNG without alpha, written in page order as
fastlane/metadata/android/vi/images/phoneScreenshots/1.png, 2.png, ...; older numbered files beyond
the current count are removed. Needs Python Playwright with Google Chrome, and ffmpeg.

    python3 tools/store_screens.py
"""
from pathlib import Path
import subprocess

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / 'tools' / 'store' / 'screens.html'
OUT = ROOT / 'fastlane' / 'metadata' / 'android' / 'vi' / 'images' / 'phoneScreenshots'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        browser = p.chromium.launch(channel='chrome')
        page = browser.new_page(viewport={'width': 1080, 'height': 1920}, device_scale_factor=1)
        page.goto(PAGE.as_uri())
        page.evaluate('document.fonts.ready')
        shots = page.query_selector_all('section.shot')
        for i, shot in enumerate(shots, 1):
            raw = OUT / f'.{i}.png'
            shot.screenshot(path=str(raw))
            # Play Console rejects screenshots with an alpha channel.
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(raw), '-pix_fmt', 'rgb24', str(OUT / f'{i}.png')], check=True)
            raw.unlink()
            print(OUT / f'{i}.png', shot.get_attribute('id'))
        browser.close()
    for old in OUT.glob('*.png'):
        if old.stem.isdigit() and int(old.stem) > len(shots):
            old.unlink()
            print('removed', old)


if __name__ == '__main__':
    main()
