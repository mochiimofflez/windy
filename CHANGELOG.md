# Changelog

All notable changes to this fork are documented here. The upstream Windy changelog lives with the
original project; this file starts at the point the Aeronautics compatibility layer was added.

## 1.2.1-aeronautics.1

### Added

- **Aeronautics: Winds & Weather integration.** Windy can now follow the local client-side wind from
  Aeronautics instead of inventing its own. Direction, speed, gusts and regional weather all come from
  the same sample Aeronautics uses for its own particle drift.
- Config category **Aeronautics** with:
  - `windSource`: `AUTO` (default), `VANILLA`, or `AERONAUTICS`.
  - `aeronauticsFullStrengthMps` (default `20.0`): physical speed mapped to Windy's nominal full
    visual strength.
  - `aeronauticsWeather` (default `true`): use regional rain, snow, dust and storm state.
- `/windy status` now reports the active source, physical speed, compass heading, visual strength and
  regional weather.
- `/windy gust` continues to add a manual visual gust on top of an Aeronautics wind; `/windy calm`
  visually calms the particles for 60 seconds without touching Aeronautics.
- A one-time status line on world join reports the active wind source, speed, heading, visual strength
  and regional weather, mirroring `/windy status`.
- Regional weather also fades ambient biome motes, so indoor/outdoor ambience follows the same weather
  as the wind.

### Changed

- Minimum NeoForge raised to `21.1.228` to match the tested Aeronautics 1.3.x stack.
- `/windy calm` is now a full visual mute: it stops Windy's motes, leaves, wisps and ribbons for its
  duration, rather than merely suppressing the gust envelope. The underlying Aeronautics wind is
  still untouched.
- `WindDirector` now separates vanilla wind advancement from particle emission so external samples
  can drive the same visuals without duplicating the spawn logic.
- Wind heading math moved into the unit-tested `WindMath` helper. The public convention is unchanged:
  yaw points **toward** the wind, while Aeronautics reports the meteorological bearing it comes
  **from**.

### Compatibility

- Without Aeronautics installed, behaviour is byte-for-byte the vanilla-wind path apart from the
  added config options and status text.
- If Aeronautics is installed but its client API cannot be linked, Windy logs one warning and falls
  back to its built-in wind rather than crashing.
- Aeronautics 1.3.x is declared as an optional `CLIENT` dependency with `ordering=BEFORE`. Aeronautics
  already declares `ordering=AFTER` on Windy; matching that direction from this side would create a
  load-order cycle.
