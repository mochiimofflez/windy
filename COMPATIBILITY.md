# Aeronautics compatibility notes

This fork adds an optional client-side bridge from Windy to **Aeronautics: Winds & Weather**. It does
not modify Aeronautics, and it does not require Aeronautics to be installed.

## Tested stack

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.228 |
| Windy (this fork) | 1.2.1-aeronautics.1 |
| Aeronautics: Winds & Weather | 1.3.1 |
| Create Aeronautics | 1.3.1 (bundled) |
| Create | 6.0.10 |
| Sable | 2.0.6 |
| Sodium | 0.8.12-beta.2+mc1.21.1 |
| Java | 21 |

Windy declares `aeronauticswinds` as an optional `CLIENT` dependency with `ordering="BEFORE"`.
Aeronautics already declares `windy` as optional `CLIENT` with `ordering="AFTER"`; using `AFTER` on
Windy's side would create a load-order cycle, which NeoForge reports as a sorting failure. `BEFORE`
preserves the intended order (Windy first, Aeronautics after) while keeping the dependency optional.

## API used

All access is through public, unobfuscated client classes in the Aeronautics 1.3.x jar:

- `com.enzonic.aeronauticswinds.WindsSim#forLevel(ClientLevel)`
- `WindsSim.Simulation#sampleWindTo(ClientLevel, double, double, double, Vector3d)`
- `WindsSim.Simulation#weatherAt(ClientLevel, double, double)`
- `com.enzonic.aeronauticswinds.weather.WeatherType`
  (`isWet`, `isFrozen`, `isSandy`, `isHot`, `isStormy`, `isFoggy`)

The jar is a `compileOnly` dependency from Modrinth Maven; it is never bundled into Windy.

The call site is isolated in `dev.fallingcloud.windy.compat.AeronauticsWindSource`, and the class is
only instantiated from `WindSources` after `ModList.get().isLoaded("aeronauticswinds")` returns true.
If the class or method is missing after an Aeronautics update, `WindSources` catches the linkage
failure, logs one warning, and returns no external source. `WindDirector` then runs its original
vanilla wind path.

## Direction conversion

Aeronautics exposes a world-space wind vector in `sampleWindTo`. Its `WindSample.bearing()` is the
meteorological direction the wind comes **from**; Windy's `windYaw` is the direction the wind blows
**toward**. The adapter deliberately ignores `bearing()` and converts the vector directly:

```java
float yawToward = (float) Math.atan2(vector.z, vector.x);
```

This avoids a 180° error. The status command converts the yaw back to a compass heading for humans
(0 = north, 90 = east).

Verified in-game:

```
/aerowind set 20 90
Wind override set to 20.0 m/s heading 90
/windy status
[Windy] source aeronautics, 20.0 m/s, heading 270 deg toward, visual 100%, cloudy, following Aeronautics
```

A wind **from** the east (bearing 90) blows **toward** the west (heading 270). The test fails if the
adapter accidentally adds or removes an extra half turn.

## Strength mapping

```java
visualStrength = clamp(speedMps / aeronauticsFullStrengthMps, 0.0, 1.4)
```

Default `aeronauticsFullStrengthMps = 20.0`. Aeronautics' base wind is 6 m/s and its cap is 55 m/s.
With the default mapping:

| Aeronautics speed | Windy visual strength |
| --- | --- |
| 6 m/s | 0.30 (light breeze) |
| 20 m/s | 1.00 (nominal full) |
| 28+ m/s | 1.40 (maximum visuals) |

The physical speed is still reported in `/windy status`; only the particle budget is saturated.

Gusts use `min(1.4, strength * 0.9)` as the sampled gust component, so strong wind produces leaf and
dust budgets near the top of Windy's range without making every frame a storm. `/windy gust` adds a
manual visual gust on top (and ends any active calm); `/windy calm` hides the visuals for 60 seconds but leaves Aeronautics' wind
untouched.

## Weather mapping

When `aeronautics.aeronauticsWeather = true`, the source samples `weatherAt` once per 20 ticks (or
after moving 16 blocks) and maps:

| Aeronautics weather | Windy effect |
| --- | --- |
| wet | ambient motes suppressed, no dust |
| frozen | blowing snow (still requires a snowy biome at the particle position) |
| dry / clear | dust allowed on sand in dry biomes |
| stormy/hot/sandy/foggy | available to the source, currently used for status and flags |

Dust still requires `BlockTags.SAND` and a `NONE`-precipitation biome, so a regional sandstorm does not
spawn dust on grass. Snow still requires `Biome.Precipitation.SNOW`, so a frozen region does not spawn
snow in a desert.

If regional weather is disabled or unavailable, Windy falls back to `level.getRainLevel(1.0f)` and the
original vanilla snow/dust checks.

## Grassier Grass

When Grassier Grass (`grassiergrass`) is installed and an external wind source is active, Windy drives
its grass wind so the blades sway the way Windy's particles blow. This is `compatibility.grassierGrassSync`
(default on).

- Grassier Grass has no formal API. Windy calls its public in-memory setters on
  `com.leonardoinc22.shortgrass.config.GrassConfig` by reflection (no compile-time dependency, nothing
  bundled, nothing written to its config file).
- Direction: Grassier Grass uses degrees on the XZ plane, 0 = +X and 90 = +Z. That is Windy's
  `atan2(z, x)` yaw, so the angle passes through unchanged.
- Strength: Grassier Grass's speed is 100 = "original wind" (0..500). Windy's effective visual strength
  (0..1.4, including `/windy gust` and `/windy calm`) maps to `100 * (0.5 + 0.5 * strength)`, so ordinary
  wind sways the grass visibly (Aeronautics' base wind of about 0.2 gives 60), strength 1.0 gives 100,
  and a calm (strength 0) gives 0. A straight `strength * 100` left normal wind too weak to see.
- Windy turns Grassier Grass's dynamic wind off while it is driving, and restores its previous speed,
  direction and dynamic-wind setting when the external source stops, on leaving a world, or when Windy
  is disabled.
- Only active while Aeronautics supplies the wind. With Windy's own wind (`VANILLA`), Grassier Grass keeps
  its own dynamic wind.
- If the reflection fails (for example a Grassier Grass update renames the setters), Windy logs one
  warning and leaves Grassier Grass alone.
- Tested against Grassier Grass 1.4.5 for 1.21.1.

## Fallback behaviour

- **Aeronautics not installed:** `AUTO` silently uses Windy's original wind. `AERONAUTICS` logs one
  warning and also falls back.
- **Aeronautics installed but API missing:** one warning, then vanilla wind.
- **Aeronautics sample throws at runtime:** a linkage error, or 3 failures in a row, logs one warning
  with the stack trace and switches to vanilla wind for the rest of the session. A single transient
  failure (for example during a dimension change) just uses vanilla wind for that tick.
- **Config `windSource = VANILLA`:** the external source is never created, even when Aeronautics is
  installed.
- **Config changed while playing:** the director notices the mode change and recreates or drops the
  source on the next tick.
- **Diagnostics:** the first client tick in a world logs the same one-line status as `/windy status`,
  including the active source, speed, heading, visual strength and weather.

## Test matrix

| Check | Result |
| --- | --- |
| `./gradlew build` (compile + 4 unit tests) | Pass |
| Full stack loads in dev client (19 mods) | Pass, no load-order cycle |
| `/windy status` with Aeronautics active | Pass: `source aeronautics`, 4.4 m/s, heading 232° |
| `/aerowind set 20 90` | Pass: 20.0 m/s, heading 270° toward, 100% |
| `/windy calm` | Pass: `visually calmed`, source still reports 20 m/s; all Windy particles/ribbons muted |
| `/windy gust 1.4` | Pass: `gust incoming at 120%`; effective visual reaches 140% |
| No-Aeronautics fallback | Pass: `source vanilla, wind heading 234 deg toward, base 39%`, no warning in `AUTO` |
| `windSource = VANILLA` with Aeronautics installed | Pass: same vanilla-source log with all four Aeronautics/Create jars present |
| One-time status diagnostics | Pass: `source aeronautics, 3.3 m/s, heading 114 deg toward, visual 17%, cloudy` on world join |
| Dedicated server | Not applicable; Windy is client-only |

### Verification log excerpts

Full stack, Aeronautics active:

```
[Windy] source aeronautics, 3.3 m/s, heading 114 deg toward, visual 17%, cloudy, following Aeronautics
```

Same world with `run/mods/aeronauticswinds-1.3.1.jar` removed (`AUTO` mode):

```
[Windy] source vanilla, wind heading 234 deg toward, base 39%, calm, next gust in ~399 ticks
```

Same full stack with `aeronautics.windSource = "VANILLA"`, the same `source vanilla` prefix appears.

The earlier command-driven check produced the 20 m/s / heading 270° status line quoted above, and
`/windy calm` / `/windy gust 1.4` were exercised in the same session.

## Known issues in the tested stack

- **`MixinCamera` warning from Aeronautics (harmless, not caused by Windy).** The log shows
  `Mixin apply for mod aeronauticswinds failed ... MixinCamera ... @Shadow method move ... was not located
  in the target class net.minecraft.client.Camera`. Aeronautics 1.3.1 shadows `Camera.move(DDD)V`, but in
  Minecraft 1.21.1 the method is `move(FFF)V`, and its jar ships no refmap to translate it. The mixin is
  `required: false` (`defaultRequire: 0`), so the game skips it and continues. The warning is identical
  with Windy's bridge disabled, and Windy does not touch `Camera`. It has to be fixed in Aeronautics.

## Known limitations

- The bridge is client-only, matching both mods' particle systems. A server does not need either mod
  for the other to work, but Aeronautics wind sampling only happens on the client.
- Windy's `windSource = AERONAUTICS` still falls back with a warning if Aeronautics is absent; it does
  not refuse to start. This is intentional so a pack can ship one config that also works before the
  optional dependency is installed.
- Aeronautics' public classes are not a formal API contract. The version range is `[1.3,1.4)`; a major
  update should re-run the test matrix.
- The regional-weather mapping intentionally keeps Windy's biome checks. A future version could expose
  per-position weather intensity to modulate snow/dust density more precisely.
- Windy's built-in wind is global, while Aeronautics' wind is local. The bridge samples at the player's
  eye position once per tick; particles spawned up to 32 blocks away use that one sample. This is the
  same locality assumption Aeronautics uses for its own particle drift.

## Reproducing the verification

```bash
# Build and unit tests
./gradlew build

# Dev client with the tested stack (jars in run/mods)
./gradlew runClient

# In-game
/aerowind query
/aerowind set 20 90
/windy status
/windy calm
/windy gust 1.4
/windy status
/aerowind clear
```

The expected status after the override is:

```
[Windy] source aeronautics, 20.0 m/s, heading 270 deg toward, visual 100%, cloudy, following Aeronautics
```

To test fallback, remove `run/mods/aeronauticswinds-1.3.1.jar`, relaunch, and run `/windy status`; it
should begin with `source vanilla`. Restore the jar afterward.
