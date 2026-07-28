package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.List;

public class SpeedrunHud {

    public static void onRenderHud(GuiGraphics g) {
        if (SpeedrunTimer.getHudState() == 2) return;
        if (!SpeedrunTimer.isRunning() && SpeedrunState.getObjectives().isEmpty()) return;

        boolean showObjectives = (SpeedrunTimer.getHudState() == 0) && !SpeedrunState.getObjectives().isEmpty();
        boolean showStats = (SpeedrunTimer.getHudState() == 0);

        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        int width = g.guiWidth();
        int margin = 5;

        String timeStr = SpeedrunTimer.currentFormattedTime();
        if (SpeedrunTimer.isPaused()) {
            timeStr += Component.translatable("gui.examplemod.paused_indicator").getString();
        }

        String statsStr = Component.translatable("gui.examplemod.deaths_label").getString() + " " + SpeedrunTimer.getDeathCount() + " | " +
            Component.translatable("gui.examplemod.distance_label").getString() + " " + String.format("%.0fm", SpeedrunTimer.getTraveledMeters()) + " | " +
            Component.translatable("gui.examplemod.days_label").getString() + " " + SpeedrunTimer.getDaysPlayed();

        renderObjectivesAndStats(g, font, width, margin, showObjectives, showStats,
            timeStr, statsStr, SpeedrunState.getObjectives(), SpeedrunTimer.isRunning(), SpeedrunTimer.isPaused());
    }

    public static void renderPreviewHud(GuiGraphics g, int width, int margin) {
        try {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            if (font == null) return;

            String timeStr = "00:42.000";
            String statsStr = Component.translatable("gui.examplemod.deaths_label").getString() + " 5 | " +
                Component.translatable("gui.examplemod.distance_label").getString() + " 1234m | " +
                Component.translatable("gui.examplemod.days_label").getString() + " 2";

            List<Objective> dummyObjectives = new ArrayList<>();

            try {
                net.minecraft.resources.Identifier ironId = net.minecraft.resources.Identifier.tryParse("minecraft:iron_ingot");
                net.minecraft.resources.Identifier dirtId = net.minecraft.resources.Identifier.tryParse("minecraft:dirt");
                net.minecraft.resources.Identifier emeraldId = net.minecraft.resources.Identifier.tryParse("minecraft:emerald");

                net.minecraft.world.item.Item iron = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ironId).map(holder -> holder.value()).orElse(null);
                net.minecraft.world.item.Item dirt = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(dirtId).map(holder -> holder.value()).orElse(null);
                net.minecraft.world.item.Item emerald = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(emeraldId).map(holder -> holder.value()).orElse(null);

                if (iron == net.minecraft.world.item.Items.AIR) iron = net.minecraft.world.item.Items.IRON_INGOT;
                if (dirt == net.minecraft.world.item.Items.AIR) dirt = net.minecraft.world.item.Items.DIRT;
                if (emerald == net.minecraft.world.item.Items.AIR) emerald = net.minecraft.world.item.Items.EMERALD;

                if (iron != null && iron != net.minecraft.world.item.Items.AIR) {
                    dummyObjectives.add(new Objective("preview_item", Component.translatable("gui.examplemod.preview_iron_ingot"), new net.minecraft.world.item.ItemStack(iron), Objective.Type.ITEM));
                }
                if (dirt != null && dirt != net.minecraft.world.item.Items.AIR) {
                    dummyObjectives.add(new Objective("preview_block", Component.translatable("gui.examplemod.preview_dirt_block"), new net.minecraft.world.item.ItemStack(dirt), Objective.Type.BLOCK));
                }
                if (emerald != null && emerald != net.minecraft.world.item.Items.AIR) {
                    dummyObjectives.add(new Objective("preview_gem", Component.translatable("gui.examplemod.preview_emerald"), new net.minecraft.world.item.ItemStack(emerald), Objective.Type.ITEM));
                }
            } catch (Throwable t) {
                System.err.println("Preview Items Error: " + t.getMessage());
                dummyObjectives.add(new Objective("preview_error", Component.translatable("gui.examplemod.preview_error"), net.minecraft.world.item.ItemStack.EMPTY, Objective.Type.ITEM));
            }

            if (dummyObjectives.isEmpty()) {
                dummyObjectives.add(new Objective("preview_fallback", Component.translatable("gui.examplemod.preview_test_item"), net.minecraft.world.item.ItemStack.EMPTY, Objective.Type.ITEM));
            }

            renderObjectivesAndStats(g, font, width, margin, true, true, timeStr, statsStr, dummyObjectives, true, false);
        } catch (Exception e) {
            System.err.println("Error rendering HUD preview: " + e.getMessage());
        }
    }

    static int parseColor(String colorStr, int defaultColor) {
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

    static void renderObjectivesAndStats(GuiGraphics g, net.minecraft.client.gui.Font font, int width, int margin,
                                          boolean showObjectivesList, boolean showStats,
                                          String timeStr, String statsStr, List<Objective> renderObjectives,
                                          boolean isTimerRunning, boolean isPaused) {

        float baseTimerScale = Config.HUD_TIMER_SCALE.get().floatValue();
        float itemScale = Config.HUD_ITEM_SCALE.get().floatValue();
        float textScale = Config.HUD_TEXT_SCALE.get().floatValue();

        int textColor = parseColor(Config.HUD_TEXT_COLOR.get(), 0xFFFFFFFF);
        int customTimerColor = parseColor(Config.HUD_TIMER_COLOR.get(), -1);

        // --- Minimal Mode (Timer Only) ---
        if (!showObjectivesList && !showStats) {
            float scale = baseTimerScale * 1.3f;
            int textWidth = font.width(timeStr);
            int textHeight = font.lineHeight;

            int boxWidth = (int)(textWidth * scale) + margin * 2 + 10;
            int boxHeight = (int)(textHeight * scale) + margin * 2 + 6;

            int boxX = width - boxWidth - margin;
            int boxY = margin;

            g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000);
            g.renderOutline(boxX - 1, boxY - 1, boxWidth + 2, boxHeight + 2, 0xFFFFFFFF);

            int timerColor = (customTimerColor != -1) ? customTimerColor :
                             (isPaused ? 0xFFFFFF55 : (isTimerRunning ? 0xFF55FF55 : 0xFFFFFFFF));

            float centerX = boxX + boxWidth / 2.0f;
            float centerY = boxY + boxHeight / 2.0f;

            g.pose().translate(centerX, centerY);
            g.pose().scale(scale, scale);
            g.drawCenteredString(font, timeStr, 0, -4, timerColor);
            g.pose().scale(1/scale, 1/scale);
            g.pose().translate(-centerX, -centerY);

            return;
        }

        // --- Standard Mode ---
        int maxTextWidth = 140;
        int itemSize = (int)(16 * itemScale);
        int lineSpacing = (int)(itemSize * 1.2);
        if (lineSpacing < 24) lineSpacing = 24;

        int timeWidth = (int)(font.width(timeStr) * baseTimerScale);
        if (timeWidth > maxTextWidth) maxTextWidth = timeWidth;

        boolean compactMode = renderObjectives.size() > 5;
        if (showObjectivesList) {
            for (Objective obj : renderObjectives) {
                int w = (int)(font.width(obj.getDisplayName()) * textScale) + (compactMode ? 0 : (itemSize + 4));
                if (w > maxTextWidth) maxTextWidth = w;
            }
        }

        if (showStats) {
            if ((int)(font.width(statsStr) * textScale) > maxTextWidth) maxTextWidth = (int)(font.width(statsStr) * textScale);
        }

        int boxWidth = maxTextWidth + margin * 2;
        int boxHeight = margin * 2;

        int timerHeight = (int)(12 * baseTimerScale) + 6;
        boxHeight += timerHeight;

        if (showObjectivesList) {
            boxHeight += 4;
            boxHeight += (int)(12 * textScale);
            if (compactMode) {
                boxHeight += renderObjectives.size() * (int)(12 * textScale);
            } else {
                boxHeight += renderObjectives.size() * lineSpacing;
            }
            boxHeight += 5;
        }

        if (showStats) {
            if (!showObjectivesList) boxHeight += 4;
            boxHeight += (int)(12 * textScale);
        }

        int boxX = width - boxWidth - margin;
        int boxY = margin;

        g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000);
        g.renderOutline(boxX - 1, boxY - 1, boxWidth + 2, boxHeight + 2, 0xFFFFFFFF);

        int currentY = boxY + margin;
        int textX = boxX + margin;

        int timerColor = (customTimerColor != -1) ? customTimerColor :
                         (isPaused ? 0xFFFFFF55 : (isTimerRunning ? 0xFF55FF55 : 0xFFFFFFFF));

        float normalScale = baseTimerScale;
        float tCenterX = boxX + boxWidth / 2.0f;
        float tCenterY = currentY + (timerHeight / 2.0f) - 2;

        g.pose().translate(tCenterX, tCenterY);
        g.pose().scale(normalScale, normalScale);
        g.drawCenteredString(font, timeStr, 0, -4, timerColor);
        g.pose().scale(1/normalScale, 1/normalScale);
        g.pose().translate(-tCenterX, -tCenterY);

        currentY += timerHeight;

        if (showObjectivesList || showStats) {
            g.fill(boxX + margin, currentY, boxX + boxWidth - margin, currentY + 1, 0xFFAAAAAA);
            currentY += 4;
        }

        // Objectives
        if (showObjectivesList) {
            g.pose().translate(textX, currentY);
            g.pose().scale(textScale, textScale);
            g.drawString(font, Component.translatable("gui.examplemod.objectives_label"), 0, 0, 0xFFAAAAAA, false);
            g.pose().scale(1/textScale, 1/textScale);
            g.pose().translate(-textX, -currentY);
            currentY += (int)(12 * textScale);

            for (Objective obj : renderObjectives) {
                Player player = Minecraft.getInstance().player;
                boolean completed = (player != null) && obj.isCompleted(player);
                int color = completed ? 0xFF55FF55 : textColor;
                Component name = obj.getDisplayName();

                if (compactMode) {
                    String prefix = completed
                        ? Component.translatable("gui.examplemod.completed_checkbox").getString() + " "
                        : Component.translatable("gui.examplemod.uncompleted_checkbox").getString() + " ";
                    g.pose().translate(textX, currentY);
                    g.pose().scale(textScale, textScale);
                    g.drawString(font, prefix + name.getString(), 0, 0, color, false);
                    g.pose().scale(1/textScale, 1/textScale);
                    g.pose().translate(-textX, -currentY);
                    currentY += (int)(12 * textScale);
                } else {
                    float textH = 9 * textScale;
                    float yOffset = (itemSize - textH) / 2.0f;

                    g.pose().translate(textX, currentY + yOffset);
                    g.pose().scale(textScale, textScale);
                    g.drawString(font, name, 0, 0, color, false);
                    g.pose().scale(1/textScale, 1/textScale);
                    g.pose().translate(-textX, -(currentY + yOffset));

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

            if (showStats) {
                g.fill(boxX + margin, currentY, boxX + boxWidth - margin, currentY + 1, 0xFF888888);
                currentY += 4;
            }
        }

        // Stats
        if (showStats) {
            float sCenterX = boxX + boxWidth / 2.0f;
            float sCenterY = currentY + 4;

            g.pose().translate(sCenterX, sCenterY);
            g.pose().scale(textScale, textScale);
            g.drawCenteredString(font, statsStr, 0, 0, 0xFFFFDDDD);
            g.pose().scale(1/textScale, 1/textScale);
            g.pose().translate(-sCenterX, -sCenterY);
        }
    }
}
