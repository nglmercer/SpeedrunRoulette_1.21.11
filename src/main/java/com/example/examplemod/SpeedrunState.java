package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpeedrunState {
    private static List<Objective> objectives = new ArrayList<>();
    private static List<Objective> objectivesBackup = null;
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
    private static int finishClaimWaitTicks = 0;

    private static boolean waitingForServerStop = false;
    private static String disconnectLabel = "";
    private static int serverStopWaitTicks = 0;

    /**
     * True while a disconnect is pending or in-progress on the render thread.
     * Set ONLY from the render thread. Cleared when TitleScreen arrives.
     */
    public static volatile boolean isTransitioning = false;

    // --- Objectives Management ---

    public static void setObjectives(List<Objective> objs, boolean save) {
        if (objs == null) return;
        objectives = new ArrayList<>(objs);
        objectivesFresh = true;
        objectivesCompleted = false;
        objectivesLoaded = true;
        autoOpenDelayTicks = 0;
        runFinished = false;
        finishClaimPending = false;
        finishClaimWaitTicks = 0;
        lastWinnerName = "";
        lastWinnerUuid = "";
        if (Config.AUTO_START.get() && !objs.isEmpty()) {
            SpeedrunTimer.start();
        }
        if (save) {
            saveObjectivesToWorld();
        }
        SpeedrunObjectiveStorage.save(objectives, activeGameMode);
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

    public static boolean isSingleplayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getSingleplayerServer() != null;
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
        activeGameMode = Config.getGameMode();
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            SpeedrunWorldData data = SpeedrunWorldData.get(server);
            data.setGameMode(activeGameMode);
            data.setObjectives(objectives);
            SpeedrunNetwork.broadcastRunState(server);
            SpeedrunRoulette.LOGGER.info("[SaveObjectives] Saved {} objectives to world data (singleplayer)", objectives.size());
        } else if (mc.player != null) {
            SpeedrunNetworkClient.sendToServer(new SpeedrunNetwork.SaveObjectivesPacket(
                    new ArrayList<>(objectives), activeGameMode));
            SpeedrunRoulette.LOGGER.info("[SaveObjectives] Sent {} objectives to server (multiplayer)", objectives.size());
        } else {
            SpeedrunRoulette.LOGGER.warn("[SaveObjectives] FAILED: no server and no player, {} objectives lost!", objectives.size());
        }
    }

    public static List<Objective> getObjectives() {
        return objectives;
    }

    public static void clearObjectives() {
        objectives = new ArrayList<>();
        objectivesBackup = null;
        objectivesCompleted = false;
        objectivesLoaded = true;
        autoOpenDelayTicks = 0;
        runFinished = false;
        finishClaimPending = false;
        finishClaimWaitTicks = 0;
        lastWinnerName = "";
        lastWinnerUuid = "";
        SpeedrunObjectiveStorage.clear();
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

        if (objectivesBackup != null && !objectivesBackup.isEmpty()) {
            SpeedrunRoulette.LOGGER.info("[LoadObjectives] Restoring {} objectives from backup (retry new seed)", objectivesBackup.size());
            objectives = new ArrayList<>(objectivesBackup);
            objectivesBackup = null;
            objectivesFresh = true;
            objectivesCompleted = false;
            objectivesLoaded = true;
            autoOpenDelayTicks = 0;
            SpeedrunObjectiveStorage.save(objectives, activeGameMode);
            return;
        }

        if (server != null) {
            SpeedrunWorldData data = SpeedrunWorldData.get(server);
            List<Objective> saved = data.getObjectives();
            SpeedrunRoulette.LOGGER.info("[LoadObjectives] Found {} saved objectives in world data", saved.size());
            if (!saved.isEmpty()) {
                objectives = new ArrayList<>(saved);
                objectivesFresh = true;
                objectivesCompleted = false;
            } else {
                objectivesFresh = false;
            }
            objectivesLoaded = true;
            autoOpenDelayTicks = 0;
            runFinished = false;
            finishClaimPending = false;
        } else {
            List<Objective> persisted = SpeedrunObjectiveStorage.load();
            if (!persisted.isEmpty()) {
                SpeedrunRoulette.LOGGER.info("[LoadObjectives] Restored {} objectives from persistent storage", persisted.size());
                objectives = persisted;
                objectivesFresh = true;
                objectivesCompleted = false;
                objectivesLoaded = true;
                autoOpenDelayTicks = 0;
            }
            runFinished = false;
            finishClaimPending = false;
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
        SpeedrunTimer.reset();
        SpeedrunSplits.reset();
        objectivesFresh = true;
        objectivesLoaded = false;
        objectivesCompleted = false;
        runFinished = false;
        finishClaimPending = false;
        SpeedrunRoulette.LOGGER.info("[Retry] Prepared for retry — objectives will reload from world data");
    }

    public static void prepareForNewGame() {
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
        if (anyPendingOrTransitioning()) {
            SpeedrunRoulette.LOGGER.warn("[Transition] beginRetryAndDisconnect BLOCKED by pending/transitioning state");
            return;
        }
        SpeedrunRoulette.LOGGER.info("[Transition] beginRetryAndDisconnect START");
        SpeedrunRoulette.pendingReplay = true;
        isTransitioning = true;
        doDisconnect("RetrySameSeed");
    }

    public static void beginGiveUpAndDisconnect() {
        if (anyPendingOrTransitioning()) {
            SpeedrunRoulette.LOGGER.warn("[Transition] beginGiveUpAndDisconnect BLOCKED by pending/transitioning state");
            return;
        }
        SpeedrunRoulette.LOGGER.info("[Transition] beginGiveUpAndDisconnect START");
        SpeedrunRoulette.pendingGiveUp = true;
        isTransitioning = true;
        doDisconnect("GiveUp");
    }

    public static void beginRetryNewSeedAndDisconnect() {
        if (anyPendingOrTransitioning()) {
            SpeedrunRoulette.LOGGER.warn("[Transition] beginRetryNewSeedAndDisconnect BLOCKED by pending/transitioning state");
            return;
        }
        SpeedrunRoulette.LOGGER.info("[Transition] beginRetryNewSeedAndDisconnect START");
        SpeedrunRoulette.pendingRetryNewSeed = true;
        isTransitioning = true;
        doDisconnect("RetryNewSeed");
    }

    public static void beginNewRunAndDisconnect() {
        if (anyPendingOrTransitioning()) {
            SpeedrunRoulette.LOGGER.warn("[Transition] beginNewRunAndDisconnect BLOCKED by pending/transitioning state");
            return;
        }
        SpeedrunRoulette.LOGGER.info("[Transition] beginNewRunAndDisconnect START");
        SpeedrunRoulette.pendingNewRun = true;
        isTransitioning = true;
        doDisconnect("NewRun");
    }

    public static void beginMainMenuAndDisconnect() {
        if (anyPendingOrTransitioning()) {
            SpeedrunRoulette.LOGGER.warn("[Transition] beginMainMenuAndDisconnect BLOCKED by pending/transitioning state");
            return;
        }
        SpeedrunRoulette.LOGGER.info("[Transition] beginMainMenuAndDisconnect START");
        SpeedrunRoulette.pendingMainMenu = true;
        isTransitioning = true;
        doDisconnect("MainMenu");
    }

    public static void beginResetAndDisconnect() {
        if (anyPendingOrTransitioning()) {
            SpeedrunRoulette.LOGGER.warn("[Transition] beginResetAndDisconnect BLOCKED by pending/transitioning state");
            return;
        }
        SpeedrunRoulette.LOGGER.info("[Transition] beginResetAndDisconnect START");
        SpeedrunRoulette.pendingReset = true;
        isTransitioning = true;
        doDisconnect("Reset");
    }

    private static void doDisconnect(String label) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            SpeedrunRoulette.LOGGER.info("[Transition][{}] Saving run info...", label);
            trySaveRunInfo(false);
            SpeedrunRoulette.LOGGER.info("[Transition][{}] Capturing level ID...", label);
            captureLevelId(mc);

            net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                if (label.equals("RetrySameSeed") || label.equals("RetryNewSeed")) {
                    SpeedrunWorldData data = SpeedrunWorldData.get(server);
                    data.clearRunResult();
                    for (Objective obj : data.getObjectives()) {
                        obj.setForceCompleted(false);
                    }
                    data.setDirty();
                    SpeedrunRoulette.LOGGER.info("[Transition][{}] Cleared run result and forceCompleted in world data", label);
                } else if (label.equals("NewRun") || label.equals("GiveUp")) {
                    SpeedrunWorldData data = SpeedrunWorldData.get(server);
                    data.clearRunResult();
                    data.setObjectives(new ArrayList<>());
                    SpeedrunRoulette.LOGGER.info("[Transition][{}] Cleared world data for new run", label);
                }
                SpeedrunRoulette.LOGGER.info("[Transition][{}] Halting integrated server (non-blocking)...", label);
                server.halt(false);
                waitingForServerStop = true;
                disconnectLabel = label;
                serverStopWaitTicks = 0;
            } else {
                SpeedrunRoulette.LOGGER.info("[Transition][{}] No server, going to TitleScreen directly", label);
                mc.setScreen(new TitleScreen());
                handleTitleScreenArrival(mc);
            }
        } else {
            SpeedrunRoulette.LOGGER.info("[Transition][{}] No active world, handling TitleScreen arrival directly", label);
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
        boolean startingRetryNewSeed = SpeedrunRoulette.pendingRetryNewSeed;
        boolean startingReset = SpeedrunRoulette.pendingReset;
        boolean startingMainMenu = SpeedrunRoulette.pendingMainMenu;

        if (!startingNew && !startingRetry && !startingRetryNewSeed && !startingReset && !startingMainMenu) {
            isTransitioning = false;
            return;
        }

        SpeedrunRoulette.LOGGER.info("TitleScreen arrival: pendingNew={}, pendingRetry={}, pendingRetryNewSeed={}, pendingReset={}, pendingMainMenu={}",
            startingNew, startingRetry, startingRetryNewSeed, startingReset, startingMainMenu);

        if (startingMainMenu) {
            SpeedrunRoulette.LOGGER.info("TitleScreen: Main Menu (no auto-nav)");
            prepareForNewGame();
            isTransitioning = false;
        } else if (startingRetry) {
            SpeedrunRoulette.LOGGER.info("TitleScreen: Preparing for Retry Same Seed");
            prepareForRetry();
            SpeedrunAutoNav.autoReEnterWorld = true;
            SpeedrunAutoNav.targetLevelId = SpeedrunRoulette.pendingLevelId;
            SpeedrunAutoNav.autoTriggerCreateWorld = false;
            SpeedrunAutoNav.resetProgress();
        } else if (startingRetryNewSeed) {
            SpeedrunRoulette.LOGGER.info("TitleScreen: Preparing for Retry New Seed — preserving {} objectives", objectives.size());
            objectivesBackup = new ArrayList<>(objectives);
            SpeedrunTimer.reset();
            SpeedrunSplits.reset();
            objectivesFresh = true;
            objectivesLoaded = true;
            SpeedrunAutoNav.autoTriggerCreateWorld = true;
            SpeedrunAutoNav.resetProgress();
        } else if (startingNew) {
            SpeedrunRoulette.LOGGER.info("TitleScreen: Preparing for New Game");
            prepareForNewGame();
            SpeedrunAutoNav.autoTriggerCreateWorld = true;
            SpeedrunAutoNav.resetProgress();
        } else {
            SpeedrunRoulette.LOGGER.info("TitleScreen: Preparing for Reset (Delete World + New)");
            prepareForNewGame();
            SpeedrunAutoNav.autoTriggerCreateWorld = true;
            SpeedrunAutoNav.resetProgress();
            SpeedrunRouletteClient.deleteWorldSave();
        }

        SpeedrunRoulette.pendingGiveUp = false;
        SpeedrunRoulette.pendingNewRun = false;
        SpeedrunRoulette.pendingReplay = false;
        SpeedrunRoulette.pendingRetryNewSeed = false;
        SpeedrunRoulette.pendingReset = false;
        SpeedrunRoulette.pendingMainMenu = false;
        SpeedrunRoulette.pendingVictoryTime = null;
        SpeedrunRoulette.pendingVictoryObjectiveName = null;
        SpeedrunRoulette.hasCheckedAutoOpen = false;

        if (!SpeedrunAutoNav.autoTriggerCreateWorld && !SpeedrunAutoNav.autoReEnterWorld) {
            finishTransition();
        } else {
            isTransitioning = false;
            SpeedrunAutoNav.resetProgress();
        }

        SpeedrunRoulette.LOGGER.info("[Transition] handleTitleScreenArrival DONE: autoCreate={}, autoReEnter={}, targetLevel={}, screen={}",
            SpeedrunAutoNav.autoTriggerCreateWorld, SpeedrunAutoNav.autoReEnterWorld,
            SpeedrunAutoNav.targetLevelId, mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");

        if (!(mc.screen instanceof TitleScreen)) {
            mc.setScreen(new TitleScreen());
        }
    }

    private static boolean anyPendingOrTransitioning() {
        return SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingNewRun
            || SpeedrunRoulette.pendingReplay || SpeedrunRoulette.pendingRetryNewSeed
            || SpeedrunRoulette.pendingReset || SpeedrunRoulette.pendingMainMenu
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
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            SpeedrunRoulette.pendingLevelId = SpeedrunRunInfo.getLevelId(server);
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
        SpeedrunRoulette.LOGGER.info("[AutoOpen] objectivesLoaded={}, hasObjs={}, fresh={}, backup={}",
            objectivesLoaded, hasObjs, objectivesFresh, objectivesBackup != null);

        if (hasObjs && objectivesFresh) {
            saveObjectivesToWorld();
            objectivesFresh = false;
            objectivesBackup = null;
        }

        if (!hasObjs) {
            SpeedrunRoulette.LOGGER.info("[AutoOpen] No objectives -> opening wheel");
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

        if (waitingForServerStop) {
            serverStopWaitTicks++;
            net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
            boolean gone = (server == null || !server.isRunning());

            if (gone || serverStopWaitTicks > 200) {
                waitingForServerStop = false;
                SpeedrunRoulette.LOGGER.info("[Transition][{}] Server stopped after {} ticks (forced={})",
                    disconnectLabel, serverStopWaitTicks, !gone);
                serverStopWaitTicks = 0;
                mc.disconnect(new TitleScreen(), false);
                SpeedrunRoulette.LOGGER.info("[Transition][{}] mc.disconnect() returned", disconnectLabel);
            }
            return;
        }

        String cmd = SpeedrunRoulette.pendingCommand;
        if (cmd != null) {
            SpeedrunRoulette.pendingCommand = null;
            SpeedrunRoulette.LOGGER.info("[Command] Processing pending command: {}", cmd);
            switch (cmd) {
                case "new" -> beginNewRunAndDisconnect();
                case "retry" -> beginRetryAndDisconnect();
                case "retrynewseed" -> beginRetryNewSeedAndDisconnect();
                case "giveup" -> beginGiveUpAndDisconnect();
                case "mainmenu" -> beginMainMenuAndDisconnect();
                case "reset" -> beginResetAndDisconnect();
                default -> SpeedrunRoulette.LOGGER.warn("[Command] Unknown pending command: {}", cmd);
            }
            return;
        }

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

        if (finishClaimPending) {
            finishClaimWaitTicks++;
            if (finishClaimWaitTicks > 100) {
                SpeedrunRoulette.LOGGER.warn("[Finish] Claim timeout after {} ticks — resetting finishClaimPending", finishClaimWaitTicks);
                finishClaimPending = false;
                finishClaimWaitTicks = 0;
            }
        }

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
                    finishClaimWaitTicks = 0;
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

    public static void onScreenInit(net.minecraft.client.gui.screens.Screen screen) {
        SpeedrunAutoNav.onScreenInit(screen);
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

    /** @deprecated Use {@link SpeedrunRunInfo.RunInfo} directly */
    @Deprecated public static class RunInfo extends SpeedrunRunInfo.RunInfo {
        public RunInfo(boolean v, String t, String o, long ts) { super(v, t, o, ts); }
        public RunInfo() { super(); }
    }
}
