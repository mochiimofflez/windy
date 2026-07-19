package dev.fallingcloud.windy.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A dry mote of dust lifted off the dunes. It skims low with the wind, bobs a little, and thins out —
 * a heat-and-wind shimmer for deserts and badlands that stays subtle by design.
 */
public class DustMoteParticle extends TextureSheetParticle {

    protected DustMoteParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz) {
        super(level, x, y, z, 0, 0, 0);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;         // dust drifts through grass and over edges
        this.gravity = 0.008f;
        this.friction = 0.992f;
        this.lifetime = 34 + this.random.nextInt(26);
        this.quadSize = 0.13f + this.random.nextFloat() * 0.13f;
        // warm sandstone tan
        this.rCol = 0.82f + this.random.nextFloat() * 0.08f;
        this.gCol = 0.72f + this.random.nextFloat() * 0.07f;
        this.bCol = 0.52f + this.random.nextFloat() * 0.06f;
        this.alpha = 0.0f;
        this.roll = this.oRoll = (this.random.nextFloat() - 0.5f) * 1.2f;
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
        float t = this.age / (float) this.lifetime;
        // The particle shader discards fragments below 0.1 combined alpha, so a low peak would clip the
        // soft edges away and pop the whole mote in and out. Keep the peak well clear of the threshold.
        this.alpha = (float) Math.sin(Math.min(1.0f, t) * Math.PI) * 0.60f;
        this.roll += 0.02f;
        // hug the ground: gentle bob instead of a climb
        this.yd += Math.sin(this.age * 0.3) * 0.0008;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            DustMoteParticle p = new DustMoteParticle(level, x, y, z, vx, vy, vz);
            p.pickSprite(sprites);
            return p;
        }
    }
}
