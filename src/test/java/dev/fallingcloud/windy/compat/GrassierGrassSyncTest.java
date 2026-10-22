package dev.fallingcloud.windy.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GrassierGrassSyncTest {
    @Test
    void calmIsStillAndOrdinaryWindIsVisible() {
        assertEquals(0, GrassierGrassSync.grassSpeed(0.0f));
        assertEquals(58, GrassierGrassSync.grassSpeed(0.16f));
        assertEquals(100, GrassierGrassSync.grassSpeed(1.0f));
        assertEquals(120, GrassierGrassSync.grassSpeed(1.4f));
    }
}
