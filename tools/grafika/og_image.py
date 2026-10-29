"""Link preview picture (Open Graph, 1200x630): the Loket castle from the demo game with the StoryHold logo.

Sources: grafika/st01.png (castle) and grafika/logo/png/StoryHold-logo-2048.png (logo rendered from the SVG).
"""
from PIL import Image, ImageDraw

ROOT = 'C:/project/MICHAL_ZAZITKOVE_HRY'
W, H = 1200, 630
NIGHT = (12, 14, 13)

castle = Image.open(f'{ROOT}/grafika/st01.png').convert('RGB')
scale = W / castle.width
castle = castle.resize((W, round(castle.height * scale)), Image.LANCZOS)
top = (castle.height - H) // 2
img = castle.crop((0, top, W, top + H)).convert('RGBA')

# Darken towards the bottom so that the logo stays readable over the picture
shade = Image.new('RGBA', (W, H))
draw = ImageDraw.Draw(shade)
for y in range(H):
    alpha = round(110 + 120 * (y / H) ** 1.5)
    draw.line([(0, y), (W, y)], fill=NIGHT + (alpha,))
img = Image.alpha_composite(img, shade)

logo = Image.open(f'{ROOT}/grafika/logo/png/StoryHold-logo-2048.png').convert('RGBA')
logo_w = 760
logo = logo.resize((logo_w, round(logo.height * logo_w / logo.width)), Image.LANCZOS)
img.alpha_composite(logo, ((W - logo_w) // 2, H - logo.height - 70))

out = f'{ROOT}/frontend/public/og-image.jpg'
img.convert('RGB').save(out, 'JPEG', quality=85, optimize=True, progressive=True)
print(out)
