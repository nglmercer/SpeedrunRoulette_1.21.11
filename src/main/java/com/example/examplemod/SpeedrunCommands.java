package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registers all /speedrun sub-commands.
 *
 * IMPORTANT: Command executors run on the Server thread (not the render thread).
 * Any operation that touches Minecraft client state (disconnect, setScreen, etc.)
 * MUST be posted to the render thread via mc.execute(() -> ...).
 *
 * The beginXxxAndDisconnect() methods themselves call mc.disconnect() directly, so
 * they MUST be wrapped in mc.execute().
 */
public class SpeedrunCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
            Commands.literal("speedrun")

                // /speedrun wheel - Open wheel to get new objectives
                .then(Commands.literal("wheel")
                    .executes(context -> {
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::openWheelNow);
                        return 1;
                    })
                )

                // /speedrun reminder - Show current objectives reminder
                .then(Commands.literal("reminder")
                    .executes(context -> {
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::openWheelOrReminder);
                        return 1;
                    })
                )

                // /speedrun new - Start a new run with new objectives
                .then(Commands.literal("new")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_new_run"), false);
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::beginNewRunAndDisconnect);
                        return 1;
                    })
                )

                // /speedrun retry - Retry with same objectives
                .then(Commands.literal("retry")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_retry"), false);
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::beginRetryAndDisconnect);
                        return 1;
                    })
                )

                // /speedrun giveup - Give up current run
                .then(Commands.literal("giveup")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_give_up"), false);
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::beginGiveUpAndDisconnect);
                        return 1;
                    })
                )

                // /speedrun reset - Reset world and create new one
                .then(Commands.literal("reset")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_reset_world"), false);
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::beginResetAndDisconnect);
                        return 1;
                    })
                )

                // /speedrun pause - Toggle pause timer
                .then(Commands.literal("pause")
                    .executes(context -> {
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::toggleManualPause);
                        return 1;
                    })
                )

                // /speedrun hud - Cycle HUD mode
                .then(Commands.literal("hud")
                    .executes(context -> {
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::toggleHud);
                        return 1;
                    })
                )

                // /speedrun config - Open config screen
                .then(Commands.literal("config")
                    .executes(context -> {
                        var mc = net.minecraft.client.Minecraft.getInstance();
                        mc.execute(() -> mc.setScreen(new SpeedrunConfigScreen(mc.screen)));
                        return 1;
                    })
                )

                // /speedrun status - Show current status
                .then(Commands.literal("status")
                    .executes(context -> {
                        var mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc.player != null) {
                            if (SpeedrunState.hasActiveObjectives()) {
                                String time = SpeedrunState.currentFormattedTime();
                                boolean completed = SpeedrunState.isCompleted();
                                mc.player.displayClientMessage(
                                    Component.translatable("gui.examplemod.cmd_status", time, completed ? "Completed" : "Running"),
                                    true
                                );
                            } else {
                                mc.player.displayClientMessage(
                                    Component.translatable("gui.examplemod.cmd_no_objectives"),
                                    true
                                );
                            }
                        }
                        return 1;
                    })
                )
        );
    }
}
