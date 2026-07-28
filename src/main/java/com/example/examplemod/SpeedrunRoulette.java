package com.example.examplemod;

import com.example.examplemod.event.AdvancementCompletedCallback;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.List;

public class SpeedrunRoulette implements ModInitializer {
    public static final String MODID = "examplemod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static String pendingVictoryTime = null;
    public static String pendingVictoryObjectiveName = null;

    public static volatile boolean pendingGiveUp = false;
    public static volatile boolean pendingReplay = false;
    public static volatile boolean pendingNewRun = false;
    public static volatile boolean pendingReset = false;

    // Auto-open wheel state
    public static boolean hasCheckedAutoOpen = false;

    public static String pendingLevelId = null;

    @Override
    public void onInitialize() {
        Config.load();

        // Networking: codecs (both sides) + server-side receivers.
        SpeedrunNetwork.registerPayloadTypes();
        SpeedrunNetwork.registerServerReceivers();

        // Commands.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SpeedrunCommands.register(dispatcher));

        // Server lifecycle.
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                LOGGER.info("HELLO from server starting"));

        // Sync full run state to players when they join so multiplayer guests
        // always get the same speedrun sample as the host.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            SpeedrunNetwork.sendRunStateToPlayer(player);
        });

        // Advancement completion (cooperative sharing + fallback finish claim).
        AdvancementCompletedCallback.EVENT.register(SpeedrunRoulette::onAdvancementCompleted);
    }

    private static void onAdvancementCompleted(ServerPlayer player, net.minecraft.advancements.AdvancementHolder advancement) {
        net.minecraft.server.MinecraftServer server = null;
        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            server = serverLevel.getServer();
        }
        if (server == null) return;

        SpeedrunWorldData data = SpeedrunWorldData.get(server);
        List<Objective> objs = data.getObjectives();
        String advId = advancement.id().toString();
        for (Objective obj : objs) {
            if (obj.getType() == Objective.Type.ADVANCEMENT && advId.equals(obj.getAdvancementId())) {
                LOGGER.info("Advancement completed: " + advId + " by " + player.getGameProfile().name());

                // Cooperative: share advancement progress with the whole team.
                // Challenge: each player must earn the advancement themselves.
                if (data.getGameMode() == SpeedrunGameMode.COOPERATIVE) {
                    obj.setForceCompleted(true);
                    data.setObjectives(objs);
                    SpeedrunNetwork.broadcastRunState(server);
                }

                // In either mode, if this player now has all objectives, claim finish.
                boolean allDone = true;
                for (Objective o : objs) {
                    if (!o.isCompleted(player)) {
                        allDone = false;
                        break;
                    }
                }
                if (allDone && !data.isRunFinished()) {
                    // Client will also claim with real timer; this covers pure-server edge cases
                    SpeedrunNetwork.handleClaimFinish(player, "--:--");
                }
                break;
            }
        }
    }
}
