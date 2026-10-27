"""Extract the 2026 Heusenstamm waste calendar (pages 1-2) from the PDF.

Each collection is a coloured box with a label (zone number, '1+2', 'A', 'S', 'GS'...).
The boxes are not vector shapes we can read reliably, so we take the label text
positions from the PDF and sample the box colour from a 200 dpi render.
"""
import collections
import json
import sys

import pdfplumber
from PIL import Image

PDF = sys.argv[1]
RENDERS = sys.argv[2:4]  # hi-1.png, hi-2.png
SCALE = 200 / 72

# legend colours (RGB) sampled by eye from the render; matched by nearest
LEGEND = {
    "rest": None,      # grey
    "papier": None,    # mint
    "bio": None,       # brown
    "schadstoff": None,  # red
    "gruen": None,     # green
    "gelb": None,      # yellow
    "baum": None,      # blue
}


def box_colour(img, w):
    x0, x1 = (w["x0"] - 1.5) * SCALE, (w["x1"] + 1.5) * SCALE
    y0, y1 = (w["top"] - 1) * SCALE, (w["bottom"] + 1) * SCALE
    cnt = collections.Counter()
    for x in range(int(x0), int(x1)):
        for y in range(int(y0), int(y1)):
            r, g, b = img.getpixel((x, y))[:3]
            if max(r, g, b) > 240 and min(r, g, b) > 240:
                continue  # white text / background
            if max(r, g, b) < 50:
                continue  # black text
            cnt[(r // 8 * 8, g // 8 * 8, b // 8 * 8)] += 1
    return cnt.most_common(1)[0][0] if cnt else None


def main():
    pdf = pdfplumber.open(PDF)
    out = []
    for pi in (0, 1):
        pg = pdf.pages[pi]
        img = Image.open(RENDERS[pi]).convert("RGB")
        ws = pg.extract_words(extra_attrs=["size"])
        # day-number column anchors
        for w in ws:
            if w["size"] > 10.5 or w["top"] < 115 or w["top"] > 545:
                continue
            col = int((w["x0"] - 28) // 130.4)
            if col < 0 or col > 5:
                continue
            day = round((w["top"] - 121.2) / 13.6) + 1
            rel = w["x0"] - (28 + col * 130.4)
            month = pi * 6 + col + 1
            out.append({
                "m": month, "d": day, "rel": round(rel, 1), "t": w["text"],
                "size": round(w["size"], 1), "c": box_colour(img, w),
            })
    json.dump(out, sys.stdout, ensure_ascii=False)


main()
