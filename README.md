# Windy

Wind you can see. A client-side Minecraft mod that keeps a living wind over the world — its direction
slowly veers, its strength rises and falls, and now and then it gathers into a gust. While the wind is
up you see it: pale wisps streak past, leaves tear loose from the canopy in the colour of their own
biome (cherry stays pink, birch stays birch), snow whips near-horizontal through cold storms, and dust
lifts off the dunes in dry heat.

Everything is a custom soft particle spawned the way a campfire spawns smoke — the mod never touches
the render frame — so it is fully compatible with Sodium and Iris shaderpacks and honours the vanilla
particle setting.

- **Minecraft:** 1.21.1, **Loader:** NeoForge
- **Client-side only** — safe to join any server with it.
- **Config:** the NeoForge mods config screen, **Mods → Windy → Config** (or edit
  `config/windy-client.toml`): intensity, range,
  gustiness, and per-effect toggles for wisps, leaves, snow and dust.

## Building

```
./gradlew assemble    # jar lands in build/libs/
```

`tools/generate.py` (Python + Pillow) regenerates all particle textures, particle JSONs, the language
file and the mod icon.

## License

MIT
