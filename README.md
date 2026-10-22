# Windy

Wind you can see. A client-side Minecraft mod that keeps a living wind over the world — its direction
slowly veers, its strength rises and falls, and now and then it gathers into a gust. While the wind is
up you see it: pale wisps streak past, leaves tear loose from the canopy in the colour of their own
biome (cherry stays pink, birch stays birch), snow whips near-horizontal through cold storms, and dust
lifts off the dunes in dry heat.

Everything is a custom soft particle spawned the way a campfire spawns smoke — the mod never touches
the render frame — so it is fully compatible with Sodium and Iris shaderpacks and honours the vanilla
particle setting.

- **Minecraft:** 1.21.1, **Loader:** NeoForge 21.1.228+
- **Client-side only** — safe to join any server with it.
- **Config:** the NeoForge mods config screen, **Mods → Windy → Config** (or edit
  `config/windy-client.toml`): intensity, range, gustiness, and per-effect toggles for wisps, leaves,
  snow and dust.

## Installation

1. Remove any existing Windy jar from `mods/`; this fork uses the same `windy` mod id and cannot be
   installed alongside upstream Windy.
2. Put `windy-1.2.1-aeronautics.1.jar` in `mods/`.
3. Optionally install **Aeronautics: Winds & Weather** (and its Create/Sable dependencies). Without it,
   Windy runs its original wind simulation exactly as before.

## Aeronautics: Winds & Weather compatibility

Windy can read the local wind and regional weather from
[Aeronautics: Winds & Weather](https://modrinth.com/mod/aeronautics-winds-and-weather) instead of
running its own wind simulation. The integration is client-side, uses Aeronautics' public client wind
sample, and is optional at every level: the mod still loads, and still behaves normally, when
Aeronautics is absent.

| Config option | Default | Effect |
| --- | --- | --- |
| `aeronautics.windSource` | `AUTO` | `AUTO` follows Aeronautics when installed; `VANILLA` keeps Windy's own wind; `AERONAUTICS` prefers the integration and warns if it cannot be used. |
| `aeronautics.aeronauticsFullStrengthMps` | `20.0` | Aeronautics wind speed that maps to Windy's nominal full visual strength. Aeronautics' base wind is 6 m/s and its cap is 55 m/s. |
| `aeronautics.aeronauticsWeather` | `true` | Use Aeronautics' regional weather for snow, dust, rain fading and storm visuals. |

The mapping is deliberately simple and tunable:

- **Direction:** Aeronautics' world-space wind vector is converted with `yaw = atan2(z, x)`. Aeronautics
  reports the bearing the wind comes *from*; Windy's yaw points the direction the wind blows *toward*.
- **Strength:** `visualStrength = clamp(speedMps / fullStrengthMps, 0, 1.4)`. With the default 20 m/s,
  Aeronautics' 6 m/s base wind is a light breeze and storm-force wind reaches Windy's strongest visuals.
- **Gusts:** the sampled speed supplies a gust component for leaf and dust budgets; `/windy gust` adds
  a manual visual gust on top. `/windy calm` hides the visuals for 60 seconds without changing the
  actual Aeronautics wind.
- **Weather:** wet/frozen regions drive rain fading and blowing snow; dry regions allow dust. If the
  regional-weather option is off, the original vanilla rain/snow checks are used.

Use `/windy status` in-game to see the active source, speed, compass heading, visual strength and
weather. When Aeronautics is providing the wind, the status begins with `source aeronautics`.

If Aeronautics is installed but its wind API cannot be linked (for example after a major update),
Windy logs one warning and falls back to its built-in wind. It does not crash.

See [COMPATIBILITY.md](COMPATIBILITY.md) for the exact API used, test matrix, and known limitations.

## Building

```
./gradlew build        # tests + jar in build/libs/
./gradlew runClient    # dev client
```

The Aeronautics jar is a `compileOnly` dependency resolved from Modrinth Maven, so a normal build does
not need a local copy. To run the dev client with the full stack, put Create, Sable, Create Aeronautics
and Aeronautics jars in `run/mods/` (see `COMPATIBILITY.md` for the tested versions).

`tools/generate.py` (Python + Pillow) regenerates all particle textures, particle JSONs, the language
file and the mod icon.

## License and attribution

MIT. This is a compatibility fork of [falling_colud/windy](https://github.com/falling-colud/windy);
the original wind simulation, particles and art are by falling_colud. The Aeronautics compatibility
layer is released under the same MIT license.
