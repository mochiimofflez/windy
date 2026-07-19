package dev.fallingcloud.windy.ambient;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * Places candidate mote positions in a sphere around a centre point and rejects any that fall inside a solid
 * block, so motes never clip terrain. Pure math + block queries; no rendering.
 */
public final class SpawnField {

    private SpawnField() {}

    /**
     * @param level        the world (for the air check)
     * @param center       the sphere centre (usually the player's eyes)
     * @param radius       sphere radius in blocks
     * @param verticalBias fraction of the radius to shift the vertical centre by (positive = spawn higher)
     * @param random       the world's {@link RandomSource}
     * @return an air position within the sphere, or {@code null} if the sampled point was inside a block
     */
    public static Vec3 sample(BlockGetter level, Vec3 center, int radius, float verticalBias, RandomSource random) {
        // Uniform-ish point in a sphere: pick a direction and a biased distance.
        double dist = radius * Math.cbrt(random.nextDouble());
        double theta = random.nextDouble() * Math.PI * 2.0;
        double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
        double dx = dist * Math.sin(phi) * Math.cos(theta);
        double dz = dist * Math.sin(phi) * Math.sin(theta);
        double dy = dist * Math.cos(phi) + verticalBias * radius;

        Vec3 pos = new Vec3(center.x + dx, center.y + dy, center.z + dz);
        BlockPos bp = BlockPos.containing(pos);
        if (!level.getBlockState(bp).isAir()) {
            return null;
        }
        return pos;
    }
}
