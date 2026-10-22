package dev.fallingcloud.windy.compat;

import dev.fallingcloud.windy.Windy;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.neoforged.fml.ModList;

/**
 * Pushes Windy's wind into Grassier Grass so the shader grass sways with the same wind as Windy's
 * particles.
 *
 * <p>Grassier Grass has no formal API, so this uses reflection on its public in-memory setters
 * ({@code GrassConfig.setWindSpeed/setWindDirectionDegrees/setDynamicWind}); nothing is linked at
 * compile time and nothing is written to its config file. Its speed scale is 100 = "original wind",
 * and its direction is degrees on the XZ plane with 0 = +X and 90 = +Z, the same convention as
 * Windy's yaw. Any failure disables the sync for the session and leaves Grassier Grass untouched.
 */
public final class GrassierGrassSync {
    public static final GrassierGrassSync INSTANCE = new GrassierGrassSync();

    private static final String MOD_ID = "grassiergrass";
    private static final String CONFIG_CLASS = "com.leonardoinc22.shortgrass.config.GrassConfig";

    private boolean resolved;
    private boolean available;
    private boolean active;
    private MethodHandle setSpeed;
    private MethodHandle setDirection;
    private MethodHandle setDynamic;
    private MethodHandle getSpeed;
    private MethodHandle getDirection;
    private MethodHandle getDynamic;

    private int savedSpeed;
    private float savedDirection;
    private boolean savedDynamic;

    private GrassierGrassSync() {}

    /**
     * Applies Windy's wind to Grassier Grass, or hands control back when {@code follow} is false.
     *
     * @param yawToward direction the wind blows toward, radians, atan2(z, x)
     * @param strength  Windy's effective visual strength, 0..1.4 (1.0 = Grassier Grass's default wind)
     */
    public void update(boolean follow, float yawToward, float strength) {
        if (!follow) {
            release();
            return;
        }
        if (!resolve()) return;
        try {
            if (!active) {
                savedSpeed = (int) getSpeed.invoke();
                savedDirection = (float) getDirection.invoke();
                savedDynamic = (boolean) getDynamic.invoke();
                active = true;
            }
            setDynamic.invoke(false);
            setDirection.invoke((float) Math.toDegrees(yawToward));
            setSpeed.invoke(Math.round(Math.max(0.0f, strength) * 100.0f));
        } catch (Throwable t) {
            disable(t);
        }
    }

    /** Restores whatever wind Grassier Grass had before Windy took over. */
    public void release() {
        if (!active) return;
        active = false;
        try {
            setDirection.invoke(savedDirection);
            setSpeed.invoke(savedSpeed);
            setDynamic.invoke(savedDynamic);
        } catch (Throwable t) {
            disable(t);
        }
    }

    private boolean resolve() {
        if (resolved) return available;
        resolved = true;
        if (!ModList.get().isLoaded(MOD_ID)) return false;
        try {
            Class<?> config = Class.forName(CONFIG_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            setSpeed = lookup.findStatic(config, "setWindSpeed", MethodType.methodType(void.class, int.class));
            setDirection = lookup.findStatic(config, "setWindDirectionDegrees",
                    MethodType.methodType(void.class, float.class));
            setDynamic = lookup.findStatic(config, "setDynamicWind", MethodType.methodType(void.class, boolean.class));
            getSpeed = lookup.findStatic(config, "configuredWindSpeed", MethodType.methodType(int.class));
            getDirection = lookup.findStatic(config, "configuredWindDirectionDegrees",
                    MethodType.methodType(float.class));
            getDynamic = lookup.findStaticGetter(config, "dynamicWind", boolean.class);
            available = true;
            Windy.LOGGER.info("[Windy] Grassier Grass found; syncing its wind with Windy.");
        } catch (Throwable t) {
            disable(t);
        }
        return available;
    }

    private void disable(Throwable t) {
        available = false;
        resolved = true;
        active = false;
        Windy.LOGGER.warn("[Windy] Grassier Grass wind sync is unavailable ({}: {}); leaving it alone.",
                t.getClass().getSimpleName(), t.getMessage());
    }
}
