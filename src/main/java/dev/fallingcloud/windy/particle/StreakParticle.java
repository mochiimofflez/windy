package dev.fallingcloud.windy.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A particle drawn as a tracer: a quad whose long axis is locked to the velocity vector <b>in world
 * space</b>, spun only around that axis to face the camera. The streak points where it is going,
 * always — turn the camera and it stays nailed to its flight line, exactly like an arrow would.
 *
 * <p>(The obvious alternative — billboarding and rolling the sprite to the screen-projected velocity —
 * looks right in a still frame but re-rotates whenever the camera moves, because the projection
 * changes. World-locking the long axis is what kills that.)
 *
 * <p>The sprite's U axis runs along the flight line (matching vanilla's local-X→U mapping), so the
 * streak textures — bright core, feathered tips — need no change.
 */
public abstract class StreakParticle extends TextureSheetParticle {

    protected StreakParticle(ClientLevel level, double x, double y, double z) {
        // the 4-arg base leaves velocity alone; the 6-arg one jitters and renormalises whatever it's given
        super(level, x, y, z);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        double speed = Math.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
        if (speed < 1.0e-4) {
            super.render(buffer, camera, partialTicks); // barely moving: plain billboard
            return;
        }

        Vec3 cam = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x);
        float cy = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y);
        float cz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z);

        // long axis: the velocity, fixed in the world
        float ax = (float) (this.xd / speed);
        float ay = (float) (this.yd / speed);
        float az = (float) (this.zd / speed);

        // side axis: perpendicular to flight line and view line, so the quad shows the camera its face
        float sx = ay * cz - az * cy;
        float sy = az * cx - ax * cz;
        float sz = ax * cy - ay * cx;
        float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0e-4f) {
            // flying dead-on at the camera: any perpendicular works, borrow the camera's up
            Vector3f up = camera.getUpVector();
            sx = ay * up.z() - az * up.y();
            sy = az * up.x() - ax * up.z();
            sz = ax * up.y() - ay * up.x();
            sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0e-4f) return;
        }
        sx /= sl;
        sy /= sl;
        sz /= sl;

        float size = this.getQuadSize(partialTicks);
        float alx = ax * size, aly = ay * size, alz = az * size;
        float six = sx * size, siy = sy * size, siz = sz * size;

        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        int light = this.getLightColor(partialTicks);

        // same corner/UV pairing as vanilla's rotated quad: +along → U1, +side → V0
        vertex(buffer, cx + alx - six, cy + aly - siy, cz + alz - siz, u1, v1, light);
        vertex(buffer, cx + alx + six, cy + aly + siy, cz + alz + siz, u1, v0, light);
        vertex(buffer, cx - alx + six, cy - aly + siy, cz - alz + siz, u0, v0, light);
        vertex(buffer, cx - alx - six, cy - aly - siy, cz - alz - siz, u0, v1, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(x, y, z).setUv(u, v)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(light);
    }
}
