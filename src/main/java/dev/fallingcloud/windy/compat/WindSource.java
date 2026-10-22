package dev.fallingcloud.windy.compat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A source of local wind for {@link dev.fallingcloud.windy.WindDirector}.
 *
 * <p>Implementations are client-only and must be safe to call once per client tick. A source that is
 * temporarily unavailable should return {@code null} rather than throwing; the director will fall back
 * to its built-in wind.
 */
public interface WindSource {
    @Nullable
    Sample sample(ClientLevel level, Vec3 position);

    /**
     * One tick's worth of wind, already converted to Windy's conventions.
     *
     * @param yawToward  radians, direction the wind blows toward
     * @param strength   Windy visual strength, 0..1.4
     * @param gust       gust component used for leaf/dust budgets, 0..1.4
     * @param speedMps   physical speed, for diagnostics
     * @param weather    regional weather, or {@code null} when the source has none
     */
    record Sample(float yawToward, float strength, float gust, double speedMps,
                  @Nullable Weather weather) {
        public Sample {
            if (!Float.isFinite(yawToward) || !Float.isFinite(strength) || !Float.isFinite(gust)
                    || !Double.isFinite(speedMps)) {
                throw new IllegalArgumentException("non-finite wind sample");
            }
        }
    }

    /**
     * Region weather flags independent of any one mod's enum, so the particle and ambient systems do
     * not need a compile-time dependency on Aeronautics.
     */
    record Weather(boolean wet, boolean frozen, boolean sandy, boolean hot,
                   boolean stormy, boolean foggy, String name) {
        public Weather {
            if (name == null || name.isBlank()) {
                name = "unknown";
            }
        }
    }
}
