package dev.fallingcloud.windy;

/**
 * Small, pure-math helpers shared by the vanilla wind director and external wind sources.
 *
 * <p>The important convention is the same one Windy has always used: a yaw is the direction the wind
 * blows <b>toward</b>, not the meteorological bearing it comes from. Aeronautics reports a bearing in
 * the opposite sense, so adapters must convert with {@link #yawTowardFromBearing(double)} rather than
 * copying the bearing into a yaw.
 */
public final class WindMath {
    private WindMath() {}

    /** Yaw (radians) of a world-space X/Z vector, pointing in the direction the wind blows toward. */
    public static float yawToward(double x, double z) {
        return (float) Math.atan2(z, x);
    }

    /** Yaw (radians) for a meteorological "wind from" bearing in degrees. */
    public static float yawTowardFromBearing(double bearingDegrees) {
        // Two steps: +180 turns "from" into "toward", and -90 converts a compass angle (0 = north,
        // 90 = east) into atan2(Z, X) (0 = east, north = -Z). Together: +90.
        return (float) Math.toRadians(bearingDegrees + 90.0);
    }

    /**
     * Maps a physical wind speed to Windy's unitless 0..1.4 visual strength.
     *
     * @param speedMps         local wind speed in metres per second
     * @param fullStrengthMps  speed at which the visuals reach nominal full strength
     */
    public static float visualStrength(double speedMps, double fullStrengthMps) {
        if (!Double.isFinite(speedMps) || !Double.isFinite(fullStrengthMps) || fullStrengthMps <= 0.0) {
            return 0.0f;
        }
        return clamp((float) (speedMps / fullStrengthMps), 0.0f, 1.4f);
    }

    /**
     * Compass heading in degrees (0 = north, 90 = east) for a "toward" yaw.
     *
     * <p>The yaw itself is a world-space atan2(Z, X), so 0 points east (+X) and 90 points south
     * (+Z). Compass headings are that angle rotated a quarter turn: east is 90, south is 180.
     */
    public static float headingDegrees(float yawToward) {
        if (!Float.isFinite(yawToward)) {
            return 0.0f;
        }
        double degrees = (Math.toDegrees(yawToward) + 90.0) % 360.0;
        if (degrees < 0.0) {
            degrees += 360.0;
        }
        if (degrees >= 359.9995) {
            degrees = 0.0;
        }
        return (float) degrees;
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
