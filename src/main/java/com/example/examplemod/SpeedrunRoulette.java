package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.minecraft.resources.Identifier;

@Mod(SpeedrunRoulette.MODID)
public class SpeedrunRoulette {
    public static final String MODID = "examplemod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static KeyMapping OPEN_WHEEL_KEY;
    public static KeyMapping PAUSE_TIMER_KEY;
    public static KeyMapping TOGGLE_HUD_KEY;
    
    public static String pendingVictoryTime = null;
    public static String pendingVictoryObjectiveName = null;

    public static boolean pendingGiveUp = false;
    public static boolean pendingReplay = false;
    public static boolean pendingNewRun = false;

    // Auto-open wheel state
    public static boolean hasCheckedAutoOpen = false;
    private static boolean languageApplied = false;

    public SpeedrunRoulette(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerKeyMappings);
        modEventBus.addListener(this::registerGuiLayers);
        modEventBus.addListener(this::registerNetwork);

        // BLOCKS.register(modEventBus);
        // ITEMS.register(modEventBus);
        // CREATIVE_MODE_TABS.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.register(new SpeedrunClientEvents());
        NeoForge.EVENT_BUS.register(new SpeedrunServerEvents());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Common setup logic if needed
    }

    private void registerNetwork(final RegisterPayloadHandlersEvent event) {
        SpeedrunNetwork.register(event);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        // Create the Category object.
        KeyMapping.Category category = new KeyMapping.Category(Identifier.tryParse("examplemod:speedrun_roulette"));
        
        event.registerCategory(category);
        
        OPEN_WHEEL_KEY = new KeyMapping("key.examplemod.open_wheel", InputConstants.Type.KEYSYM, InputConstants.KEY_R, category);
        event.register(OPEN_WHEEL_KEY);

        PAUSE_TIMER_KEY = new KeyMapping("key.examplemod.pause_timer", InputConstants.Type.KEYSYM, InputConstants.KEY_P, category);
        event.register(PAUSE_TIMER_KEY);

        TOGGLE_HUD_KEY = new KeyMapping("key.examplemod.toggle_hud", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category);
        event.register(TOGGLE_HUD_KEY);
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.tryParse(SpeedrunRoulette.MODID + ":hud"), (guiGraphics, partialTick) -> {
            SpeedrunState.onRenderHud(guiGraphics);
        });
    }

    public static class SpeedrunClientEvents {
        @SubscribeEvent
        public void onClientTick(ClientTickEvent.Post event) {
            // Apply forced language on first tick
            if (!languageApplied) {
                languageApplied = true;
                SpeedrunConfigScreen.applyForcedLanguage();
            }

            // Auto-open wheel logic
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !SpeedrunRoulette.hasCheckedAutoOpen) {
                SpeedrunRoulette.hasCheckedAutoOpen = true;
                SpeedrunState.checkAutoOpen();
            }

            // Sync system pause state with Minecraft's pause state
            // This handles ALL menus (Options, Controls, Advancements, etc.) automatically.
            // If the game is paused (singleplayer menu open), we pause the timer.
            if (mc.isPaused()) {
                SpeedrunState.onSystemPause(true);
            } else {
                // Only resume system pause if we were system paused.
                // We rely on SpeedrunState to handle "isManualPaused" separately.
                SpeedrunState.onSystemPause(false);
            }
            
            // Auto-Navigation for New Run — only after the integrated server is fully gone
            if (SpeedrunAutoNav.autoTriggerCreateWorld && SpeedrunAutoNav.canAutoNavigateMenus()) {
                SpeedrunAutoNav.tickAutoNavFromTitle(mc);
            }

            // Skip input while saving/disconnecting so we don't open menus on top of "Saving world"
            if (!SpeedrunAutoNav.isDisconnectingOrSaving()) {
                if (OPEN_WHEEL_KEY != null && OPEN_WHEEL_KEY.consumeClick()) {
                    SpeedrunState.openWheelOrReminder();
                }
                if (PAUSE_TIMER_KEY != null && PAUSE_TIMER_KEY.consumeClick()) {
                    SpeedrunState.toggleManualPause();
                }
                if (TOGGLE_HUD_KEY != null && TOGGLE_HUD_KEY.consumeClick()) {
                    SpeedrunState.toggleHud();
                }
            }

            SpeedrunState.onClientTick();
        }

        @SubscribeEvent
        public void onScreenInit(ScreenEvent.Init.Post event) {
            if (event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                boolean startingNew = SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingNewRun;
                boolean startingRetry = SpeedrunRoulette.pendingReplay;

                if (startingNew) {
                    LOGGER.info("TitleScreen: Preparing for New Game (Clear Objectives)");
                    SpeedrunState.prepareForNewGame();
                    SpeedrunAutoNav.autoTriggerCreateWorld = true;
                    SpeedrunAutoNav.resetProgress();
                } else if (startingRetry) {
                    LOGGER.info("TitleScreen: Preparing for Retry (Keep Objectives)");
                    SpeedrunState.prepareForRetry();
                    SpeedrunAutoNav.autoTriggerCreateWorld = false;
                }

                if (startingNew || startingRetry) {
                    SpeedrunRoulette.pendingGiveUp = false;
                    SpeedrunRoulette.pendingNewRun = false;
                    SpeedrunRoulette.pendingReplay = false;
                    SpeedrunRoulette.pendingVictoryTime = null;
                    SpeedrunRoulette.pendingVictoryObjectiveName = null;
                    SpeedrunRoulette.hasCheckedAutoOpen = false;
                    // Keep isTransitioning while auto-nav runs; finish when Create is pressed.
                    if (!SpeedrunAutoNav.autoTriggerCreateWorld) {
                        SpeedrunState.finishTransition();
                    }
                }
            }

            SpeedrunState.onScreenInit(event);
        }

        @SubscribeEvent
        public void onScreenRender(ScreenEvent.Render.Post event) {
            if (event.getScreen() instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen screen) {
                 net.minecraft.client.gui.screens.worldselection.WorldSelectionList list = null;
                 for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
                    if (child instanceof net.minecraft.client.gui.screens.worldselection.WorldSelectionList l) {
                        list = l;
                        break;
                    }
                }
                
                if (list == null) return;
                
                try {
                    // Reflection to access list properties
                    java.lang.reflect.Method getRowTop = net.minecraft.client.gui.components.AbstractSelectionList.class.getDeclaredMethod("getRowTop", int.class);
                    getRowTop.setAccessible(true);
                    
                    java.lang.reflect.Method getRowLeft = net.minecraft.client.gui.components.AbstractSelectionList.class.getDeclaredMethod("getRowLeft");
                    getRowLeft.setAccessible(true);
                    
                    java.lang.reflect.Method getRowWidth = net.minecraft.client.gui.components.AbstractSelectionList.class.getDeclaredMethod("getRowWidth");
                    getRowWidth.setAccessible(true);
                    
                    java.util.List<?> children = list.children();
                    int mouseX = event.getMouseX();
                    int mouseY = event.getMouseY();
                    
                    int rowLeft = (int) getRowLeft.invoke(list);
                    int rowWidth = (int) getRowWidth.invoke(list);
                    
                    for (int i = 0; i < children.size(); i++) {
                         Object entryObj = children.get(i);
                         if (entryObj instanceof net.minecraft.client.gui.screens.worldselection.WorldSelectionList.WorldListEntry entry) {
                             int top = (int) getRowTop.invoke(list, i);
                             
                             // Check visibility (simplified)
                             if (top < 0 || top > screen.height) continue;
                             
                             java.lang.reflect.Field summaryField = net.minecraft.client.gui.screens.worldselection.WorldSelectionList.WorldListEntry.class.getDeclaredField("summary");
                             summaryField.setAccessible(true);
                             net.minecraft.world.level.storage.LevelSummary summary = (net.minecraft.world.level.storage.LevelSummary) summaryField.get(entry);
                             
                             SpeedrunState.RunInfo info = SpeedrunState.getRunInfo(summary.getLevelId());
                             
                             if (info.hasInfo) {
                                 // Icon Position: Right side of entry
                                 int x = rowLeft + rowWidth - 30; 
                                 int y = top + 2;
                                 
                                 net.minecraft.client.gui.GuiGraphics g = event.getGuiGraphics();
                                 String icon = info.isVictory ? "★" : "☠";
                                 int color = info.isVictory ? 0xFF55FF55 : 0xFFFF5555;
                                 
                                 g.pose().translate(x, y);
                                 g.pose().scale(1.5f, 1.5f);
                                 g.drawString(Minecraft.getInstance().font, icon, 0, 0, color, false);
                                 g.pose().scale(1/1.5f, 1/1.5f);
                                 g.pose().translate(-x, -y);
                                 
                                  // Hover Area (approx 15x15)
                                  if (mouseX >= x && mouseX <= x + 15 && mouseY >= y && mouseY <= y + 15) {
                                      List<Component> tooltip = new java.util.ArrayList<>();
                                      tooltip.add(info.isVictory
                                          ? Component.translatable("gui.examplemod.victory_indicator").withStyle(net.minecraft.ChatFormatting.GREEN)
                                          : Component.translatable("gui.examplemod.defeat_indicator").withStyle(net.minecraft.ChatFormatting.RED));
                                      tooltip.add(Component.translatable("gui.examplemod.time_label").append(" " + info.time).withStyle(net.minecraft.ChatFormatting.YELLOW));
                                      tooltip.add(Component.translatable("gui.examplemod.objective_label").append(" " + info.objective).withStyle(net.minecraft.ChatFormatting.GRAY));

                                      String date = new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date(info.timestamp));
                                      tooltip.add(Component.literal(date).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                                     
                                     java.util.List<ClientTooltipComponent> components = tooltip.stream()
                                         .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                                         .collect(java.util.stream.Collectors.toList());
                                         
                                     // Assuming renderTooltip(Font, List<ClientTooltipComponent>, int, int, ClientTooltipPositioner)
                                     // Or maybe identifier is needed?
                                     // The error said: renderTooltip(Font,List<ClientTooltipComponent>,int,int,ClientTooltipPositioner,Identifier)
                                     // I can pass null for Identifier?
                                     
                                     // Let's try passing null for Identifier if required.
                                     // Or maybe there is an overload without Identifier?
                                     // Error said: (Font, List, int, int, Positioner, Identifier)
                                     // So I must provide Identifier?
                                     
                                     // What Identifier? Texture?
                                     // Maybe null works.
                                     
                                     // Wait, there was NO method with 5 args?
                                     // Error said: "method ... is not applicable (actual and formal argument lists differ in length)"
                                     // I called it with 5 args (Font, List, Optional, int, int).
                                     // It expected 6 args (Font, List, int, int, Positioner, Identifier).
                                     
                                     g.renderTooltip(Minecraft.getInstance().font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
                                 }
                             }
                         }
                    }
                } catch (Throwable t) {
                    // ignore
                }
            }
        }

        @SubscribeEvent
        public void onScreenClosing(ScreenEvent.Closing event) {
             // We rely on ClientTick to handle pause state now.
        }

        @SubscribeEvent
        public void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
            if (event.getScreen() instanceof PoolCustomizationScreen screen) {
                if (screen.handleMouseClick(event.getMouseX(), event.getMouseY(), event.getButton())) {
                    event.setCanceled(true);
                }
            }
        }
    }

    public static class SpeedrunServerEvents {
        @SubscribeEvent
        public void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                net.minecraft.server.MinecraftServer server = null;
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    server = serverLevel.getServer();
                }

                if (server != null) {
                    SpeedrunWorldData data = SpeedrunWorldData.get(server);
                    List<Objective> saved = data.getObjectives();
                    if (!saved.isEmpty()) {
                        SpeedrunNetwork.sendToPlayer(new SpeedrunNetwork.SyncObjectivesPacket(saved), player);
                    } else {
                        SpeedrunNetwork.sendToPlayer(new SpeedrunNetwork.SyncObjectivesPacket(new java.util.ArrayList<>()), player);
                    }
                }
            }
        }

        @SubscribeEvent
        public void onClientPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            // SpeedrunRoulette.hasCheckedAutoOpen = false;
            // SpeedrunState.markObjectivesStale();
        }

        @SubscribeEvent
        public void onServerStopping(ServerStoppingEvent event) {
            // LOGGER.info("ServerStopping: World saving...");
        }


        @SubscribeEvent
        public void onAdvancementProgress(AdvancementEvent.AdvancementProgressEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player && event.getAdvancementProgress().isDone()) {
                net.minecraft.server.MinecraftServer server = null;
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    server = serverLevel.getServer();
                }
                if (server != null) {
                    SpeedrunWorldData data = SpeedrunWorldData.get(server);
                    List<Objective> objs = data.getObjectives();
                    String advId = event.getAdvancement().id().toString();
                    for (Objective obj : objs) {
                        if (obj.getType() == Objective.Type.ADVANCEMENT && advId.equals(obj.getAdvancementId())) {
                            LOGGER.info("Advancement completed: " + advId);
                            obj.setForceCompleted(true);
                            data.setObjectives(objs);
                            SpeedrunNetwork.sendToAllPlayers(new SpeedrunNetwork.SyncObjectivesPacket(new ArrayList<>(objs)));
                            break;
                        }
                    }
                }
            }
        }
    }
}
