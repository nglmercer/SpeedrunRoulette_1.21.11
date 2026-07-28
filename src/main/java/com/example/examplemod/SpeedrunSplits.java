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
            java.lang.reflect.Field progressField =
                net.minecraft.client.multiplayer.ClientAdvancements.class.getDeclaredField("progress");
            progressField.setAccessible(true);
            java.util.Map<?, ?> progressMap = (java.util.Map<?, ?>) progressField.get(advancements);

            for (java.util.Map.Entry<?, ?> entry : progressMap.entrySet()) {
                Object holder = entry.getKey();
                Object prog = entry.getValue();

                java.lang.reflect.Method idMethod = holder.getClass().getMethod("id");
                Identifier advId = (Identifier) idMethod.invoke(holder);

                if (advId.equals(loc)) {
                    java.lang.reflect.Method isDoneMethod = prog.getClass().getMethod("isDone");
                    boolean done = (boolean) isDoneMethod.invoke(prog);
                    if (done) {
                        record(splitName);
                    }
                    return;
                }
            }
        } catch (Throwable t) {
            // Ignore reflection errors to prevent crash
        }
    }
}
