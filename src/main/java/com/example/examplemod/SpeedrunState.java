package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SpeedrunState {
    private static List<Objective> objectives = Collections.emptyList();
    private static boolean objectivesCompleted = false;
    private static boolean objectivesFresh = false;

    public static boolean keepObjectivesForNextRun = false;
    public static boolean isTransitioning = false;

    // --- Objectives Management ---

    public static void setObjectives(List<Objective> objs, boolean save) {
        objectives = objs;
        objectivesFresh = true;
        objectivesCompleted = false;
        if (Config.AUTO_START.get() && !objs.isEmpty()) {
            SpeedrunTimer.start();
        }
        if (save) {
            saveObjectivesToWorld();
        }
    }

    public static void setObjectives(List<Objective> objs) {
        setObjectives(objs, true);
    }

    public static void saveObjectivesToWorld() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            SpeedrunWorldData data = SpeedrunWorldData.get(server);
            data.setObjectives(objectives);
        } else if (mc.player != null) {
            SpeedrunNetwork.sendToServer(new SpeedrunNetwork.SaveObjectivesPacket(new ArrayList<>(objectives)));
        }
    }

    public static List<Objective> getObjectives() {
        return objectives;
    }

    public static void clearObjectives() {
        objectives = Collections.emptyList();
        objectivesCompleted = false;
    }

    public static boolean hasActiveObjectives() {
        return !objectives.isEmpty();
    }

    public static boolean isCompleted() {
        return objectivesCompleted;
    }

    public static void markObjectivesStale() {
        objectivesFresh = false;
    }

    // --- Lifecycle ---

    public static void prepareForRetry() {
        keepObjectivesForNextRun = true;
        isTransitioning = true;
        SpeedrunTimer.reset();
        SpeedrunSplits.reset();
        objectivesFresh = true;
    }

    public static void prepareForNewGame() {
        keepObjectivesForNextRun = false;
        isTransitioning = true;
        clearObjectives();
        SpeedrunTimer.reset();
        SpeedrunSplits.reset();
        objectivesFresh = false;
    }

    public static void finishTransition() {
        isTransitioning = false;
        SpeedrunAutoNav.resetProgress();
    }

    public static void beginGiveUpAndDisconnect() {
        if (SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingNewRun || SpeedrunRoulette.pendingReplay || isTransitioning) {
            return;
        }

        SpeedrunRoulette.pendingGiveUp = true;
        isTransitioning = true;

        try {
            if (hasActiveObjectives()) {
                SpeedrunRunInfo.save(false);
            }
        } catch (Throwable t) {
            SpeedrunRoulette.LOGGER.error("Failed to save run info on give up", t);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.disconnect(new TitleScreen(), false);
        } else {
            prepareForNewGame();
            SpeedrunAutoNav.autoTriggerCreateWorld = true;
            SpeedrunRoulette.hasCheckedAutoOpen = false;
            SpeedrunRoulette.pendingGiveUp = false;
            if (!(mc.screen instanceof TitleScreen)) {
                mc.setScreen(new TitleScreen());
            }
        }
    }

    public static void beginNewRunAndDisconnect() {
        if (SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingNewRun || SpeedrunRoulette.pendingReplay || isTransitioning) {
            return;
        }

        SpeedrunRoulette.pendingNewRun = true;
        isTransitioning = true;

        try {
            if (hasActiveObjectives()) {
                SpeedrunRunInfo.save(false);
            }
        } catch (Throwable t) {
            SpeedrunRoulette.LOGGER.error("Failed to save run info on new run", t);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.disconnect(new TitleScreen(), false);
        } else {
            prepareForNewGame();
            SpeedrunAutoNav.autoTriggerCreateWorld = true;
            SpeedrunRoulette.hasCheckedAutoOpen = false;
            SpeedrunRoulette.pendingNewRun = false;
            if (!(mc.screen instanceof TitleScreen)) {
                mc.setScreen(new TitleScreen());
            }
        }
    }

    public static void openWheelNow() {
        Minecraft.getInstance().setScreen(new WheelScreen());
    }

    public static void openWheelOrReminder() {
        if (hasActiveObjectives()) {
            Minecraft.getInstance().setScreen(new ReminderScreen());
        } else {
            openWheelNow();
        }
    }

    public static void checkAutoOpen() {
        if (!Config.AUTO_OPEN_WHEEL.get()) return;

        boolean hasObjs = !objectives.isEmpty();

        if (!keepObjectivesForNextRun && hasObjs && !objectivesFresh) {
            clearObjectives();
            hasObjs = false;
        }

        if (!hasObjs) {
            openWheelNow();
        } else {
            if (!SpeedrunTimer.isRunning()) SpeedrunTimer.start();
            Minecraft.getInstance().player.displayClientMessage(
                Component.translatable("gui.examplemod.objectives_active"), true);
        }
    }

    // --- Client Tick ---

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        if (SpeedrunAutoNav.isDisconnectingOrSaving()) {
            if (SpeedrunAutoNav.autoTriggerCreateWorld && SpeedrunAutoNav.canAutoNavigateMenus()
                && mc.screen instanceof CreateWorldScreen screen) {
                SpeedrunAutoNav.tryAutoPressCreateWorld(screen);
            }
            return;
        }

        if (SpeedrunAutoNav.autoTriggerCreateWorld && SpeedrunAutoNav.canAutoNavigateMenus()
            && mc.screen instanceof CreateWorldScreen screen) {
            SpeedrunAutoNav.tryAutoPressCreateWorld(screen);
        }

        if (mc.screen instanceof WheelScreen && SpeedrunTimer.isRunning()) {
            SpeedrunTimer.reset();
            SpeedrunSplits.reset();
        }

        if (mc.level == null || mc.player == null) return;
        if (mc.player.connection == null) return;

        if (mc.player != null) {
            SpeedrunTimer.trackPlayerStats();

            if (SpeedrunTimer.isRunning() && !SpeedrunTimer.isPaused() && !mc.isPaused()) {
                SpeedrunSplits.track();
            }

            if (SpeedrunTimer.isRunning() && !SpeedrunTimer.isPaused() && !mc.isPaused()) {
                if (areObjectivesComplete(mc.player)) {
                    objectivesCompleted = true;
                    SpeedrunTimer.markCompleted();

                    SpeedrunRoulette.pendingVictoryTime =
                        SpeedrunTimer.getFormattedTimeFromNanos(SpeedrunTimer.getFinalElapsedNanos());

                    if (objectives != null && !objectives.isEmpty()) {
                        if (objectives.size() > 1) {
                            SpeedrunRoulette.pendingVictoryObjectiveName =
                                Component.translatable("gui.examplemod.item_list", objectives.size()).getString();
                        } else {
                            SpeedrunRoulette.pendingVictoryObjectiveName =
                                objectives.get(0).getDisplayName().getString();
                        }
                    }

                    mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
                    mc.setScreen(new VictoryScreen());
                }
            }
        }
    }

    private static boolean areObjectivesComplete(net.minecraft.world.entity.player.Player player) {
        if (objectives.isEmpty()) return false;
        for (Objective obj : objectives) {
            if (!obj.isCompleted(player)) return false;
        }
        return true;
    }

    // --- Screen Init (delegates to SpeedrunAutoNav) ---

    public static void onScreenInit(ScreenEvent.Init.Post event) {
        SpeedrunAutoNav.onScreenInit(event);
    }

    // --- Facade methods for backward compatibility ---

    /** @deprecated Use {@link SpeedrunTimer#reset()} directly */
    public static void resetTimer() {
        SpeedrunTimer.reset();
        SpeedrunSplits.reset();
    }

    /** @deprecated Use {@link SpeedrunTimer#start()} directly */
    public static void startTimer() { SpeedrunTimer.start(); }

    /** @deprecated Use {@link SpeedrunTimer#stop()} directly */
    public static void stopTimer() { SpeedrunTimer.stop(); }

    /** @deprecated Use {@link SpeedrunTimer#toggleHud()} directly */
    public static void toggleHud() { SpeedrunTimer.toggleHud(); }

    /** @deprecated Use {@link SpeedrunTimer#toggleManualPause()} directly */
    public static void toggleManualPause() { SpeedrunTimer.toggleManualPause(); }

    /** @deprecated Use {@link SpeedrunTimer#onSystemPause(boolean)} directly */
    public static void onSystemPause(boolean paused) { SpeedrunTimer.onSystemPause(paused); }

    /** @deprecated Use {@link SpeedrunTimer#currentFormattedTime()} directly */
    public static String currentFormattedTime() { return SpeedrunTimer.currentFormattedTime(); }

    /** @deprecated Use {@link SpeedrunTimer#getFormattedTimeFromNanos(long)} directly */
    public static String getFormattedTimeFromNanos(long nanos) { return SpeedrunTimer.getFormattedTimeFromNanos(nanos); }

    /** @deprecated Use {@link SpeedrunTimer#getDeathCount()} directly */
    public static int getDeathCount() { return SpeedrunTimer.getDeathCount(); }

    /** @deprecated Use {@link SpeedrunTimer#getTraveledMeters()} directly */
    public static double getTraveledMeters() { return SpeedrunTimer.getTraveledMeters(); }

    /** @deprecated Use {@link SpeedrunTimer#getDaysPlayed()} directly */
    public static long getDaysPlayed() { return SpeedrunTimer.getDaysPlayed(); }

    /** @deprecated Use {@link SpeedrunHud#onRenderHud(GuiGraphics)} directly */
    public static void onRenderHud(GuiGraphics g) { SpeedrunHud.onRenderHud(g); }

    /** @deprecated Use {@link SpeedrunHud#renderPreviewHud(GuiGraphics, int, int)} directly */
    public static void renderPreviewHud(GuiGraphics g, int width, int margin) { SpeedrunHud.renderPreviewHud(g, width, margin); }

    /** @deprecated Use {@link SpeedrunRunInfo#save(boolean)} directly */
    public static void saveRunInfo(boolean isVictory) { SpeedrunRunInfo.save(isVictory); }

    /** @deprecated Use {@link SpeedrunRunInfo#get(String)} directly */
    public static SpeedrunRunInfo.RunInfo getRunInfo(String levelId) { return SpeedrunRunInfo.get(levelId); }

    /** @deprecated Use {@link SpeedrunRunInfo#show(net.minecraft.client.gui.screens.Screen, String)} directly */
    public static void showRunInfo(net.minecraft.client.gui.screens.Screen parent, String levelId) { SpeedrunRunInfo.show(parent, levelId); }

    /** @deprecated Use {@link SpeedrunRunInfo#getLevelId(net.minecraft.server.MinecraftServer)} directly */
    public static String getLevelId(net.minecraft.server.MinecraftServer server) { return SpeedrunRunInfo.getLevelId(server); }

    /** @deprecated Use {@link SpeedrunSplits#getSplits()} directly */
    public static Map<String, String> getSplits() { return SpeedrunSplits.getSplits(); }

    /** @deprecated Use {@link SpeedrunAutoNav#canAutoNavigateMenus()} directly */
    public static boolean canAutoNavigateMenus() { return SpeedrunAutoNav.canAutoNavigateMenus(); }

    /** @deprecated Use {@link SpeedrunAutoNav#isDisconnectingOrSaving()} directly */
    public static boolean isDisconnectingOrSaving() { return SpeedrunAutoNav.isDisconnectingOrSaving(); }

    /** @deprecated Use {@link SpeedrunAutoNav#tickAutoNavFromTitle(Minecraft)} directly */
    public static void tickAutoNavFromTitle(Minecraft mc) { SpeedrunAutoNav.tickAutoNavFromTitle(mc); }

    /** @deprecated Use {@link SpeedrunAutoNav#cancel()} directly */
    public static void cancelAutoNav() { SpeedrunAutoNav.cancel(); }

    /** @deprecated Use {@link SpeedrunAutoNav#resetProgress()} directly */
    public static void resetAutoNavProgress() { SpeedrunAutoNav.resetProgress(); }


    /** @deprecated Use {@link SpeedrunAutoNav#setAutoTriggerCreateWorld(boolean)} directly */
    public static void setAutoTriggerCreateWorld(boolean v) { SpeedrunAutoNav.setAutoTriggerCreateWorld(v); }

    /** @deprecated Use {@link SpeedrunAutoNav#isAutoTriggerCreateWorld()} directly */
    public static boolean isAutoTriggerCreateWorld() { return SpeedrunAutoNav.isAutoTriggerCreateWorld(); }

    /** @deprecated Use field directly */
    public static void setKeepObjectivesForNextRun(boolean v) { keepObjectivesForNextRun = v; }

    /** @deprecated Use field directly */
    public static boolean isKeepObjectivesForNextRun() { return keepObjectivesForNextRun; }

    /** @deprecated Use {@link SpeedrunRunInfo.RunInfo} directly */
    public static class RunInfo extends SpeedrunRunInfo.RunInfo {
        public RunInfo(boolean v, String t, String o, long ts) { super(v, t, o, ts); }
        public RunInfo() { super(); }
    }
}
