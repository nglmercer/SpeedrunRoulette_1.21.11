package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registers all /speedrun sub-commands.
 *
 * IMPORTANT: Command executors run on the Server thread (not the render thread).
 * Commands that trigger a disconnect must NOT use mc.execute() because
 * mc.disconnect() stops the integrated server, which deadlocks when the
 * server thread is waiting for the posted task to complete.
 *
 * Instead, disconnect commands set SpeedrunRoulette.pendingCommand which is
 * consumed by SpeedrunState.onClientTick() on the render thread.
 */
public class SpeedrunCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(
            Commands.literal("speedrun")

                .then(Commands.literal("wheel")
                    .executes(context -> {
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::openWheelNow);
                        return 1;
                    })
                )

                .then(Commands.literal("reminder")
                    .executes(context -> {
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::openWheelOrReminder);
                        return 1;
                    })
                )

                .then(Commands.literal("new")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_new_run"), false);
                        SpeedrunRoulette.pendingCommand = "new";
                        return 1;
                    })
                )

                .then(Commands.literal("retry")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_retry"), false);
                        SpeedrunRoulette.pendingCommand = "retry";
                        return 1;
                    })
                )

                .then(Commands.literal("retrynewseed")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_retry_new_seed"), false);
                        SpeedrunRoulette.pendingCommand = "retrynewseed";
                        return 1;
                    })
                )

                .then(Commands.literal("giveup")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_give_up"), false);
                        SpeedrunRoulette.pendingCommand = "giveup";
                        return 1;
                    })
                )

                .then(Commands.literal("mainmenu")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_main_menu"), false);
                        SpeedrunRoulette.pendingCommand = "mainmenu";
                        return 1;
                    })
                )

                .then(Commands.literal("reset")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_reset_world"), false);
                        SpeedrunRoulette.pendingCommand = "reset";
                        return 1;
                    })
                )

                .then(Commands.literal("pause")
                    .executes(context -> {
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::toggleManualPause);
                        return 1;
                    })
                )

                .then(Commands.literal("hud")
                    .executes(context -> {
                        net.minecraft.client.Minecraft.getInstance().execute(SpeedrunState::toggleHud);
                        return 1;
                    })
                )

                .then(Commands.literal("config")
                    .executes(context -> {
                        var mc = net.minecraft.client.Minecraft.getInstance();
                        mc.execute(() -> mc.setScreen(new SpeedrunConfigScreen(mc.screen)));
                        return 1;
                    })
                )

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
