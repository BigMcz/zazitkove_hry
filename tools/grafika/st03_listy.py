"""Writes the four chronicle entries onto the blank pages of grafika/st03.png (krok 3 dema).

Usage: python tools/grafika/st03_listy.py  (needs Pillow; output frontend/public/images/demo/st03.webp)
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

from web_images import save_web

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
FONT = os.path.join(os.path.dirname(__file__), 'Caveat.ttf')  # OFL, github.com/google/fonts/tree/main/ofl/caveat
OUT = sys.argv[1] if len(sys.argv) > 1 else f'{ROOT}/frontend/public/images/demo/st03.webp'

INK = (52, 34, 20)

# Page centre (x, y), rotation in degrees (counter-clockwise), letter, lines.
# The pages are deliberately not in date order: the player has to sort them.
PAGES = [
    ((352, 412), 9.0, 'C', ['Rozsudek', 'byl vykonán', '19. srpna 1822']),
    ((790, 462), -6.5, 'A', ['Soud vynesl', 'rozsudek smrti', '24. května 1822']),
    ((378, 752), 13.0, 'D', ['Kat navštívil', 'odsouzeného v cele', '18. srpna 1822']),
    ((868, 815), -13.0, 'B', ['Hains zavraždil', 'Matyáše Gerstnera', '16. dubna 1822']),
]


def font(size, weight):
    f = ImageFont.truetype(FONT, size)
    try:
        f.set_variation_by_axes([weight])
    except OSError:
        pass
    return f


def page_layer(letter, lines):
    layer = Image.new('RGBA', (420, 320), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    big = font(100, 700)
    text = font(47, 600)
    draw.text((210, 64), letter, font=big, fill=INK + (235,), anchor='mm')
    for i, line in enumerate(lines):
        draw.text((210, 148 + i * 50), line, font=text, fill=INK + (225,), anchor='mm')
    return layer


img = Image.open(f'{ROOT}/grafika/st03.png').convert('RGBA')
for (cx, cy), angle, letter, lines in PAGES:
    layer = page_layer(letter, lines).rotate(angle, resample=Image.BICUBIC, expand=True)
    img.alpha_composite(layer, (cx - layer.width // 2, cy - layer.height // 2))

out = img.convert('RGB')
out.thumbnail((1200, 900), Image.LANCZOS)
save_web(out, OUT)
