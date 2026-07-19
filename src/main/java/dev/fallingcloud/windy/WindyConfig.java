package dev.fallingcloud.windy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User-facing settings for Windy, backed by a NeoForge {@link ModConfigSpec} and edited from the
 * <b>Mods &rarr; Windy &rarr; Config</b> screen (NeoForge auto-generates the GUI). Persisted to
 * {@code config/windy-client.toml}.
 *
 * <p>Values are read through accessor methods rather than public fields so the backing store stays an
 * implementation detail. If a {@code config/zephyr.json} from before the rename is present its values seed
 * the defaults, so
 * settings tuned under the old Sodium options page carry over on first launch. That file is only ever read,
 * never written or deleted.
 */
public final class WindyConfig {
    public static final ModConfigSpec SPEC;

    private static final WindyConfig INSTANCE = new WindyConfig();

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.EnumValue<Intensity> INTENSITY;
    private static final ModConfigSpec.IntValue RADIUS;
    private static final ModConfigSpec.IntValue GUSTINESS;
    private static final ModConfigSpec.BooleanValue STRIPES;
    private static final ModConfigSpec.BooleanValue MOTES;
    private static final ModConfigSpec.BooleanValue WISPS;
    private static final ModConfigSpec.BooleanValue LEAVES;
    private static final ModConfigSpec.BooleanValue SNOW;
    private static final ModConfigSpec.BooleanValue DUST;
    private static final ModConfigSpec.BooleanValue RESPECT_PARTICLES;

    private static final ModConfigSpec.BooleanValue AMBIENT_ENABLED;
    private static final ModConfigSpec.EnumValue<Density> AMBIENT_DENSITY;
    private static final ModConfigSpec.IntValue AMBIENT_RADIUS;
    private static final ModConfigSpec.BooleanValue FIREFLIES;
    private static final ModConfigSpec.BooleanValue POLLEN;
    private static final ModConfigSpec.BooleanValue HEAT_DUST;
    private static final ModConfigSpec.BooleanValue EMBERS;
    private static final ModConfigSpec.BooleanValue SNOW_SPARKLE;
    private static final ModConfigSpec.BooleanValue CHERRY_PETALS;
    private static final ModConfigSpec.BooleanValue DAY_NIGHT;
    private static final ModConfigSpec.BooleanValue WEATHER_AWARE;
    private static final ModConfigSpec.BooleanValue RESPECT_ENCLOSURE;

    /** How much of the wind shows per tick. */
    public enum Intensity { OFF, SUBTLE, NORMAL, LIVELY }

    /** How many ambient biome motes hang in the air. */
    public enum Density { OFF, SUBTLE, LUSH, DENSE }

    static {
        JsonObject old = readLegacyJson();
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("How much wind there is and how far it reaches.")
                .translation("windy.config.general").push("general");
        ENABLED = b.comment("Master switch for the living wind.")
                .translation("windy.config.enabled")
                .define("enabled", bool(old, "enabled", true));
        INTENSITY = b.comment("How much of the wind you see per tick. OFF stops it entirely.")
                .translation("windy.config.intensity")
                .defineEnum("intensity", legacyIntensity(old));
        RADIUS = b.comment("How far around you the wind is drawn, in blocks.")
                .translation("windy.config.radius")
                .defineInRange("radius", integer(old, "radius", 18), 8, 48);
        GUSTINESS = b.comment("How often the wind gathers into a gust, as a percentage. 0 keeps the world calm.")
                .translation("windy.config.gustiness")
                .defineInRange("gustiness", integer(old, "gustiness", 65), 0, 100);
        b.pop();

        b.comment("Individual wind effects.")
                .translation("windy.config.effects").push("effects");
        STRIPES = b.comment("Long flowing ribbons of air that snake downwind and follow the land over hills.")
                .translation("windy.config.stripes")
                .define("stripes", bool(old, "stripes", true));
        MOTES = b.comment("Tiny specks tumbling in the current, everywhere the wind reaches.")
                .translation("windy.config.motes")
                .define("motes", bool(old, "motes", true));
        WISPS = b.comment("Pale streaks that ride the wind while a gust is up.")
                .translation("windy.config.wisps")
                .define("wisps", bool(old, "wisps", true));
        LEAVES = b.comment("Leaves pulled from tree canopies, tinted to each biome's own foliage.")
                .translation("windy.config.leaves")
                .define("leaves", bool(old, "leaves", true));
        SNOW = b.comment("Snow whipped near-horizontal while it snows in cold biomes.")
                .translation("windy.config.snow")
                .define("snow", bool(old, "snow", true));
        DUST = b.comment("Dry motes lifted off sand in hot, rainless biomes.")
                .translation("windy.config.dust")
                .define("dust", bool(old, "dust", true));
        b.pop();

        JsonObject oldAmbient = readLegacyAmbientJson();
        b.comment("Ambient motes that hang in the air of each biome.")
                .translation("windy.config.ambient").push("ambient");
        AMBIENT_ENABLED = b.comment("Master switch for the ambient biome motes.")
                .translation("windy.config.ambient_enabled")
                .define("enabled", bool(oldAmbient, "enabled", true));
        AMBIENT_DENSITY = b.comment("How much living air to emit. OFF stops it entirely.")
                .translation("windy.config.ambient_density")
                .defineEnum("density", legacyDensity(oldAmbient));
        AMBIENT_RADIUS = b.comment("Radius of the sphere around you that motes appear in, in blocks.")
                .translation("windy.config.ambient_radius")
                .defineInRange("radius", integer(oldAmbient, "radius", 18), 8, 64);
        FIREFLIES = b.comment("Warm motes over swamps and forests after dark.")
                .translation("windy.config.fireflies")
                .define("fireflies", bool(oldAmbient, "fireflies", true));
        POLLEN = b.comment("Spores drifting through jungles, flower forests and lush caves by day.")
                .translation("windy.config.pollen")
                .define("pollen", bool(oldAmbient, "pollen", true));
        HEAT_DUST = b.comment("Fine ash skidding across deserts, badlands and savannas.")
                .translation("windy.config.heat_dust")
                .define("heatDust", bool(oldAmbient, "heatDust", true));
        EMBERS = b.comment("Embers, flame flecks and smoke near lava and in the volcanic Nether.")
                .translation("windy.config.embers")
                .define("embers", bool(oldAmbient, "embers", true));
        SNOW_SPARKLE = b.comment("Sparkling snow motes on frozen peaks and snowy biomes.")
                .translation("windy.config.snow_sparkle")
                .define("snowSparkle", bool(oldAmbient, "snowSparkle", true));
        CHERRY_PETALS = b.comment("Blossom petals swirling through cherry groves.")
                .translation("windy.config.cherry_petals")
                .define("cherryPetals", bool(oldAmbient, "cherryPetals", true));
        DAY_NIGHT = b.comment("Fireflies come out at dusk; spores and dust show by day.")
                .translation("windy.config.day_night")
                .define("dayNightAware", bool(oldAmbient, "dayNightAware", true));
        WEATHER_AWARE = b.comment("Fade outdoor motes out during rain and thunder.")
                .translation("windy.config.weather_aware")
                .define("weatherAware", bool(oldAmbient, "weatherAware", true));
        RESPECT_ENCLOSURE = b.comment("Damp outdoor motes when you can't see the sky (indoors / deep underground).")
                .translation("windy.config.enclosure")
                .define("respectEnclosure", bool(oldAmbient, "respectEnclosure", true));
        b.pop();

        b.comment("How Windy behaves alongside vanilla settings.")
                .translation("windy.config.compat").push("compatibility");
        RESPECT_PARTICLES = b.comment("Ease off when the vanilla particle setting is Decreased, and stop on Minimal.")
                .translation("windy.config.vanilla_particles")
                .define("respectVanillaParticleSetting", bool(old, "respectVanillaParticleSetting", true));
        b.pop();

        SPEC = b.build();
    }

    private WindyConfig() {}

    public static WindyConfig get() {
        return INSTANCE;
    }

    // --- accessors -----------------------------------------------------------
    public boolean enabled()                       { return ENABLED.get(); }
    public Intensity intensity()                   { return INTENSITY.get(); }
    public int radius()                            { return RADIUS.get(); }
    public int gustiness()                         { return GUSTINESS.get(); }
    public boolean stripes()                       { return STRIPES.get(); }
    public boolean motes()                         { return MOTES.get(); }
    public boolean wisps()                         { return WISPS.get(); }
    public boolean leaves()                        { return LEAVES.get(); }
    public boolean snow()                          { return SNOW.get(); }
    public boolean dust()                          { return DUST.get(); }
    public boolean respectVanillaParticleSetting() { return RESPECT_PARTICLES.get(); }

    // --- ambient biome motes -------------------------------------------------
    public boolean ambientEnabled()   { return AMBIENT_ENABLED.get(); }
    public Density ambientDensity()   { return AMBIENT_DENSITY.get(); }
    public int ambientRadius()        { return AMBIENT_RADIUS.get(); }
    public boolean fireflies()        { return FIREFLIES.get(); }
    public boolean pollen()           { return POLLEN.get(); }
    public boolean heatDust()         { return HEAT_DUST.get(); }
    public boolean embers()           { return EMBERS.get(); }
    public boolean snowSparkle()      { return SNOW_SPARKLE.get(); }
    public boolean cherryPetals()     { return CHERRY_PETALS.get(); }
    public boolean dayNightAware()    { return DAY_NIGHT.get(); }
    public boolean weatherAware()     { return WEATHER_AWARE.get(); }
    public boolean respectEnclosure() { return RESPECT_ENCLOSURE.get(); }

    // --- legacy config/zephyr.json seeding -----------------------------------
    // The mod was called Zephyr until 2026-07-18, so the file to migrate from still has the old name.
    private static JsonObject readLegacyJson() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("zephyr.json");
            if (Files.exists(path)) {
                JsonElement parsed = JsonParser.parseString(Files.readString(path));
                if (parsed.isJsonObject()) {
                    Windy.LOGGER.info("[Windy] Seeding config defaults from legacy config/zephyr.json.");
                    return parsed.getAsJsonObject();
                }
            }
        } catch (Exception e) {
            Windy.LOGGER.warn("[Windy] Could not read legacy config/zephyr.json, using built-in defaults", e);
        }
        return null;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        try {
            return o != null && o.has(key) ? o.get(key).getAsBoolean() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static int integer(JsonObject o, String key, int def) {
        try {
            return o != null && o.has(key) ? o.get(key).getAsInt() : def;
        } catch (Exception e) {
            return def;
        }
    }

    /** The ambient-mote settings previously lived in their own file; read it so tuning carries over. */
    private static JsonObject readLegacyAmbientJson() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("driftmote.json");
            if (Files.exists(path)) {
                JsonElement parsed = JsonParser.parseString(Files.readString(path));
                if (parsed.isJsonObject()) {
                    Windy.LOGGER.info("[Windy] Seeding ambient-mote defaults from legacy config/driftmote.json.");
                    return parsed.getAsJsonObject();
                }
            }
        } catch (Exception e) {
            Windy.LOGGER.warn("[Windy] Could not read legacy config/driftmote.json, using built-in defaults", e);
        }
        return null;
    }

    private static Density legacyDensity(JsonObject o) {
        int i = integer(o, "density", Density.LUSH.ordinal());
        Density[] values = Density.values();
        return values[Math.max(0, Math.min(values.length - 1, i))];
    }

    private static Intensity legacyIntensity(JsonObject o) {
        int i = integer(o, "intensity", Intensity.NORMAL.ordinal());
        Intensity[] values = Intensity.values();
        return values[Math.max(0, Math.min(values.length - 1, i))];
    }
}
