#!/usr/bin/env python3
"""
Zorix Chess - brand asset generator.

Builds every bitmap the Android app needs from the original Zorix logo
(`zorix_logo_source.png`), and renders the "ZORIX CHESS" wordmark that
replaces the old "ZORIXCODE" text:

  * app/src/main/res/drawable-nodpi/zc_emblem.png      (hi-res emblem used in the UI)
  * app/src/main/res/drawable-*dpi/splash_icon.png      (Android 12+ system splash)
  * app/src/main/res/mipmap-*dpi/ic_launcher_*.png      (adaptive launcher icon layers)
  * branding/out/*.png                                  (store / README artwork)

  * app/src/main/res/drawable-nodpi/piece_*.png          (chess pieces, from pieces/*.svg)

Requirements:  pip install pillow numpy cairosvg
Run from anywhere:  python android/branding/generate_assets.py
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
RES = HERE.parent / "app" / "src" / "main" / "res"
OUT = HERE / "out"
SOURCE = HERE / "zorix_logo_source.png"
FONT = HERE / "fonts" / "Orbitron-Variable.ttf"

# Emblem ("ZC" mark) bounds inside the source artwork, with a little breathing room.
EMBLEM_BOX = (588, 356, 934, 569)

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

RED_STOPS = [(0.0, (255, 84, 84)), (0.45, (226, 12, 24)), (1.0, (122, 0, 10))]
GRAPHITE_STOPS = [(0.0, (128, 128, 136)), (0.5, (78, 78, 84)), (1.0, (40, 40, 44))]
SILVER_STOPS = [(0.0, (250, 250, 252)), (0.55, (190, 190, 198)), (1.0, (120, 120, 130))]


def load_emblem() -> Image.Image:
    src = Image.open(SOURCE).convert("RGBA")
    emblem = src.crop(EMBLEM_BOX)
    # Trim to the exact visible pixels, then add a uniform transparent margin.
    bbox = emblem.getchannel("A").point(lambda a: 255 if a > 6 else 0).getbbox()
    emblem = emblem.crop(bbox)
    pad = 6
    canvas = Image.new("RGBA", (emblem.width + pad * 2, emblem.height + pad * 2), (0, 0, 0, 0))
    canvas.alpha_composite(emblem, (pad, pad))
    return canvas


def upscale(img: Image.Image, factor: float) -> Image.Image:
    """High quality upscale in premultiplied space so edges don't get dark fringes."""
    arr = np.asarray(img).astype(np.float32) / 255.0
    rgb, a = arr[..., :3], arr[..., 3:4]
    premul = np.concatenate([rgb * a, a], axis=-1)
    size = (round(img.width * factor), round(img.height * factor))
    channels = [
        Image.fromarray((premul[..., i] * 255).astype(np.uint8)).resize(size, Image.LANCZOS)
        for i in range(4)
    ]
    out = np.stack([np.asarray(c).astype(np.float32) / 255.0 for c in channels], axis=-1)
    alpha = out[..., 3:4]
    color = np.where(alpha > 1e-4, out[..., :3] / np.maximum(alpha, 1e-4), 0)
    merged = np.concatenate([np.clip(color, 0, 1), alpha], axis=-1)
    result = Image.fromarray((merged * 255).round().astype(np.uint8), "RGBA")
    rgb_img = result.convert("RGB").filter(ImageFilter.UnsharpMask(radius=1.6, percent=70, threshold=2))
    rgb_img.putalpha(result.getchannel("A"))
    return rgb_img


def fit_width(img: Image.Image, width: int) -> Image.Image:
    height = round(img.height * width / img.width)
    return img.resize((width, height), Image.LANCZOS)


def centered(canvas_size: int, img: Image.Image, dy: int = 0) -> Image.Image:
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    canvas.alpha_composite(img, ((canvas_size - img.width) // 2, (canvas_size - img.height) // 2 + dy))
    return canvas


def gradient_fill(mask: Image.Image, stops) -> Image.Image:
    """Colors `mask` (L) with a vertical gradient limited to the glyph bounds."""
    w, h = mask.size
    bbox = mask.getbbox() or (0, 0, w, h)
    top, bottom = bbox[1], max(bbox[3], bbox[1] + 1)
    ys = np.clip((np.arange(h) - top) / (bottom - top), 0, 1)
    pos = [s[0] for s in stops]
    cols = np.array([s[1] for s in stops], dtype=np.float32)
    column = np.stack([np.interp(ys, pos, cols[:, i]) for i in range(3)], axis=-1)
    rgb = np.repeat(column[:, None, :], w, axis=1).astype(np.uint8)
    img = Image.fromarray(rgb, "RGB").convert("RGBA")
    img.putalpha(mask)
    return img


def wordmark(height: int, second_word_stops, tracking: float = 0.42, stretch: float = 1.12) -> Image.Image:
    """Renders 'ZORIX' + 'CHESS' in the style of the original ZORIXCODE lettering."""
    font = ImageFont.truetype(str(FONT), size=int(height * 1.38))
    font.set_variation_by_axes([900])
    words = [("ZORIX", RED_STOPS), ("CHESS", second_word_stops)]
    probe = ImageDraw.Draw(Image.new("L", (1, 1)))
    gap = height * tracking
    word_gap = height * 0.55
    widths = []
    for word, _ in words:
        widths.append(sum(probe.textlength(ch, font=font) for ch in word) + gap * (len(word) - 1))
    total_w = int(sum(widths) + word_gap) + 8
    canvas_h = int(height * 2.2)
    out = Image.new("RGBA", (total_w, canvas_h), (0, 0, 0, 0))
    x = 4.0
    for (word, stops), w in zip(words, widths):
        mask = Image.new("L", out.size, 0)
        d = ImageDraw.Draw(mask)
        cx = x
        for ch in word:
            d.text((cx, canvas_h / 2), ch, font=font, fill=255, anchor="lm")
            cx += probe.textlength(ch, font=font) + gap
        out.alpha_composite(gradient_fill(mask, stops))
        x += w + word_gap
    out = out.crop(out.getbbox())
    out = out.resize((round(out.width * stretch), out.height), Image.LANCZOS)
    return out


def underline(width: int, thickness: int) -> Image.Image:
    xs = np.linspace(-1, 1, width)
    alpha = np.clip(1.0 - np.abs(xs) ** 3, 0, 1)
    ys = np.linspace(-1, 1, thickness)
    profile = np.clip(1.0 - np.abs(ys) ** 2, 0.15, 1)
    a = (np.outer(profile, alpha) * 255).astype(np.uint8)
    rgb = np.zeros((thickness, width, 3), dtype=np.uint8)
    rgb[..., 0], rgb[..., 1], rgb[..., 2] = 214, 18, 28
    img = Image.fromarray(rgb, "RGB").convert("RGBA")
    img.putalpha(Image.fromarray(a, "L"))
    return img


def full_logo(emblem: Image.Image, dark: bool) -> Image.Image:
    emblem_w = 660
    em = fit_width(emblem, emblem_w)
    wm = wordmark(50, SILVER_STOPS if dark else GRAPHITE_STOPS)
    line = underline(int(wm.width * 0.97), 7)
    pad_x, pad_y = 120, 110
    width = max(em.width, wm.width) + pad_x * 2
    height = pad_y * 2 + em.height + 48 + wm.height + 36 + line.height
    bg = (14, 14, 17, 255) if dark else (0, 0, 0, 0)
    canvas = Image.new("RGBA", (width, height), bg)
    if dark:
        canvas.alpha_composite(radial_glow(width, height))
    y = pad_y
    canvas.alpha_composite(em, ((width - em.width) // 2, y))
    y += em.height + 48
    shadow = wm.copy()
    shadow.putalpha(wm.getchannel("A").point(lambda a: a * 0.35).filter(ImageFilter.GaussianBlur(4)))
    black = Image.new("RGBA", wm.size, (0, 0, 0, 0))
    black.putalpha(shadow.getchannel("A"))
    canvas.alpha_composite(black, ((width - wm.width) // 2, y + 5))
    canvas.alpha_composite(wm, ((width - wm.width) // 2, y))
    y += wm.height + 36
    canvas.alpha_composite(line, ((width - line.width) // 2, y))
    return canvas


def radial_glow(width: int, height: int) -> Image.Image:
    yy, xx = np.mgrid[0:height, 0:width].astype(np.float32)
    cx, cy = width / 2, height * 0.42
    r = np.sqrt(((xx - cx) / (width * 0.6)) ** 2 + ((yy - cy) / (height * 0.6)) ** 2)
    glow = np.clip(1 - r, 0, 1) ** 2
    rgb = np.zeros((height, width, 3), dtype=np.uint8)
    rgb[..., 0], rgb[..., 1], rgb[..., 2] = 60, 12, 16
    img = Image.fromarray(rgb, "RGB").convert("RGBA")
    img.putalpha(Image.fromarray((glow * 150).astype(np.uint8), "L"))
    return img


def monochrome(img: Image.Image) -> Image.Image:
    alpha = img.getchannel("A").point(lambda a: 255 if a > 90 else int(a * 255 / 90))
    white = Image.new("RGBA", img.size, (255, 255, 255, 0))
    white.putalpha(alpha)
    return white


def render_pieces() -> None:
    """Rasterises the cburnett SVG pieces at 256 px (sharp even on large tablets)."""
    import io

    import cairosvg

    for svg in sorted((HERE / "pieces").glob("*.svg")):
        png = cairosvg.svg2png(url=str(svg), output_width=256, output_height=256)
        img = Image.open(io.BytesIO(png)).convert("RGBA")
        save(img, RES / "drawable-nodpi" / f"piece_{svg.stem.lower()}.png")


def save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)
    print(f"  {path.relative_to(HERE.parent)}  {img.width}x{img.height}")


def main() -> None:
    emblem = load_emblem()
    hires = upscale(emblem, 2.0)
    print("Emblem:")
    save(hires, RES / "drawable-nodpi" / "zc_emblem.png")

    print("Adaptive launcher icon layers (108dp canvas, emblem 62dp wide):")
    for name, scale in DENSITIES.items():
        canvas = round(108 * scale)
        em = fit_width(hires, round(62 * scale))
        fg = centered(canvas, em)
        save(fg, RES / f"mipmap-{name}" / "ic_launcher_foreground.png")
        save(monochrome(fg), RES / f"mipmap-{name}" / "ic_launcher_monochrome.png")

    print("System splash icon (288dp canvas, emblem 150dp wide):")
    for name, scale in DENSITIES.items():
        em = fit_width(hires, round(150 * scale))
        save(centered(round(288 * scale), em), RES / f"drawable-{name}" / "splash_icon.png")

    print("Chess pieces:")
    render_pieces()

    print("Store / README artwork:")
    save(full_logo(emblem, dark=False), OUT / "zorix_chess_logo_light.png")
    save(full_logo(emblem, dark=True), OUT / "zorix_chess_logo_dark.png")
    save(wordmark(56, GRAPHITE_STOPS), OUT / "zorix_chess_wordmark.png")

    icon = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    icon.alpha_composite(Image.new("RGBA", (512, 512), (18, 18, 22, 255)))
    icon.alpha_composite(radial_glow(512, 512))
    icon.alpha_composite(centered(512, fit_width(hires, 330)))
    save(icon.convert("RGB"), OUT / "play_store_icon_512.png")


if __name__ == "__main__":
    main()
