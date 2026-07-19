package dev.fallingcloud.windy;

import dev.fallingcloud.windy.particle.WindParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Windy — wind you can see.
 *
 * <p>A client-tick director keeps a living wind over the world: its direction slowly veers, its strength
 * rises and falls, and now and then it gathers into a gust. While the wind is up it spawns particles that
 * ride it — pale wisp streaks, leaves torn from the canopy tinted to the biome's foliage, snow whipped
 * sideways through cold storms, and dust lifted off the dunes.
 *
 * <p>Underneath the wind, every biome also gets its own still air: fireflies twinkling over swamps at dusk,
 * pollen in the jungle canopy, heat dust across the dunes, embers off lava, snow sparkle on the peaks and
 * petals in a cherry grove — resolved from the biome, time of day and weather by
 * {@link dev.fallingcloud.windy.ambient.AmbianceDirector}.
 *
 * <p>Nothing here touches the render frame, so it stays fully compatible with Sodium and Iris, and it
 * honours your vanilla particle setting.
 */
@Mod(value = Windy.MOD_ID, dist = Dist.CLIENT)
public final class Windy {
    public static final String MOD_ID = "windy";
    public static final String VERSION = "1.2.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("Windy");

    public Windy(IEventBus modEventBus, ModContainer modContainer) {
        // Settings live in the NeoForge mods config screen (Mods -> Windy -> Config).
        modContainer.registerConfig(ModConfig.Type.CLIENT, WindyConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        WindParticles.register(modEventBus);
        modEventBus.addListener(WindParticles::registerProviders);
        NeoForge.EVENT_BUS.register(WindDirector.INSTANCE);
        NeoForge.EVENT_BUS.register(dev.fallingcloud.windy.ambient.AmbianceDirector.INSTANCE);
        NeoForge.EVENT_BUS.register(WindRibbons.INSTANCE);
        NeoForge.EVENT_BUS.addListener(WindyCommands::onRegisterClientCommands);
        LOGGER.info("[Windy] Initialised.");
    }
}
