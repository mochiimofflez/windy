package dev.fallingcloud.windy.ambient;

import dev.fallingcloud.windy.WindyConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Drives the whole effect off the client tick. Each tick it samples the biome/time/weather around the
 * player, resolves a {@link BiomePalette}, works out a polite emission budget, and asks the vanilla particle
 * engine to add that many motes at random air positions nearby. No frame injection whatsoever.
 */
public final class AmbianceDirector {
    public static final AmbianceDirector INSTANCE = new AmbianceDirector();

    private String cachedBiomeKey = null;
    private BiomePalette cachedPalette = BiomePalette.NONE;

    private AmbianceDirector() {}

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        WindyConfig cfg = WindyConfig.get();
        if (!cfg.ambientEnabled() || cfg.ambientDensity() == WindyConfig.Density.OFF) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;

        RandomSource random = level.random;
        BlockPos feet = player.blockPosition();

        // Resolve the palette for the biome the player stands in (cached until the biome id changes).
        Holder<Biome> biome = level.getBiome(feet);
        String key = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
        if (!key.equals(cachedBiomeKey)) {
            cachedBiomeKey = key;
            cachedPalette = BiomePalette.classify(key, biome.value().getBaseTemperature(), biome.value().hasPrecipitation());
        }
        BiomePalette palette = cachedPalette;
        if (palette == BiomePalette.NONE || !palette.enabledIn(cfg) || palette.particles.isEmpty()) return;

        // Time-of-day gate.
        if (cfg.dayNightAware() && palette.timeGate != BiomePalette.TimeGate.ALWAYS) {
            boolean daytime = (level.getDayTime() % 24000L) < 12000L;
            boolean active = switch (palette.timeGate) {
                case DAY -> daytime;
                case DUSK_NIGHT -> !daytime;
                case ALWAYS -> true;
            };
            if (!active) return;
        }

        double budget = baseBudget(cfg.ambientDensity());

        // Honour the vanilla particle setting.
        if (cfg.respectVanillaParticleSetting()) {
            switch (mc.options.particles().get()) {
                case MINIMAL -> { return; }
                case DECREASED -> budget *= 0.5;
                default -> { /* ALL: full budget */ }
            }
        }

        // Outdoor palettes fade with weather and enclosure; indoor/volcanic ones are unaffected.
        if (palette.outdoor) {
            if (cfg.weatherAware()) {
                budget *= (1.0 - level.getRainLevel(1.0f));
            }
            if (cfg.respectEnclosure() && !level.canSeeSky(feet)) {
                budget *= 0.7;
            }
        }
        if (budget <= 0.0) return;

        int count = (int) Math.floor(budget) + (random.nextDouble() < (budget - Math.floor(budget)) ? 1 : 0);
        if (count <= 0) return;

        Vec3 center = player.getEyePosition();
        int radius = cfg.ambientRadius();
        for (int i = 0; i < count; i++) {
            Vec3 pos = SpawnField.sample(level, center, radius, palette.verticalBias, random);
            if (pos == null) continue;
            ParticleOptions type = palette.particles.get(random.nextInt(palette.particles.size())).get();
            double jitter = 0.012;
            double vx = (random.nextDouble() - 0.5) * jitter;
            double vy = palette.rise + (random.nextDouble() - 0.5) * jitter;
            double vz = (random.nextDouble() - 0.5) * jitter;
            level.addParticle(type, pos.x, pos.y, pos.z, vx, vy, vz);
        }
    }

    private static double baseBudget(WindyConfig.Density density) {
        return switch (density) {
            case SUBTLE -> 2.5;
            case LUSH -> 8.0;
            case DENSE -> 18.0;
            default -> 0.0;
        };
    }
}
