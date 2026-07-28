package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.LinkedHashMap;
import java.util.Map;

public class SpeedrunSplits {
    private static final Map<String, String> splits = new LinkedHashMap<>();
    private static ResourceKey<Level> lastDimension = Level.OVERWORLD;

    public static void reset() {
        splits.clear();
        lastDimension = Level.OVERWORLD;
    }

    public static Map<String, String> getSplits() {
        return splits;
    }

    public static void record(String name) {
        if (!splits.containsKey(name)) {
            splits.put(name, SpeedrunTimer.currentFormattedTime());
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
        }
    }

    public static void track() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Dimension changes
        ResourceKey<Level> currentDim = mc.player.level().dimension();
        if (currentDim != lastDimension) {
            if (currentDim == Level.NETHER) {
                record(Component.translatable("gui.examplemod.split.nether_entry").getString());
            } else if (currentDim == Level.END) {
                record(Component.translatable("gui.examplemod.split.end_entry").getString());
            }
            lastDimension = currentDim;
        }

        // Village detection (every 20 ticks)
        String villageSplit = Component.translatable("gui.examplemod.split.village_found").getString();
        if (mc.player.tickCount % 20 == 0 && !splits.containsKey(villageSplit)) {
            AABB box = mc.player.getBoundingBox().inflate(32);
            java.util.List<Entity> entities = mc.player.level().getEntities(
                mc.player, box, e -> e.getType() == EntityType.VILLAGER);
            if (!entities.isEmpty()) {
                record(villageSplit);
            }
        }

        // Advancement-based structure detection (every 20 ticks)
        if (mc.player.tickCount % 20 == 0 && mc.player.connection != null) {
            net.minecraft.client.multiplayer.ClientAdvancements advancements =
                mc.player.connection.getAdvancements();
            if (advancements != null) {
                checkAdvancement(advancements, "minecraft:nether/find_fortress",
                    Component.translatable("gui.examplemod.split.fortress_found").getString());
                checkAdvancement(advancements, "minecraft:nether/find_bastion",
                    Component.translatable("gui.examplemod.split.bastion_found").getString());
                checkAdvancement(advancements, "minecraft:story/follow_ender_eye",
                    Component.translatable("gui.examplemod.split.stronghold_found").getString());
            }
        }
    }

    private static void checkAdvancement(net.minecraft.client.multiplayer.ClientAdvancements advancements,
                                          String id, String splitName) {
        if (splits.containsKey(splitName)) return;

        Identifier loc = Identifier.tryParse(id);
        if (loc == null) return;

        try {
            for (Map.Entry<net.minecraft.advancements.AdvancementHolder, net.minecraft.advancements.AdvancementProgress> entry : advancements.progress.entrySet()) {
                if (entry.getKey().id().equals(loc)) {
                    if (entry.getValue().isDone()) {
                        record(splitName);
                    }
                    return;
                }
            }
        } catch (Throwable t) {
            // Ignore errors to prevent crash
        }
    }
}
