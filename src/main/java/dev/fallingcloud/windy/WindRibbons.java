package dev.fallingcloud.windy;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The wind's long stripes: flowing ribbons of air that snake downwind, weave side to side, and follow
 * the terrain over hills. Each ribbon is a head point that advances with the wind every tick, dragging
 * a trail of its past positions behind it; the trail renders as a camera-facing strip — thin, feathered
 * at both tips, brightest in the middle — through a vanilla translucent {@link RenderType} in
 * {@code AFTER_TRANSLUCENT_BLOCKS} (state set up and cleared by the RenderType — nothing leaks).
 *
 * <p>Because ribbons are world geometry rather than particles, they are not subject to the particle
 * engine's 32-block cull — a gust front rolling in from 40 blocks away actually reads as one.
 */
public final class WindRibbons {
    public static final WindRibbons INSTANCE = new WindRibbons();

    private static final ResourceLocation RIBBON_TEX = ResourceLocation.parse("windy:textures/ribbon.png");

    /** Textured, translucent, double-sided, depth-tested but not depth-writing. */
    private static final RenderType RIBBON = RenderType.create(
            "windy:ribbon",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(RIBBON_TEX, true, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private static final int MAX_POINTS = 24;    // trail length in ticks of travel
    private static final int MAX_RIBBONS = 20;

    /** One stripe of wind: a moving head dragging its past positions. */
    private static final class Ribbon {
        final List<Vec3> points = new ArrayList<>(MAX_POINTS); // oldest first, head last
        double x, y, z;
        double vx, vy, vz;      // last tick's motion, for render-time head interpolation
        int age;
        int lifetime;
        float width;
        float phase;            // personal weave offset
        float speedMul;
    }

    private final List<Ribbon> ribbons = new ArrayList<>();
    private ClientLevel boundLevel;

    private WindRibbons() {}

    // ---------------------------------------------------------------- advance (per tick)
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            ribbons.clear();
            boundLevel = null;
            return;
        }
        if (level != boundLevel) {
            boundLevel = level;
            ribbons.clear();
        }
        if (mc.isPaused()) return;

        WindyConfig cfg = WindyConfig.get();
        if (!cfg.enabled() || !cfg.stripes() || cfg.intensity() == WindyConfig.Intensity.OFF) {
            ribbons.clear();
            return;
        }

        WindDirector wind = WindDirector.INSTANCE;
        float eff = wind.effectiveStrength();
        float gust = wind.gustStrength();
        if (eff <= 0.01f && gust <= 0.01f) {
            // /windy calm (or a windless external sample) should clear existing ribbons too.
            ribbons.clear();
            return;
        }
        double wx = wind.windX(), wz = wind.windZ();
        RandomSource rng = level.random;

        double intensityMul = switch (cfg.intensity()) {
            case SUBTLE -> 0.5;
            case LIVELY -> 1.7;
            default -> 1.0;
        };
        int target = (int) Math.min(MAX_RIBBONS, (2 + 4 * eff + 9 * gust) * intensityMul);

        // ---- spawn toward the target population ----
        int radius = Math.min(cfg.radius() + 10, 40);   // geometry, not particles: no 32-block cull
        if (ribbons.size() < target && rng.nextFloat() < 0.5f) {
            double sx = player.getX() + (rng.nextDouble() * 2 - 1) * radius;
            double sz = player.getZ() + (rng.nextDouble() * 2 - 1) * radius;
            BlockPos col = BlockPos.containing(sx, player.getY(), sz);
            if (level.isLoaded(col)) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, col.getX(), col.getZ());
                Ribbon r = new Ribbon();
                r.x = sx;
                r.y = top + 1.2 + rng.nextDouble() * 4.5;
                r.z = sz;
                r.age = 0;
                r.lifetime = 90 + rng.nextInt(80);
                r.width = 0.05f + rng.nextFloat() * 0.055f;
                r.phase = rng.nextFloat() * (float) Math.PI * 2;
                r.speedMul = 0.8f + rng.nextFloat() * 0.45f;
                r.points.add(new Vec3(r.x, r.y, r.z));
                ribbons.add(r);
            }
        }

        // ---- advance every ribbon ----
        double px = player.getX(), pz = player.getZ();
        Iterator<Ribbon> it = ribbons.iterator();
        while (it.hasNext()) {
            Ribbon r = it.next();
            r.age++;
            double dx = r.x - px, dz = r.z - pz;
            if (r.age > r.lifetime || dx * dx + dz * dz > 56 * 56) {
                it.remove();
                continue;
            }

            double speed = (0.30 + 0.34 * eff) * r.speedMul;
            // weave: swing across the wind, bob along it, so the stripe snakes instead of ruling a line
            double perpX = -wz, perpZ = wx;
            double weave = Math.sin(r.age * 0.11 + r.phase) * 0.34;
            r.vx = (wx + perpX * weave) * speed;
            r.vz = (wz + perpZ * weave) * speed;
            r.vy = Math.sin(r.age * 0.07 + r.phase * 2) * 0.022;

            // follow the land: every few ticks, ease toward a lane a couple of blocks over the surface
            if (r.age % 4 == 0) {
                BlockPos here = BlockPos.containing(r.x, r.y, r.z);
                if (level.isLoaded(here)) {
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, here.getX(), here.getZ());
                    double lane = top + 2.0 + Math.sin(r.phase * 3) * 1.6;
                    r.vy += (lane - r.y) * 0.045;
                }
            }

            r.x += r.vx;
            r.y += r.vy;
            r.z += r.vz;
            r.points.add(new Vec3(r.x, r.y, r.z));
            if (r.points.size() > MAX_POINTS) r.points.remove(0);
        }
    }

    // ---------------------------------------------------------------- draw (per frame)
    @SubscribeEvent
    public void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ribbons.isEmpty()) return;
        WindyConfig cfg = WindyConfig.get();
        if (!cfg.enabled() || !cfg.stripes()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (Ribbon r : ribbons) {
            int n = r.points.size();
            if (n < 3) continue;

            // whole-ribbon envelope: fade in, fade out
            float ageEnv = Math.min(1.0f, r.age / 14.0f)
                    * Math.min(1.0f, (r.lifetime - r.age) / 24.0f);
            if (ageEnv <= 0.02f) continue;

            // joints: the stored points, with the head pushed on by this frame's fraction of a tick
            Vec3[] pts = new Vec3[n];
            for (int i = 0; i < n - 1; i++) pts[i] = r.points.get(i);
            Vec3 head = r.points.get(n - 1);
            pts[n - 1] = head.add(r.vx * partial, r.vy * partial, r.vz * partial);

            for (int i = 0; i < n - 1; i++) {
                float t0 = i / (float) (n - 1);
                float t1 = (i + 1) / (float) (n - 1);
                Vec3 s0 = sideAt(pts, i, cam, r.width, t0);
                Vec3 s1 = sideAt(pts, i + 1, cam, r.width, t1);
                int a0 = (int) (alphaAt(t0) * ageEnv * 255);
                int a1 = (int) (alphaAt(t1) * ageEnv * 255);
                if (a0 <= 3 && a1 <= 3) continue;
                vtx(bb, pose, cam, pts[i].add(s0), 0.03f, t0, a0);
                vtx(bb, pose, cam, pts[i].subtract(s0), 0.97f, t0, a0);
                vtx(bb, pose, cam, pts[i + 1].subtract(s1), 0.97f, t1, a1);
                vtx(bb, pose, cam, pts[i + 1].add(s1), 0.03f, t1, a1);
            }
        }

        MeshData mesh = bb.build();
        if (mesh != null) {
            RIBBON.draw(mesh); // sets up + clears all render state around the draw
        }
    }

    /** Half-width vector at a joint: across the path as the camera sees it, tapered at both tips. */
    private static Vec3 sideAt(Vec3[] pts, int i, Vec3 cam, float width, float t) {
        int n = pts.length;
        Vec3 along = pts[Math.min(n - 1, i + 1)].subtract(pts[Math.max(0, i - 1)]);
        Vec3 view = pts[i].subtract(cam);
        Vec3 s = along.cross(view);
        double len = s.length();
        if (len < 1.0e-4) return Vec3.ZERO;
        double w = width * (0.35 + 0.65 * Math.sin(Math.PI * t));
        return s.scale(w / len);
    }

    /** Brightness along the stripe: feathered tail and tip, full through the middle. */
    private static float alphaAt(float t) {
        return (float) Math.pow(Math.sin(Math.PI * t), 0.65) * 0.45f;
    }

    private static void vtx(BufferBuilder bb, Matrix4f pose, Vec3 cam, Vec3 p, float u, float v, int a) {
        bb.addVertex(pose, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                .setUv(u, v).setColor(235, 245, 252, a);
    }
}
