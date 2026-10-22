# Upstream PR notes

## Title

Add optional Aeronautics: Winds & Weather wind and weather integration

## Summary

This branch makes Windy follow the local client-side wind and regional weather from **Aeronautics:
Winds & Weather** when it is installed, while preserving Windy's original wind simulation when it is
not. Aeronautics is never modified; the integration is a compile-only client bridge behind a mod-list
guard.

## What changed

- Added `compat/` with `WindSource`, `WindSources`, `AeronauticsWindSource`, and a small `WindMath`
  helper for unit-tested compass/yaw conversion.
- `WindDirector` can now sample an external wind source each client tick, falling back to the original
  vanilla walk on absence, linkage failure, or a runtime sampling error.
- `[aeronautics]` config section: `windSource` (`AUTO` / `VANILLA` / `AERONAUTICS`),
  `aeronauticsFullStrengthMps`, and `aeronauticsWeather`.
- Regional weather now drives rain fading, blowing snow, dust eligibility, and ambient-mote fading.
- `/windy status` reports the active source, physical speed, compass heading, visual strength, and
  regional weather. `/windy calm` is now a full visual mute; `/windy gust` still layers on top.
- One-time `/windy status`-equivalent log on world join for diagnostics.
- Optional `aeronauticswinds` client dependency pinned to `[1.3,1.4)`, with `ordering="BEFORE"` to
  avoid the cycle caused by Aeronautics' existing `ordering="AFTER"` on Windy.
- README, COMPATIBILITY notes, CHANGELOG, unit tests, and a GitHub Actions build workflow.

## API used

- `WindsSim.forLevel(ClientLevel)`
- `WindsSim.Simulation#sampleWindTo(...)`
- `WindsSim.Simulation#weatherAt(...)`
- `WeatherType` predicates (`isWet`, `isFrozen`, `isSandy`, `isHot`, `isStormy`, `isFoggy`)

Aeronautics is a `compileOnly` Gradle dependency; none of its classes are bundled into the Windy jar.

## Direction conversion

Aeronautics' vector is world-space; Windy's yaw points **toward** the wind. The adapter converts with:

```java
float yawToward = (float) Math.atan2(vector.z, vector.x);
```

and deliberately ignores `WindSample.bearing()` to avoid a 180° error. With `/aerowind set 20 90`
(wind from the east), Windy reports heading 270° toward (west), as expected.

## Test plan and results

- `./gradlew test build` passes; 4 `WindMath` unit tests, 0 failures.
- Full dev-client stack with Create 6.0.10, Sable 2.0.6, Create Aeronautics 1.3.1, Aeronautics 1.3.1,
  Sodium, and Windy loads without a mod-sort cycle.
- `source aeronautics, 3.3 m/s, heading 114 deg toward, visual 17%, cloudy` on world join.
- `/aerowind set 20 90` → `/windy status` reports 20.0 m/s, heading 270° toward, visual 100%,
  `cloudy`, following Aeronautics.
- Removing the Aeronautics jar (`AUTO` mode) logs
  `source vanilla, wind heading 234 deg toward, base 39%, calm, next gust in ~399 ticks`.
- Setting `windSource = "VANILLA"` with Aeronautics installed logs the same `source vanilla` line.
- `/windy calm` and `/windy gust 1.4` exercised in the same session.

## Reviewer notes

- The `BEFORE` ordering is intentional; using `AFTER` on Windy's side made NeoForge report
  `Mod Sorting failed / Detected Cycles`.
- Do not merge the Aeronautics jar or its classes into Windy.
- Windy remains client-only; no server changes are needed.
- The integration degrades to vanilla wind rather than crashing if Aeronautics changes its API.
