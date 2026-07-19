package dev.fallingcloud.windy.particle;

import dev.fallingcloud.windy.Windy;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Windy's four custom particle types and their client providers. Each type is a plain
 * {@link SimpleParticleType}; the behaviour is baked into the provider registered for it.
 *
 * <ul>
 *   <li><b>wisp</b> — a pale streak that rides a gust.</li>
 *   <li><b>leaf</b> — a leaf torn from the canopy, tinted to the biome's foliage.</li>
 *   <li><b>snow</b> — a snow streak whipped sideways through a cold storm.</li>
 *   <li><b>dust</b> — a dry mote lifted off sand.</li>
 *   <li><b>firefly / pollen / heat_dust</b> — soft ambient motes that hang in the air of a biome,
 *       all sharing one texture tinted per type.</li>
 * </ul>
 */
public final class WindParticles {

    public static final DeferredRegister<ParticleType<?>> TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Windy.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WISP =
            TYPES.register("wisp", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LEAF =
            TYPES.register("leaf", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNOW =
            TYPES.register("snow", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST =
            TYPES.register("dust", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MOTE =
            TYPES.register("mote", () -> new SimpleParticleType(false));

    /** Warm, twinkling motes for swamps and forests after dark. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIREFLY =
            TYPES.register("firefly", () -> new SimpleParticleType(false));
    /** Pale green-gold pollen for jungles, flower forests and lush caves. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> POLLEN =
            TYPES.register("pollen", () -> new SimpleParticleType(false));
    /** Tan heat dust for deserts, badlands and savannas. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEAT_DUST =
            TYPES.register("heat_dust", () -> new SimpleParticleType(false));

    private WindParticles() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(WISP.get(), WispParticle.Provider::new);
        event.registerSpriteSet(LEAF.get(), LeafParticle.Provider::new);
        event.registerSpriteSet(SNOW.get(), SnowStreakParticle.Provider::new);
        event.registerSpriteSet(DUST.get(), DustMoteParticle.Provider::new);
        event.registerSpriteSet(MOTE.get(), MoteParticle.Provider::new);

        // color, blink, gravity, lifetime(ticks), size, baseAlpha
        event.registerSpriteSet(FIREFLY.get(), sprites ->
                new DriftMoteParticle.Provider(sprites, 0xFFE884, true, 0.0f, 90, 0.17f, 1.0f));
        event.registerSpriteSet(POLLEN.get(), sprites ->
                new DriftMoteParticle.Provider(sprites, 0xD2E89C, false, 0.0016f, 100, 0.13f, 0.9f));
        event.registerSpriteSet(HEAT_DUST.get(), sprites ->
                new DriftMoteParticle.Provider(sprites, 0xDBC8A2, false, -0.0008f, 90, 0.15f, 0.8f));
    }
}
