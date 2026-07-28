package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = SpeedrunRoulette.MODID)
public class SpeedrunCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("speedrun")

                // /speedrun wheel - Open wheel to get new objectives
                .then(Commands.literal("wheel")
                    .executes(context -> {
                        SpeedrunState.openWheelNow();
                        return 1;
                    })
                )

                // /speedrun reminder - Show current objectives reminder
                .then(Commands.literal("reminder")
                    .executes(context -> {
                        SpeedrunState.openWheelOrReminder();
                        return 1;
                    })
                )

                // /speedrun new - Start a new run with new objectives
                .then(Commands.literal("new")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_new_run"), false);
                        SpeedrunState.beginNewRunAndDisconnect();
                        return 1;
                    })
                )

                // /speedrun retry - Retry with same objectives
                .then(Commands.literal("retry")
                    .executes(context -> {
                        if (SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingNewRun || SpeedrunRoulette.pendingReplay || SpeedrunState.isTransitioning) {
                            return 0;
                        }
                        SpeedrunRoulette.pendingReplay = true;
                        SpeedrunState.isTransitioning = true;
                        try {
                            SpeedrunState.saveRunInfo(false);
                        } catch (Throwable ignored) {}
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_retry"), false);
                        var mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc.level != null) {
                            // prepareForRetry happens on TitleScreen after disconnect completes
                            mc.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false);
                        } else {
                            SpeedrunState.prepareForRetry();
                            SpeedrunRoulette.pendingReplay = false;
                            SpeedrunState.finishTransition();
                        }
                        return 1;
                    })
                )

                // /speedrun giveup - Give up current run
                .then(Commands.literal("giveup")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_give_up"), false);
                        SpeedrunState.beginGiveUpAndDisconnect();
                        return 1;
                    })
                )

                // /speedrun reset - Reset world and create new one
                .then(Commands.literal("reset")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.translatable("gui.examplemod.cmd_reset_world"), false);
                        SpeedrunState.beginNewRunAndDisconnect();
                        return 1;
                    })
                )

                // /speedrun pause - Toggle pause timer
                .then(Commands.literal("pause")
                    .executes(context -> {
                        SpeedrunState.toggleManualPause();
                        return 1;
                    })
                )

                // /speedrun hud - Cycle HUD mode
                .then(Commands.literal("hud")
                    .executes(context -> {
                        SpeedrunState.toggleHud();
                        return 1;
                    })
                )

                // /speedrun config - Open config screen
                .then(Commands.literal("config")
                    .executes(context -> {
                        var mc = net.minecraft.client.Minecraft.getInstance();
                        mc.setScreen(new SpeedrunConfigScreen(mc.screen));
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
                                boolean paused = SpeedrunState.isCompleted();
                                mc.player.displayClientMessage(
                                    Component.translatable("gui.examplemod.cmd_status", time, paused ? "Completed" : "Running"),
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
