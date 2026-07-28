package com.example.examplemod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SpeedrunRouletteClient implements ClientModInitializer {

    public static KeyMapping OPEN_WHEEL_KEY;
    public static KeyMapping PAUSE_TIMER_KEY;
    public static KeyMapping TOGGLE_HUD_KEY;

    private static boolean languageApplied = false;

    @Override
    public void onInitializeClient() {
        // Client-side packet receivers (server -> client).
        SpeedrunNetworkClient.registerClientReceivers();

        registerKeyMappings();

        // HUD overlay.
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> SpeedrunState.onRenderHud(guiGraphics));

        // Client tick.
        ClientTickEvents.END_CLIENT_TICK.register(SpeedrunRouletteClient::onClientTick);

        // Screen events (per-screen wiring done on init).
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            SpeedrunAutoNav.onScreenInit(screen);

            if (screen instanceof SelectWorldScreen) {
                ScreenEvents.afterRender(screen).register((s, g, mx, my, tickDelta) ->
                        renderWorldSelectionIcons(s, g, mx, my));
            }

            if (screen instanceof PoolCustomizationScreen) {
                ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
                    PoolCustomizationScreen pcs = (PoolCustomizationScreen) s;
                    // Returning false cancels vanilla handling when the click was consumed.
                    return !pcs.handleMouseClick(click.x(), click.y(), click.button());
                });
            }
        });
    }

    private void registerKeyMappings() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.tryParse("examplemod:speedrun_roulette"));

        OPEN_WHEEL_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.examplemod.open_wheel", InputConstants.Type.KEYSYM, InputConstants.KEY_R, category));
        PAUSE_TIMER_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.examplemod.pause_timer", InputConstants.Type.KEYSYM, InputConstants.KEY_P, category));
        TOGGLE_HUD_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.examplemod.toggle_hud", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));
    }

    private static void onClientTick(Minecraft mc) {
        // Apply forced language on first tick.
        if (!languageApplied) {
            languageApplied = true;
            SpeedrunConfigScreen.applyForcedLanguage();
        }

        // Auto-open wheel logic.
        if (mc.player != null && !SpeedrunRoulette.hasCheckedAutoOpen && SpeedrunState.isObjectivesLoaded()) {
            SpeedrunRoulette.hasCheckedAutoOpen = true;
            SpeedrunState.checkAutoOpen();
        }

        // Sync system pause state with Minecraft's pause state.
        if (mc.isPaused()) {
            SpeedrunState.onSystemPause(true);
        } else {
            SpeedrunState.onSystemPause(false);
        }

        // Auto-Navigation for New Run — only after the integrated server is fully gone.
        if (SpeedrunAutoNav.autoTriggerCreateWorld && SpeedrunAutoNav.canAutoNavigateMenus()) {
            SpeedrunAutoNav.tickAutoNavFromTitle(mc);
        }

        // Skip input while saving/disconnecting so we don't open menus on top of "Saving world".
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
        SpeedrunE2ETestRunner.onClientTick();
    }

    /** Renders victory/defeat icons on the world selection list rows. */
    private static void renderWorldSelectionIcons(Screen screen, GuiGraphics g, int mouseX, int mouseY) {
        if (!(screen instanceof SelectWorldScreen selectWorldScreen)) return;

        net.minecraft.client.gui.screens.worldselection.WorldSelectionList list = null;
        for (GuiEventListener child : selectWorldScreen.children()) {
            if (child instanceof net.minecraft.client.gui.screens.worldselection.WorldSelectionList l) {
                list = l;
                break;
            }
        }
        if (list == null) return;

        try {
            java.util.List<?> children = list.children();
            int rowLeft = list.getRowLeft();
            int rowWidth = list.getRowWidth();

            for (int i = 0; i < children.size(); i++) {
                Object entryObj = children.get(i);
                if (entryObj instanceof net.minecraft.client.gui.screens.worldselection.WorldSelectionList.WorldListEntry entry) {
                    int top = list.getRowTop(i);
                    if (top < 0 || top > selectWorldScreen.height) continue;

                    net.minecraft.world.level.storage.LevelSummary summary = entry.getLevelSummary();
                    SpeedrunRunInfo.RunInfo info = SpeedrunRunInfo.get(summary.getLevelId());

                    if (info.hasInfo) {
                        int x = rowLeft + rowWidth - 30;
                        int y = top + 2;

                        String icon = info.isVictory ? "★" : "☠";
                        int color = info.isVictory ? 0xFF55FF55 : 0xFFFF5555;

                        g.pose().translate(x, y);
                        g.pose().scale(1.5f, 1.5f);
                        g.drawString(Minecraft.getInstance().font, icon, 0, 0, color, false);
                        g.pose().scale(1 / 1.5f, 1 / 1.5f);
                        g.pose().translate(-x, -y);

                        if (mouseX >= x && mouseX <= x + 15 && mouseY >= y && mouseY <= y + 15) {
                            List<Component> tooltip = new ArrayList<>();
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

                            g.renderTooltip(Minecraft.getInstance().font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    public static void deleteWorldSave() {
        try {
            Minecraft mc = Minecraft.getInstance();
            String levelId = SpeedrunRoulette.pendingLevelId;
            SpeedrunRoulette.pendingLevelId = null;
            if (levelId == null) {
                if (mc.level == null) return;
                net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
                if (server == null) return;
                levelId = SpeedrunRunInfo.getLevelId(server);
                if (levelId == null) return;
            }
            File savesDir = mc.gameDirectory.toPath().resolve("saves").toFile();
            File levelDir = new File(savesDir, levelId);
            if (levelDir.isDirectory()) {
                deleteDirectory(levelDir);
                SpeedrunRoulette.LOGGER.info("Deleted world save directory: " + levelDir.getAbsolutePath());
            }
        } catch (Throwable t) {
            SpeedrunRoulette.LOGGER.error("Failed to delete world save on reset", t);
        }
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}
