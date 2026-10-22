package dev.fallingcloud.windy;

import dev.fallingcloud.windy.compat.GrassierGrassSync;
import dev.fallingcloud.windy.compat.WindSource;
import dev.fallingcloud.windy.compat.WindSources;
import dev.fallingcloud.windy.particle.LeafParticle;
import dev.fallingcloud.windy.particle.WindParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The heart of Windy. Every client tick this advances a living wind — a direction that slowly veers, a
 * strength that rises and falls on two slow swells, and now and then a gust that gathers, holds and lets
 * go — then spends a small particle budget making that wind visible around the player:
 *
 * <ul>
 *   <li><b>Wisps</b> in open air while a gust is up.</li>
 *   <li><b>Leaves</b> torn from canopy tops, tinted like the leaves they left.</li>
 *   <li><b>Snow streaks</b> whipped near-horizontal where it is snowing.</li>
 *   <li><b>Dust</b> lifted off sand in dry weather.</li>
 * </ul>
 *
 * <p>When Aeronautics: Winds &amp; Weather is installed, the wind can instead be sampled from its
 * client-side simulation. The vanilla walk stays the fallback, so Windy remains fully usable without
 * Aeronautics and can be switched back with one config option.
 *
 * <p>Everything spawns the way a campfire spawns smoke — never touching the render frame — and honours the
 * vanilla particle setting.
 */
public final class WindDirector {
    public static final WindDirector INSTANCE = new WindDirector();

    // ---- the wind itself ----
    private float windYaw;              // radians; direction the wind blows TOWARD
    private float baseStrength;         // 0..1 slow background wind
    private int gustTicksLeft;          // > 0 while a gust is running
    private int gustDuration;
    private float gustPeak;             // 0.6..1.2 strength of the current gust
    private int gustCooldown = 400;     // ticks until the next gust may start
    private int calmTicks;              // > 0 while /windy calm is hiding the visuals
    private float lastEffective;        // the eff computed on the last live tick, for other systems
    private long lastGameTime = Long.MIN_VALUE;

    // ---- external wind (Aeronautics) ----
    private WindSource externalSource;
    private WindSource.Sample externalSample;
    private WindyConfig.WindSourceMode lastSourceMode;
    private boolean externalSourceChecked;
    private int externalFailures;
    private static final int MAX_EXTERNAL_FAILURES = 3;
    private boolean sourceLogged;

    // ---- shared wind state (read by the ribbon renderer) ----
    public double windX() { return Math.cos(windYaw); }
    public double windZ() { return Math.sin(windYaw); }
    public float effectiveStrength() { return lastEffective; }

    public float gustStrength() {
        if (calmTicks > 0) return 0.0f;
        if (externalSample == null) return gustEnvelope();
        return clamp(externalSample.gust() + gustEnvelope(), 0.0f, 1.4f);
    }

    /**
     * Regional weather from the external source, or {@code null} when vanilla weather should be used.
     * Read by the ambient-mote director so indoor/outdoor fading follows the same weather as the wind.
     */
    public WindSource.Weather externalWeather() {
        return externalSample != null ? externalSample.weather() : null;
    }

    private WindDirector() {
        this.windYaw = (float) (Math.random() * Math.PI * 2);
    }

    // ---- hooks for /windy ----------------------------------------------------------------------

    /** Kick off a gust right now (also used by the natural scheduler). */
    public void startGust(float peak, int duration) {
        gustDuration = duration;
        gustTicksLeft = duration;
        gustPeak = clamp(peak, 0.3f, 1.4f);
    }

    /** Kill the running gust and hold the air still for a while. */
    /** A manual gust cancels /windy calm so it is never silently swallowed. */
    public void endCalm() {
        calmTicks = 0;
    }

    /** Drop any /windy calm or manual gust and go back to the natural wind. */
    public void reset() {
        calmTicks = 0;
        gustTicksLeft = 0;
    }

    public void calm() {
        gustTicksLeft = 0;
        gustCooldown = Math.max(gustCooldown, 1200);
        calmTicks = 1200;
    }

    /** One-line weather report for the status command. */
    public String describe() {
        int heading = Math.floorMod(Math.round(WindMath.headingDegrees(windYaw)), 360);
        if (externalSample != null) {
            String weather = externalSample.weather() != null ? externalSample.weather().name() : "vanilla weather";
            String state = calmTicks > 0 ? "visually calmed" : "following Aeronautics";
            return String.format("source aeronautics, %.1f m/s, heading %d deg toward, visual %.0f%%, %s, %s",
                    externalSample.speedMps(), heading, lastEffective * 100.0f, weather, state);
        }
        float gust = gustEnvelope();
        String state = calmTicks > 0
                ? "visually calmed"
                : gustTicksLeft > 0
                        ? String.format("GUST %.0f%% (%d ticks left)", gust * 100, gustTicksLeft)
                        : String.format("calm, next gust in ~%d ticks", Math.max(0, gustCooldown));
        return String.format("source vanilla, wind heading %d deg toward, base %.0f%%, %s",
                heading, baseStrength * 100, state);
    }

    /** Envelope of the running gust, 0..gustPeak (attack 25%, hold, release 35%). */
    private float gustEnvelope() {
        if (gustTicksLeft <= 0 || gustDuration <= 0) return 0f;
        float t = 1.0f - gustTicksLeft / (float) gustDuration; // 0 at start -> 1 at end
        float env;
        if (t < 0.25f) env = t / 0.25f;
        else if (t > 0.65f) env = (1.0f - t) / 0.35f;
        else env = 1.0f;
        return gustPeak * env;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        WindyConfig cfg = WindyConfig.get();
        if (!cfg.enabled() || cfg.intensity() == WindyConfig.Intensity.OFF) {
            externalSample = null;
            GrassierGrassSync.INSTANCE.release();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            externalSample = null; // do not leak the last world's weather into the next one
            GrassierGrassSync.INSTANCE.release();
            return;
        }
        if (mc.isPaused()) return;

        RandomSource rng = level.random;
        long now = level.getGameTime();
        if (now == lastGameTime) return; // don't advance twice on the same world tick
        lastGameTime = now;

        // ---- advance or sample the wind ----
        WindSource.Sample sampled = sampleExternal(level, player, cfg);
        if (sampled != null) {
            externalSample = sampled;
            windYaw = sampled.yawToward();
            baseStrength = clamp(sampled.strength(), 0.0f, 1.0f);
            if (gustTicksLeft > 0) gustTicksLeft--;
        } else {
            externalSample = null;
            advanceVanillaWind(cfg, rng, now);
        }
        if (calmTicks > 0) calmTicks--;

        float manualGust = gustEnvelope();
        float gust;
        float eff;
        if (calmTicks > 0) {
            // /windy calm is a full visual mute, not just a gust suppressor.
            gust = 0.0f;
            eff = 0.0f;
        } else if (externalSample != null) {
            gust = clamp(externalSample.gust() + manualGust, 0.0f, 1.4f);
            eff = clamp(externalSample.strength() + manualGust, 0.0f, 1.4f);
        } else {
            gust = manualGust;
            eff = clamp(baseStrength * 0.45f + gust, 0f, 1.4f);   // effective visible wind
        }
        lastEffective = eff;
        GrassierGrassSync.INSTANCE.update(cfg.grassierGrassSync() && externalSample != null, windYaw, eff);
        if (!sourceLogged) {
            sourceLogged = true;
            Windy.LOGGER.info("[Windy] {}", describe());
        }
        double wx = Math.cos(windYaw), wz = Math.sin(windYaw);      // unit wind direction

        // Calm, or a truly windless Aeronautics sample, should not leave motes and leaves drifting
        // with a stale direction. Manual gusts still bypass this because they raise eff above zero.
        if (calmTicks > 0 || (externalSample != null && eff <= 0.01f)) {
            return;
        }

        double densityMul = switch (cfg.intensity()) {
            case SUBTLE -> 0.45;
            case LIVELY -> 2.0;
            default -> 1.0;
        };
        // When the player asks us to respect the vanilla setting we thin out ourselves and let vanilla's
        // limiter drop the rest. When they ask us not to, we must spawn forced: vanilla silently discards
        // every unforced particle on Minimal, so an unforced spawn would ignore the toggle entirely.
        boolean force = !cfg.respectVanillaParticleSetting();
        if (cfg.respectVanillaParticleSetting()) {
            switch (mc.options.particles().get()) {
                case MINIMAL -> { return; }
                case DECREASED -> densityMul *= 0.5;
                default -> { }
            }
        }

        // Vanilla distance-culls non-limiter particles past ~32 blocks; don't waste budget beyond that.
        int radius = Math.min(cfg.radius(), 32);
        double px = player.getX(), py = player.getY(), pz = player.getZ();

        // Regional weather is optional: when it is unavailable, fall back to vanilla rain/snow exactly
        // as before. A wet region is treated like steady rain; a frozen one like snowfall.
        WindSource.Weather regional = externalSample != null ? externalSample.weather() : null;
        float rain = regional != null
                ? (regional.wet() || regional.frozen() ? 0.75f : 0.0f)
                : level.getRainLevel(1.0f);
        boolean snowWeather = regional != null ? regional.frozen() : rain > 0.1f;
        boolean dustWeather = regional != null
                ? !regional.wet() && !regional.frozen()
                : rain < 0.05f;

        // ---- wisps: wind made visible — a trickle on ordinary wind, a flurry in gusts ----
        if (cfg.wisps() && eff > 0.24f) {
            int count = poisson(4.5 * densityMul * (eff - 0.20f), rng);
            for (int i = 0; i < count; i++) {
                double x = px + (rng.nextDouble() * 2 - 1) * radius;
                double z = pz + (rng.nextDouble() * 2 - 1) * radius;
                double y = py + (rng.nextDouble() * 14 - 4);
                BlockPos pos = BlockPos.containing(x, y, z);
                if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
                if (!level.canSeeSky(pos)) continue;
                double speed = 0.28 + 0.30 * eff + rng.nextDouble() * 0.10;
                level.addParticle(WindParticles.WISP.get(), force, x, y, z,
                        wx * speed + rng.nextGaussian() * 0.02,
                        (rng.nextDouble() - 0.45) * 0.02,
                        wz * speed + rng.nextGaussian() * 0.02);
            }
        }

        // ---- motes: tiny specks tumbling in the airflow, everywhere the wind reaches ----
        if (cfg.motes()) {
            int count = poisson(5.5 * densityMul * (0.30 + 0.55 * eff), rng);
            for (int i = 0; i < count; i++) {
                double x = px + (rng.nextDouble() * 2 - 1) * radius;
                double z = pz + (rng.nextDouble() * 2 - 1) * radius;
                double y = py + (rng.nextDouble() * 18 - 6);
                BlockPos pos = BlockPos.containing(x, y, z);
                if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
                // near the surface is enough — motes drift under the canopy too, unlike wisps
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
                if (y < top - 10) continue;
                double speed = (0.14 + 0.30 * eff) * (0.6 + rng.nextDouble() * 0.4);
                level.addParticle(WindParticles.MOTE.get(), force, x, y, z,
                        wx * speed + rng.nextGaussian() * 0.015,
                        (rng.nextDouble() - 0.4) * 0.015,
                        wz * speed + rng.nextGaussian() * 0.015);
            }
        }

        // ---- leaves: torn off canopy tops; a steady ambient shed, a flurry in gusts ----
        if (cfg.leaves()) {
            double budget = 3.2 * densityMul * (0.40 + 1.1 * gust) * (0.6 + 0.4 * baseStrength);
            int count = poisson(budget, rng);
            for (int i = 0; i < count; i++) {
                int x = (int) Math.floor(px + (rng.nextDouble() * 2 - 1) * radius);
                int z = (int) Math.floor(pz + (rng.nextDouble() * 2 - 1) * radius);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos leafPos = new BlockPos(x, top - 1, z);
                if (!level.isLoaded(leafPos)) continue;
                BlockState leaf = level.getBlockState(leafPos);
                if (!leaf.is(BlockTags.LEAVES)) continue;
                int palette = paletteFor(leaf);
                double lx = x + rng.nextDouble();
                double lz = z + rng.nextDouble();
                // `top` is the first EMPTY cell above the canopy, so spawn just above it: a leaf started
                // inside the canopy's collision volume would be clamped by the leaf block below it,
                // freeze on contact and fade away buried in the tree instead of sailing off.
                double ly = top + 0.05 + rng.nextDouble() * 0.4;
                double speed = 0.10 + 0.22 * eff;
                level.addParticle(WindParticles.LEAF.get(), force, lx, ly, lz,
                        wx * speed + rng.nextGaussian() * 0.03,
                        palette,
                        wz * speed + rng.nextGaussian() * 0.03);
            }
        }

        // ---- blowing snow: near-horizontal streaks where it is snowing ----
        // (isRainingAt tests ==RAIN, so it is false in snow — combine the weather flag with the
        //  biome's own precipitation at that position instead.)
        if (cfg.snow() && snowWeather) {
            int count = poisson(9.0 * densityMul * rain * (0.55 + 0.8 * gust), rng);
            for (int i = 0; i < count; i++) {
                int x = (int) Math.floor(px + (rng.nextDouble() * 2 - 1) * radius);
                int z = (int) Math.floor(pz + (rng.nextDouble() * 2 - 1) * radius);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                double y = Math.max(top, py - 3) + rng.nextDouble() * 9;
                BlockPos pos = BlockPos.containing(x, y, z);
                if (!level.isLoaded(pos)) continue;
                Biome biome = level.getBiome(pos).value();
                if (biome.getPrecipitationAt(pos) != Biome.Precipitation.SNOW) continue;
                if (!level.canSeeSky(pos)) continue;
                double speed = 0.30 + 0.35 * eff + rng.nextDouble() * 0.08;
                level.addParticle(WindParticles.SNOW.get(), force,
                        x + rng.nextDouble(), y, z + rng.nextDouble(),
                        wx * speed + rng.nextGaussian() * 0.03,
                        -0.02 - rng.nextDouble() * 0.05,
                        wz * speed + rng.nextGaussian() * 0.03);
            }
        }

        // ---- dust: lifted off sand in dry weather ----
        if (cfg.dust() && dustWeather) {
            int count = poisson(3.6 * densityMul * (0.40 + 0.9 * gust), rng);
            for (int i = 0; i < count; i++) {
                int x = (int) Math.floor(px + (rng.nextDouble() * 2 - 1) * radius);
                int z = (int) Math.floor(pz + (rng.nextDouble() * 2 - 1) * radius);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos surface = new BlockPos(x, top - 1, z);
                if (!level.isLoaded(surface)) continue;
                if (!level.getBlockState(surface).is(BlockTags.SAND)) continue;
                Biome biome = level.getBiome(surface).value();
                if (biome.getPrecipitationAt(surface) != Biome.Precipitation.NONE) continue; // deserts/badlands
                double y = top + 0.2 + rng.nextDouble() * 1.2;
                double speed = 0.16 + 0.28 * eff;
                level.addParticle(WindParticles.DUST.get(), force,
                        x + rng.nextDouble(), y, z + rng.nextDouble(),
                        wx * speed + rng.nextGaussian() * 0.02,
                        0.004 + rng.nextDouble() * 0.006,
                        wz * speed + rng.nextGaussian() * 0.02);
            }
        }
    }

    private void advanceVanillaWind(WindyConfig cfg, RandomSource rng, long now) {
        windYaw += (float) (rng.nextGaussian() * 0.004);
        baseStrength = clamp(0.46f
                + 0.30f * (float) Math.sin(now * 9.2e-4 + 1.7)
                + 0.22f * (float) Math.sin(now * 2.7e-4 + 4.1), 0f, 1f);
        if (gustTicksLeft > 0) {
            gustTicksLeft--;
        } else if (cfg.gustiness() > 0 && --gustCooldown <= 0) {
            startGust(0.6f + rng.nextFloat() * 0.6f, 70 + rng.nextInt(110));
            gustCooldown = 160 + rng.nextInt(1 + (int) (1400 * (1.05f - cfg.gustiness() / 100f)));
        }
    }

    private WindSource.Sample sampleExternal(ClientLevel level, LocalPlayer player, WindyConfig cfg) {
        WindyConfig.WindSourceMode mode = cfg.windSource();
        if (mode != lastSourceMode) {
            lastSourceMode = mode;
            externalSource = null;
            externalSourceChecked = false;
            externalFailures = 0;
            sourceLogged = false;
        }
        if (mode == WindyConfig.WindSourceMode.VANILLA) {
            return null;
        }
        if (!externalSourceChecked) {
            externalSourceChecked = true;
            externalSource = WindSources.create(mode);
        }
        if (externalSource == null) {
            return null;
        }
        try {
            WindSource.Sample sample = externalSource.sample(level, player.getEyePosition());
            externalFailures = 0;
            return sample;
        } catch (Throwable t) {
            // A linkage error is permanent; anything else may be a one-off during a level change.
            if (t instanceof LinkageError || ++externalFailures >= MAX_EXTERNAL_FAILURES) {
                Windy.LOGGER.warn("[Windy] Aeronautics wind sampling failed; falling back to built-in wind.", t);
                externalSource = null;
                externalSourceChecked = true;
            }
            return null;
        }
    }

    /** Leaf blocks that keep their own colour instead of the biome foliage tint. */
    private static int paletteFor(BlockState leaf) {
        if (leaf.is(Blocks.CHERRY_LEAVES)) return LeafParticle.Palette.CHERRY;
        if (leaf.is(Blocks.BIRCH_LEAVES)) return LeafParticle.Palette.BIRCH;
        if (leaf.is(Blocks.AZALEA_LEAVES) || leaf.is(Blocks.FLOWERING_AZALEA_LEAVES)) return LeafParticle.Palette.AZALEA;
        if (leaf.is(Blocks.SPRUCE_LEAVES)) return LeafParticle.Palette.SPRUCE;
        return LeafParticle.Palette.FOLIAGE;
    }

    /** Whole-number sample of a fractional budget (floor + fractional-probability bump). */
    private static int poisson(double budget, RandomSource rng) {
        if (budget <= 0) return 0;
        int base = (int) Math.floor(budget);
        return base + (rng.nextDouble() < (budget - base) ? 1 : 0);
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }
}
