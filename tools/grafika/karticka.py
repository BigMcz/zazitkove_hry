"""Graphics of the printed cards with access codes (grafika/karticky/chatgpt-nastaveni.md, docs/07-tisk-karticek.md).

  python tools/grafika/karticka.py rozvrzeni
      Layout references for ChatGPT: grafika/karticky/rozvrzeni-predni.png and rozvrzeni-zadni.png
      (the card with its bleed, 96 x 56 mm, and the zones of the texts, the QR code and the code).

  python tools/grafika/karticka.py priprav <obrazek.png> [--zadni] [--spadavka 3]
      Crops a generated picture to the card with bleed (12:7, from the middle) and writes next to it
      <name>-tisk.png (to upload in the card template editor) and <name>-kontrola.png (the trim, the safe
      zone and, for the back, the places the app prints the QR code and the code over). The pictures are
      made with 3 mm around the trim; the printed graphic has the bleed of the template, 2 mm by default
      (--spadavka for another one, at most 3): the extra millimetres are cut off evenly on each side, the
      trim stays in the same place.

Needs Pillow. Positions in millimetres follow docs/07-tisk-karticek.md: the trim 90 x 50 mm, 3 mm around it,
3 mm safe zone, the default layout of the back (QR x 5 y 6 size 30, code x 60 y 32, serial y 37.5).
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = 'C:/project/MICHAL_ZAZITKOVE_HRY'
OUT = f'{ROOT}/grafika/karticky'

# The generated picture: trim + 3 mm on each side (a reserve), the printed graphic has the bleed of the
# template (2 mm by default, as the printers ask for business cards)
WIDTH_MM, HEIGHT_MM = 96, 56
BLEED_MM = 3
PRINT_BLEED_MM = 2
SAFE_MM = 3
MIN_DPI = 300
MIN_WIDTH_PX = round(WIDTH_MM / 25.4 * MIN_DPI)

# Zones of the back on the graphic (millimetres from its top-left corner = trim position + bleed)
QR = (8, 9, 38, 39)
STEPS = (41, 9, 85, 24)
CODE_FIELD = (41, 26, 85, 42)
CODE_LINE = (46, 30.5, 80, 35)
SERIAL_LINE = (53, 38.5, 73, 40.5)
BAND_TOP = 44
BAND_TEXT = (9, 45.5, 87, 49.5)

# Zones of the front
LOGO = (9, 9, 40, 16)
TITLE = (9, 18, 52, 34)
RIBBON = (20, 42, 76, 49)

FONT = 'C:/Windows/Fonts/arialbd.ttf'
FONT_REGULAR = 'C:/Windows/Fonts/arial.ttf'


def font(size, bold=True):
    return ImageFont.truetype(FONT if bold else FONT_REGULAR, size)


def layouts():
    scale = 17.5  # px per mm, 1680 x 980 px
    for side in ('predni', 'zadni'):
        img = Image.new('RGB', (round(WIDTH_MM * scale), round(HEIGHT_MM * scale)), (236, 236, 232))
        draw = ImageDraw.Draw(img)

        def box(zone, fill, outline, label, size=34, color=(30, 30, 30)):
            x1, y1, x2, y2 = (round(value * scale) for value in zone)
            draw.rectangle((x1, y1, x2, y2), fill=fill, outline=outline, width=4)
            draw.multiline_text(((x1 + x2) / 2, (y1 + y2) / 2), label, fill=color, font=font(size), anchor='mm',
                                align='center', spacing=8)

        if side == 'zadni':
            band = round(BAND_TOP * scale)
            draw.rectangle((0, band, img.width, img.height), fill=(12, 14, 13))
            box(QR, (255, 255, 255), (200, 0, 0), 'QR CODE\nLEAVE EMPTY\n(printed later)', 36, (200, 0, 0))
            box(STEPS, (214, 232, 250), (40, 90, 160), 'STEP 1  scan   ->   STEP 2  play\n(icons + short texts)', 30)
            box(CODE_FIELD, (255, 255, 255), (200, 0, 0), '', 30)
            draw.text((round(63 * scale), round(27.8 * scale)), 'small label "Kód hry"', fill=(30, 30, 30),
                      font=font(24, False), anchor='mm')
            box(CODE_LINE, (255, 230, 230), (200, 0, 0), 'CODE  LEAVE EMPTY', 30, (200, 0, 0))
            box(SERIAL_LINE, (255, 230, 230), (200, 0, 0), 'serial, empty', 20, (200, 0, 0))
            box(BAND_TEXT, (40, 44, 42), (198, 241, 53), 'web address line(s), www.storyhold.cz', 30, (198, 241, 53))
        else:
            box(LOGO, (214, 232, 250), (40, 90, 160), 'LOGO StoryHold', 30)
            box(TITLE, (214, 232, 250), (40, 90, 160), 'GAME TITLE\nsubtitle', 40)
            box(RIBBON, (214, 232, 250), (40, 90, 160), 'ribbon: one short line', 28)
            draw.text((round(70 * scale), round(24 * scale)), 'ILLUSTRATION\n(whole card,\nto every edge)', fill=(90, 90, 90),
                      font=font(40), anchor='mm', align='center')

        # Bleed (cut off), trim and safe zone
        bleed = round(BLEED_MM * scale)
        safe = round((BLEED_MM + SAFE_MM) * scale)
        draw.rectangle((bleed, bleed, img.width - bleed, img.height - bleed), outline=(210, 0, 120), width=5)
        for offset in range(safe, img.width - safe, 30):
            draw.line((offset, safe, offset + 15, safe), fill=(0, 140, 200), width=4)
            draw.line((offset, img.height - safe, offset + 15, img.height - safe), fill=(0, 140, 200), width=4)
        for offset in range(safe, img.height - safe, 30):
            draw.line((safe, offset, safe, offset + 15), fill=(0, 140, 200), width=4)
            draw.line((img.width - safe, offset, img.width - safe, offset + 15), fill=(0, 140, 200), width=4)
        draw.text((bleed + 12, 8), 'pink line = cut, outside is cut off   ·   blue dashed = keep texts and frames inside',
                  fill=(210, 0, 120), font=font(24, False))
        path = f'{OUT}/rozvrzeni-{side}.png'
        img.save(path)
        print(path)


def prepare(path, back, bleed_mm=PRINT_BLEED_MM):
    image = Image.open(path).convert('RGB')
    ratio = WIDTH_MM / HEIGHT_MM
    if image.width / image.height > ratio:
        width, height = round(image.height * ratio), image.height
    else:
        width, height = image.width, round(image.width / ratio)
    left = (image.width - width) // 2
    top = (image.height - height) // 2
    card = image.crop((left, top, left + width, top + height))
    if card.width < MIN_WIDTH_PX:
        print(f'Pozor: jen {card.width} px na šířku, pro 300 DPI je potřeba {MIN_WIDTH_PX} px. Zvětšuji, ostrost to nepřidá.')
        card = card.resize((MIN_WIDTH_PX, round(MIN_WIDTH_PX / ratio)), Image.LANCZOS)
    base = os.path.splitext(path)[0]
    # Smaller bleed: the same trim, less of the picture around it
    extra_mm = BLEED_MM - bleed_mm
    extra_x = round(extra_mm * card.width / WIDTH_MM)
    extra_y = round(extra_mm * card.height / HEIGHT_MM)
    printed = card.crop((extra_x, extra_y, card.width - extra_x, card.height - extra_y))
    print_width_mm = WIDTH_MM - 2 * extra_mm
    dpi = printed.width / (print_width_mm / 25.4)
    printed.save(f'{base}-tisk.png', dpi=(dpi, dpi))
    print(f'{base}-tisk.png  {printed.width} x {printed.height} px = {print_width_mm:g} x {HEIGHT_MM - 2 * extra_mm:g} mm'
          f' (spadávka {bleed_mm:g} mm), {round(dpi)} DPI')

    check = card.copy()
    draw = ImageDraw.Draw(check, 'RGBA')
    scale = card.width / WIDTH_MM
    bleed = BLEED_MM * scale
    safe = (BLEED_MM + SAFE_MM) * scale
    # What the knife cuts off is greyed out
    for rect in ((0, 0, check.width, bleed), (0, check.height - bleed, check.width, check.height),
                 (0, bleed, bleed, check.height - bleed), (check.width - bleed, bleed, check.width, check.height - bleed)):
        draw.rectangle(rect, fill=(255, 255, 255, 150))
    draw.rectangle((bleed, bleed, check.width - bleed, check.height - bleed), outline=(210, 0, 120), width=3)
    draw.rectangle((safe, safe, check.width - safe, check.height - safe), outline=(0, 140, 200), width=2)
    if back:
        for zone in (QR, CODE_LINE, SERIAL_LINE):
            draw.rectangle(tuple(value * scale for value in zone), fill=(255, 0, 0, 70), outline=(200, 0, 0), width=3)
    check.save(f'{base}-kontrola.png')
    print(f'{base}-kontrola.png  růžová = ořez, modrá = bezpečná zóna' + (', červená = QR a kód z aplikace' if back else ''))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    if len(sys.argv) >= 2 and sys.argv[1] == 'rozvrzeni':
        layouts()
    elif len(sys.argv) >= 3 and sys.argv[1] == 'priprav':
        bleed = float(sys.argv[sys.argv.index('--spadavka') + 1].replace(',', '.')) if '--spadavka' in sys.argv else PRINT_BLEED_MM
        if not 0 <= bleed <= BLEED_MM:
            print(f'Spadávka musí být 0 až {BLEED_MM} mm, obrázky se generují s {BLEED_MM} mm.')
            sys.exit(1)
        prepare(sys.argv[2], '--zadni' in sys.argv, bleed)
    else:
        print(__doc__)
        sys.exit(1)
