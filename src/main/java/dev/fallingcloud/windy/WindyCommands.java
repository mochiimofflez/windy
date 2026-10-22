package dev.fallingcloud.windy;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client-side test commands. {@code /windy status} tells you what the wind is doing right now,
 * {@code /windy gust [strength]} summons a gust on the spot, {@code /windy calm} kills it, {@code /windy reset} ends a calm or gust early.
 */
public final class WindyCommands {

    private WindyCommands() {}

    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("windy")
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("[Windy] " + WindDirector.INSTANCE.describe()), false);
                    return 1;
                }))
                .then(Commands.literal("gust")
                        .executes(ctx -> gust(ctx.getSource(), 1.2f))
                        .then(Commands.argument("strength", FloatArgumentType.floatArg(0.3f, 1.4f))
                                .executes(ctx -> gust(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "strength")))))
                .then(Commands.literal("calm").executes(ctx -> {
                    WindDirector.INSTANCE.calm();
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("[Windy] the air goes still."), false);
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    WindDirector.INSTANCE.reset();
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("[Windy] back to the natural wind."), false);
                    return 1;
                })));
    }

    private static int gust(CommandSourceStack source, float strength) {
        WindDirector.INSTANCE.endCalm();
        WindDirector.INSTANCE.startGust(strength, 200);
        source.sendSuccess(
                () -> Component.literal(String.format("[Windy] gust incoming at %.0f%%.", strength * 100)),
                false);
        return 1;
    }
}
