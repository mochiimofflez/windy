package dev.fallingcloud.windy.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A soft, tinted mote — the building block for Driftmote's fireflies, pollen and heat dust. It fades in and
 * out, drifts with a little wander, and (for fireflies) twinkles. Colour, blink, gravity, lifetime and size
 * are baked per palette by the {@link Provider}.
 */
public class DriftMoteParticle extends TextureSheetParticle {

    private final boolean blink;
    private final float baseAlpha;
    private final double wanderPhase;

    protected DriftMoteParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                                int color, boolean blink, float gravity, int lifetime, float size, float baseAlpha) {
        super(level, x, y, z);
        this.blink = blink;
        this.baseAlpha = baseAlpha;
        this.hasPhysics = false; // float freely through foliage
        this.gravity = gravity;
        this.lifetime = lifetime + this.random.nextInt(Math.max(1, lifetime / 2));
        this.quadSize = size * (0.7f + this.random.nextFloat() * 0.6f);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.rCol = ((color >> 16) & 0xFF) / 255.0f;
        this.gCol = ((color >> 8) & 0xFF) / 255.0f;
        this.bCol = (color & 0xFF) / 255.0f;
        this.alpha = 0.0f;
        this.wanderPhase = this.random.nextDouble() * Math.PI * 2.0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;

        // Gentle horizontal wander so the field feels alive.
        double t = (this.age + this.wanderPhase);
        this.xd += Math.cos(t * 0.20) * 0.0009;
        this.zd += Math.sin(t * 0.17) * 0.0009;
        this.xd *= 0.94;
        this.zd *= 0.94;

        // Fade in over the first few ticks, out over the last several.
        float fade = Math.min(1.0f, Math.min(this.age / 5.0f, (this.lifetime - this.age) / 10.0f));
        float a = this.baseAlpha * Math.max(0.0f, fade);
        if (this.blink) {
            // gentle glow pulse — never dims to invisible, so the field stays readable
            a *= 0.60f + 0.40f * (0.5f + 0.5f * (float) Math.sin(t * 0.30));
        }
        this.alpha = a < 0 ? 0 : Math.min(a, 1.0f);
    }

    /** One provider per palette flavour, carrying the baked colour/behaviour. */
    public record Provider(SpriteSet sprites, int color, boolean blink, float gravity,
                           int lifetime, float size, float baseAlpha) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double dx, double dy, double dz) {
            DriftMoteParticle p = new DriftMoteParticle(level, x, y, z, dx, dy, dz,
                    color, blink, gravity, lifetime, size, baseAlpha);
            p.pickSprite(sprites);
            return p;
        }
    }
}
