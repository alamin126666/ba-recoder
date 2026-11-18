#!/usr/bin/env python3
"""
BA Screen Record — brand banner generator.

Renders the repository artwork straight from code so the visual identity can be
re-produced (and tweaked) without any external design tool. Everything is drawn
with Pillow primitives only: no stock images, no fonts outside the device.

Palette is taken from the app's own `res/values/colors.xml` so the artwork and
the running UI stay the same green.

    python3 tools/make_banners.py

Output (docs/banners/):
    hero.png              1600 x 640   README header
    features.png          1600 x 460   feature strip
    social-preview.png    1280 x 640   GitHub social preview
"""

from __future__ import annotations

import math
import os
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

# --------------------------------------------------------------------------- #
# Brand tokens — mirrored from app/src/main/res/values/colors.xml
# --------------------------------------------------------------------------- #

INK = (7, 21, 12)             # deepest background
INK_2 = (12, 32, 19)          # raised surface
INK_3 = (18, 46, 27)          # card surface

EMERALD = (0, 200, 83)        # emerald_primary  #00C853
EMERALD_BRIGHT = (0, 230, 118)  # emerald_bright #00E676
MINT = (105, 240, 174)        # mint_accent    #69F0AE
MINT_SOFT = (200, 240, 214)

WHITE = (255, 255, 255)
MUTED = (150, 186, 163)
REC_RED = (255, 23, 68)       # record_red     #FF1744
AMBER = (255, 179, 0)

FONT_DIR = "/system/fonts"
F_LATIN = os.path.join(FONT_DIR, "Roboto-Regular.ttf")
F_MONO = os.path.join(FONT_DIR, "CutiveMono.ttf")
F_BENGALI = os.path.join(FONT_DIR, "NotoSansBengali-VF.ttf")

OUT = Path(__file__).resolve().parent.parent / "docs" / "banners"

_font_cache: dict[tuple[str, int], ImageFont.FreeTypeFont] = {}


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    """Load a truetype face, falling back to the Pillow default if absent."""
    key = (path, size)
    if key in _font_cache:
        return _font_cache[key]
    try:
        f = ImageFont.truetype(path, size)
    except OSError:
        f = ImageFont.load_default()
    _font_cache[key] = f
    return f


# --------------------------------------------------------------------------- #
# Drawing helpers
# --------------------------------------------------------------------------- #


def vertical_gradient(size: tuple[int, int], top: tuple[int, int, int],
                      bottom: tuple[int, int, int]) -> Image.Image:
    """Smooth vertical two-stop gradient built from a single-column strip."""
    w, h = size
    strip = Image.new("RGB", (1, h))
    px = strip.load()
    for y in range(h):
        t = y / max(h - 1, 1)
        px[0, y] = (
            int(top[0] + (bottom[0] - top[0]) * t),
            int(top[1] + (bottom[1] - top[1]) * t),
            int(top[2] + (bottom[2] - top[2]) * t),
        )
    return strip.resize((w, h), Image.BILINEAR)


def radial_mask(size: tuple[int, int], center: tuple[float, float],
                radius: float, peak: float = 90) -> Image.Image:
    """
    Soft circular falloff as an 8-bit mask.

    Rendered at quarter resolution and upscaled — a full-resolution radial
    evaluation would cost a million Python iterations for no visible gain.
    """
    w, h = size
    k = 4
    sw, sh = max(w // k, 2), max(h // k, 2)
    small = Image.new("L", (sw, sh), 0)
    px = small.load()
    cx, cy = center[0] / k, center[1] / k
    r = max(radius / k, 1.0)
    for y in range(sh):
        for x in range(sw):
            d = math.hypot(x - cx, y - cy) / r
            if d < 1.0:
                # squared falloff reads more like real light than linear
                px[x, y] = int(peak * (1.0 - d) ** 2.4)
    return small.resize((w, h), Image.BICUBIC)


def apply_glow(base: Image.Image, center, radius, color, peak=90) -> Image.Image:
    """Screen a coloured light source over `base` (screen blend, never clips)."""
    mask = radial_mask(base.size, center, radius, peak)
    glow = Image.new("RGB", base.size, color)
    lit = Image.composite(glow, Image.new("RGB", base.size, (0, 0, 0)), mask)
    return ImageChops.screen(base.convert("RGB"), lit)


def dot_grid(img: Image.Image, spacing: int = 34, alpha: int = 16,
             color=(140, 200, 165)) -> None:
    """Faint engineering grid — adds texture without competing with text."""
    w, h = img.size
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for y in range(spacing // 2, h, spacing):
        for x in range(spacing // 2, w, spacing):
            d.point((x, y), fill=color + (alpha,))
            d.ellipse((x - 1, y - 1, x + 1, y + 1), fill=color + (alpha,))
    img.paste(Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB"), (0, 0))


def text_width(draw: ImageDraw.ImageDraw, s: str, f: ImageFont.FreeTypeFont,
               stroke: int = 0) -> int:
    box = draw.textbbox((0, 0), s, font=f, stroke_width=stroke)
    return box[2] - box[0]


def draw_bold(draw: ImageDraw.ImageDraw, xy, s, f, fill=WHITE, weight=0,
              anchor=None):
    """
    Emulate a bold weight.

    The device ships only Roboto-Regular, so a hairline stroke in the *same*
    colour is used as a synthesised bold — visually indistinguishable at these
    sizes and it keeps the artwork dependency free.
    """
    draw.text(xy, s, font=f, fill=fill, stroke_width=weight,
              stroke_fill=fill, anchor=anchor)


def chip(draw: ImageDraw.ImageDraw, xy, label: str, f, *, fg=MINT,
         border=(38, 92, 58), bg=(16, 40, 24), pad_x=18, pad_y=11,
         radius=999, accent=False):
    """Pill-shaped label. Returns the right edge so chips can be laid inline."""
    x, y = xy
    tw = text_width(draw, label, f)
    h = pad_y * 2 + (f.size + 4)
    w = tw + pad_x * 2 + (18 if accent else 0)
    draw.rounded_rectangle((x, y, x + w, y + h), radius=min(radius, h // 2),
                           fill=bg, outline=border, width=2)
    if accent:
        cy = y + h // 2
        draw.ellipse((x + pad_x - 4, cy - 5, x + pad_x + 6, cy + 5),
                     fill=EMERALD_BRIGHT)
        draw.text((x + pad_x + 16, cy), label, font=f, fill=fg, anchor="lm")
    else:
        draw.text((x + pad_x, y + h // 2), label, font=f, fill=fg, anchor="lm")
    return x + w


def logo_mark(img: Image.Image, box: tuple[int, int, int, int]) -> None:
    """Rounded-square brand mark: record aperture + a draw stroke across it."""
    x0, y0, x1, y1 = box
    size = x1 - x0
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    # gradient tile
    tile = vertical_gradient((size, size), EMERALD_BRIGHT, EMERALD)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1),
                                           radius=int(size * 0.28), fill=255)
    layer.paste(tile, (x0, y0), mask)

    # inner record aperture
    r = size * 0.30
    cx, cy = x0 + size / 2, y0 + size / 2
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=WHITE, width=max(3, size // 26))
    rr = size * 0.095
    d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=WHITE)

    # draw stroke — the "live draw" half of the product
    d.line([(x0 + size * 0.14, y1 - size * 0.20),
            (x0 + size * 0.40, y0 + size * 0.42),
            (x0 + size * 0.62, y1 - size * 0.24)],
           fill=AMBER, width=max(3, size // 30), joint="curve")
    d.ellipse((x0 + size * 0.14 - size * 0.045, y1 - size * 0.245,
               x0 + size * 0.14 + size * 0.045, y1 - size * 0.155), fill=AMBER)

    img.paste(Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB"), (0, 0))


def phone_mockup(img: Image.Image, box: tuple[int, int, int, int]) -> None:
    """Minimal device frame showing the recording state that defines the app."""
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    pad = int(w * 0.045)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    # soft drop shadow
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle(
        (x0 + 10, y0 + 22, x1 + 10, y1 + 22), radius=int(w * 0.13),
        fill=(0, 0, 0, 130))
    sh = sh.filter(ImageFilter.GaussianBlur(26))
    d.bitmap((0, 0), Image.new("L", (1, 1)))
    layer = Image.alpha_composite(sh, layer)
    d = ImageDraw.Draw(layer)

    # frame
    d.rounded_rectangle((x0, y0, x1, y1), radius=int(w * 0.13),
                        fill=(9, 26, 15), outline=(46, 96, 62), width=3)

    # screen
    sx0, sy0, sx1, sy1 = x0 + pad, y0 + pad, x1 - pad, y1 - pad
    screen = vertical_gradient((sx1 - sx0, sy1 - sy0), (16, 48, 28), (8, 24, 15))
    smask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(smask).rounded_rectangle(
        (0, 0, screen.size[0] - 1, screen.size[1] - 1),
        radius=int(w * 0.10), fill=255)
    layer.paste(screen, (sx0, sy0), smask)
    d = ImageDraw.Draw(layer)

    # subtle grid inside the screen
    for gy in range(sy0, sy1, 26):
        d.line([(sx0, gy), (sx1, gy)], fill=(28, 66, 42, 60))
    for gx in range(sx0, sx1, 26):
        d.line([(gx, sy0), (gx, sy1)], fill=(28, 66, 42, 60))

    # REC pill
    rx, ry = sx0 + w * 0.10, sy0 + h * 0.085
    pw, ph = w * 0.40, h * 0.072
    d.rounded_rectangle((rx, ry, rx + pw, ry + ph), radius=ph / 2,
                        fill=(38, 10, 16, 235), outline=(120, 32, 48), width=2)
    d.ellipse((rx + pw * 0.13, ry + ph * 0.32, rx + pw * 0.13 + ph * 0.36,
               ry + ph * 0.32 + ph * 0.36), fill=REC_RED)
    ftimer = font(F_MONO, int(ph * 0.52))
    d.text((rx + pw * 0.32, ry + ph / 2), "00:12:34", font=ftimer,
           fill=(255, 210, 218), anchor="lm")

    # 2K badge on the other side
    tx1 = sx1 - w * 0.10
    d.text((tx1, ry + ph / 2), "2K", font=font(F_LATIN, int(ph * 0.62)),
           fill=MINT, anchor="rm", stroke_width=1, stroke_fill=MINT)

    # waveform
    fw_y = sy0 + h * 0.30
    bars = 26
    bw = (sx1 - sx0 - 2 * w * 0.10) / (bars * 1.8)
    bx = sx0 + w * 0.10
    for i in range(bars):
        amp = (math.sin(i * 0.72) * 0.5 + math.sin(i * 1.9) * 0.28 + 0.42)
        amp = max(0.12, min(1.0, abs(amp)))
        bh = h * 0.085 * amp
        col = EMERALD_BRIGHT if i < bars * 0.62 else (44, 104, 66)
        d.rounded_rectangle((bx, fw_y - bh, bx + bw, fw_y + bh),
                            radius=bw / 2, fill=col)
        bx += bw * 1.8

    # annotation stroke (the live-draw layer)
    pts = [(sx0 + w * 0.12, sy0 + h * 0.50),
           (sx0 + w * 0.34, sy0 + h * 0.42),
           (sx0 + w * 0.55, sy0 + h * 0.55),
           (sx0 + w * 0.82, sy0 + h * 0.44)]
    d.line(pts, fill=AMBER, width=max(4, int(w * 0.016)), joint="curve")
    ax, ay = pts[-1]
    d.polygon([(ax, ay), (ax - w * 0.075, ay - h * 0.050),
               (ax - w * 0.052, ay + h * 0.036)], fill=AMBER)

    # bottom control row
    by = sy1 - h * 0.115
    for i, cx in enumerate((sx0 + w * 0.22, sx0 + w * 0.5, sx0 + w * 0.78)):
        rr = w * (0.062 if i == 1 else 0.046)
        if i == 1:
            d.ellipse((cx - rr, by - rr, cx + rr, by + rr), fill=REC_RED)
            d.rounded_rectangle((cx - rr * 0.32, by - rr * 0.32,
                                 cx + rr * 0.32, by + rr * 0.32),
                                radius=rr * 0.14, fill=WHITE)
        else:
            d.ellipse((cx - rr, by - rr, cx + rr, by + rr),
                      outline=(70, 132, 90), width=3)
            d.ellipse((cx - rr * 0.34, by - rr * 0.34, cx + rr * 0.34,
                       by + rr * 0.34), fill=(70, 132, 90))

    img.paste(Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB"), (0, 0))


def base_canvas(size: tuple[int, int]) -> Image.Image:
    """Dark green gradient + two light sources + faint grid."""
    img = vertical_gradient(size, (13, 36, 22), INK)
    img = apply_glow(img, (size[0] * 0.06, size[1] * 0.02), size[0] * 0.52,
                     EMERALD, 74)
    img = apply_glow(img, (size[0] * 0.96, size[1] * 1.02), size[0] * 0.46,
                     (0, 168, 96), 50)
    dot_grid(img, spacing=32, alpha=13)
    return img


def build_hero() -> Image.Image:
    W, H = 1600, 640
    img = base_canvas((W, H))
    d = ImageDraw.Draw(img)

    # accent hairline along the very top
    d.rectangle((0, 0, W, 3), fill=EMERALD)
    for i in range(W):
        d.line([(i, 0), (i, 3)],
               fill=(int(0 + 0 * i / W), int(230 - 90 * i / W), int(118 - 40 * i / W)))

    logo_mark(img, (76, 68, 152, 144))
    d = ImageDraw.Draw(img)

    f_brand = font(F_LATIN, 27)
    draw_bold(d, (170, 88), "BA  STUDIO", f_brand, fill=MINT, weight=1)
    d.text((170, 122), "ANDROID  ·  KOTLIN  ·  JETPACK COMPOSE",
           font=font(F_MONO, 15), fill=(112, 158, 130))

    # headline
    f_title = font(F_LATIN, 96)
    draw_bold(d, (76, 206), "BA Screen Record", f_title, fill=WHITE, weight=3)

    # gradient rule under the headline
    for i in range(300):
        t = i / 299
        col = (int(EMERALD_BRIGHT[0] * (1 - t) + MINT[0] * t),
               int(EMERALD_BRIGHT[1] * (1 - t) + MINT[1] * t),
               int(EMERALD_BRIGHT[2] * (1 - t) + MINT[2] * t))
        d.line([(80 + i, 330), (80 + i, 334)], fill=col)

    f_tag = font(F_LATIN, 32)
    d.text((80, 356), "2K screen recorder with a live draw overlay",
           font=f_tag, fill=MINT_SOFT)
    d.text((80, 398), "Record, annotate and narrate in one pass.",
           font=font(F_LATIN, 24), fill=MUTED)

    # chips
    f_chip = font(F_LATIN, 19)
    x = 80
    y = 462
    for label in ("2K QHD", "60 FPS", "H.264 / H.265", "Mic + System Audio",
                  "Signed Release"):
        nx = chip(d, (x, y), label, f_chip,
                  fg=(206, 236, 216), accent=(label == "Signed Release"))
        x = nx + 12
        if x > 700:
            x = 80
            y += 46

    # right-hand device
    phone_mockup(img, (1140, 132, 1470, 640 - 34))

    # footer rule + build stamp
    d = ImageDraw.Draw(img)
    d.line([(76, 596), (1524, 596)], fill=(30, 74, 48), width=1)
    d.text((76, 610), "v1.0.0", font=font(F_MONO, 15), fill=(96, 140, 112))
    d.text((1524, 610), "APK SIGNED · v2 + v3",
           font=font(F_MONO, 15), fill=(96, 140, 112), anchor="ra")

    return img


# --------------------------------------------------------------------------- #
# Banner 2 — feature strip
# --------------------------------------------------------------------------- #

FEATURES = [
    ("2K / 1440p", "Up to 2K QHD capture", (0, 230, 118)),
    ("60 FPS", "Smooth, frame-accurate motion", (105, 240, 174)),
    ("Live Draw", "Annotate while recording", (255, 179, 0)),
    ("Mic + System", "Narrate over internal audio", (0, 200, 83)),
]


def feature_icon(d: ImageDraw.ImageDraw, kind: str, box, col):
    x0, y0, x1, y1 = box
    w = x1 - x0
    c = w / 2
    lw = max(3, int(w * 0.075))
    if kind == "res":
        for i, (s, a) in enumerate(((0.34, 255), (0.55, 190), (0.76, 130))):
            o = c * s
            d.rounded_rectangle((x0 + c - o, y0 + c - o, x0 + c + o, y0 + c + o),
                                radius=o * 0.25, outline=col + (a,), width=lw)
    elif kind == "fps":
        d.arc((x0, y0, x1, y1), 200, 340, fill=col, width=lw)
        d.ellipse((x0 + c - lw, y0 + c - lw, x0 + c + lw, y0 + c + lw), fill=col)
        d.line([(x0 + c, y0 + c), (x1 - w * 0.18, y0 + c * 0.62)], fill=col, width=lw)
    elif kind == "draw":
        d.line([(x0 + w * 0.10, y1 - w * 0.22), (x0 + w * 0.40, y0 + w * 0.22),
                (x0 + w * 0.74, y1 - w * 0.30)], fill=col, width=lw, joint="curve")
        d.polygon([(x1 - w * 0.06, y0 + w * 0.46), (x1 - w * 0.40, y0 + w * 0.20),
                   (x1 - w * 0.34, y0 + w * 0.58)], fill=col)
    elif kind == "mic":
        d.rounded_rectangle((x0 + c - w * 0.16, y0 + w * 0.10,
                             x0 + c + w * 0.16, y0 + w * 0.58),
                            radius=w * 0.16, fill=col)
        d.arc((x0 + w * 0.20, y0 + w * 0.30, x1 - w * 0.20, y1 - w * 0.10),
              0, 180, fill=col, width=lw)
        d.line([(x0 + c, y1 - w * 0.22), (x0 + c, y1 - w * 0.04)], fill=col, width=lw)
        d.line([(x0 + c - w * 0.14, y1 - w * 0.04),
                (x0 + c + w * 0.14, y1 - w * 0.04)], fill=col, width=lw)


def build_features() -> Image.Image:
    W, H = 1600, 460
    img = base_canvas((W, H))
    d = ImageDraw.Draw(img)

    f_eyebrow = font(F_MONO, 15)
    d.text((80, 52), "WHAT IT DOES", font=f_eyebrow, fill=(112, 158, 130))
    draw_bold(d, (80, 82), "Built for people who explain things on screen",
              font(F_LATIN, 34), fill=WHITE, weight=1)

    card_w, card_h = 350, 200
    gap = (W - 160 - card_w * 4) // 3
    top = 158

    for i, (title, sub, col) in enumerate(FEATURES):
        x = 80 + i * (card_w + gap)
        y = top
        d.rounded_rectangle((x, y, x + card_w, y + card_h), radius=20,
                            fill=(14, 38, 23), outline=(32, 80, 50), width=2)
        # accent corner tick
        d.rounded_rectangle((x, y, x + 5, y + 42), radius=3, fill=col + (255,))
        ic = (x + 26, y + 26, x + 26 + 62, y + 26 + 62)
        d.rounded_rectangle(ic, radius=16, fill=(9, 26, 15), outline=(34, 84, 54), width=2)
        feature_icon(d, ("res", "fps", "draw", "mic")[i],
                     (ic[0] + 11, ic[1] + 11, ic[2] - 11, ic[3] - 11), col)

        draw_bold(d, (x + 26, y + 108), title, font(F_LATIN, 29), fill=WHITE, weight=1)
        d.text((x + 26, y + 148), sub, font=font(F_LATIN, 17), fill=MUTED)

    return img


# --------------------------------------------------------------------------- #
# Banner 3 — social preview
# --------------------------------------------------------------------------- #


def build_social() -> Image.Image:
    W, H = 1280, 640
    img = vertical_gradient((W, H), (14, 40, 24), (5, 15, 9))
    img = apply_glow(img, (W / 2, H * 0.34), W * 0.62, EMERALD, 86)
    img = apply_glow(img, (W * 0.94, H * 1.0), W * 0.42, (0, 168, 96), 54)
    dot_grid(img, spacing=30, alpha=12)
    d = ImageDraw.Draw(img)

    d.rectangle((0, 0, W, 4), fill=EMERALD_BRIGHT)

    logo_mark(img, (W // 2 - 62, 92, W // 2 + 62, 216))
    d = ImageDraw.Draw(img)

    title = "BA Screen Record"
    f_t = font(F_LATIN, 74)
    tw = text_width(d, title, f_t, stroke=3)
    draw_bold(d, ((W - tw) / 2, 252), title, f_t, fill=WHITE, weight=3)

    sub = "2K screen recording with a live draw overlay"
    f_s = font(F_LATIN, 26)
    sw = text_width(d, sub, f_s)
    d.text(((W - sw) / 2, 352), sub, font=f_s, fill=MINT_SOFT)

    # rule
    d.line([(W // 2 - 150, 404), (W // 2 + 150, 404)], fill=(40, 96, 62), width=2)
    d.ellipse((W // 2 - 4, 400, W // 2 + 4, 408), fill=EMERALD_BRIGHT)

    f_chip = font(F_LATIN, 18)
    labels = ["2K QHD", "60 FPS", "Live Draw", "Mic Audio", "H.265"]
    widths = [text_width(d, l, f_chip) + 34 for l in labels]
    total = sum(widths) + 12 * (len(labels) - 1)
    x = (W - total) / 2
    for l, w in zip(labels, widths):
        chip(d, (x, 436), l, f_chip, fg=(206, 236, 216), pad_x=17, pad_y=10)
        x += w + 12

    f_ft = font(F_MONO, 15)
    ft = "github.com/alamin126666/ba-recoder"
    d.text(((W - text_width(d, ft, f_ft)) / 2, 552), ft, font=f_ft, fill=(96, 140, 112))

    return img


# --------------------------------------------------------------------------- #
# Entry point
# --------------------------------------------------------------------------- #


def save(img: Image.Image, name: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / name
    img.save(path, "PNG", optimize=True)
    kb = path.stat().st_size / 1024
    print(f"  {name:22} {img.size[0]}x{img.size[1]}  {kb:6.1f} KB")


def main() -> None:
    print("Rendering BA Screen Record banners")
    save(build_hero(), "hero.png")
    save(build_features(), "features.png")
    save(build_social(), "social-preview.png")
    print(f"\nWritten to {OUT}")


if __name__ == "__main__":
    main()