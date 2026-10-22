package dev.fallingcloud.windy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindMathTest {
    private static final float EPSILON = 1.0e-4f;

    @Test
    void yawPointsTowardTheWindVector() {
        // Aeronautics vectors are world-space "toward" vectors: +X is east, +Z is south.
        assertEquals(90.0f, WindMath.headingDegrees(WindMath.yawToward(1.0, 0.0)), EPSILON);
        assertEquals(180.0f, WindMath.headingDegrees(WindMath.yawToward(0.0, 1.0)), EPSILON);
        assertEquals(270.0f, WindMath.headingDegrees(WindMath.yawToward(-1.0, 0.0)), EPSILON);
        assertEquals(0.0f, WindMath.headingDegrees(WindMath.yawToward(0.0, -1.0)), EPSILON);
    }

    @Test
    void meteorologicalBearingIsConvertedByHalfATurn() {
        // A wind FROM the east (bearing 90) blows TOWARD the west (heading 270).
        assertEquals(270.0f, WindMath.headingDegrees(WindMath.yawTowardFromBearing(90.0)), EPSILON);
        // A wind FROM the north (bearing 0) blows TOWARD the south (heading 180).
        assertEquals(180.0f, WindMath.headingDegrees(WindMath.yawTowardFromBearing(0.0)), EPSILON);
    }

    @Test
    void visualStrengthMapsPhysicalSpeedIntoWindysBudget() {
        // Default mapping: 20 m/s is nominal full strength, 6 m/s is a light breeze, and the
        // visual budget saturates at 1.4 rather than following Aeronautics to 55 m/s.
        assertEquals(0.30f, WindMath.visualStrength(6.0, 20.0), EPSILON);
        assertEquals(1.00f, WindMath.visualStrength(20.0, 20.0), EPSILON);
        assertEquals(1.40f, WindMath.visualStrength(40.0, 20.0), EPSILON);
        assertEquals(0.0f, WindMath.visualStrength(-1.0, 20.0), EPSILON);
        assertEquals(0.0f, WindMath.visualStrength(10.0, 0.0), EPSILON);
    }

    @Test
    void headingIsNormalised() {
        // World yaw 260 points a little north of east, which is compass heading 350.
        assertEquals(350.0f, WindMath.headingDegrees((float) Math.toRadians(260.0)), EPSILON);
        assertEquals(10.0f, WindMath.headingDegrees((float) Math.toRadians(280.0)), EPSILON);
    }
}
