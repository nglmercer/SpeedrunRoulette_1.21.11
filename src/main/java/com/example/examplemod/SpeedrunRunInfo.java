package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpeedrunRunInfo {

    public static class RunInfo {
        public boolean hasInfo;
        public boolean isVictory;
        public String time;
        public String objective;
        public long timestamp;

        public RunInfo(boolean v, String t, String o, long ts) {
            this.hasInfo = true;
            this.isVictory = v;
            this.time = t;
            this.objective = o;
            this.timestamp = ts;
        }
        public RunInfo() { this.hasInfo = false; }
    }

    private static final Map<String, RunInfo> runInfoCache = new HashMap<>();

    public static void save(boolean isVictory) {
        try {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();

            String time = SpeedrunRoulette.pendingVictoryTime != null
                ? SpeedrunRoulette.pendingVictoryTime
                : SpeedrunTimer.currentFormattedTime();

            String objectiveName;
            List<Objective> objs = SpeedrunState.getObjectives();
            if (!objs.isEmpty()) {
                if (objs.size() > 1) {
                    objectiveName = Component.translatable("gui.examplemod.list_of_items", objs.size()).getString();
                } else {
                    objectiveName = objs.get(0).getDisplayName().getString();
                }
            } else {
                objectiveName = Component.translatable("gui.examplemod.default_speedrun_name").getString();
            }

            if (server != null) {
                if (!server.isRunning() || server.isStopped()) {
                    return;
                }

                String levelId = getLevelId(server);
                if (levelId == null) return;

                File savesDir = mc.gameDirectory.toPath().resolve("saves").toFile();
                File levelDir = new File(savesDir, levelId);
                if (!levelDir.isDirectory()) return;

                File infoFile = new File(levelDir, "speedrun_info.nbt");

                CompoundTag tag = new CompoundTag();
                tag.putBoolean("isVictory", isVictory);
                tag.putString("time", time);
                tag.putLong("timestamp", System.currentTimeMillis());
                tag.putString("objectiveName", objectiveName);

                NbtIo.writeCompressed(tag, infoFile.toPath());
                SpeedrunRoulette.LOGGER.info("Saved run info to " + infoFile.getAbsolutePath());
            } else if (mc.player != null && mc.player.connection != null) {
                SpeedrunNetworkClient.sendToServer(new SpeedrunNetwork.SaveRunInfoPacket(isVictory, time, objectiveName));
            }
        } catch (Throwable t) {
            SpeedrunRoulette.LOGGER.error("Failed to save run info", t);
        }
    }

    public static RunInfo get(String levelId) {
        if (runInfoCache.containsKey(levelId)) return runInfoCache.get(levelId);

        File savesDir = Minecraft.getInstance().gameDirectory.toPath().resolve("saves").toFile();
        File levelDir = new File(savesDir, levelId);
        File infoFile = new File(levelDir, "speedrun_info.nbt");

        if (infoFile.exists()) {
            try {
                CompoundTag tag = NbtIo.readCompressed(infoFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                boolean v = tag.getBoolean("isVictory").orElse(false);
                String t = tag.getString("time").orElse("??");
                String o = tag.getString("objectiveName").orElse("??");
                long ts = tag.getLong("timestamp").orElse(0L);
                RunInfo info = new RunInfo(v, t, o, ts);
                runInfoCache.put(levelId, info);
                return info;
            } catch (Exception e) {
                runInfoCache.put(levelId, new RunInfo());
                return new RunInfo();
            }
        } else {
            runInfoCache.put(levelId, new RunInfo());
            return new RunInfo();
        }
    }

    public static void show(net.minecraft.client.gui.screens.Screen parent, String levelId) {
        File savesDir = Minecraft.getInstance().gameDirectory.toPath().resolve("saves").toFile();
        File levelDir = new File(savesDir, levelId);
        File infoFile = new File(levelDir, "speedrun_info.nbt");

        if (infoFile.exists()) {
            try {
                CompoundTag tag = NbtIo.readCompressed(infoFile.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                boolean isVictory = tag.getBoolean("isVictory").orElse(false);
                String time = tag.getString("time").orElse("00:00");
                String objName = tag.getString("objectiveName").orElse("Unknown");
                long timestamp = tag.getLong("timestamp").orElse(0L);
                String date = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(timestamp));

                MutableComponent titleComp = isVictory
                    ? Component.translatable("gui.examplemod.victory_indicator")
                    : Component.translatable("gui.examplemod.defeat_indicator");
                int color = isVictory ? 0xFF55FF55 : 0xFFFF5555;

                MutableComponent msg = titleComp.withStyle(style -> style.withColor(color).withBold(true))
                    .append("\n\n")
                    .append(Component.translatable("gui.examplemod.objective_label").append(" " + objName).withStyle(net.minecraft.ChatFormatting.WHITE))
                    .append("\n")
                    .append(Component.translatable("gui.examplemod.time_label").append(" " + time).withStyle(net.minecraft.ChatFormatting.YELLOW))
                    .append("\n")
                    .append(Component.translatable("gui.examplemod.date_label").append(" " + date).withStyle(net.minecraft.ChatFormatting.GRAY));

                Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                    (yes) -> Minecraft.getInstance().setScreen(parent),
                    Component.translatable("gui.examplemod.run_info_title"),
                    msg,
                    Component.translatable("gui.examplemod.close_button"),
                    Component.literal("")
                ));
            } catch (Exception e) {
                SpeedrunRoulette.LOGGER.error("Failed to read run info", e);
            }
        } else {
            Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                (yes) -> Minecraft.getInstance().setScreen(parent),
                Component.translatable("gui.examplemod.run_info_title"),
                Component.translatable("gui.examplemod.no_run_info"),
                Component.translatable("gui.examplemod.close_button"),
                Component.literal("")
            ));
        }
    }

    public static String getLevelId(net.minecraft.server.MinecraftServer server) {
        try {
            java.lang.reflect.Field f = net.minecraft.server.MinecraftServer.class.getDeclaredField("storageSource");
            f.setAccessible(true);
            Object storage = f.get(server);
            java.lang.reflect.Method m = storage.getClass().getMethod("getLevelId");
            return (String) m.invoke(storage);
        } catch (Exception e) {
            SpeedrunRoulette.LOGGER.error("Failed to get level ID via reflection", e);
            return null;
        }
    }
}
