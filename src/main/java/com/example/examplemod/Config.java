package com.example.examplemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight JSON-backed configuration that mirrors the small slice of the old
 * NeoForge {@code ModConfigSpec} API used by this mod ({@code .get()}, {@code .set()},
 * {@code SPEC.save()}). Values persist to {@code config/examplemod.json}.
 */
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<ConfigValue<?>> ALL = new ArrayList<>();
    private static boolean loaded = false;

    public static final BooleanValue LOG_DIRT_BLOCK;
    public static final IntValue MAGIC_NUMBER;
    public static final StringValue MAGIC_NUMBER_INTRODUCTION;
    public static final StringListValue ITEM_STRINGS;

    public static final BooleanValue AUTO_OPEN_WHEEL;
    public static final BooleanValue AUTO_START;
    public static final IntValue OBJECTIVE_COUNT;
    /** Default multiplayer mode: COOPERATIVE or CHALLENGE. Applied when a run starts. */
    public static final StringValue GAME_MODE;

    public static final BooleanValue ENABLE_ITEMS;
    public static final BooleanValue ENABLE_BLOCKS;
    public static final BooleanValue ENABLE_ADVANCEMENTS;
    public static final StringValue POOL_FILTER;
    public static final StringListValue BLACKLIST;
    public static final StringValue FORCED_LANGUAGE;

    // HUD Configs
    public static final DoubleValue HUD_TIMER_SCALE;
    public static final DoubleValue HUD_ITEM_SCALE;
    public static final DoubleValue HUD_TEXT_SCALE;
    public static final StringValue HUD_TEXT_COLOR;
    public static final StringValue HUD_TIMER_COLOR;
    public static final StringValue HUD_POSITION;
    public static final DoubleValue HUD_BG_OPACITY;
    public static final BooleanValue HUD_SHOW_BACKGROUND;
    public static final BooleanValue HUD_SHOW_BORDER;
    public static final BooleanValue HUD_SHOW_STATS;
    public static final BooleanValue HUD_SHOW_OBJECTIVES;
    public static final StringValue HUD_STATS_COLOR;
    public static final StringValue HUD_COMPLETED_COLOR;
    public static final IntValue HUD_OFFSET_X;
    public static final IntValue HUD_OFFSET_Y;

    static {
        LOG_DIRT_BLOCK = new BooleanValue("logDirtBlock", true);
        MAGIC_NUMBER = new IntValue("magicNumber", 42);
        MAGIC_NUMBER_INTRODUCTION = new StringValue("magicNumberIntroduction", "The magic number is... ");
        ITEM_STRINGS = new StringListValue("items", List.of("minecraft:iron_ingot"));

        AUTO_OPEN_WHEEL = new BooleanValue("autoOpenWheel", true);
        AUTO_START = new BooleanValue("autoStart", true);
        OBJECTIVE_COUNT = new IntValue("objectiveCount", 1);
        GAME_MODE = new StringValue("gameMode", "COOPERATIVE");

        ENABLE_ITEMS = new BooleanValue("enableItems", true);
        ENABLE_BLOCKS = new BooleanValue("enableBlocks", true);
        ENABLE_ADVANCEMENTS = new BooleanValue("enableAdvancements", true);
        POOL_FILTER = new StringValue("poolFilter", "");
        BLACKLIST = new StringListValue("blacklist", List.of());
        FORCED_LANGUAGE = new StringValue("forcedLanguage", "");

        HUD_TIMER_SCALE = new DoubleValue("hudTimerScale", 1.25);
        HUD_ITEM_SCALE = new DoubleValue("hudItemScale", 1.5);
        HUD_TEXT_SCALE = new DoubleValue("hudTextScale", 1.0);
        HUD_TEXT_COLOR = new StringValue("hudTextColor", "#FFFFFFFF");
        HUD_TIMER_COLOR = new StringValue("hudTimerColor", "");
        HUD_POSITION = new StringValue("hudPosition", "top_right");
        HUD_BG_OPACITY = new DoubleValue("hudBgOpacity", 0.88);
        HUD_SHOW_BACKGROUND = new BooleanValue("hudShowBackground", true);
        HUD_SHOW_BORDER = new BooleanValue("hudShowBorder", true);
        HUD_SHOW_STATS = new BooleanValue("hudShowStats", true);
        HUD_SHOW_OBJECTIVES = new BooleanValue("hudShowObjectives", true);
        HUD_STATS_COLOR = new StringValue("hudStatsColor", "#FFFFDDDD");
        HUD_COMPLETED_COLOR = new StringValue("hudCompletedColor", "#FF55FF55");
        HUD_OFFSET_X = new IntValue("hudOffsetX", 0);
        HUD_OFFSET_Y = new IntValue("hudOffsetY", 0);
    }

    public static final Spec SPEC = new Spec();

    public static final class Spec {
        public void save() {
            Config.save();
        }
    }

    public static SpeedrunGameMode getGameMode() {
        return SpeedrunGameMode.fromString(GAME_MODE.get());
    }

    public static void setGameMode(SpeedrunGameMode mode) {
        GAME_MODE.set(mode.name());
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("examplemod.json");
    }

    /** Loads config from disk (if present) and marks the config as ready. */
    public static void load() {
        try {
            Path path = configPath();
            if (Files.exists(path)) {
                JsonObject obj = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                for (ConfigValue<?> value : ALL) {
                    JsonElement el = obj.get(value.key);
                    if (el != null) {
                        value.fromJson(el);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to load config, using defaults", t);
        }
        loaded = true;
        save();
    }

    public static void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            JsonObject obj = new JsonObject();
            for (ConfigValue<?> value : ALL) {
                obj.add(value.key, value.toJson());
            }
            Files.writeString(path, GSON.toJson(obj));
        } catch (Throwable t) {
            LOGGER.error("Failed to save config", t);
        }
    }

    // --- Value types ---

    public abstract static class ConfigValue<T> {
        protected final String key;
        protected T value;
        protected final T defaultValue;

        ConfigValue(String key, T defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            ALL.add(this);
        }

        public T get() {
            return value;
        }

        public void set(T newValue) {
            this.value = newValue;
            if (loaded) {
                save();
            }
        }

        abstract JsonElement toJson();

        abstract void fromJson(JsonElement el);
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(String key, boolean defaultValue) {
            super(key, defaultValue);
        }

        public void set(boolean newValue) {
            set(Boolean.valueOf(newValue));
        }

        @Override
        JsonElement toJson() {
            return new JsonPrimitive(value);
        }

        @Override
        void fromJson(JsonElement el) {
            if (el != null && el.isJsonPrimitive()) {
                value = el.getAsBoolean();
            }
        }
    }

    public static final class IntValue extends ConfigValue<Integer> {
        IntValue(String key, int defaultValue) {
            super(key, defaultValue);
        }

        public void set(int newValue) {
            set(Integer.valueOf(newValue));
        }

        @Override
        JsonElement toJson() {
            return new JsonPrimitive(value);
        }

        @Override
        void fromJson(JsonElement el) {
            if (el != null && el.isJsonPrimitive()) {
                value = el.getAsInt();
            }
        }
    }

    public static final class DoubleValue extends ConfigValue<Double> {
        DoubleValue(String key, double defaultValue) {
            super(key, defaultValue);
        }

        public void set(double newValue) {
            set(Double.valueOf(newValue));
        }

        @Override
        JsonElement toJson() {
            return new JsonPrimitive(value);
        }

        @Override
        void fromJson(JsonElement el) {
            if (el != null && el.isJsonPrimitive()) {
                value = el.getAsDouble();
            }
        }
    }

    public static final class StringValue extends ConfigValue<String> {
        StringValue(String key, String defaultValue) {
            super(key, defaultValue);
        }

        @Override
        JsonElement toJson() {
            return new JsonPrimitive(value);
        }

        @Override
        void fromJson(JsonElement el) {
            if (el != null && el.isJsonPrimitive()) {
                value = el.getAsString();
            }
        }
    }

    public static final class StringListValue extends ConfigValue<List<? extends String>> {
        StringListValue(String key, List<? extends String> defaultValue) {
            super(key, defaultValue);
        }

        @Override
        JsonElement toJson() {
            JsonArray array = new JsonArray();
            for (String s : value) {
                array.add(s);
            }
            return array;
        }

        @Override
        void fromJson(JsonElement el) {
            if (el != null && el.isJsonArray()) {
                List<String> list = new ArrayList<>();
                for (JsonElement e : el.getAsJsonArray()) {
                    list.add(e.getAsString());
                }
                value = list;
            }
        }
    }
}
