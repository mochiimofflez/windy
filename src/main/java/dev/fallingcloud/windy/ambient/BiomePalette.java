package dev.fallingcloud.windy.ambient;

import dev.fallingcloud.windy.WindyConfig;
import dev.fallingcloud.windy.particle.WindParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import java.util.List;
import java.util.function.Supplier;

/**
 * The set of ambient "moods" Windy can emit, plus the logic that maps a biome + time-of-day + weather
 * context onto one of them. Fireflies, pollen and heat dust use Windy's own soft tinted motes; the rest
 * reuse fitting vanilla particles. Particle options are held as suppliers so the custom types resolve after
 * registration rather than at class-load.
 */
public enum BiomePalette {
    NONE(List.of(), TimeGate.ALWAYS, false, 0.0f, 0.0f),

    /** Warm, twinkling motes hanging in the air over swamps and forests after dark. */
    FIREFLIES(List.of(WindParticles.FIREFLY::get, WindParticles.FIREFLY::get),
            TimeGate.DUSK_NIGHT, true, 0.15f, 0.004f),

    /** Drifting pollen in jungles, flower forests and lush caves during the day. */
    SPORES(List.of(WindParticles.POLLEN::get, () -> ParticleTypes.SPORE_BLOSSOM_AIR),
            TimeGate.DAY, true, 0.10f, 0.002f),

    /** Fine heat dust skidding low across deserts, badlands and savannas by day. */
    HEAT_DUST(List.of(WindParticles.HEAT_DUST::get, () -> ParticleTypes.WHITE_ASH),
            TimeGate.DAY, true, -0.2f, 0.006f),

    /** Embers, flame flecks and smoke rising near lava and in the volcanic Nether biomes. */
    EMBERS(List.of(() -> ParticleTypes.LAVA, () -> ParticleTypes.FLAME, () -> ParticleTypes.SMALL_FLAME, () -> ParticleTypes.SMOKE),
            TimeGate.ALWAYS, false, 0.15f, 0.03f),

    /** Soul-fire tinted embers for the Soul Sand Valley. */
    SOUL_EMBERS(List.of(() -> ParticleTypes.SOUL_FIRE_FLAME, () -> ParticleTypes.SOUL, () -> ParticleTypes.SMOKE),
            TimeGate.ALWAYS, false, 0.15f, 0.03f),

    /** Sparkling snow motes on frozen peaks and snowy biomes. */
    SNOW(List.of(() -> ParticleTypes.SNOWFLAKE),
            TimeGate.ALWAYS, true, 0.1f, 0.01f),

    /** Cherry blossom petals swirling through the grove. */
    PETALS(List.of(() -> ParticleTypes.CHERRY_LEAVES),
            TimeGate.ALWAYS, true, 0.25f, 0.02f);

    /** When during the day/night cycle this palette is active (only consulted when day/night awareness is on). */
    public enum TimeGate { ALWAYS, DAY, DUSK_NIGHT }

    public final List<Supplier<ParticleOptions>> particles;
    public final TimeGate timeGate;
    /** Outdoor palettes fade with weather and enclosure; indoor/volcanic ones don't. */
    public final boolean outdoor;
    /** Vertical bias applied to spawn height, in fraction of radius (positive = above eye height). */
    public final float verticalBias;
    /** Base upward velocity given to each mote so it drifts rather than falls. */
    public final float rise;

    BiomePalette(List<Supplier<ParticleOptions>> particles, TimeGate timeGate, boolean outdoor,
                 float verticalBias, float rise) {
        this.particles = particles;
        this.timeGate = timeGate;
        this.outdoor = outdoor;
        this.verticalBias = verticalBias;
        this.rise = rise;
    }

    public boolean enabledIn(WindyConfig cfg) {
        return switch (this) {
            case FIREFLIES -> cfg.fireflies();
            case SPORES -> cfg.pollen();
            case HEAT_DUST -> cfg.heatDust();
            case EMBERS, SOUL_EMBERS -> cfg.embers();
            case SNOW -> cfg.snowSparkle();
            case PETALS -> cfg.cherryPetals();
            case NONE -> false;
        };
    }

    /**
     * Classify a biome into a palette purely from its registry id path, base temperature and precipitation.
     * Using the id path (rather than biome tags) keeps this free of any tag-existence assumptions.
     */
    public static BiomePalette classify(String path, float temperature, boolean hasPrecipitation) {
        if (path == null) path = "";

        if (path.contains("soul_sand")) return SOUL_EMBERS;
        if (path.contains("basalt_deltas") || path.contains("nether_wastes")
                || path.contains("crimson_forest") || path.contains("warped_forest")) return EMBERS;

        if (path.contains("cherry")) return PETALS;

        if (path.contains("swamp") || path.contains("mangrove")) return FIREFLIES;

        if (path.contains("jungle") || path.contains("bamboo")
                || path.contains("flower_forest") || path.contains("lush_caves")) return SPORES;

        if (path.contains("desert") || path.contains("badlands")
                || path.contains("mesa") || path.contains("savanna")) return HEAT_DUST;

        if (path.contains("snowy") || path.contains("frozen") || path.contains("ice")
                || path.contains("grove") || path.contains("peak") || path.contains("taiga")
                || temperature < 0.15f) return SNOW;

        if (path.contains("forest") || path.contains("birch")
                || path.contains("meadow")) return FIREFLIES;

        return NONE;
    }
}
