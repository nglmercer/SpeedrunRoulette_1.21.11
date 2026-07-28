package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpeedrunState {
    private static List<Objective> objectives = new ArrayList<>();
    private static boolean objectivesCompleted = false;
    private static boolean objectivesFresh = false;
    private static boolean objectivesLoaded = false;
    private static int autoOpenDelayTicks = 0;

    /** Active multiplayer mode for the current run (synced from server when multiplayer). */
    private static SpeedrunGameMode activeGameMode = SpeedrunGameMode.COOPERATIVE;
    private static boolean runFinished = false;
    private static String lastWinnerName = "";
    private static String lastWinnerUuid = "";
    private static boolean finishClaimPending = false;

    public static boolean keepObjectivesForNextRun = false;

    /**
     * True while a disconnect is pending or in-progress on the render thread.
     * Set ONLY from the render thread. Cleared when TitleScreen arrives.
     */
    public static volatile boolean isTransitioning = false;

    // --- Objectives Management ---

    public static void setObjectives(List<Objective> objs, boolean save) {
        if (objs == null) return;
        objectives = objs;
        objectivesFresh = true;
        objectivesCompleted = false;
        objectivesLoaded = true;
        autoOpenDelayTicks = 0;
        runFinished = false;
        finishClaimPending = false;
        lastWinnerName = "";
        lastWinnerUuid = "";
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

    public static SpeedrunGameMode getActiveGameMode() {
        return activeGameMode;
    }

    public static void setActiveGameMode(SpeedrunGameMode mode) {
        activeGameMode = mode != null ? mode : SpeedrunGameMode.COOPERATIVE;
    }

    public static boolean isRunFinished() {
        return runFinished;
    }

    public static String getLastWinnerName() {
        return lastWinnerName;
    }

    /**
     * Apply full run state from the server (multiplayer join / objective broadcast).
     */
    public static void applySyncedRunState(SpeedrunNetwork.SyncRunStatePacket payload) {
        if (payload == null) return;
        activeGameMode = payload.gameMode;
        setObjectives(payload.objectives, false);
        runFinished = payload.runFinished;
        lastWinnerUuid = payload.winnerUuid != null ? payload.winnerUuid : "";
        lastWinnerName = payload.winnerName != null ? payload.winnerName : "";
        if (payload.runFinished && payload.finishTime != null && !payload.finishTime.isEmpty()) {
            SpeedrunRoulette.pendingVictoryTime = payload.finishTime;
        }
        // If we joined mid-finished challenge run, show the appropriate end screen once.
        if (payload.runFinished && !objectivesCompleted) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                boolean localWin = payload.winnerUuid != null
                        && payload.winnerUuid.equals(mc.player.getUUID().toString());
                boolean coop = payload.gameMode == SpeedrunGameMode.COOPERATIVE;
                objectivesCompleted = true;
                SpeedrunTimer.markCompleted();
                if (coop || localWin) {
                    if (!(mc.screen instanceof VictoryScreen)) {
                        mc.setScreen(new VictoryScreen());
                    }
                } else if (!(mc.screen instanceof LoseScreen)) {
                    mc.setScreen(new LoseScreen(payload.winnerName, payload.finishTime));
                }
            }
        }
    }

    /**
     * Server announced a finish — show victory (self/co-op) or defeat (challenge loss).
     */
    public static void handleRunFinished(SpeedrunNetwork.RunFinishedPacket payload) {
        if (payload == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        runFinished = true;
        finishClaimPending = false;
        activeGameMode = payload.gameMode;
        lastWinnerName = payload.winnerName;
        lastWinnerUuid = payload.winnerUuid;
        objectivesCompleted = true;
        SpeedrunTimer.markCompleted();

        SpeedrunRoulette.pendingVictoryTime = payload.finishTime;
        if (objectives != null && !objectives.isEmpty()) {
            if (objectives.size() > 1) {
                SpeedrunRoulette.pendingVictoryObjectiveName =
                        Component.translatable("gui.examplemod.item_list", objectives.size()).getString();
            } else {
                SpeedrunRoulette.pendingVictoryObjectiveName =
                        objectives.get(0).getDisplayName().getString();
            }
        }

        boolean localWin = payload.isLocalPlayerWinner(mc.player.getUUID());
        boolean coop = payload.gameMode == SpeedrunGameMode.COOPERATIVE;

        net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> winSound = coop || localWin
                ? net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE)
                : net.minecraft.sounds.SoundEvents.RAID_HORN;
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(winSound, 1.0F));

        if (coop || localWin) {
            if (!(mc.screen instanceof VictoryScreen)) {
                mc.setScreen(new VictoryScreen());
            }
        } else {
            if (!(mc.screen instanceof LoseScreen)) {
                mc.setScreen(new LoseScreen(payload.winnerName, payload.finishTime));
            }
        }
    }

    public static void saveObjectivesToWorld() {
        Minecraft mc = Minecraft.getInstance();
        // Prefer config mode when host starts a run; world data is source of truth after sync
        activeGameMode = Config.getGameMode();
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            SpeedrunWorldData data = SpeedrunWorldData.get(server);
            data.setGameMode(activeGameMode);
            data.setObjectives(objectives);
            // Integrated server (LAN): broadcast so other clients get the same sample
            SpeedrunNetwork.broadcastRunState(server);
        } else if (mc.player != null) {
            SpeedrunNetworkClient.sendToServer(new SpeedrunNetwork.SaveObjectivesPacket(
                    new ArrayList<>(objectives), activeGameMode));
        }
    }

    public static List<Objective> getObjectives() {
        return objectives;
    }

    public static void clearObjectives() {
        objectives = new ArrayList<>();
        objectivesCompleted = false;
        objectivesLoaded = true;
        autoOpenDelayTicks = 0;
        runFinished = false;
        finishClaimPending = false;
        lastWinnerName = "";
        lastWinnerUuid = "";
    }

    public static boolean hasActiveObjectives() {
        return !objectives.isEmpty();
    }

    public static boolean isObjectivesLoaded() {
        return objectivesLoaded;
    }

    public static void loadObjectivesFromWorld() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            SpeedrunWorldData data = SpeedrunWorldData.get(server);
            List<Objective> saved = data.getObjectives();
            if (!saved.isEmpty()) {
                objectives = new ArrayList<>(saved);
                objectivesFresh = true;
                objectivesCompleted = false;
            } else {
                objectivesFresh = false;
            }
            objectivesLoaded = true;
            autoOpenDelayTicks = 0;
        }
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
        SpeedrunTimer.reset();
        SpeedrunSplits.reset();
        objectivesFresh = true;
        objectivesLoaded = true;
        // Do NOT call saveObjectivesToWorld() here — server is already gone after disconnect.
    }

    public static void prepareForNewGame() {
        keepObjectivesForNextRun = false;
        clearObjectives();
        SpeedrunTimer.reset();
        SpeedrunSplits.reset();
        objectivesFresh = false;
        objectivesLoaded = false;
    }

    public static void finishTransition() {
        isTransitioning = false;
        SpeedrunAutoNav.resetProgress();
    }

    /**
     * Called ONLY from the render/client thread (SpeedrunRoulette.onClientTick).
     * Sets pending flag + schedules the actual disconnect on this same thread.
     */
    public static void beginRetryAndDisconnect() {
        if (anyPendingOrTransitioning()) return;

        SpeedrunRoulette.pendingReplay = true;
        isTransitioning = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            // Save run info now (we're on render thread, server is still up).
            trySaveRunInfo(false);
            // Capture level ID for world deletion if needed later.
            captureLevelId(mc);
            mc.disconnect(new TitleScreen(), false);
        } else {
            // Already disconnected: handle transition immediately.
            handleTitleScreenArrival(mc);
        }
    }

    public static void beginGiveUpAndDisconnect() {
        if (anyPendingOrTransitioning()) return;

        SpeedrunRoulette.pendingGiveUp = true;
        isTransitioning = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            trySaveRunInfo(false);
            captureLevelId(mc);
            mc.disconnect(new TitleScreen(), false);
        } else {
            handleTitleScreenArrival(mc);
        }
    }

    public static void beginNewRunAndDisconnect() {
        if (anyPendingOrTransitioning()) return;

        SpeedrunRoulette.pendingNewRun = true;
        isTransitioning = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            trySaveRunInfo(false);
            captureLevelId(mc);
            mc.disconnect(new TitleScreen(), false);
        } else {
            handleTitleScreenArrival(mc);
        }
    }

    public static void beginResetAndDisconnect() {
        if (anyPendingOrTransitioning()) return;

        SpeedrunRoulette.pendingReset = true;
        isTransitioning = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            trySaveRunInfo(false);
            captureLevelId(mc);
            mc.disconnect(new TitleScreen(), false);
        } else {
            handleTitleScreenArrival(mc);
        }
    }

    /**
     * Called from onScreenInit when TitleScreen is detected, OR from the else-path
     * above when there's no active world.
     * Must run on the render thread.
     */
    static void handleTitleScreenArrival(Minecraft mc) {
        boolean startingNew = SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingNewRun;
        boolean startingRetry = SpeedrunRoulette.pendingReplay;
        boolean startingReset = SpeedrunRoulette.pendingReset;

        if (!startingNew && !startingRetry && !startingReset) {
            // Nothing pending — just ensure transitioning is cleared.
            isTransitioning = false;
            return;
        }

        SpeedrunRoulette.LOGGER.info("TitleScreen arrival: pendingNew={}, pendingRetry={}, pendingReset={}",
            startingNew, startingRetry, startingReset);

        if (startingNew) {
            SpeedrunRoulette.LOGGER.info("TitleScreen: Preparing for New Game");
            prepareForNewGame();
            SpeedrunAutoNav.autoTriggerCreateWorld = true;
            SpeedrunAutoNav.resetProgress();
        } else if (startingRetry) {
            SpeedrunRoulette.LOGGER.info("TitleScreen: Preparing for Retry");
            prepareForRetry();
            SpeedrunAutoNav.autoTriggerCreateWorld = false;
        } else {
            SpeedrunRoulette.LOGGER.info("TitleScreen: Preparing for Reset (Delete World + New)");
            prepareForNewGame();
            SpeedrunAutoNav.autoTriggerCreateWorld = true;
            SpeedrunAutoNav.resetProgress();
            SpeedrunRoulette.deleteWorldSave();
        }

        // Clear all pending flags.
        SpeedrunRoulette.pendingGiveUp = false;
        SpeedrunRoulette.pendingNewRun = false;
        SpeedrunRoulette.pendingReplay = false;
        SpeedrunRoulette.pendingReset = false;
        SpeedrunRoulette.pendingVictoryTime = null;
        SpeedrunRoulette.pendingVictoryObjectiveName = null;
        SpeedrunRoulette.hasCheckedAutoOpen = false;

        // Finish transition: if autoTriggerCreateWorld is set, finishTransition is
        // called later when the CreateWorldScreen auto-press completes.
        if (!SpeedrunAutoNav.autoTriggerCreateWorld) {
            finishTransition();
        } else {
            // isTransitioning stays true until CreateWorldScreen press; but
            // reset the nav progress so AutoNav can proceed.
            isTransitioning = false; // Let AutoNav proceed without deadlocking.
            SpeedrunAutoNav.resetProgress();
        }

        // Ensure the title screen is shown if not already.
        if (!(mc.screen instanceof TitleScreen)) {
            mc.setScreen(new TitleScreen());
        }
    }

    private static boolean anyPendingOrTransitioning() {
        return SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingNewRun
            || SpeedrunRoulette.pendingReplay || SpeedrunRoulette.pendingReset
            || isTransitioning;
    }

    private static void trySaveRunInfo(boolean isVictory) {
        try {
            if (hasActiveObjectives()) {
                SpeedrunRunInfo.save(isVictory);
            }
        } catch (Throwable t) {
            SpeedrunRoulette.LOGGER.error("Failed to save run info during transition", t);
        }
    }

    private static void captureLevelId(Minecraft mc) {
        if (SpeedrunRoulette.pendingReset) {
            net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                SpeedrunRoulette.pendingLevelId = SpeedrunRunInfo.getLevelId(server);
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

        if (!objectivesLoaded) {
            loadObjectivesFromWorld();
        }

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

        if (!objectivesLoaded) {
            autoOpenDelayTicks++;
            if (autoOpenDelayTicks >= 20) {
                loadObjectivesFromWorld();
            }
        }

        // Handle pending disconnection transitions.
        // isDisconnectingOrSaving() no longer blocks this path — transitions are
        // handled immediately on the render thread via beginXxxAndDisconnect().

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
                if (!runFinished && !finishClaimPending && !objectivesCompleted
                        && areObjectivesComplete(mc.player)) {
                    String time = SpeedrunTimer.currentFormattedTime();

                    // Multiplayer / LAN: server decides winner so challenge races stay fair.
                    // Integrated host resolves on the server thread so LAN guests get the result.
                    finishClaimPending = true;
                    SpeedrunRoulette.pendingVictoryTime = time;

                    if (objectives != null && !objectives.isEmpty()) {
                        if (objectives.size() > 1) {
                            SpeedrunRoulette.pendingVictoryObjectiveName =
                                    Component.translatable("gui.examplemod.item_list", objectives.size()).getString();
                        } else {
                            SpeedrunRoulette.pendingVictoryObjectiveName =
                                    objectives.get(0).getDisplayName().getString();
                        }
                    }

                    net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
                    if (server != null) {
                        final java.util.UUID localId = mc.player.getUUID();
                        server.execute(() -> {
                            for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                                if (sp.getUUID().equals(localId)) {
                                    SpeedrunNetwork.handleClaimFinish(sp, time);
                                    break;
                                }
                            }
                        });
                    } else if (mc.player.connection != null) {
                        SpeedrunNetworkClient.sendToServer(new SpeedrunNetwork.ClaimFinishPacket(time));
                    } else {
                        finishClaimPending = false;
                        objectivesCompleted = true;
                        SpeedrunTimer.markCompleted();
                        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
                        mc.setScreen(new VictoryScreen());
                    }
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
    @Deprecated public static void resetTimer() {
        SpeedrunTimer.reset();
        SpeedrunSplits.reset();
    }

    /** @deprecated Use {@link SpeedrunTimer#start()} directly */
    @Deprecated public static void startTimer() { SpeedrunTimer.start(); }

    /** @deprecated Use {@link SpeedrunTimer#stop()} directly */
    @Deprecated public static void stopTimer() { SpeedrunTimer.stop(); }

    /** @deprecated Use {@link SpeedrunTimer#toggleHud()} directly */
    @Deprecated public static void toggleHud() { SpeedrunTimer.toggleHud(); }

    /** @deprecated Use {@link SpeedrunTimer#toggleManualPause()} directly */
    @Deprecated public static void toggleManualPause() { SpeedrunTimer.toggleManualPause(); }

    /** @deprecated Use {@link SpeedrunTimer#onSystemPause(boolean)} directly */
    @Deprecated public static void onSystemPause(boolean paused) { SpeedrunTimer.onSystemPause(paused); }

    /** @deprecated Use {@link SpeedrunTimer#currentFormattedTime()} directly */
    @Deprecated public static String currentFormattedTime() { return SpeedrunTimer.currentFormattedTime(); }

    /** @deprecated Use {@link SpeedrunTimer#getFormattedTimeFromNanos(long)} directly */
    @Deprecated public static String getFormattedTimeFromNanos(long nanos) { return SpeedrunTimer.getFormattedTimeFromNanos(nanos); }

    /** @deprecated Use {@link SpeedrunTimer#getDeathCount()} directly */
    @Deprecated public static int getDeathCount() { return SpeedrunTimer.getDeathCount(); }

    /** @deprecated Use {@link SpeedrunTimer#getTraveledMeters()} directly */
    @Deprecated public static double getTraveledMeters() { return SpeedrunTimer.getTraveledMeters(); }

    /** @deprecated Use {@link SpeedrunTimer#getDaysPlayed()} directly */
    @Deprecated public static long getDaysPlayed() { return SpeedrunTimer.getDaysPlayed(); }

    /** @deprecated Use {@link SpeedrunHud#onRenderHud(GuiGraphics)} directly */
    @Deprecated public static void onRenderHud(GuiGraphics g) { SpeedrunHud.onRenderHud(g); }

    /** @deprecated Use {@link SpeedrunHud#renderPreviewHud(GuiGraphics, int, int)} directly */
    @Deprecated public static void renderPreviewHud(GuiGraphics g, int width, int margin) { SpeedrunHud.renderPreviewHud(g, width, margin); }

    /** @deprecated Use {@link SpeedrunRunInfo#save(boolean)} directly */
    @Deprecated public static void saveRunInfo(boolean isVictory) { SpeedrunRunInfo.save(isVictory); }

    /** @deprecated Use {@link SpeedrunRunInfo#get(String)} directly */
    @Deprecated public static SpeedrunRunInfo.RunInfo getRunInfo(String levelId) { return SpeedrunRunInfo.get(levelId); }

    /** @deprecated Use {@link SpeedrunRunInfo#show(net.minecraft.client.gui.screens.Screen, String)} directly */
    @Deprecated public static void showRunInfo(net.minecraft.client.gui.screens.Screen parent, String levelId) { SpeedrunRunInfo.show(parent, levelId); }

    /** @deprecated Use {@link SpeedrunRunInfo#getLevelId(net.minecraft.server.MinecraftServer)} directly */
    @Deprecated public static String getLevelId(net.minecraft.server.MinecraftServer server) { return SpeedrunRunInfo.getLevelId(server); }

    /** @deprecated Use {@link SpeedrunSplits#getSplits()} directly */
    @Deprecated public static Map<String, String> getSplits() { return SpeedrunSplits.getSplits(); }

    /** @deprecated Use {@link SpeedrunAutoNav#canAutoNavigateMenus()} directly */
    @Deprecated public static boolean canAutoNavigateMenus() { return SpeedrunAutoNav.canAutoNavigateMenus(); }

    /** @deprecated Use {@link SpeedrunAutoNav#isDisconnectingOrSaving()} directly */
    @Deprecated public static boolean isDisconnectingOrSaving() { return SpeedrunAutoNav.isDisconnectingOrSaving(); }

    /** @deprecated Use {@link SpeedrunAutoNav#tickAutoNavFromTitle(Minecraft)} directly */
    @Deprecated public static void tickAutoNavFromTitle(Minecraft mc) { SpeedrunAutoNav.tickAutoNavFromTitle(mc); }

    /** @deprecated Use {@link SpeedrunAutoNav#cancel()} directly */
    @Deprecated public static void cancelAutoNav() { SpeedrunAutoNav.cancel(); }

    /** @deprecated Use {@link SpeedrunAutoNav#resetProgress()} directly */
    @Deprecated public static void resetAutoNavProgress() { SpeedrunAutoNav.resetProgress(); }

    /** @deprecated Use {@link SpeedrunAutoNav#setAutoTriggerCreateWorld(boolean)} directly */
    @Deprecated public static void setAutoTriggerCreateWorld(boolean v) { SpeedrunAutoNav.setAutoTriggerCreateWorld(v); }

    /** @deprecated Use {@link SpeedrunAutoNav#isAutoTriggerCreateWorld()} directly */
    @Deprecated public static boolean isAutoTriggerCreateWorld() { return SpeedrunAutoNav.isAutoTriggerCreateWorld(); }

    /** @deprecated Use field directly */
    @Deprecated public static void setKeepObjectivesForNextRun(boolean v) { keepObjectivesForNextRun = v; }

    /** @deprecated Use field directly */
    @Deprecated public static boolean isKeepObjectivesForNextRun() { return keepObjectivesForNextRun; }

    /** @deprecated Use {@link SpeedrunRunInfo.RunInfo} directly */
    @Deprecated public static class RunInfo extends SpeedrunRunInfo.RunInfo {
        public RunInfo(boolean v, String t, String o, long ts) { super(v, t, o, ts); }
        public RunInfo() { super(); }
    }
}
