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
    public static volatile boolean pendingRetryNewSeed = false;
    public static volatile boolean pendingNewRun = false;
    public static volatile boolean pendingReset = false;
    public static volatile boolean pendingMainMenu = false;

    public static volatile String pendingCommand = null;

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
        for (int i = 0; i < objs.size(); i++) {
            Objective obj = objs.get(i);
            if (obj.getType() == Objective.Type.ADVANCEMENT && advId.equals(obj.getAdvancementId())) {
                LOGGER.info("Advancement completed: {} by {}", advId, player.getGameProfile().name());

                if (data.getGameMode() == SpeedrunGameMode.COOPERATIVE) {
                    data.updateForceCompleted(i, true);
                    SpeedrunNetwork.broadcastRunState(server);
                }
                break;
            }
        }
    }
}
