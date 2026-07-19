package dev.fallingcloud.windy.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A leaf torn from the canopy. It sails with the wind, flutters (side-to-side wobble + spin), sinks
 * slowly, and settles briefly where it lands before fading.
 *
 * <p>The spawn call carries the horizontal wind velocity in {@code vx}/{@code vz}; {@code vy} carries a
 * small palette id (see {@link Palette}) because most leaves tint themselves to the biome's foliage
 * colour — queried right here at spawn — while cherry, birch and azalea keep their own fixed colours,
 * exactly like the leaf blocks they fall from.
 */
public class LeafParticle extends TextureSheetParticle {

    /** Palette ids the director packs into the spawn vy. */
    public static final class Palette {
        public static final int FOLIAGE = 0; // biome foliage colour (oak/jungle/dark oak/mangrove/acacia)
        public static final int CHERRY = 1;
        public static final int BIRCH = 2;
        public static final int AZALEA = 3;
        public static final int SPRUCE = 4;

        private Palette() {}
    }

    private final float flutterPhase;
    private final float spinSpeed;

    protected LeafParticle(ClientLevel level, double x, double y, double z,
                           double vx, double vz, int palette) {
        super(level, x, y, z, 0, 0, 0);
        // the 6-arg super jitters and renormalises velocity — set exactly what the wind gives
        this.xd = vx;
        this.yd = -0.02 - this.random.nextDouble() * 0.02;
        this.zd = vz;
        this.hasPhysics = true;          // leaves land on the ground and pile against blocks
        this.gravity = 0.09f;
        this.friction = 0.985f;
        this.lifetime = 90 + this.random.nextInt(60);
        this.quadSize = 0.125f + this.random.nextFloat() * 0.075f;
        applyPalette(level, x, y, z, palette);
        this.alpha = 0.95f;
        this.flutterPhase = this.random.nextFloat() * (float) Math.PI * 2;
        this.spinSpeed = (this.random.nextFloat() - 0.5f) * 0.45f;
        this.roll = this.oRoll = this.random.nextFloat() * (float) Math.PI * 2;
    }

    private void applyPalette(ClientLevel level, double x, double y, double z, int palette) {
        float r, g, b;
        switch (palette) {
            case Palette.CHERRY -> { r = 0.91f; g = 0.65f; b = 0.79f; }
            case Palette.BIRCH  -> { r = 0.50f; g = 0.66f; b = 0.33f; }
            case Palette.AZALEA -> { r = 0.40f; g = 0.62f; b = 0.28f; }
            case Palette.SPRUCE -> { r = 0.38f; g = 0.60f; b = 0.38f; }
            default -> {
                int c = BiomeColors.getAverageFoliageColor(level, BlockPos.containing(x, y, z));
                r = ((c >> 16) & 0xFF) / 255.0f;
                g = ((c >> 8) & 0xFF) / 255.0f;
                b = (c & 0xFF) / 255.0f;
            }
        }
        // slight per-leaf variation so a gustful doesn't look like confetti of one colour
        float v = 0.85f + this.random.nextFloat() * 0.3f;
        setColor(Math.min(1.0f, r * v), Math.min(1.0f, g * v), Math.min(1.0f, b * v));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();
        if (this.removed) return;

        if (!this.onGround) {
            // flutter: a falling leaf rocks side to side and spins
            this.xd += Math.sin(this.age * 0.22 + this.flutterPhase) * 0.006;
            this.zd += Math.cos(this.age * 0.19 + this.flutterPhase) * 0.006;
            // fluttering fights the fall: cap descent speed
            if (this.yd < -0.12) this.yd = -0.12;
            this.roll += this.spinSpeed * 0.35f;
        } else {
            // settled: stop drifting and fade out quickly
            this.xd *= 0.4;
            this.zd *= 0.4;
            if (this.lifetime - this.age > 14) {
                this.age = this.lifetime - 14;
            }
        }
        float t = this.age / (float) this.lifetime;
        if (t > 0.82f) {
            this.alpha = 0.95f * (1.0f - (t - 0.82f) / 0.18f);
        }
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            LeafParticle p = new LeafParticle(level, x, y, z, vx, vz, (int) Math.round(vy));
            p.pickSprite(sprites);
            return p;
        }
    }
}
