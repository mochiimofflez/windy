package dev.fallingcloud.windy.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A pale streak that rides the wind — the moment wind becomes visible. It flies fast along the wind the
 * director gives it, wobbles a little vertically, and fades in and out over a short life. It points the
 * way it is going (see {@link StreakParticle}), and its sprite is picked at random from a few lengths,
 * so a gust reads as a scatter of long and short streaks rather than a swarm of identical dashes. No
 * physics: wisps pass through leaves and long grass the way wind does.
 */
public class WispParticle extends StreakParticle {

    protected WispParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = 0.995f;
        this.lifetime = 30 + this.random.nextInt(22);
        // half-extent: the streak spans twice this, and the sprite keeps it a tenth as thick as it is long
        this.quadSize = 0.22f + this.random.nextFloat() * 0.28f;
        // near-white with a cool touch, so it reads over grass and sky alike
        this.rCol = 0.92f;
        this.gCol = 0.96f;
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
        float t = this.age / (float) this.lifetime;
        // Soft in, soft out — but kept above the particle shader's 0.1 alpha discard at the peak, or the
        // streak's feathered ends would clip off instead of fading.
        this.alpha = (float) Math.sin(Math.min(1.0f, t) * Math.PI) * 0.62f;
        // gentle vertical wobble so the streak doesn't fly a laser-straight line
        this.yd += Math.sin(this.age * 0.35 + this.quadSize * 40) * 0.0012;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            WispParticle p = new WispParticle(level, x, y, z, vx, vy, vz);
            p.pickSprite(sprites);   // random length variant
            return p;
        }
    }
}
