#!/usr/bin/env python3
"""Windy asset + data generator.

Regenerates every custom particle texture, the particle definition JSONs, the language file and the
mod icon. Run from anywhere:

    python tools/generate.py

Textures are small soft procedural sprites (Pillow). Everything else is derived from the tables below
and matches the particle types registered in WindParticles.java.
"""
import json, math, os
from PIL import Image, ImageDraw, ImageFilter

import brandkit

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODID = "windy"
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", MODID)
TEX = os.path.join(ASSETS, "textures", "particle")


def ensure(p):
    os.makedirs(p, exist_ok=True)
    return p


def write_json(path, obj):
    ensure(os.path.dirname(path))
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)


# ------------------------------------------------------------------ textures
def streak_tex(path, size, length, thick, color, blur=0.5):
    """A thin horizontal hairline — wind made visible.

    The streak runs along the sprite's U axis, because that is the axis the particle code turns to face
    the direction of travel. `length` is the fraction of the sprite it spans and `thick` its half-height,
    both as fractions of the sprite: the quad is always square, so keeping the streak thin here is the
    only way to get a hairline rather than a lozenge. The core is painted at full alpha — the particle
    shader discards fragments under 0.1 combined (texture x vertex) alpha, so softness has to live in the
    falloff, not in a dim core.
    """
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    cy = size / 2.0
    x0 = size * (0.5 - length / 2.0)
    x1 = size * (0.5 + length / 2.0)
    half_t = max(0.8, size * thick)
    for y in range(size):
        for x in range(size):
            fx = (x + 0.5 - x0) / (x1 - x0)          # 0..1 along the streak
            if fx <= 0.0 or fx >= 1.0:
                continue
            dy = abs(y + 0.5 - cy) / half_t
            if dy >= 1.0:
                continue
            along = math.sin(fx * math.pi) ** 0.45   # feathered tips
            a = (1.0 - dy) ** 1.1 * along
            if a <= 0.02:
                continue
            px[x, y] = (color[0], color[1], color[2], int(255 * min(1.0, a * 1.7)))
    img = img.filter(ImageFilter.GaussianBlur(blur))
    ensure(os.path.dirname(path))
    img.save(path)


# Streak sprites come in a few lengths; the provider picks one at random per particle, so a gust is a
# scatter of long and short streaks instead of a swarm of identical dashes.
WISP_LENGTHS = (0.34, 0.64, 0.96)
SNOW_LENGTHS = (0.30, 0.55, 0.82)


def leaf_tex(path, size=16, variant=0):
    """A pale leaf silhouette (tinted in code by the biome's foliage colour)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = size / 2.0
    if variant == 0:      # classic pointed leaf
        pts = []
        for i in range(24):
            t = i / 24.0 * math.pi * 2
            r = (size * 0.36) * (0.55 + 0.45 * abs(math.cos(t)))
            pts.append((c + math.cos(t) * r * 0.62, c + math.sin(t) * r))
        d.polygon(pts, fill=(228, 232, 218, 255))
        d.line([(c, size * 0.12), (c, size * 0.88)], fill=(200, 205, 190, 255), width=1)
    elif variant == 1:    # rounder leaf with a notch
        d.ellipse([c - size * 0.30, c - size * 0.38, c + size * 0.30, c + size * 0.38],
                  fill=(226, 230, 216, 255))
        d.polygon([(c, c - size * 0.42), (c - size * 0.10, c - size * 0.20), (c + size * 0.10, c - size * 0.20)],
                  fill=(0, 0, 0, 0))
        d.line([(c, size * 0.16), (c, size * 0.84)], fill=(198, 203, 188, 255), width=1)
    else:                 # slim willow-ish blade
        pts = []
        for i in range(24):
            t = i / 24.0 * math.pi * 2
            r = (size * 0.42) * (0.35 + 0.65 * abs(math.cos(t)))
            pts.append((c + math.cos(t) * r * 0.38, c + math.sin(t) * r))
        d.polygon(pts, fill=(230, 234, 220, 255))
    img = img.filter(ImageFilter.GaussianBlur(0.3))
    img.save(path)




def dust_tex(path, size=16):
    """A soft dry mote (tinted warm in code).

    Painted with a full-alpha core and a gentle falloff: the particle shader discards fragments under
    0.1 combined (texture x vertex) alpha, so a dim core would clip to a hard flickering speck.
    """
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = size / 2.0
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c) / (size * 0.46)
            if d >= 1.0:
                continue
            a = (1.0 - d) ** 1.1
            # a little grain so it reads as dust, not fog
            g = 0.90 + 0.10 * math.sin(x * 2.1) * math.cos(y * 1.7)
            px[x, y] = (240, 232, 214, int(255 * min(1.0, a * 1.5) * g))
    img.save(path)


def mote_tex(path, size=8):
    """A tiny soft speck — the random particles of the airflow."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = size / 2.0
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c) / (size * 0.48)
            if d >= 1.0:
                continue
            a = (1.0 - d) ** 1.2
            px[x, y] = (248, 250, 255, int(255 * min(1.0, a * 1.6)))
    img.save(path)


def ribbon_tex(path, w=16, h=64):
    """Cross-section for the wind ribbons: U runs ACROSS the strip (bright core, feathered edges) and V
    runs along it, carrying a faint lengthwise unevenness so a long stripe doesn't look extruded. The
    tips are feathered by the renderer's per-vertex alpha, not here."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    cx = w / 2.0
    for y in range(h):
        vary = 0.82 + 0.18 * math.sin(y * 0.42) * math.cos(y * 0.17)
        for x in range(w):
            du = abs(x + 0.5 - cx) / (w * 0.5)
            if du >= 1.0:
                continue
            a = (1.0 - du) ** 1.6 * vary
            if a <= 0.02:
                continue
            px[x, y] = (245, 250, 255, int(255 * min(1.0, a * 1.5)))
    ensure(os.path.dirname(path))
    img.save(path)


def build_textures():
    ensure(TEX)
    for v, length in enumerate(WISP_LENGTHS):
        streak_tex(os.path.join(TEX, f"wisp{v}.png"), 32, length, 0.055, (235, 244, 252))
    for v, length in enumerate(SNOW_LENGTHS):
        streak_tex(os.path.join(TEX, f"snow{v}.png"), 16, length, 0.085, (248, 250, 255), blur=0.35)
    for v in range(3):
        leaf_tex(os.path.join(TEX, f"leaf{v}.png"), variant=v)
    dust_tex(os.path.join(TEX, "dust.png"))
    mote_tex(os.path.join(TEX, "mote.png"))
    ribbon_tex(os.path.join(ASSETS, "textures", "ribbon.png"))


# --------------------------------------------------------------------- data
def build_particles():
    write_json(os.path.join(ASSETS, "particles", "wisp.json"),
               {"textures": [f"{MODID}:wisp{v}" for v in range(len(WISP_LENGTHS))]})
    write_json(os.path.join(ASSETS, "particles", "leaf.json"),
               {"textures": [f"{MODID}:leaf0", f"{MODID}:leaf1", f"{MODID}:leaf2"]})
    write_json(os.path.join(ASSETS, "particles", "snow.json"),
               {"textures": [f"{MODID}:snow{v}" for v in range(len(SNOW_LENGTHS))]})
    write_json(os.path.join(ASSETS, "particles", "dust.json"), {"textures": [f"{MODID}:dust"]})
    write_json(os.path.join(ASSETS, "particles", "mote.json"), {"textures": [f"{MODID}:mote"]})


LANG = {
    "windy.page.title": "Windy",
    "windy.option.enabled": "Enabled",
    "windy.option.enabled.tooltip": "Master switch for the living wind.",
    "windy.option.intensity": "Intensity",
    "windy.option.intensity.tooltip": "How much of the wind you see per tick.",
    "windy.option.radius": "Range",
    "windy.option.radius.tooltip": "How far around you the wind is drawn.",
    "windy.option.gustiness": "Gustiness",
    "windy.option.gustiness.tooltip": "How often the wind gathers into a gust. 0 keeps the world calm.",
    "windy.option.stripes": "Wind stripes",
    "windy.option.stripes.tooltip": "Long flowing ribbons of air that snake downwind and follow the land over hills.",
    "windy.option.motes": "Air motes",
    "windy.option.motes.tooltip": "Tiny specks tumbling in the current, everywhere the wind reaches.",
    "windy.option.wisps": "Gust wisps",
    "windy.option.wisps.tooltip": "Pale streaks that ride the wind while a gust is up.",
    "windy.option.leaves": "Torn leaves",
    "windy.option.leaves.tooltip": "Leaves pulled from tree canopies, tinted to each biome's own foliage.",
    "windy.option.snow": "Blowing snow",
    "windy.option.snow.tooltip": "Snow whipped near-horizontal while it snows in cold biomes.",
    "windy.option.dust": "Desert dust",
    "windy.option.dust.tooltip": "Dry motes lifted off sand in hot, rainless biomes.",
    "windy.option.vanilla_particles": "Respect particle setting",
    "windy.option.vanilla_particles.tooltip": "Ease off when the vanilla particle setting is Decreased, and stop on Minimal.",
    "windy.intensity.off": "Off",
    "windy.intensity.subtle": "Subtle",
    "windy.intensity.normal": "Normal",
    "windy.intensity.lively": "Lively",
}


def build_lang():
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), LANG)


def build_icon():
    brandkit.icon(os.path.join(ASSETS, "icon.png"), "gust", 128)


def main():
    build_textures()
    build_particles()
    build_lang()
    build_icon()
    print("[windy] generated textures, particle JSONs, lang and icon.")


if __name__ == "__main__":
    main()
