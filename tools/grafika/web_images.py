"""Pictures of the games for the web: WebP 900 px wide (on a phone they are about 480 px wide, 900 px is sharp
enough also on high-density displays), about 60 % smaller than the 1200 px JPG. The game downloads all of them
into the phone for playing without a signal, so every kilobyte counts.

Usage: python tools/grafika/web_images.py SOURCE... OUT_DIR   (needs Pillow)
  e.g. python tools/grafika/web_images.py grafika/st01.png grafika/st02.png frontend/public/images/demo
  writes OUT_DIR/st01.webp, OUT_DIR/st02.webp
"""
import os
import sys

from PIL import Image

WIDTH = 900
QUALITY = 78


def save_web(image, path):
    """Saves the picture as WebP WIDTH px wide (smaller pictures stay as they are)."""
    image = image.convert('RGB')
    if image.width > WIDTH:
        image = image.resize((WIDTH, round(image.height * WIDTH / image.width)), Image.LANCZOS)
    image.save(path, 'WEBP', quality=QUALITY, method=6)
    print(path, image.size, os.path.getsize(path) // 1024, 'kB')


if __name__ == '__main__':
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    *sources, out_dir = sys.argv[1:]
    for source in sources:
        name = os.path.splitext(os.path.basename(source))[0] + '.webp'
        save_web(Image.open(source), os.path.join(out_dir, name))
