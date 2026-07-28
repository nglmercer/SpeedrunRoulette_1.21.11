package com.example.examplemod.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired on the server thread when a player fully completes an advancement.
 * Replacement for NeoForge's {@code AdvancementEvent.AdvancementProgressEvent}.
 */
public interface AdvancementCompletedCallback {
    Event<AdvancementCompletedCallback> EVENT = EventFactory.createArrayBacked(
            AdvancementCompletedCallback.class,
            listeners -> (player, advancement) -> {
                for (AdvancementCompletedCallback listener : listeners) {
                    listener.onCompleted(player, advancement);
                }
            });

    void onCompleted(ServerPlayer player, AdvancementHolder advancement);
}
