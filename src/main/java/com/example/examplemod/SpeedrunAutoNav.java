package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

public class SpeedrunAutoNav {
    public static boolean autoTriggerCreateWorld = false;
    public static boolean autoReEnterWorld = false;
    public static String targetLevelId = null;

    private static boolean autoNavLeftTitle = false;
    private static int reEnterAttempts = 0;

    public static void tickAutoNavFromTitle(Minecraft mc) {
        if (autoReEnterWorld && !autoNavLeftTitle && mc.screen instanceof TitleScreen) {
            autoNavLeftTitle = true;
            SpeedrunRoulette.LOGGER.info("AutoNav: Transitioning TitleScreen -> SelectWorldScreen (re-enter)");
            mc.setScreen(new SelectWorldScreen(mc.screen));
            return;
        }

        if (!autoTriggerCreateWorld || autoNavLeftTitle || !canAutoNavigateMenus()) {
            return;
        }
        if (mc.screen instanceof TitleScreen) {
            autoNavLeftTitle = true;
            SpeedrunRoulette.LOGGER.info("AutoNav: Transitioning TitleScreen -> SelectWorldScreen");
            mc.setScreen(new SelectWorldScreen(mc.screen));
        }
    }

    public static void tickAutoReEnter(Minecraft mc) {
        if (!autoReEnterWorld || targetLevelId == null) return;
        if (!(mc.screen instanceof SelectWorldScreen screen)) return;
        if (!canAutoNavigateMenus()) return;

        reEnterAttempts++;
        if (reEnterAttempts > 200) {
            SpeedrunRoulette.LOGGER.warn("AutoNav: Gave up trying to re-enter world '{}'", targetLevelId);
            cancel();
            return;
        }

        WorldSelectionList list = null;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof WorldSelectionList l) {
                list = l;
                break;
            }
        }
        if (list == null) return;

        for (Object entry : list.children()) {
            if (entry instanceof WorldSelectionList.WorldListEntry wle) {
                if (wle.getLevelSummary().getLevelId().equals(targetLevelId)) {
                    SpeedrunRoulette.LOGGER.info("AutoNav: Re-entering world '{}'", targetLevelId);
                    autoReEnterWorld = false;
                    targetLevelId = null;
                    reEnterAttempts = 0;
                    SpeedrunState.finishTransition();
                    wle.joinWorld();
                    return;
                }
            }
        }
    }

    public static boolean canAutoNavigateMenus() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) return false;
        if (mc.level != null || mc.player != null) return false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.GenericMessageScreen) return false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) return false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.ProgressScreen) return false;
        return true;
    }

    public static boolean isDisconnectingOrSaving() {
        Minecraft mc = Minecraft.getInstance();
        return SpeedrunRoulette.pendingGiveUp
            || SpeedrunRoulette.pendingNewRun
            || SpeedrunRoulette.pendingReplay
            || SpeedrunRoulette.pendingRetryNewSeed
            || SpeedrunRoulette.pendingReset
            || SpeedrunRoulette.pendingMainMenu
            || mc.screen instanceof net.minecraft.client.gui.screens.GenericMessageScreen
            || mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
            || mc.screen instanceof net.minecraft.client.gui.screens.ProgressScreen;
    }

    public static void onScreenInit(Screen screen) {
        if (screen instanceof TitleScreen) {
            Screens.getButtons(screen).add(Button.builder(Component.translatable("gui.examplemod.speedrun_config_button"), (btn) -> {
                Minecraft.getInstance().setScreen(new SpeedrunConfigScreen(screen));
            }).bounds(10, 10, 100, 20).build());

            if (SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingNewRun
                    || SpeedrunRoulette.pendingReplay || SpeedrunRoulette.pendingRetryNewSeed
                    || SpeedrunRoulette.pendingReset || SpeedrunRoulette.pendingMainMenu) {
                SpeedrunState.handleTitleScreenArrival(Minecraft.getInstance());
            }
        }

        if (screen instanceof SelectWorldScreen) {
            if (autoTriggerCreateWorld && canAutoNavigateMenus()) {
                for (GuiEventListener child : screen.children()) {
                    if (child instanceof Button btn) {
                        if (btn.getMessage().equals(Component.translatable("selectWorld.create"))) {
                            SpeedrunRoulette.LOGGER.info("SpeedrunState: Clicking 'Create New World'");
                            try {
                                pressButton(btn);
                            } catch (Throwable t) {
                                SpeedrunRoulette.LOGGER.error("SpeedrunState: Failed to click Create button", t);
                                cancel();
                            }
                            break;
                        }
                    }
                }
            }
        }

        if (screen instanceof CreateWorldScreen createWorldScreen) {
            if (autoTriggerCreateWorld && canAutoNavigateMenus()) {
                tryAutoPressCreateWorld(createWorldScreen);
            }
        }
    }

    public static void tryAutoPressCreateWorld(CreateWorldScreen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button btn) {
                if (btn.getMessage().equals(Component.translatable("selectWorld.create"))) {
                    if (btn.active) {
                        SpeedrunRoulette.LOGGER.info("SpeedrunState: Clicking 'Create' (Final)");
                        autoTriggerCreateWorld = false;
                        SpeedrunState.finishTransition();
                        try {
                            pressButton(btn);
                        } catch (Throwable t) {
                            SpeedrunRoulette.LOGGER.error("SpeedrunState: Failed to click Final Create button", t);
                            cancel();
                        }
                    }
                    break;
                }
            }
        }
    }

    private static void pressButton(Button btn) {
        btn.onPress(new net.minecraft.client.input.InputWithModifiers() {
            @Override public int input() { return 257; }
            @Override public int modifiers() { return 0; }
        });
    }

    public static void cancel() {
        autoTriggerCreateWorld = false;
        autoReEnterWorld = false;
        targetLevelId = null;
        autoNavLeftTitle = false;
        reEnterAttempts = 0;
        SpeedrunState.finishTransition();
    }

    public static void resetProgress() {
        autoNavLeftTitle = false;
        reEnterAttempts = 0;
    }

    public static boolean hasLeftTitle() {
        return autoNavLeftTitle;
    }

    public static void setAutoTriggerCreateWorld(boolean v) { autoTriggerCreateWorld = v; }
    public static boolean isAutoTriggerCreateWorld() { return autoTriggerCreateWorld; }
}
