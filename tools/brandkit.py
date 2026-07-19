#!/usr/bin/env python3
"""Shared brand-kit generator for the Elements immersion collection.

Produces a cohesive icon (128), logo (480x160) and banner (960x320) per mod. Every mod shares the
same composition (vertical gradient, drifting motif bands, glyph left, name right) but carries its
own elemental palette and glyph: a gust swirl (windy / air), a meteor streak (starfall / sky), a
trail of boot prints (footfall / earth) and a waterfall with a rainbow (cascade / water).
Pillow only. Called from each mod's tools/generate.py and from the packaging step.
"""
import math, os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# kind -> (top colour, bottom colour, band colour, accent)
PALETTES = {
    "gust":      ((196, 224, 230), (86, 138, 152),  (240, 250, 252), (255, 255, 255)),
    "meteor":    ((10, 14, 42),    (30, 24, 66),    (150, 160, 220), (255, 224, 160)),
    "footprint": ((150, 110, 78),  (88, 62, 44),    (226, 202, 164), (244, 232, 210)),
    "waterfall": ((24, 78, 104),   (10, 42, 64),    (198, 232, 244), (232, 248, 252)),
}


def _font(size):
    for name in ("segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf", "Arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def _vgrad(w, h, top, bot):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        row = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3))
        for x in range(w):
            px[x, y] = row
    return img


def _band(draw, w, y0, amp, wl, color, thick, phase=0.0):
    pts = []
    x = -4
    while x <= w + 4:
        yy = y0 + math.sin((x / wl) + phase) * amp
        pts.append((x, yy))
        x += 3
    draw.line(pts, fill=color, width=thick, joint="curve")


def _glyph(kind, s):
    """An RGBA glyph image of size s x s for the given elemental motif."""
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    _, _, band, accent = PALETTES[kind]
    if kind == "gust":  # three sweeping wind curls + two loose leaves
        for i, (yy, r0, a) in enumerate([(0.30, 0.34, 255), (0.52, 0.42, 235), (0.74, 0.30, 210)]):
            y = s * yy
            d.arc([s*0.06, y - s*r0*0.5, s*0.06 + s*r0*2.2, y + s*r0*0.5],
                  200, 355, fill=(255, 255, 255, a), width=max(3, s // 22))
            # curl tail: a small spiral hook at the end
            cx = s*0.06 + s*r0*2.2
            d.arc([cx - s*0.14, y - s*0.14, cx + s*0.06, y + s*0.06], 0, 250,
                  fill=(255, 255, 255, max(0, a - 40)), width=max(2, s // 30))
        for (lx, ly, rot) in [(0.68, 0.26, 0.6), (0.80, 0.62, -0.4)]:
            # a simple leaf: a rotated rhombus
            lw = s * 0.13
            x0, y0 = s * lx, s * ly
            d.polygon([(x0, y0),
                       (x0 + lw*math.cos(rot) - lw*0.35*math.sin(rot), y0 + lw*math.sin(rot) + lw*0.35*math.cos(rot)),
                       (x0 + 2*lw*math.cos(rot), y0 + 2*lw*math.sin(rot)),
                       (x0 + lw*math.cos(rot) + lw*0.35*math.sin(rot), y0 + lw*math.sin(rot) - lw*0.35*math.cos(rot))],
                      fill=(126, 178, 96, 240))
    elif kind == "meteor":  # a bright tapered streak with a glowing head + companion stars
        x0, y0, x1, y1 = s*0.88, s*0.10, s*0.24, s*0.66
        # tapered streak: many short segments, thin/dim at the tail -> thick/bright at the head
        segs = 26
        for i in range(segs):
            t0, t1 = i / segs, (i + 1) / segs
            ax, ay = x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0
            bx, by = x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1
            a = int(40 + 215 * t1**1.5)
            wdt = max(1, int(s * 0.008 + s * 0.030 * t1))
            col = (255, int(230 + 18 * t1), int(170 + 60 * t1))
            d.line([(ax, ay), (bx, by)], fill=col + (a,), width=wdt)
        # head glow: layered soft discs
        r = s * 0.075
        d.ellipse([x1 - r*2.1, y1 - r*2.1, x1 + r*2.1, y1 + r*2.1], fill=(255, 236, 190, 60))
        d.ellipse([x1 - r*1.4, y1 - r*1.4, x1 + r*1.4, y1 + r*1.4], fill=(255, 240, 200, 120))
        d.ellipse([x1 - r, y1 - r, x1 + r, y1 + r], fill=(255, 250, 232, 255))
        for (sx, sy, rr) in [(0.30, 0.20, 0.018), (0.56, 0.44, 0.014), (0.74, 0.58, 0.02),
                             (0.18, 0.42, 0.013), (0.62, 0.12, 0.016)]:
            d.ellipse([s*sx - s*rr, s*sy - s*rr, s*sx + s*rr, s*sy + s*rr], fill=(226, 232, 255, 235))
    elif kind == "footprint":  # a curving trail of boot prints, fading back
        steps = [(0.24, 0.82, -18, 255), (0.46, 0.66, -10, 235), (0.36, 0.48, -2, 205),
                 (0.58, 0.34, 6, 170), (0.50, 0.18, 12, 135)]
        for i, (fx, fy, rot, a) in enumerate(steps):
            side = 1 if i % 2 == 0 else -1
            _boot(d, s*fx + side * s*0.005, s*fy, s * (0.16 - i*0.008), rot, (*band, a))
    elif kind == "waterfall":  # a falling column, plunge mist and a small rainbow
        # cliff hint
        d.polygon([(s*0.16, s*0.10), (s*0.34, s*0.10), (s*0.34, s*0.58), (s*0.16, s*0.66)],
                  fill=(16, 52, 74, 255))
        # the fall: vertical streaks
        for i, fx in enumerate((0.36, 0.42, 0.48)):
            a = 235 - i*30
            d.line([(s*fx, s*0.10), (s*fx, s*0.66)], fill=band + (a,), width=max(3, int(s*(0.045 - i*0.008))))
        # plunge mist puffs
        for (mx, my, rr, a) in [(0.42, 0.72, 0.13, 210), (0.55, 0.76, 0.10, 180),
                                (0.30, 0.78, 0.09, 170), (0.47, 0.84, 0.15, 140)]:
            d.ellipse([s*mx - s*rr, s*my - s*rr, s*mx + s*rr, s*my + s*rr], fill=(236, 248, 252, a))
        # small rainbow arc over the mist
        cols = [(255, 96, 96), (255, 196, 90), (250, 244, 120), (120, 220, 130), (110, 170, 250), (170, 120, 240)]
        for i, col in enumerate(cols):
            r = s*0.30 - i * max(2, s // 52)
            d.arc([s*0.60 - r, s*0.66 - r, s*0.60 + r, s*0.66 + r], 195, 345,
                  fill=col + (200,), width=max(2, s // 48))
    return img


def _boot(d, x, y, size, rot_deg, fill):
    """A single boot print (sole + heel) rotated by rot_deg, toe toward -y' (walking up-image)."""
    rot = math.radians(rot_deg)

    def T(px, py):
        return (x + px*math.cos(rot) - py*math.sin(rot), y + px*math.sin(rot) + py*math.cos(rot))

    w, l = size*0.62, size
    # sole: an ellipse squashed slightly at the arch
    pts = []
    for i in range(24):
        t = i / 24.0 * math.pi * 2
        rx = w*0.5 * (1.0 - 0.12*math.cos(t*2))
        pts.append(T(math.cos(t)*rx, -l*0.32 + math.sin(t)*l*0.30))
    d.polygon(pts, fill=fill)
    # heel
    pts = []
    for i in range(20):
        t = i / 20.0 * math.pi * 2
        pts.append(T(math.cos(t)*w*0.38, l*0.28 + math.sin(t)*l*0.17))
    d.polygon(pts, fill=fill)


def icon(path, kind, size=128):
    top, bot, band, _ = PALETTES[kind]
    base = _vgrad(size, size, top, bot).convert("RGBA")
    d = ImageDraw.Draw(base)
    for i, yy in enumerate((0.66, 0.78, 0.90)):
        _band(d, size, int(size*yy), size*0.018, size*0.32, band + (64 - i*16,),
              max(2, size//44), phase=i*1.4)
    g = _glyph(kind, int(size*0.80))
    base.alpha_composite(g, (int(size*0.10), int(size*0.06)))
    base = base.filter(ImageFilter.SMOOTH_MORE)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    base.convert("RGBA").save(path)


def logo(path, title, kind, w=480, h=160):
    top, bot, band, _ = PALETTES[kind]
    base = _vgrad(w, h, top, bot).convert("RGBA")
    d = ImageDraw.Draw(base)
    for i, yy in enumerate((0.84, 0.94)):
        _band(d, w, int(h*yy), h*0.03, w*0.15, band + (56 - i*18,), 3, phase=i*1.2)
    g = _glyph(kind, int(h*0.86))
    base.alpha_composite(g, (int(h*0.10), int(h*0.07)))
    f = _font(int(h*0.40))
    tx = int(h*1.06)
    d.text((tx+2, h*0.30+2), title, font=f, fill=(0, 0, 0, 150))
    d.text((tx, h*0.30), title, font=f, fill=(250, 252, 255, 255))
    fs = _font(int(h*0.15))
    d.text((tx, h*0.66), "falling_colud", font=fs, fill=band + (255,))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    base.convert("RGB").save(path)


def banner(path, title, tagline, kind, w=960, h=320):
    top, bot, band, _ = PALETTES[kind]
    base = _vgrad(w, h, top, bot).convert("RGBA")
    d = ImageDraw.Draw(base)
    for i in range(4):
        yy = h*(0.74 + i*0.075)
        _band(d, w, int(yy), h*0.022, w*0.11, band + (max(18, 80 - i*16),), 4, phase=i*0.9)
    g = _glyph(kind, int(h*0.66))
    base.alpha_composite(g, (int(w*0.055), int(h*0.14)))
    f = _font(int(h*0.19))
    d.text((int(w*0.30)+2, int(h*0.26)+2), title, font=f, fill=(0, 0, 0, 150))
    d.text((int(w*0.30), int(h*0.26)), title, font=f, fill=(250, 252, 255, 255))
    ft = _font(int(h*0.072))
    d.text((int(w*0.30), int(h*0.56)), tagline, font=ft, fill=band + (240,))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    base.convert("RGB").save(path)


if __name__ == "__main__":
    out = os.path.join(os.path.dirname(__file__), "brandkit-preview")
    for kind in ("gust", "meteor", "footprint", "waterfall"):
        icon(os.path.join(out, f"{kind}-icon.png"), kind)
    print("previews written to", out)
