#!/usr/bin/env python3
"""対象サイトからアプリ名とアイコンを取得し、Android のリソースを上書きする（CI のビルド前に実行）。

取得の順番：
  名前   … manifest の short_name → name → og:site_name → <title>
  アイコン … manifest の icons（大きいもの優先。maskable があればそれを優先）
             → <link rel="apple-touch-icon"> → <link rel="icon"> → /favicon.ico
取得できなかった項目は、リポジトリにある仮の名前・アイコンのまま。失敗してもビルドは止めない。

使い方: python3 tools/fetch_site_meta.py https://soraraw.com/ app/src/main/res
"""
import io
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from html import unescape
from html.parser import HTMLParser

UA = ("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
LOG = []


def log(msg):
    print(msg)
    LOG.append(msg)


def fetch(url, limit=5_000_000):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=20) as res:
        return res.geturl(), res.read(limit)


class HeadParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []  # (rel, href, sizes, type)
        self.meta = {}
        self.title = ""
        self._in_title = False

    def handle_starttag(self, tag, attrs):
        a = {k.lower(): (v or "") for k, v in attrs}
        if tag == "link" and a.get("href"):
            self.links.append((a.get("rel", "").lower(), a["href"], a.get("sizes", ""), a.get("type", "")))
        elif tag == "meta":
            key = (a.get("property") or a.get("name") or "").lower()
            if key and "content" in a:
                self.meta.setdefault(key, a["content"])
        elif tag == "title":
            self._in_title = True

    def handle_endtag(self, tag):
        if tag == "title":
            self._in_title = False

    def handle_data(self, data):
        if self._in_title:
            self.title += data


def size_of(sizes):
    best = 0
    for s in (sizes or "").lower().split():
        if s == "any":
            return 10_000
        m = re.match(r"(\d+)x(\d+)", s)
        if m:
            best = max(best, int(m.group(1)))
    return best


def load_image(url):
    from PIL import Image
    _, data = fetch(url)
    if data.lstrip()[:5].lower() in (b"<?xml", b"<svg ") or url.lower().endswith(".svg"):
        raise ValueError("SVG は変換しない")
    img = Image.open(io.BytesIO(data))
    if getattr(img, "format", "") == "ICO":
        # .ico は一番大きい画像を使う
        sizes = sorted(img.info.get("sizes", []), key=lambda s: s[0])
        if sizes:
            img.size = sizes[-1]
    img.load()
    return img.convert("RGBA")


def parse_color(value):
    if not value:
        return None
    v = value.strip()
    m = re.fullmatch(r"#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})", v)
    if not m:
        return None
    h = m.group(1)
    if len(h) == 3:
        h = "".join(c * 2 for c in h)
    return "#" + h.upper()


def android_string(text):
    t = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    t = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    if t.startswith(("@", "?")):
        t = "\\" + t
    return t


def main():
    site, res_dir = sys.argv[1], sys.argv[2]
    name = None
    icon_candidates = []  # (優先度, サイズ, url, maskable)
    bg_color = None

    try:
        page_url, html = fetch(site)
    except Exception as e:  # noqa: BLE001
        log(f"サイトに接続できませんでした（{site}）: {e}")
        return finish(name, None, False, bg_color)
    log(f"サイトを取得しました: {page_url}")

    p = HeadParser()
    p.feed(html.decode("utf-8", "replace"))

    manifest_href = next((h for rel, h, _, _ in p.links if "manifest" in rel.split()), None)
    if manifest_href:
        murl = urllib.parse.urljoin(page_url, manifest_href)
        try:
            murl, mdata = fetch(murl)
            m = json.loads(mdata.decode("utf-8-sig"))
            log(f"manifest を取得しました: {murl}")
            name = (m.get("short_name") or m.get("name") or "").strip() or None
            bg_color = parse_color(m.get("background_color")) or parse_color(m.get("theme_color"))
            for ic in m.get("icons", []):
                if not ic.get("src"):
                    continue
                purposes = (ic.get("purpose") or "any").split()
                maskable = "maskable" in purposes
                prio = 0 if maskable else 1
                icon_candidates.append((prio, size_of(ic.get("sizes")),
                                        urllib.parse.urljoin(murl, ic["src"]), maskable))
        except Exception as e:  # noqa: BLE001
            log(f"manifest を読めませんでした: {e}")
    else:
        log("manifest はありませんでした")

    if not name:
        # title は「サイト名 | ページ名」の形が多いので、区切りより前だけを使う
        title = re.split(r"\s+[|｜\-–—:]\s+", unescape(p.title).strip())[0].strip()
        name = p.meta.get("og:site_name") or p.meta.get("application-name") or title or None
    bg_color = bg_color or parse_color(p.meta.get("theme-color"))

    for rel, href, sizes, _ in p.links:
        rels = rel.split()
        url = urllib.parse.urljoin(page_url, href)
        if "apple-touch-icon" in rels or "apple-touch-icon-precomposed" in rels:
            icon_candidates.append((2, size_of(sizes) or 180, url, False))
        elif "icon" in rels:
            icon_candidates.append((3, size_of(sizes), url, False))
    icon_candidates.append((4, 0, urllib.parse.urljoin(page_url, "/favicon.ico"), False))

    # 優先度が高く、大きいものから順に試す
    icon_candidates.sort(key=lambda c: (c[0], -c[1]))
    img, maskable = None, False
    for _, _, url, mask in icon_candidates:
        try:
            img = load_image(url)
            maskable = mask
            log(f"アイコンを取得しました: {url}（{img.width}x{img.height}{'、maskable' if mask else ''}）")
            break
        except Exception as e:  # noqa: BLE001
            log(f"アイコンを読めませんでした: {url}: {e}")
    return finish(name, img, maskable, bg_color, res_dir)


def finish(name, img, maskable, bg_color, res_dir=None):
    res_dir = res_dir or sys.argv[2]
    if name:
        name = re.sub(r"\s+", " ", name)[:30]
        with open(os.path.join(res_dir, "values", "app_name.xml"), "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- tools/fetch_site_meta.py が生成 -->\n'
                    f'<resources>\n    <string name="app_name">{android_string(name)}</string>\n</resources>\n')
        log(f"アプリ名: {name}")
    else:
        log("アプリ名は取得できなかったので、仮の名前のままにします")

    if img is not None:
        from PIL import Image
        # アダプティブアイコンの前景（108dp 四方）。maskable はそのまま全面に、
        # それ以外は切り取られない安全領域（中央 66dp）に収まるよう縮小して中央に置く
        canvas_px = 432
        fg = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        target = canvas_px if maskable else int(canvas_px * 60 / 108)
        icon = img.copy()
        icon.thumbnail((target, target), Image.LANCZOS)
        if icon.width < target:  # 小さい画像は拡大する
            icon = icon.resize((target, round(icon.height * target / icon.width)), Image.LANCZOS)
        fg.paste(icon, ((canvas_px - icon.width) // 2, (canvas_px - icon.height) // 2), icon)
        if not bg_color:
            # 背景色の指定がなければ、透明な部分がある画像は白、ない画像は四隅の色
            corner = img.getpixel((0, 0))
            bg_color = "#FFFFFF" if corner[3] < 250 else "#{:02X}{:02X}{:02X}".format(*corner[:3])
        os.makedirs(os.path.join(res_dir, "drawable-nodpi"), exist_ok=True)
        fg.save(os.path.join(res_dir, "drawable-nodpi", "ic_launcher_site.png"))
        with open(os.path.join(res_dir, "values", "site_colors.xml"), "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- tools/fetch_site_meta.py が生成 -->\n'
                    f'<resources>\n    <color name="ic_launcher_site_background">{bg_color}</color>\n</resources>\n')
        with open(os.path.join(res_dir, "mipmap-anydpi-v26", "ic_launcher.xml"), "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- tools/fetch_site_meta.py が生成 -->\n'
                    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                    '    <background android:drawable="@color/ic_launcher_site_background" />\n'
                    '    <foreground android:drawable="@drawable/ic_launcher_site" />\n'
                    '</adaptive-icon>\n')
        log(f"アイコンを設定しました（背景色 {bg_color}）")
    else:
        log("アイコンは取得できなかったので、仮のアイコンのままにします")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("### サイト情報の取得\n\n" + "\n".join(f"- {m}" for m in LOG) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
