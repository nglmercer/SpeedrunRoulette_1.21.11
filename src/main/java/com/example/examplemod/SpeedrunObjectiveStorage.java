package com.example.examplemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SpeedrunObjectiveStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path storagePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("speedrun_roulette").resolve("active_objectives.json");
    }

    public static void save(List<Objective> objectives, SpeedrunGameMode mode) {
        try {
            Path path = storagePath();
            Files.createDirectories(path.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("gameMode", mode != null ? mode.name() : SpeedrunGameMode.COOPERATIVE.name());

            JsonArray arr = new JsonArray();
            for (Objective obj : objectives) {
                Objective.CODEC.encodeStart(JsonOps.INSTANCE, obj)
                        .result()
                        .ifPresent(arr::add);
            }
            root.add("objectives", arr);

            Files.writeString(path, GSON.toJson(root));
            SpeedrunRoulette.LOGGER.info("[ObjectiveStorage] Saved {} objectives to disk", objectives.size());
        } catch (Throwable t) {
            SpeedrunRoulette.LOGGER.error("[ObjectiveStorage] Failed to save objectives", t);
        }
    }

    public static List<Objective> load() {
        List<Objective> result = new ArrayList<>();
        try {
            Path path = storagePath();
            if (!Files.exists(path)) return result;

            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("objectives");
            if (arr == null) return result;

            for (JsonElement el : arr) {
                Objective.CODEC.parse(JsonOps.INSTANCE, el)
                        .result()
                        .ifPresent(result::add);
            }

            String modeStr = root.has("gameMode") ? root.get("gameMode").getAsString() : null;
            if (modeStr != null) {
                SpeedrunState.setActiveGameMode(SpeedrunGameMode.fromString(modeStr));
            }

            SpeedrunRoulette.LOGGER.info("[ObjectiveStorage] Loaded {} objectives from disk", result.size());
        } catch (Throwable t) {
            SpeedrunRoulette.LOGGER.error("[ObjectiveStorage] Failed to load objectives", t);
        }
        return result;
    }

    public static void clear() {
        try {
            Path path = storagePath();
            if (Files.exists(path)) {
                Files.delete(path);
                SpeedrunRoulette.LOGGER.info("[ObjectiveStorage] Cleared persistent objectives");
            }
        } catch (Throwable t) {
            SpeedrunRoulette.LOGGER.error("[ObjectiveStorage] Failed to clear objectives", t);
        }
    }

    public static boolean exists() {
        return Files.exists(storagePath());
    }
}
