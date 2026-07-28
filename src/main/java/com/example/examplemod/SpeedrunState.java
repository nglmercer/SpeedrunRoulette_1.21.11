package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.stats.Stats;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import java.io.File;

public class SpeedrunState {
    private static List<Objective> objectives = Collections.emptyList();
    private static boolean timerRunning = false;
    private static long startTime = 0;
    private static long elapsedNanos = 0;
    private static long finalElapsedNanos = 0;
    private static boolean objectivesCompleted = false;
    private static int deathCount = 0;
    private static double traveledMeters = 0;
    private static long daysPlayed = 0;
    
    // HUD: 0=COMPLET, 1=MINIMAL, 2=MASQUÉ
    private static int hudState = 0;
    private static boolean manualPaused = false;
    private static boolean systemPaused = false;
    private static long pauseStartTime = 0;
    private static long totalPauseNanos = 0;
    
    // Manual Tracking Fields
    private static net.minecraft.world.phys.Vec3 lastPos = null;
    private static boolean isStatsInitialized = false;
    private static boolean wasDead = false;

    public static boolean autoTriggerCreateWorld = false;
    public static boolean keepObjectivesForNextRun = false;

    public static void prepareForRetry() {
        // keepObjectivesForNextRun = true;
        // autoTriggerCreateWorld = true;
        resetTimer();
    }

    public static void prepareForNewGame() {
        keepObjectivesForNextRun = false;
        // autoTriggerCreateWorld = true;
        resetTimer();
    }

    // Splits
    private static final java.util.Map<String, String> splits = new java.util.LinkedHashMap<>();
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastDimension = net.minecraft.world.level.Level.OVERWORLD;

    public static void resetTimer() {
        timerRunning = false;
        startTime = 0;
        elapsedNanos = 0;
        totalPauseNanos = 0;
        manualPaused = false;
        systemPaused = false;
        objectivesCompleted = false;
        
        // Reset manual tracking
        lastPos = null;
        isStatsInitialized = false;
        wasDead = false;
        deathCount = 0;
        traveledMeters = 0;
        daysPlayed = 0;
        
        // Reset Splits
        splits.clear();
        lastDimension = net.minecraft.world.level.Level.OVERWORLD;
    }
    
    public static java.util.Map<String, String> getSplits() {
        return splits;
    }
    
    private static void recordSplit(String name) {
        if (!splits.containsKey(name)) {
            splits.put(name, currentFormattedTime());
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
        }
    }

    private static boolean objectivesFresh = false;

    public static void setObjectives(List<Objective> objs, boolean save) {
        objectives = objs;
        objectivesFresh = true;
        objectivesCompleted = false;
        if (Config.AUTO_START.get() && !objs.isEmpty()) {
            startTimer();
        }
        if (save) {
            saveObjectivesToWorld();
        }
    }
    
    // Overload for backward compatibility if needed, or just update callers
    public static void setObjectives(List<Objective> objs) {
        setObjectives(objs, true);
    }
    
    public static void saveObjectivesToWorld() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            SpeedrunWorldData data = SpeedrunWorldData.get(server);
            data.setObjectives(objectives);
        } else if (mc.player != null) {
            SpeedrunNetwork.sendToServer(new SpeedrunNetwork.SaveObjectivesPacket(new ArrayList<>(objectives)));
        }
    }

    public static List<Objective> getObjectives() {
        return objectives;
    }

    public static void clearObjectives() {
        objectives = Collections.emptyList();
        objectivesCompleted = false;
    }

    public static void startTimer() {
        if (!timerRunning) {
            timerRunning = true;
            startTime = System.nanoTime();
            totalPauseNanos = 0;
            deathCount = 0;
            traveledMeters = 0;
            objectivesCompleted = false;
        }
    }

    public static void stopTimer() {
        if (timerRunning) {
            timerRunning = false;
            elapsedNanos = System.nanoTime() - startTime - totalPauseNanos;
        }
    }

    public static void toggleHud() {
        hudState = (hudState + 1) % 3;
    }
    
    public static void toggleManualPause() {
        if (!timerRunning && !objectivesCompleted) return;
        
        manualPaused = !manualPaused;
        if (manualPaused) {
            // Only set start time if not already system paused
            if (!systemPaused) {
                pauseStartTime = System.nanoTime();
            }
        } else {
            // Only add time if not system paused (otherwise system pause handles it)
            if (!systemPaused) {
                totalPauseNanos += (System.nanoTime() - pauseStartTime);
            }
        }
    }

    public static void onSystemPause(boolean paused) {
        if (!timerRunning || manualPaused) return; // Manual pause takes precedence or timer not running

        if (paused) {
            if (!systemPaused) {
                systemPaused = true;
                pauseStartTime = System.nanoTime();
            }
        } else {
            if (systemPaused) {
                systemPaused = false;
                totalPauseNanos += (System.nanoTime() - pauseStartTime);
            }
        }
    }

    public static void openWheelNow() {
        Minecraft.getInstance().setScreen(new WheelScreen());
    }

    public static void openWheelOrReminder() {
        if (hasActiveObjectives()) {
            Minecraft.getInstance().setScreen(new ReminderScreen());
        } else {
            openWheelNow();
        }
    }
    
    public static boolean hasActiveObjectives() {
        return !objectives.isEmpty();
    }
    
    public static boolean isCompleted() {
        return objectivesCompleted;
    }
    
    // Getters for Stats
    public static int getDeathCount() { return deathCount; }
    public static double getTraveledMeters() { return traveledMeters; }
    public static long getDaysPlayed() { return daysPlayed; }

    public static void markObjectivesStale() {
        objectivesFresh = false;
    }

    public static void checkAutoOpen() {
        if (!Config.AUTO_OPEN_WHEEL.get()) return;

        boolean hasObjs = !objectives.isEmpty();
        
        // If we want new run (keep=false) and objectives are stale (!fresh), clear them.
        if (!keepObjectivesForNextRun && hasObjs && !objectivesFresh) {
             clearObjectives();
             hasObjs = false;
        }

        if (!hasObjs) {
             openWheelNow();
        } else {
             // We have objectives (kept from retry or loaded from world).
             if (!timerRunning) startTimer(); 
             Minecraft.getInstance().player.displayClientMessage(Component.translatable("gui.examplemod.objectives_active"), true);
        }
    }

    public static void saveRunInfo(boolean isVictory) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();

        String time = SpeedrunRoulette.pendingVictoryTime != null ? SpeedrunRoulette.pendingVictoryTime : currentFormattedTime();

        String objectiveName;
        List<Objective> objs = getObjectives();
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
            String levelId = getLevelId(server);
            if (levelId == null) return;

            File savesDir = mc.gameDirectory.toPath().resolve("saves").toFile();
            File levelDir = new File(savesDir, levelId);
            File infoFile = new File(levelDir, "speedrun_info.nbt");

            try {
                CompoundTag tag = new CompoundTag();
                tag.putBoolean("isVictory", isVictory);
                tag.putString("time", time);
                tag.putLong("timestamp", System.currentTimeMillis());
                tag.putString("objectiveName", objectiveName);

                NbtIo.writeCompressed(tag, infoFile.toPath());
                SpeedrunRoulette.LOGGER.info("Saved run info to " + infoFile.getAbsolutePath());
            } catch (Exception e) {
                SpeedrunRoulette.LOGGER.error("Failed to save run info", e);
            }
        } else if (mc.player != null) {
            SpeedrunNetwork.sendToServer(new SpeedrunNetwork.SaveRunInfoPacket(isVictory, time, objectiveName));
        }
    }

    public static String getLevelId(net.minecraft.server.MinecraftServer server) {
        try {
            // Try standard mapping name 'storageSource'
            java.lang.reflect.Field f = net.minecraft.server.MinecraftServer.class.getDeclaredField("storageSource");
            f.setAccessible(true);
            Object storage = f.get(server);
            java.lang.reflect.Method m = storage.getClass().getMethod("getLevelId");
            return (String) m.invoke(storage);
        } catch (Exception e) {
            // Try obfuscated fallback or just return null
            SpeedrunRoulette.LOGGER.error("Failed to get level ID via reflection", e);
            return null;
        }
    }



    // Run Info Cache
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
    
    private static final java.util.Map<String, RunInfo> runInfoCache = new java.util.HashMap<>();
    
    public static RunInfo getRunInfo(String levelId) {
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

    public static void showRunInfo(net.minecraft.client.gui.screens.Screen parent, String levelId) {
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
                
                Component titleComp = isVictory
                    ? Component.translatable("gui.examplemod.victory_indicator")
                    : Component.translatable("gui.examplemod.defeat_indicator");
                int color = isVictory ? 0xFF55FF55 : 0xFFFF5555;

                Component msg = titleComp.withStyle(style -> style.withColor(color).withBold(true))
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
                Component.literal("Infos de la Run"),
                Component.literal("Aucune information enregistrée pour ce monde."),
                Component.literal("Fermer"),
                Component.literal("")
            ));
        }
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        
        // Safety: If we are in the process of giving up or restarting, do NOT run tick logic
        if (SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingReplay || SpeedrunRoulette.pendingNewRun) {
            return;
        }

        // Safety: Ensure timer is not running if WheelScreen is open
        if (mc.screen instanceof WheelScreen && timerRunning) {
            resetTimer();
        }

        // --- NEW SAFETY CHECK ---
        // If the game is paused (saving/disconnecting), DO NOT access server level or player data heavily
        // 'mc.isPaused()' is for in-game pause menu.
        // We need to check if connection is active and world is loaded.
        if (mc.level == null || mc.player == null) return;
        
        // Also check if we are in the process of disconnecting/saving
        // ReceivingLevelScreen in 1.21 is ReceivingLevelScreen.
        // But maybe it's not imported or name changed?
        // Let's use string check for safety or just check LevelLoadingScreen
        if (mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
            return;
        }
        // Also check "Saving Level" screen which is GenericMessageScreen usually.
        if (mc.screen instanceof net.minecraft.client.gui.screens.GenericMessageScreen) {
            // Usually this is the "Saving Level" screen
             return;
        }

        if (mc.player != null) {
             // Manual Stats Tracking (Client Side)
             if (!isStatsInitialized || lastPos == null) {
                 lastPos = mc.player.position();
                 isStatsInitialized = true;
                 wasDead = mc.player.isDeadOrDying();
             }
             
             // Distance
             net.minecraft.world.phys.Vec3 currentPos = mc.player.position();
             double dist = currentPos.distanceTo(lastPos);
             // Ignore massive jumps (teleports) if > 10 blocks/tick? 
             // 10 blocks/tick = 200m/s. Elytra can do ~30-60m/s. 
             // Let's set a loose cap or just count it. 
             // User wants "Meters Traveled". If I tp, I traveled.
             traveledMeters += dist;
             lastPos = currentPos;
             
             // Deaths
             boolean isDead = mc.player.isDeadOrDying();
             if (isDead && !wasDead) {
                 deathCount++;
             }
             wasDead = isDead;
             
             // Days
             daysPlayed = mc.player.level().getDayTime() / 24000L;
             
             // Splits Logic
             if (timerRunning && !manualPaused && !mc.isPaused()) {
                 // 1. Dimensions
                 net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> currentDim = mc.player.level().dimension();
                 if (currentDim != lastDimension) {
                     if (currentDim == net.minecraft.world.level.Level.NETHER) {
                         recordSplit(Component.translatable("gui.examplemod.split.nether_entry").getString());
                     } else if (currentDim == net.minecraft.world.level.Level.END) {
                         recordSplit(Component.translatable("gui.examplemod.split.end_entry").getString());
                     }
                     lastDimension = currentDim;
                 }
                 
                 // 2. Village Found (Heuristic: Villager Nearby)
                 // Check every 20 ticks
                 if (mc.player.tickCount % 20 == 0 && !splits.containsKey(Component.translatable("gui.examplemod.split.village_found").getString())) {
                     net.minecraft.world.phys.AABB box = mc.player.getBoundingBox().inflate(32); // 32 blocks radius
                     // Try with entity.npc.Villager, but if that fails, try checking entity registry or just entity type
                     // Actually Villager is usually in net.minecraft.world.entity.npc
                     // But maybe we need to import it or use full path. We are using full path.
                     // Wait, maybe in 1.21 it moved? Or mappings?
                     // Let's try searching by EntityType.VILLAGER
                     
                     java.util.List<net.minecraft.world.entity.Entity> entities = mc.player.level().getEntities(mc.player, box, e -> e.getType() == net.minecraft.world.entity.EntityType.VILLAGER);
                     
                     if (!entities.isEmpty()) {
                         recordSplit(Component.translatable("gui.examplemod.split.village_found").getString());
                     }
                 }

                 // 3. Structures via Advancements
                 // Check every 20 ticks
                 if (mc.player.tickCount % 20 == 0 && mc.player.connection != null) {
                     net.minecraft.client.multiplayer.ClientAdvancements advancements = mc.player.connection.getAdvancements();
                     if (advancements != null) {
                         // Fortress: minecraft:nether/find_fortress
                         // Bastion: minecraft:nether/find_bastion
                         // Using hardcoded IDs or better logic? Hardcoded is standard for speedrun tools.
                         
                         checkAdvancement(advancements, "minecraft:nether/find_fortress", Component.translatable("gui.examplemod.split.fortress_found").getString());
                         checkAdvancement(advancements, "minecraft:nether/find_bastion", Component.translatable("gui.examplemod.split.bastion_found").getString());
                         checkAdvancement(advancements, "minecraft:story/follow_ender_eye", Component.translatable("gui.examplemod.split.stronghold_found").getString());
                     }
                 }
                 
                 // 4. Structures via Server Level (Singleplayer Only - More reliable)
                 if (mc.player.tickCount % 20 == 0) {
                     net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
                     // STRICT CHECK: Server must be running and NOT shutting down
                     if (server != null && server.isRunning() && !server.isStopped()) {
                         try {
                             net.minecraft.server.level.ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                             if (serverPlayer != null) {
                                 net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) serverPlayer.level();
                                 net.minecraft.core.BlockPos pos = serverPlayer.blockPosition();
                                 
                                 // Check if chunk is loaded to prevent forcing load during shutdown
                                 if (level.hasChunkAt(pos)) {
                                      checkStructure(level, pos, BuiltinStructures.END_CITY, Component.translatable("gui.examplemod.split.end_city").getString());
                                      checkStructure(level, pos, BuiltinStructures.ANCIENT_CITY, Component.translatable("gui.examplemod.split.ancient_city").getString());
                                      checkStructure(level, pos, BuiltinStructures.SHIPWRECK, Component.translatable("gui.examplemod.split.shipwreck").getString());
                                      checkStructure(level, pos, BuiltinStructures.OCEAN_MONUMENT, Component.translatable("gui.examplemod.split.ocean_monument").getString());
                                      checkStructure(level, pos, BuiltinStructures.PILLAGER_OUTPOST, Component.translatable("gui.examplemod.split.pillager_outpost").getString());
                                      checkStructure(level, pos, BuiltinStructures.BURIED_TREASURE, Component.translatable("gui.examplemod.split.buried_treasure").getString());
                                      checkStructure(level, pos, BuiltinStructures.DESERT_PYRAMID, Component.translatable("gui.examplemod.split.desert_pyramid").getString());
                                      checkStructure(level, pos, BuiltinStructures.FORTRESS, Component.translatable("gui.examplemod.split.fortress_found").getString());
                                      checkStructure(level, pos, BuiltinStructures.BASTION_REMNANT, Component.translatable("gui.examplemod.split.bastion_found").getString());
                                      checkStructure(level, pos, BuiltinStructures.STRONGHOLD, Component.translatable("gui.examplemod.split.stronghold_found").getString());
                                      checkStructure(level, pos, BuiltinStructures.IGLOO, Component.translatable("gui.examplemod.split.igloo").getString());
                                      checkStructure(level, pos, BuiltinStructures.JUNGLE_TEMPLE, Component.translatable("gui.examplemod.split.jungle_temple").getString());
                                      checkStructure(level, pos, BuiltinStructures.SWAMP_HUT, Component.translatable("gui.examplemod.split.swamp_hut").getString());
                                      checkStructure(level, pos, BuiltinStructures.WOODLAND_MANSION, Component.translatable("gui.examplemod.split.woodland_mansion").getString());
                                      checkStructure(level, pos, BuiltinStructures.NETHER_FOSSIL, Component.translatable("gui.examplemod.split.nether_fossil").getString());
                                      
                                      // 1.21 Trial Chambers
                                      try {
                                          checkStructure(level, pos, BuiltinStructures.TRIAL_CHAMBERS, Component.translatable("gui.examplemod.split.trial_chamber").getString());
                                      } catch (NoSuchFieldError | NoClassDefFoundError e) {
                                          // Ignore
                                      }
                                 }
                             }
                         } catch (Exception e) {
                             // Ignore errors during server access (e.g. shutdown)
                         }
                      }
                  }
              }
             
             if (timerRunning && !manualPaused && !mc.isPaused()) {
                 if (areObjectivesComplete(mc.player)) {
                     objectivesCompleted = true;
                     finalElapsedNanos = computeEffectiveNanos();
                     stopTimer();
                     
                     // Handle Victory
                     SpeedrunRoulette.pendingVictoryTime = getFormattedTimeFromNanos(finalElapsedNanos);
                     
                     if (objectives != null && !objectives.isEmpty()) {
                         if (objectives.size() > 1) {
                            SpeedrunRoulette.pendingVictoryObjectiveName = Component.translatable("gui.examplemod.item_list", objectives.size()).getString();
                         } else {
                            SpeedrunRoulette.pendingVictoryObjectiveName = objectives.get(0).getDisplayName().getString();
                         }
                     }
                     
                     mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
                     mc.setScreen(new VictoryScreen());
                 }
             }
        }
        
        // Auto create world logic
        if (autoTriggerCreateWorld && mc.screen instanceof CreateWorldScreen screen) {
            // Find "Create New World" button
            // In 1.20/1.21, buttons are in children.
            // We can iterate children or look for specific button logic.
            // Since we can't easily find by text without iterating, we'll try to trigger the action directly if possible.
            // Or access the button via reflection/access transformer.
            
            // Simpler approach: CreateWorldScreen has a 'onCreate()' method or similar.
            // But 'screen.onCreate()' is protected/private usually.
            
            // Let's try to find a Button with message "Create New World" (or translation key)
            
            for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
                if (child instanceof Button btn) {
                    if (btn.getMessage().equals(Component.translatable("selectWorld.create"))) {
                         if (btn.active) {
                             autoTriggerCreateWorld = false; // Set false BEFORE action to prevent recursion
                             try {
                                 // Use reflection to call onPress to avoid signature issues
                                 java.lang.reflect.Method onPressMethod = net.minecraft.client.gui.components.Button.class.getMethod("onPress");
                                 onPressMethod.invoke(btn);
                             } catch (Throwable t) {
                                 try {
                                      // Try field access if method fails
                                      java.lang.reflect.Field f = net.minecraft.client.gui.components.Button.class.getDeclaredField("onPress");
                                      f.setAccessible(true);
                                      net.minecraft.client.gui.components.Button.OnPress onPress = (net.minecraft.client.gui.components.Button.OnPress) f.get(btn);
                                      onPress.onPress(btn);
                                 } catch (Throwable t2) {}
                             }
                         }
                         break;
                    }
                }
            }
        }
    }
    
    private static boolean areObjectivesComplete(Player player) {
        if (objectives.isEmpty()) return false;
        for (Objective obj : objectives) {
            if (!obj.isCompleted(player)) return false;
        }
        return true;
    }
    
    private static long computeEffectiveNanos() {
        if (!timerRunning) return elapsedNanos;
        if (manualPaused || systemPaused) return pauseStartTime - startTime - totalPauseNanos;
        return System.nanoTime() - startTime - totalPauseNanos;
    }
    
    public static String getFormattedTimeFromNanos(long nanos) {
        long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
        long h = TimeUnit.MILLISECONDS.toHours(millis);
        long m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long ms = millis % 1000;
        if (h > 0) return String.format("%02d:%02d:%02d.%03d", h, m, s, ms);
        return String.format("%02d:%02d.%03d", m, s, ms);
    }
    
    public static String currentFormattedTime() {
        return getFormattedTimeFromNanos(computeEffectiveNanos());
    }

    public static void onRenderHud(GuiGraphics g) {
        if (hudState == 2) return;
        if (!timerRunning && objectives.isEmpty()) return;
        
        boolean showObjectives = (hudState == 0) && !objectives.isEmpty();
        boolean showStats = (hudState == 0); // Only show stats in Full mode
        
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        int width = g.guiWidth();
        int margin = 5;
        
        String timeStr = currentFormattedTime();
        if (manualPaused || systemPaused) {
             timeStr += " (PAUSE)";
        }
        
        String statsStr = "Morts: " + deathCount + " | Distance: " + String.format("%.0fm", traveledMeters) + " | Jours: " + daysPlayed;
        
        renderObjectivesAndStats(g, font, width, margin, showObjectives, showStats, timeStr, statsStr, objectives, timerRunning, manualPaused || systemPaused);
    }
    
    // New public method for Preview
    public static void renderPreviewHud(GuiGraphics g, int width, int margin) {
        try {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            if (font == null) return; // Safety check
            
            // Dummy Data
            String timeStr = "00:42.000";
            String statsStr = "Morts: 5 | Distance: 1234m | Jours: 2";
            
            // Create dummy objective
            List<Objective> dummyObjectives = new java.util.ArrayList<>();
            
            // Use Items.IRON_INGOT and Items.DIRT safely
            try {
                // In 1.21, Identifier might be ResourceLocation. Use tryParse.
                // And registry access might return Optional or Holder.
                
                // Let's go back to simplest: Use Items class but wrapped in try-catch for class loading issues
                // If direct access failed before, maybe it was just a fluke or specific load order?
                // But let's try ResourceLocation/Identifier properly.
                
                net.minecraft.resources.Identifier ironId = net.minecraft.resources.Identifier.tryParse("minecraft:iron_ingot");
                net.minecraft.resources.Identifier dirtId = net.minecraft.resources.Identifier.tryParse("minecraft:dirt");
                net.minecraft.resources.Identifier emeraldId = net.minecraft.resources.Identifier.tryParse("minecraft:emerald");
                
                net.minecraft.world.item.Item iron = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ironId).map(holder -> holder.value()).orElse(null);
                net.minecraft.world.item.Item dirt = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(dirtId).map(holder -> holder.value()).orElse(null);
                net.minecraft.world.item.Item emerald = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(emeraldId).map(holder -> holder.value()).orElse(null);
                
                // If registry lookup fails (shouldn't if game is running), try static fields as fallback
                if (iron == net.minecraft.world.item.Items.AIR) iron = net.minecraft.world.item.Items.IRON_INGOT;
                if (dirt == net.minecraft.world.item.Items.AIR) dirt = net.minecraft.world.item.Items.DIRT;
                if (emerald == net.minecraft.world.item.Items.AIR) emerald = net.minecraft.world.item.Items.EMERALD;

                if (iron != null && iron != net.minecraft.world.item.Items.AIR) {
                    dummyObjectives.add(new Objective("preview_item", Component.literal("Iron Ingot"), new net.minecraft.world.item.ItemStack(iron), Objective.Type.ITEM));
                }
                if (dirt != null && dirt != net.minecraft.world.item.Items.AIR) {
                    dummyObjectives.add(new Objective("preview_block", Component.literal("Dirt Block"), new net.minecraft.world.item.ItemStack(dirt), Objective.Type.BLOCK));
                }
                if (emerald != null && emerald != net.minecraft.world.item.Items.AIR) {
                    dummyObjectives.add(new Objective("preview_gem", Component.literal("Emerald"), new net.minecraft.world.item.ItemStack(emerald), Objective.Type.ITEM));
                }
            } catch (Throwable t) {
                System.err.println("Preview Items Error: " + t.getMessage());
                // Fallback if Items access fails
                dummyObjectives.add(new Objective("preview_error", Component.literal("Error Loading Items"), net.minecraft.world.item.ItemStack.EMPTY, Objective.Type.ITEM));
            }
            
            // If dummy objectives empty, add fallback
            if (dummyObjectives.isEmpty()) {
                 dummyObjectives.add(new Objective("preview_fallback", Component.literal("Test Item"), net.minecraft.world.item.ItemStack.EMPTY, Objective.Type.ITEM));
            }
            
            renderObjectivesAndStats(g, font, width, margin, true, true, timeStr, statsStr, dummyObjectives, true, false);
        } catch (Exception e) {
            // Log error or ignore to prevent crash
            System.err.println("Error rendering HUD preview: " + e.getMessage());
        }
    }

    private static int parseColor(String colorStr, int defaultColor) {
        if (colorStr == null || colorStr.isEmpty()) return defaultColor;
        try {
            if (colorStr.startsWith("#")) {
                return (int) Long.parseLong(colorStr.substring(1), 16);
            }
            return (int) Long.parseLong(colorStr, 16);
        } catch (NumberFormatException e) {
            return defaultColor;
        }
    }

    private static void renderObjectivesAndStats(GuiGraphics g, net.minecraft.client.gui.Font font, int width, int margin, boolean showObjectivesList, boolean showStats, 
                                                 String timeStr, String statsStr, List<Objective> renderObjectives, boolean isTimerRunning, boolean isPaused) {
        
        // Get Config Values
        float timerScaleMin = Config.HUD_TIMER_SCALE.get().floatValue(); // For minimal mode (user wanted bigger, let's say 1.6 * user scale / 1.25 base?)
        // Wait, user config sets the "scale". Minimal mode uses a larger scale than normal.
        // Let's assume the config value is the "Normal Mode Scale".
        // Minimal mode should probably be Normal * (1.6/1.25) ~ 1.28x bigger? Or just separate config?
        // Let's simplify: 
        // Normal Timer Scale = Config.HUD_TIMER_SCALE
        // Minimal Timer Scale = Config.HUD_TIMER_SCALE * 1.3
        
        float baseTimerScale = Config.HUD_TIMER_SCALE.get().floatValue();
        float itemScale = Config.HUD_ITEM_SCALE.get().floatValue();
        float textScale = Config.HUD_TEXT_SCALE.get().floatValue(); // Use this for objective text and stats?
        
        int textColor = parseColor(Config.HUD_TEXT_COLOR.get(), 0xFFFFFFFF);
        int customTimerColor = parseColor(Config.HUD_TIMER_COLOR.get(), -1); // -1 means use dynamic
        
        // --- Minimal Mode Handling (Timer Only) ---
        if (!showObjectivesList && !showStats) {
            float scale = baseTimerScale * 1.3f; // Boost for minimal mode
            int textWidth = font.width(timeStr);
            int textHeight = font.lineHeight;
            
            int boxWidth = (int)(textWidth * scale) + margin * 2 + 10;
            int boxHeight = (int)(textHeight * scale) + margin * 2 + 6; 
            
            int boxX = width - boxWidth - margin;
            int boxY = margin;
            
            // Render Background
            g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000);
            g.renderOutline(boxX - 1, boxY - 1, boxWidth + 2, boxHeight + 2, 0xFFFFFFFF);
            
            // Render Timer Scaled & Centered
            int timerColor = (customTimerColor != -1) ? customTimerColor : 
                             (isPaused ? 0xFFFFFF55 : (isTimerRunning ? 0xFF55FF55 : 0xFFFFFFFF));
            
            // Define center variables
            float centerX = boxX + boxWidth / 2.0f;
            float centerY = boxY + boxHeight / 2.0f;
            
            // Scale UP
            g.pose().translate(centerX, centerY);
            g.pose().scale(scale, scale);
            
            // Draw centered at (0,0) - half text height (approx 4.5 for default font)
            g.drawCenteredString(font, timeStr, 0, -4, timerColor);
            
            // Scale DOWN (Reverse)
            g.pose().scale(1/scale, 1/scale);
            g.pose().translate(-centerX, -centerY);
            
            return; 
        }

        // --- Standard Mode Handling ---
        // --- 1. Calculate Sizes ---
        
        int maxTextWidth = 140; // Base width
        // float itemScale = 1.5f; // From Config
        int itemSize = (int)(16 * itemScale); // 24
        int lineSpacing = (int)(itemSize * 1.2); // Dynamic spacing based on item size (e.g. 28 for 24)
        if (lineSpacing < 24) lineSpacing = 24;
        
        int timeWidth = (int)(font.width(timeStr) * baseTimerScale); 
        if (timeWidth > maxTextWidth) maxTextWidth = timeWidth;

        // Objectives Width
        boolean compactMode = renderObjectives.size() > 5;
        if (showObjectivesList) {
            for (Objective obj : renderObjectives) {
                int w = (int)(font.width(obj.getDisplayName()) * textScale) + (compactMode ? 0 : (itemSize + 4));
                if (w > maxTextWidth) maxTextWidth = w;
            }
        }
        
        // Stats Width
        // "Morts: 0 | Distance: 0m | Jours: 0"
        // String statsStr = "Morts: " + deathCount + " | Distance: " + String.format("%.0fm", traveledMeters) + " | Jours: " + daysPlayed;
        if (showStats) {
            if ((int)(font.width(statsStr) * textScale) > maxTextWidth) maxTextWidth = (int)(font.width(statsStr) * textScale);
        }
        
        int boxWidth = maxTextWidth + margin * 2;
        
        // Calculate Height
        int boxHeight = margin * 2; 
        
        // Header (Timer)
        int timerHeight = (int)(12 * baseTimerScale) + 6;
        boxHeight += timerHeight; 
        
        if (showObjectivesList) {
            boxHeight += 4; // Spacer
            boxHeight += (int)(12 * textScale); // Title
            
            if (compactMode) {
                boxHeight += renderObjectives.size() * (int)(12 * textScale);
            } else {
                boxHeight += renderObjectives.size() * lineSpacing;
            }
            boxHeight += 5; // Separator
        }
        
        // Stats
        if (showStats) {
            if (!showObjectivesList) {
                  boxHeight += 4;
            }
            boxHeight += (int)(12 * textScale); 
        }
        
        int boxX = width - boxWidth - margin;
        int boxY = margin;
        
        // --- 2. Render Background ---
        
        // Semi-transparent black background
        g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000);
        
        // White Border
        g.renderOutline(boxX - 1, boxY - 1, boxWidth + 2, boxHeight + 2, 0xFFFFFFFF);
        
        int currentY = boxY + margin;
        int textX = boxX + margin;
        
        // --- 3. Render Text Elements (Timer & Title) ---
        
        int timerColor = (customTimerColor != -1) ? customTimerColor : 
                         (isPaused ? 0xFFFFFF55 : (isTimerRunning ? 0xFF55FF55 : 0xFFFFFFFF));
        
        // Scale 1.25x (Configurable) for Normal HUD Timer
        float normalScale = baseTimerScale;
        
        // Calculate center for timer
        float tCenterX = boxX + boxWidth / 2.0f;
        float tCenterY = currentY + (timerHeight / 2.0f) - 2; 
        
        g.pose().translate(tCenterX, tCenterY);
        g.pose().scale(normalScale, normalScale);
        
        g.drawCenteredString(font, timeStr, 0, -4, timerColor);
        
        g.pose().scale(1/normalScale, 1/normalScale);
        g.pose().translate(-tCenterX, -tCenterY);
        
        currentY += timerHeight; // Move past the timer space
        
        // Separator (only if more content follows)
        if (showObjectivesList || showStats) {
             g.fill(boxX + margin, currentY, boxX + boxWidth - margin, currentY + 1, 0xFFAAAAAA);
             currentY += 4;
        }
        
        // --- 4. Render Objectives ---
        
        if (showObjectivesList) {
            // Title Scale
             g.pose().translate(textX, currentY);
             g.pose().scale(textScale, textScale);
             g.drawString(font, "Objectifs:", 0, 0, 0xFFAAAAAA, false);
             g.pose().scale(1/textScale, 1/textScale);
             g.pose().translate(-textX, -currentY);
             currentY += (int)(12 * textScale);
            
            for (Objective obj : renderObjectives) {
                Player player = Minecraft.getInstance().player;
                boolean completed = (player != null) && obj.isCompleted(player);
                int color = completed ? 0xFF55FF55 : textColor;
                Component name = obj.getDisplayName();
                
                if (compactMode) {
                    String prefix = completed ? "[v] " : "[ ] ";
                    g.pose().translate(textX, currentY);
                    g.pose().scale(textScale, textScale);
                    g.drawString(font, prefix + name.getString(), 0, 0, color, false);
                    g.pose().scale(1/textScale, 1/textScale);
                    g.pose().translate(-textX, -currentY);
                    currentY += (int)(12 * textScale);
                } else {
                    // Draw Text FIRST
                    // Center text vertically relative to itemSize
                    // itemSize e.g. 24. Font height 9 * textScale.
                    float textH = 9 * textScale;
                    float yOffset = (itemSize - textH) / 2.0f;
                    
                    g.pose().translate(textX, currentY + yOffset);
                    g.pose().scale(textScale, textScale);
                    g.drawString(font, name, 0, 0, color, false);
                    g.pose().scale(1/textScale, 1/textScale);
                    g.pose().translate(-textX, -(currentY + yOffset));
                    
                    // Draw Item (Scaled)
                    int itemBaseX = boxX + boxWidth - margin - itemSize; 
                    int itemBaseY = currentY;
                    
                    g.pose().translate(itemBaseX, itemBaseY);
                    g.pose().scale(itemScale, itemScale);
                    
                    g.renderItem(obj.getIcon(), 0, 0);
                    
                    g.pose().scale(1/itemScale, 1/itemScale);
                    g.pose().translate(-itemBaseX, -itemBaseY);
                    
                    currentY += lineSpacing;
                }
            }
            
            // Separator
            if (showStats) {
                g.fill(boxX + margin, currentY, boxX + boxWidth - margin, currentY + 1, 0xFF888888);
                currentY += 4;
            }
        }
        
        // --- 5. Render Stats ---
        if (showStats) {
            float sCenterX = boxX + boxWidth / 2.0f;
            float sCenterY = currentY + 4; // Padding
            
            g.pose().translate(sCenterX, sCenterY);
            g.pose().scale(textScale, textScale);
            // Draw centered
            g.drawCenteredString(font, statsStr, 0, 0, 0xFFFFDDDD);
            g.pose().scale(1/textScale, 1/textScale);
            g.pose().translate(-sCenterX, -sCenterY);
        }
    }
    
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen) {
             // Add button to open ConfigScreen or WheelScreen
             event.addListener(Button.builder(Component.literal("Speedrun Config"), (btn) -> {
                 Minecraft.getInstance().setScreen(new SpeedrunConfigScreen(event.getScreen()));
             }).bounds(10, 10, 100, 20).build());
             
             // Auto-trigger Create World if requested
             if (autoTriggerCreateWorld) {
                 // Simulate click on "Singleplayer" -> "Create New World".
                 for (net.minecraft.client.gui.components.events.GuiEventListener child : event.getScreen().children()) {
                    if (child instanceof Button btn) {
                        if (btn.getMessage().equals(Component.translatable("menu.singleplayer"))) {
                             // Simulate click via reflection
                             try {
                                 // Try accessing onPress field directly (most reliable for Button)
                                 java.lang.reflect.Field f = net.minecraft.client.gui.components.Button.class.getDeclaredField("onPress");
                                 f.setAccessible(true);
                                 net.minecraft.client.gui.components.Button.OnPress onPress = (net.minecraft.client.gui.components.Button.OnPress) f.get(btn);
                                 onPress.onPress(btn);
                             } catch (Throwable t) {
                                 // Fallback to method if field fails (unlikely)
                             }
                             break;
                        }
                    }
                }
             }
        }
        
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen) {
            if (autoTriggerCreateWorld) {
                 // com.example.examplemod.SpeedrunRoulette.LOGGER.info("SpeedrunState: Searching for 'Create New World' button in SelectWorldScreen...");
                 for (net.minecraft.client.gui.components.events.GuiEventListener child : event.getScreen().children()) {
                    if (child instanceof Button btn) {
                        if (btn.getMessage().equals(Component.translatable("selectWorld.create"))) {
                             com.example.examplemod.SpeedrunRoulette.LOGGER.info("SpeedrunState: Clicking 'Create New World'");
                             try {
                                 java.lang.reflect.Field f = net.minecraft.client.gui.components.Button.class.getDeclaredField("onPress");
                                 f.setAccessible(true);
                                 net.minecraft.client.gui.components.Button.OnPress onPress = (net.minecraft.client.gui.components.Button.OnPress) f.get(btn);
                                 onPress.onPress(btn);
                             } catch (Throwable t) {
                                 com.example.examplemod.SpeedrunRoulette.LOGGER.error("SpeedrunState: Failed to click Create button", t);
                             }
                             break;
                        }
                    }
                }
            }
        }
        
        // Handle CreateWorldScreen init too (try to click immediately if active)
        if (event.getScreen() instanceof CreateWorldScreen) {
            if (autoTriggerCreateWorld) {
                 // com.example.examplemod.SpeedrunRoulette.LOGGER.info("SpeedrunState: Searching for 'Create' button in CreateWorldScreen...");
                 for (net.minecraft.client.gui.components.events.GuiEventListener child : event.getScreen().children()) {
                    if (child instanceof Button btn) {
                        if (btn.getMessage().equals(Component.translatable("selectWorld.create"))) {
                             // Only click if active!
                             if (btn.active) {
                                 com.example.examplemod.SpeedrunRoulette.LOGGER.info("SpeedrunState: Clicking 'Create' (Final)");
                                 autoTriggerCreateWorld = false;
                                 try {
                                     java.lang.reflect.Field f = net.minecraft.client.gui.components.Button.class.getDeclaredField("onPress");
                                     f.setAccessible(true);
                                     net.minecraft.client.gui.components.Button.OnPress onPress = (net.minecraft.client.gui.components.Button.OnPress) f.get(btn);
                                     onPress.onPress(btn);
                                 } catch (Throwable t) {
                                     com.example.examplemod.SpeedrunRoulette.LOGGER.error("SpeedrunState: Failed to click Final Create button", t);
                                 }
                             } else {
                                 com.example.examplemod.SpeedrunRoulette.LOGGER.info("SpeedrunState: Final Create button not active yet.");
                             }
                             break;
                        }
                    }
                }
            }
        }
    }
    
    private static void checkAdvancement(net.minecraft.client.multiplayer.ClientAdvancements advancements, String id, String splitName) {
        if (splits.containsKey(splitName)) return;
        
        net.minecraft.resources.Identifier loc = net.minecraft.resources.Identifier.tryParse(id);
        if (loc == null) return;
        
        // We can't access progress directly from ClientAdvancements without access transformers easily.
        // But we can check if the advancement is in the list of completed ones?
        // ClientAdvancements maintains a Map<AdvancementHolder, AdvancementProgress> progress.
        // But it's private.
        
        // However, there is a public listener event for advancements?
        // Or we can try to use the 'advancements.get(loc)' which might return the holder?
        // No, 'get' is not available.
        
        // Let's use reflection to access the 'progress' map.
        // Map<AdvancementHolder, AdvancementProgress> progress
        
        try {
            java.lang.reflect.Field progressField = net.minecraft.client.multiplayer.ClientAdvancements.class.getDeclaredField("progress");
            progressField.setAccessible(true);
            java.util.Map<?, ?> progressMap = (java.util.Map<?, ?>) progressField.get(advancements);
            
            for (java.util.Map.Entry<?, ?> entry : progressMap.entrySet()) {
                // Key is AdvancementHolder
                Object holder = entry.getKey();
                // Value is AdvancementProgress
                Object prog = entry.getValue();
                
                // Get ID from holder
                // holder.id() -> ResourceLocation
                java.lang.reflect.Method idMethod = holder.getClass().getMethod("id");
                net.minecraft.resources.Identifier advId = (net.minecraft.resources.Identifier) idMethod.invoke(holder);
                
                if (advId.equals(loc)) {
                    // Check if done
                    // prog.isDone()
                    java.lang.reflect.Method isDoneMethod = prog.getClass().getMethod("isDone");
                    boolean done = (boolean) isDoneMethod.invoke(prog);
                    
                    if (done) {
                        recordSplit(splitName);
                    }
                    return; // Found match
                }
            }
        } catch (Throwable t) {
            // Ignore reflection errors to prevent crash
        }
    }
    
    public static void setAutoTriggerCreateWorld(boolean v) { autoTriggerCreateWorld = v; }
    public static boolean isAutoTriggerCreateWorld() { return autoTriggerCreateWorld; }
    
    public static void setKeepObjectivesForNextRun(boolean v) { keepObjectivesForNextRun = v; }
    public static boolean isKeepObjectivesForNextRun() { return keepObjectivesForNextRun; }
    
    private static void checkStructure(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> key, String name) {
        if (splits.containsKey(name)) return;
        
        net.minecraft.core.HolderLookup.RegistryLookup<net.minecraft.world.level.levelgen.structure.Structure> registry = level.registryAccess().lookup(net.minecraft.core.registries.Registries.STRUCTURE).orElse(null);
        if (registry == null) return;
        
        net.minecraft.world.level.levelgen.structure.Structure structure = registry.get(key).map(holder -> holder.value()).orElse(null);
        if (structure != null) {
            if (level.structureManager().getStructureWithPieceAt(pos, structure).isValid()) {
                recordSplit(name);
            }
        }
    }
}
