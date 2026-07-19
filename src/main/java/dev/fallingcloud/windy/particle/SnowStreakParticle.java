package dev.fallingcloud.windy.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A snow streak whipped sideways through a cold storm — the fast, near-horizontal snow vanilla's gentle
 * flakes never show. It flies with the wind, points the way it is going (see {@link StreakParticle}),
 * sinks only slightly, and dies against the first thing it hits, so a wall of streaks breaks correctly
 * around shelter. Sprite length is picked at random, so the storm has short flecks and long lashes.
 */
public class SnowStreakParticle extends StreakParticle {

    protected SnowStreakParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = true;
        this.gravity = 0.02f;
        this.friction = 0.99f;
        this.lifetime = 20 + this.random.nextInt(14);
        this.quadSize = 0.11f + this.random.nextFloat() * 0.13f;
        this.rCol = 0.96f;
        this.gCol = 0.97f;
        this.bCol = 1.0f;
        this.alpha = 0.0f;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;
        if (this.onGround) {
            this.remove();
            return;
        }
        float t = this.age / (float) this.lifetime;
        this.alpha = (float) Math.sin(Math.min(1.0f, t) * Math.PI) * 0.85f;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            SnowStreakParticle p = new SnowStreakParticle(level, x, y, z, vx, vy, vz);
            p.pickSprite(sprites);   // random length variant
            return p;
        }
    }
}
