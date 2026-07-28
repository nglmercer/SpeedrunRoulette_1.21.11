package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

public class SpeedrunAutoNav {
    public static boolean autoTriggerCreateWorld = false;
    private static boolean autoNavLeftTitle = false;

    public static void tickAutoNavFromTitle(Minecraft mc) {
        if (!autoTriggerCreateWorld || autoNavLeftTitle || !canAutoNavigateMenus()) {
            return;
        }
        if (mc.screen instanceof TitleScreen) {
            autoNavLeftTitle = true;
            SpeedrunRoulette.LOGGER.info("AutoNav: Transitioning TitleScreen -> SelectWorldScreen");
            mc.setScreen(new SelectWorldScreen(mc.screen));
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

    /**
     * Returns true while Minecraft is in a screen transition state (saving world,
     * loading world, etc.). Does NOT check isTransitioning to avoid deadlocks —
     * transitions are now managed solely by pending flags and screen type.
     */
    public static boolean isDisconnectingOrSaving() {
        Minecraft mc = Minecraft.getInstance();
        return SpeedrunRoulette.pendingGiveUp
            || SpeedrunRoulette.pendingNewRun
            || SpeedrunRoulette.pendingReplay
            || SpeedrunRoulette.pendingReset
            || mc.screen instanceof net.minecraft.client.gui.screens.GenericMessageScreen
            || mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
            || mc.screen instanceof net.minecraft.client.gui.screens.ProgressScreen;
    }

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen) {
            event.addListener(Button.builder(Component.translatable("gui.examplemod.speedrun_config_button"), (btn) -> {
                Minecraft.getInstance().setScreen(new SpeedrunConfigScreen(event.getScreen()));
            }).bounds(10, 10, 100, 20).build());

            // Handle any pending transition that was triggered before we reached TitleScreen.
            if (SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingNewRun
                    || SpeedrunRoulette.pendingReplay || SpeedrunRoulette.pendingReset) {
                SpeedrunState.handleTitleScreenArrival(Minecraft.getInstance());
            }
        }

        if (event.getScreen() instanceof SelectWorldScreen) {
            if (autoTriggerCreateWorld && canAutoNavigateMenus()) {
                for (net.minecraft.client.gui.components.events.GuiEventListener child : event.getScreen().children()) {
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

        if (event.getScreen() instanceof CreateWorldScreen screen) {
            if (autoTriggerCreateWorld && canAutoNavigateMenus()) {
                tryAutoPressCreateWorld(screen);
            }
        }
    }

    public static void tryAutoPressCreateWorld(CreateWorldScreen screen) {
        for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
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

    private static void pressButton(Button btn) throws Exception {
        try {
            java.lang.reflect.Field f = Button.class.getDeclaredField("onPress");
            f.setAccessible(true);
            Button.OnPress onPress = (Button.OnPress) f.get(btn);
            onPress.onPress(btn);
            return;
        } catch (NoSuchFieldException ignored) {
        }
        java.lang.reflect.Method onPressMethod = Button.class.getMethod("onPress");
        onPressMethod.invoke(btn);
    }

    public static void cancel() {
        autoTriggerCreateWorld = false;
        autoNavLeftTitle = false;
        SpeedrunState.finishTransition();
    }

    public static void resetProgress() {
        autoNavLeftTitle = false;
    }

    public static boolean hasLeftTitle() {
        return autoNavLeftTitle;
    }

    public static void setAutoTriggerCreateWorld(boolean v) { autoTriggerCreateWorld = v; }
    public static boolean isAutoTriggerCreateWorld() { return autoTriggerCreateWorld; }
}
