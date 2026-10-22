package dev.fallingcloud.windy.compat;

import dev.fallingcloud.windy.Windy;
import dev.fallingcloud.windy.WindyConfig;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Creates the configured external wind source without ever hard-linking Aeronautics classes into the
 * normal Windy code path.
 */
public final class WindSources {
    private static boolean warned;

    private WindSources() {}

    @Nullable
    public static WindSource create(WindyConfig.WindSourceMode mode) {
        if (mode == WindyConfig.WindSourceMode.VANILLA) {
            return null;
        }
        if (!ModList.get().isLoaded("aeronauticswinds")) {
            if (mode == WindyConfig.WindSourceMode.AERONAUTICS) {
                warnOnce("Aeronautics is not loaded; using Windy's built-in wind.");
            }
            return null;
        }
        try {
            return newAeronautics();
        } catch (Throwable t) {
            warnOnce("Aeronautics wind integration is unavailable (" + t.getClass().getSimpleName()
                    + ": " + t.getMessage() + "); using Windy's built-in wind.");
            return null;
        }
    }

    /**
     * Kept in its own method so the JVM only resolves {@link AeronauticsWindSource} when an
     * Aeronautics-loaded game actually reaches this branch.
     */
    private static WindSource newAeronautics() {
        return new AeronauticsWindSource();
    }

    private static void warnOnce(String message) {
        if (!warned) {
            warned = true;
            Windy.LOGGER.warn("[Windy] {}", message);
        }
    }
}
