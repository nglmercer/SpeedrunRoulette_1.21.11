package com.example.examplemod;

import net.minecraft.network.chat.Component;

/**
 * Multiplayer speedrun modes.
 * <ul>
 *   <li>{@link #COOPERATIVE} (default): all players share the same rolled objectives
 *       and win together when any teammate finishes.</li>
 *   <li>{@link #CHALLENGE}: same shared objectives, but first player to finish wins;
 *       everyone else gets a defeat screen.</li>
 * </ul>
 */
public enum SpeedrunGameMode {
    COOPERATIVE,
    CHALLENGE;

    public static SpeedrunGameMode fromString(String value) {
        if (value == null || value.isEmpty()) return COOPERATIVE;
        try {
            return SpeedrunGameMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return COOPERATIVE;
        }
    }

    public SpeedrunGameMode next() {
        return this == COOPERATIVE ? CHALLENGE : COOPERATIVE;
    }

    public Component displayName() {
        return switch (this) {
            case COOPERATIVE -> Component.translatable("gui.examplemod.mode.cooperative");
            case CHALLENGE -> Component.translatable("gui.examplemod.mode.challenge");
        };
    }

    public Component description() {
        return switch (this) {
            case COOPERATIVE -> Component.translatable("gui.examplemod.mode.cooperative.desc");
            case CHALLENGE -> Component.translatable("gui.examplemod.mode.challenge.desc");
        };
    }

    public boolean isCompetitive() {
        return this == CHALLENGE;
    }
}
