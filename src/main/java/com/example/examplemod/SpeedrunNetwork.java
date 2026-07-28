package com.example.examplemod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SpeedrunNetwork {

    private static final String PROTOCOL_VERSION = "2";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("examplemod")
                .versioned(PROTOCOL_VERSION)
                .optional();

        // Server -> Client: full run state (objectives + mode + finish)
        registrar.playToClient(
                SyncRunStatePacket.TYPE,
                SyncRunStatePacket.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        SpeedrunState.applySyncedRunState(payload)
                )
        );

        // Legacy objectives-only sync still supported for compatibility
        registrar.playToClient(
                SyncObjectivesPacket.TYPE,
                SyncObjectivesPacket.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        SpeedrunState.setObjectives(payload.objectives, false)
                )
        );

        // Server -> Client: run finished (victory / defeat)
        registrar.playToClient(
                RunFinishedPacket.TYPE,
                RunFinishedPacket.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        SpeedrunState.handleRunFinished(payload)
                )
        );

        // Client -> Server: save objectives + mode and broadcast to all
        registrar.playToServer(
                SaveObjectivesPacket.TYPE,
                SaveObjectivesPacket.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        var server = getServer(sp);
                        if (server == null) return;
                        SpeedrunWorldData data = SpeedrunWorldData.get(server);
                        data.setGameMode(payload.gameMode);
                        data.setObjectives(payload.objectives);
                        broadcastRunState(server);
                    }
                })
        );

        // Client -> Server: set game mode only
        registrar.playToServer(
                SetGameModePacket.TYPE,
                SetGameModePacket.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        var server = getServer(sp);
                        if (server == null) return;
                        SpeedrunWorldData data = SpeedrunWorldData.get(server);
                        data.setGameMode(payload.gameMode);
                        broadcastRunState(server);
                    }
                })
        );

        // Client -> Server: claim finish (server is authoritative)
        registrar.playToServer(
                ClaimFinishPacket.TYPE,
                ClaimFinishPacket.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        handleClaimFinish(sp, payload.time);
                    }
                })
        );

        registrar.playToServer(
                SaveRunInfoPacket.TYPE,
                SaveRunInfoPacket.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        SpeedrunWorldData.get(getServer(sp)).setRunInfo(payload.isVictory, payload.time, payload.objectiveName);
                    }
                })
        );
    }

    static net.minecraft.server.MinecraftServer getServer(ServerPlayer player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            return serverLevel.getServer();
        }
        return null;
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(CustomPacketPayload payload, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }

    public static void broadcastRunState(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        SpeedrunWorldData data = SpeedrunWorldData.get(server);
        SyncRunStatePacket packet = SyncRunStatePacket.fromWorldData(data);
        sendToAllPlayers(packet);
    }

    public static void sendRunStateToPlayer(ServerPlayer player) {
        var server = getServer(player);
        if (server == null) return;
        SpeedrunWorldData data = SpeedrunWorldData.get(server);
        sendToPlayer(SyncRunStatePacket.fromWorldData(data), player);
    }

    /**
     * Server-side: first valid claim wins (challenge) or shared win (cooperative).
     */
    public static void handleClaimFinish(ServerPlayer player, String time) {
        var server = getServer(player);
        if (server == null) return;

        SpeedrunWorldData data = SpeedrunWorldData.get(server);
        if (data.getObjectives().isEmpty()) return;

        // Verify the claiming player actually completed all objectives
        for (Objective obj : data.getObjectives()) {
            if (!obj.isCompleted(player)) {
                return;
            }
        }

        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().name();
        String finishTime = time != null ? time : "--:--";

        if (!data.tryFinishRun(uuid, name, finishTime)) {
            // Already finished — re-broadcast existing result so late claimants still see it
            RunFinishedPacket existing = new RunFinishedPacket(
                    data.getGameMode(),
                    data.getWinnerUuid(),
                    data.getWinnerName(),
                    data.getFinishTime()
            );
            sendToPlayer(existing, player);
            return;
        }

        data.setRunInfo(true, finishTime, summarizeObjectives(data.getObjectives()));

        RunFinishedPacket result = new RunFinishedPacket(
                data.getGameMode(),
                uuid,
                name,
                finishTime
        );
        sendToAllPlayers(result);
        // Keep clients' objective/mode state in sync too
        broadcastRunState(server);
    }

    private static String summarizeObjectives(List<Objective> objs) {
        if (objs == null || objs.isEmpty()) return "Speedrun";
        if (objs.size() == 1) return objs.get(0).getDisplayName().getString();
        return objs.size() + " objectives";
    }

    static List<Objective> readObjectives(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Objective> objectives = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                objectives.add(Objective.load(tag, null));
            }
        }
        return objectives;
    }

    static void writeObjectives(FriendlyByteBuf buf, List<Objective> objectives) {
        buf.writeInt(objectives.size());
        for (Objective obj : objectives) {
            buf.writeNbt(obj.save(null));
        }
    }

    // --- Packets ---

    public static class SyncRunStatePacket implements CustomPacketPayload {
        public static final Type<SyncRunStatePacket> TYPE = new Type<>(Identifier.tryParse("examplemod:sync_run_state"));
        public static final StreamCodec<FriendlyByteBuf, SyncRunStatePacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    writeObjectives(buf, pkt.objectives);
                    buf.writeUtf(pkt.gameMode.name());
                    buf.writeBoolean(pkt.runFinished);
                    buf.writeUtf(pkt.winnerUuid);
                    buf.writeUtf(pkt.winnerName);
                    buf.writeUtf(pkt.finishTime);
                },
                (buf) -> new SyncRunStatePacket(
                        readObjectives(buf),
                        SpeedrunGameMode.fromString(buf.readUtf()),
                        buf.readBoolean(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf()
                )
        );

        public final List<Objective> objectives;
        public final SpeedrunGameMode gameMode;
        public final boolean runFinished;
        public final String winnerUuid;
        public final String winnerName;
        public final String finishTime;

        public SyncRunStatePacket(List<Objective> objectives, SpeedrunGameMode gameMode,
                                  boolean runFinished, String winnerUuid, String winnerName, String finishTime) {
            this.objectives = objectives;
            this.gameMode = gameMode != null ? gameMode : SpeedrunGameMode.COOPERATIVE;
            this.runFinished = runFinished;
            this.winnerUuid = winnerUuid != null ? winnerUuid : "";
            this.winnerName = winnerName != null ? winnerName : "";
            this.finishTime = finishTime != null ? finishTime : "";
        }

        public static SyncRunStatePacket fromWorldData(SpeedrunWorldData data) {
            return new SyncRunStatePacket(
                    new ArrayList<>(data.getObjectives()),
                    data.getGameMode(),
                    data.isRunFinished(),
                    data.getWinnerUuid(),
                    data.getWinnerName(),
                    data.getFinishTime()
            );
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static class SyncObjectivesPacket implements CustomPacketPayload {
        public static final Type<SyncObjectivesPacket> TYPE = new Type<>(Identifier.tryParse("examplemod:sync_objectives"));
        public static final StreamCodec<FriendlyByteBuf, SyncObjectivesPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> writeObjectives(buf, pkt.objectives),
                (buf) -> new SyncObjectivesPacket(readObjectives(buf))
        );

        public final List<Objective> objectives;

        public SyncObjectivesPacket(List<Objective> objectives) {
            this.objectives = objectives;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static class SaveObjectivesPacket implements CustomPacketPayload {
        public static final Type<SaveObjectivesPacket> TYPE = new Type<>(Identifier.tryParse("examplemod:save_objectives"));
        public static final StreamCodec<FriendlyByteBuf, SaveObjectivesPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    writeObjectives(buf, pkt.objectives);
                    buf.writeUtf(pkt.gameMode.name());
                },
                (buf) -> new SaveObjectivesPacket(
                        readObjectives(buf),
                        SpeedrunGameMode.fromString(buf.readUtf())
                )
        );

        public final List<Objective> objectives;
        public final SpeedrunGameMode gameMode;

        public SaveObjectivesPacket(List<Objective> objectives) {
            this(objectives, Config.getGameMode());
        }

        public SaveObjectivesPacket(List<Objective> objectives, SpeedrunGameMode gameMode) {
            this.objectives = objectives;
            this.gameMode = gameMode != null ? gameMode : SpeedrunGameMode.COOPERATIVE;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static class SetGameModePacket implements CustomPacketPayload {
        public static final Type<SetGameModePacket> TYPE = new Type<>(Identifier.tryParse("examplemod:set_game_mode"));
        public static final StreamCodec<FriendlyByteBuf, SetGameModePacket> CODEC = StreamCodec.of(
                (buf, pkt) -> buf.writeUtf(pkt.gameMode.name()),
                (buf) -> new SetGameModePacket(SpeedrunGameMode.fromString(buf.readUtf()))
        );

        public final SpeedrunGameMode gameMode;

        public SetGameModePacket(SpeedrunGameMode gameMode) {
            this.gameMode = gameMode != null ? gameMode : SpeedrunGameMode.COOPERATIVE;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static class ClaimFinishPacket implements CustomPacketPayload {
        public static final Type<ClaimFinishPacket> TYPE = new Type<>(Identifier.tryParse("examplemod:claim_finish"));
        public static final StreamCodec<FriendlyByteBuf, ClaimFinishPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> buf.writeUtf(pkt.time),
                (buf) -> new ClaimFinishPacket(buf.readUtf())
        );

        public final String time;

        public ClaimFinishPacket(String time) {
            this.time = time != null ? time : "--:--";
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static class RunFinishedPacket implements CustomPacketPayload {
        public static final Type<RunFinishedPacket> TYPE = new Type<>(Identifier.tryParse("examplemod:run_finished"));
        public static final StreamCodec<FriendlyByteBuf, RunFinishedPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    buf.writeUtf(pkt.gameMode.name());
                    buf.writeUtf(pkt.winnerUuid);
                    buf.writeUtf(pkt.winnerName);
                    buf.writeUtf(pkt.finishTime);
                },
                (buf) -> new RunFinishedPacket(
                        SpeedrunGameMode.fromString(buf.readUtf()),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf()
                )
        );

        public final SpeedrunGameMode gameMode;
        public final String winnerUuid;
        public final String winnerName;
        public final String finishTime;

        public RunFinishedPacket(SpeedrunGameMode gameMode, String winnerUuid, String winnerName, String finishTime) {
            this.gameMode = gameMode != null ? gameMode : SpeedrunGameMode.COOPERATIVE;
            this.winnerUuid = winnerUuid != null ? winnerUuid : "";
            this.winnerName = winnerName != null ? winnerName : "";
            this.finishTime = finishTime != null ? finishTime : "--:--";
        }

        public boolean isLocalPlayerWinner(UUID localUuid) {
            if (localUuid == null || winnerUuid == null || winnerUuid.isEmpty()) return false;
            return winnerUuid.equals(localUuid.toString());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static class SaveRunInfoPacket implements CustomPacketPayload {
        public static final Type<SaveRunInfoPacket> TYPE = new Type<>(Identifier.tryParse("examplemod:save_run_info"));
        public static final StreamCodec<FriendlyByteBuf, SaveRunInfoPacket> CODEC = StreamCodec.of(
                (buf, pkt) -> {
                    buf.writeBoolean(pkt.isVictory);
                    buf.writeUtf(pkt.time);
                    buf.writeUtf(pkt.objectiveName);
                },
                (buf) -> new SaveRunInfoPacket(buf.readBoolean(), buf.readUtf(), buf.readUtf())
        );

        public final boolean isVictory;
        public final String time;
        public final String objectiveName;

        public SaveRunInfoPacket(boolean isVictory, String time, String objectiveName) {
            this.isVictory = isVictory;
            this.time = time;
            this.objectiveName = objectiveName;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
