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

public class SpeedrunNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("examplemod")
                .versioned(PROTOCOL_VERSION)
                .optional();

        registrar.playToClient(
                SyncObjectivesPacket.TYPE,
                SyncObjectivesPacket.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        SpeedrunState.setObjectives(payload.objectives, false)
                )
        );

        registrar.playToServer(
                SaveObjectivesPacket.TYPE,
                SaveObjectivesPacket.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        SpeedrunWorldData data = SpeedrunWorldData.get(getServer(sp));
                        data.setObjectives(payload.objectives);
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
                (buf, pkt) -> writeObjectives(buf, pkt.objectives),
                (buf) -> new SaveObjectivesPacket(readObjectives(buf))
        );

        public final List<Objective> objectives;

        public SaveObjectivesPacket(List<Objective> objectives) {
            this.objectives = objectives;
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
