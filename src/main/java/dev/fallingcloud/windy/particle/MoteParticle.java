package dev.fallingcloud.windy.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A tiny speck tumbling in the airflow — the "random particles" of the wind. Small enough to read as
 * dust or seed-fluff caught in the current, it drifts with the wind, wanders a little, and blinks out.
 * These fill the air between the big effects so the wind feels present everywhere, not just where a
 * stripe happens to fly.
 */
public class MoteParticle extends TextureSheetParticle {

    private final float wanderPhase;

    protected MoteParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        super(level, x, y, z, 0, 0, 0);
        // the 6-arg super jitters and renormalises velocity — assign exactly what the director sent
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = 0.996f;
        this.lifetime = 44 + this.random.nextInt(40);
        this.quadSize = 0.028f + this.random.nextFloat() * 0.032f;
        float warm = this.random.nextFloat() * 0.08f;
        this.rCol = 0.90f + warm;
        this.gCol = 0.92f + warm * 0.5f;
        this.bCol = 0.96f;
        this.alpha = 0.0f;
        this.wanderPhase = this.random.nextFloat() * (float) Math.PI * 2;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;
        float t = this.age / (float) this.lifetime;
        this.alpha = (float) Math.sin(Math.min(1.0f, t) * Math.PI) * 0.55f;
        // a light tumble around the main current
        this.xd += Math.sin(this.age * 0.31 + wanderPhase) * 0.0009;
        this.yd += Math.cos(this.age * 0.23 + wanderPhase) * 0.0009;
        this.zd += Math.sin(this.age * 0.27 + wanderPhase * 2) * 0.0009;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            MoteParticle p = new MoteParticle(level, x, y, z, vx, vy, vz);
            p.pickSprite(sprites);
            return p;
        }
    }
}
