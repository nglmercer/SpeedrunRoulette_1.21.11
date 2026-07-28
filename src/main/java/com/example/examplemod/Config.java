package com.example.examplemod;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK;
    public static final ModConfigSpec.IntValue MAGIC_NUMBER;
    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS;
    
    public static final ModConfigSpec.BooleanValue AUTO_OPEN_WHEEL;
    public static final ModConfigSpec.BooleanValue AUTO_START;
    public static final ModConfigSpec.IntValue OBJECTIVE_COUNT;
    
    public static final ModConfigSpec.BooleanValue ENABLE_ITEMS;
    public static final ModConfigSpec.BooleanValue ENABLE_BLOCKS;
    public static final ModConfigSpec.BooleanValue ENABLE_ADVANCEMENTS;
    public static final ModConfigSpec.ConfigValue<String> POOL_FILTER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    
    // HUD Configs
    public static final ModConfigSpec.DoubleValue HUD_TIMER_SCALE;
    public static final ModConfigSpec.DoubleValue HUD_ITEM_SCALE;
    public static final ModConfigSpec.DoubleValue HUD_TEXT_SCALE;
    public static final ModConfigSpec.ConfigValue<String> HUD_TEXT_COLOR;
    public static final ModConfigSpec.ConfigValue<String> HUD_TIMER_COLOR;

    static {
        BUILDER.push("Configs for Speedrun Roulette");

        LOG_DIRT_BLOCK = BUILDER.comment("Whether to log the dirt block on common setup")
                .define("logDirtBlock", true);
        
        // ... existing ...

        MAGIC_NUMBER = BUILDER.comment("A magic number")
                .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

        MAGIC_NUMBER_INTRODUCTION = BUILDER.comment("What you want the introduction message to be for the magic number")
                .define("magicNumberIntroduction", "The magic number is... ");

        ITEM_STRINGS = BUILDER.comment("A list of items to log on common setup")
                .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);
        
        AUTO_OPEN_WHEEL = BUILDER.comment("Automatically open wheel on new world")
                .define("autoOpenWheel", true);

        AUTO_START = BUILDER.comment("Automatically start timer when objectives are set")
                .define("autoStart", true);

        OBJECTIVE_COUNT = BUILDER.comment("Number of objectives (1-10)")
                .defineInRange("objectiveCount", 1, 1, 10);
                
        ENABLE_ITEMS = BUILDER.comment("Include Items in the objective pool")
                .define("enableItems", true);

        ENABLE_BLOCKS = BUILDER.comment("Include Blocks in the objective pool")
                .define("enableBlocks", true);

        ENABLE_ADVANCEMENTS = BUILDER.comment("Include Advancements in the objective pool")
                .define("enableAdvancements", true);

        POOL_FILTER = BUILDER.comment("Filter string for objectives (contains)")
                .define("poolFilter", "");

        BLACKLIST = BUILDER.comment("List of disabled objective IDs")
                .defineListAllowEmpty("blacklist", List.of(), s -> s instanceof String);
                
        BUILDER.push("HUD Configuration");
        
        HUD_TIMER_SCALE = BUILDER.comment("Scale of the timer text")
                .defineInRange("hudTimerScale", 1.25, 0.5, 5.0);
        
        HUD_ITEM_SCALE = BUILDER.comment("Scale of the objective items")
                .defineInRange("hudItemScale", 1.5, 0.5, 5.0);
                
        HUD_TEXT_SCALE = BUILDER.comment("Scale of the text (objectives/stats)")
                .defineInRange("hudTextScale", 1.0, 0.5, 5.0);
                
        HUD_TEXT_COLOR = BUILDER.comment("Hex color for text (Format: #AARRGGBB or #RRGGBB)")
                .define("hudTextColor", "#FFFFFFFF");
                
        HUD_TIMER_COLOR = BUILDER.comment("Hex color for timer (Format: #AARRGGBB or #RRGGBB). Leave empty to use dynamic colors.")
                .define("hudTimerColor", "");
                
        BUILDER.pop();

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && itemName.length() > 0;
    }
}
