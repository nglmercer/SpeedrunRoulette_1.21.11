package com.example.examplemod;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-side networking: registers handlers for server->client packets and
 * provides {@link #sendToServer(CustomPacketPayload)}.
 */
public class SpeedrunNetworkClient {

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(SpeedrunNetwork.SyncRunStatePacket.TYPE, (payload, context) ->
                context.client().execute(() -> SpeedrunState.applySyncedRunState(payload)));

        ClientPlayNetworking.registerGlobalReceiver(SpeedrunNetwork.SyncObjectivesPacket.TYPE, (payload, context) ->
                context.client().execute(() -> SpeedrunState.setObjectives(payload.objectives, false)));

        ClientPlayNetworking.registerGlobalReceiver(SpeedrunNetwork.RunFinishedPacket.TYPE, (payload, context) ->
                context.client().execute(() -> SpeedrunState.handleRunFinished(payload)));
    }

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
