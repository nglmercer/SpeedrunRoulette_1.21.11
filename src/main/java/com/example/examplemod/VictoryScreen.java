package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;

public class VictoryScreen extends Screen {
    private boolean showOptions = false;

    public VictoryScreen() {
        super(Component.translatable("gui.examplemod.victory"));
    }

    @Override
    protected void init() {
        if (showOptions) {
            initOptionButtons();
        } else {
            initContinueButton();
        }
    }

    private void initContinueButton() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int y = this.height - 35;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.continue"), (btn) -> {
            showOptions = true;
            rebuildWidgets();
        }).bounds(this.width / 2 - buttonWidth / 2, y, buttonWidth, buttonHeight).build());
    }

    private void initOptionButtons() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24;
        int buttonCount = 3;
        int totalHeight = buttonCount * buttonHeight + (buttonCount - 1) * spacing;
        int startY = this.height - totalHeight - 20;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.retry_new_seed"), (btn) -> {
            if (isTransitionPending()) return;
            btn.active = false;
            SpeedrunState.saveRunInfo(true);
            SpeedrunState.beginRetryNewSeedAndDisconnect();
        }).bounds(this.width / 2 - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.main_menu"), (btn) -> {
            if (isTransitionPending()) return;
            btn.active = false;
            SpeedrunState.saveRunInfo(true);
            SpeedrunState.beginMainMenuAndDisconnect();
        }).bounds(this.width / 2 - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.stay_in_game"), (btn) -> {
            this.onClose();
        }).bounds(this.width / 2 - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight).build());
    }

    private static boolean isTransitionPending() {
        return SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingNewRun
            || SpeedrunRoulette.pendingReplay || SpeedrunRoulette.pendingRetryNewSeed
            || SpeedrunRoulette.pendingReset || SpeedrunRoulette.pendingMainMenu;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        if (keyEvent.key() == 256) {
            if (showOptions) {
                showOptions = false;
                rebuildWidgets();
                return true;
            }
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        int bgAlpha = (int)(Config.END_BG_OPACITY.get() * 255) & 0xFF;
        g.fill(0, 0, this.width, this.height, (bgAlpha << 24));

        super.render(g, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        boolean singleplayer = SpeedrunState.isSingleplayer();

        if (showOptions) {
            renderOptionsView(g, centerX, singleplayer);
        } else {
            renderInfoView(g, centerX, singleplayer);
        }
    }

    private void renderInfoView(GuiGraphics g, int centerX, boolean singleplayer) {
        int currentY = 20;

        g.drawCenteredString(this.font, Component.translatable("gui.examplemod.victory_title").withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.GOLD), centerX, currentY, 0xFFD700);
        currentY += 20;

        if (!singleplayer) {
            SpeedrunGameMode mode = SpeedrunState.getActiveGameMode();
            g.drawCenteredString(this.font, mode.displayName(), centerX, currentY, 0xFF55FFFF);
            currentY += 14;
            String winner = SpeedrunState.getLastWinnerName();
            if (winner != null && !winner.isEmpty()) {
                g.drawCenteredString(this.font,
                        Component.translatable("gui.examplemod.winner_label", winner).withStyle(net.minecraft.ChatFormatting.YELLOW),
                        centerX, currentY, 0xFFFFFF55);
                currentY += 14;
            }
            currentY += 6;
        }

        java.util.List<Objective> objs = SpeedrunState.getObjectives();
        net.minecraft.world.item.ItemStack icon = net.minecraft.world.item.ItemStack.EMPTY;
        Component objNameComp = Component.translatable("gui.examplemod.unknown_objective");

        if (objs != null && !objs.isEmpty()) {
            Objective obj = objs.get(0);
            icon = obj.getIcon();
            if (objs.size() > 1) {
                objNameComp = Component.translatable("gui.examplemod.item_list", objs.size());
            } else {
                objNameComp = obj.getDisplayName();
            }
        } else if (SpeedrunRoulette.pendingVictoryObjectiveName != null) {
            objNameComp = Component.literal(SpeedrunRoulette.pendingVictoryObjectiveName);
        }

        if (Config.END_SHOW_ICON.get() && !icon.isEmpty()) {
            float scale = 4.0f;
            g.pose().translate((float) centerX, (float) (currentY + 32));
            g.pose().scale(scale, scale);
            g.pose().translate(-8.0f, -8.0f);
            g.renderItem(icon, 0, 0);
            g.pose().translate(8.0f, 8.0f);
            g.pose().scale(1.0f / scale, 1.0f / scale);
            g.pose().translate(-(float) centerX, -(float) (currentY + 32));
            currentY += 70;
        } else {
            currentY += 10;
        }

        g.drawCenteredString(this.font, objNameComp, centerX, currentY, 0xFFFFFFFF);
        currentY += 18;

        String time = SpeedrunRoulette.pendingVictoryTime;
        if (time == null) time = "--:--";

        float timeScale = 2.0f;
        g.pose().translate((float) centerX, (float) (currentY + 5));
        g.pose().scale(timeScale, timeScale);
        g.drawCenteredString(this.font, time, 0, 0, 0xFF55FF55);
        g.pose().scale(1.0f / timeScale, 1.0f / timeScale);
        g.pose().translate(-(float) centerX, -(float) (currentY + 5));
        currentY += 32;

        if (Config.END_SHOW_STATS.get()) {
            currentY += 5;
            g.drawCenteredString(this.font, Component.translatable("gui.examplemod.statistics").withStyle(net.minecraft.ChatFormatting.UNDERLINE), centerX, currentY, 0xFFAAAAAA);
            currentY += 14;
            g.drawCenteredString(this.font, Component.translatable("gui.examplemod.deaths", SpeedrunState.getDeathCount()), centerX, currentY, 0xFFFFFFFF);
            currentY += 12;
            g.drawCenteredString(this.font, Component.translatable("gui.examplemod.distance", (int) SpeedrunState.getTraveledMeters()), centerX, currentY, 0xFFFFFFFF);
            currentY += 12;
            g.drawCenteredString(this.font, Component.translatable("gui.examplemod.days", SpeedrunState.getDaysPlayed()), centerX, currentY, 0xFFFFFFFF);
            currentY += 12;
        }

        if (Config.END_SHOW_SPLITS.get()) {
            java.util.Map<String, String> splits = SpeedrunState.getSplits();
            if (!splits.isEmpty()) {
                currentY += 8;
                g.drawCenteredString(this.font, Component.translatable("gui.examplemod.splits").withStyle(net.minecraft.ChatFormatting.UNDERLINE), centerX, currentY, 0xFFAAAAAA);
                currentY += 14;
                for (java.util.Map.Entry<String, String> entry : splits.entrySet()) {
                    g.drawCenteredString(this.font, entry.getKey() + ": " + entry.getValue(), centerX, currentY, 0xFFDDDDDD);
                    currentY += 12;
                }
            }
        }
    }

    private void renderOptionsView(GuiGraphics g, int centerX, boolean singleplayer) {
        int currentY = 25;

        g.drawCenteredString(this.font, Component.translatable("gui.examplemod.victory_title").withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.GOLD), centerX, currentY, 0xFFD700);
        currentY += 22;

        java.util.List<Objective> objs = SpeedrunState.getObjectives();
        Component objNameComp = Component.translatable("gui.examplemod.unknown_objective");
        if (objs != null && !objs.isEmpty()) {
            if (objs.size() > 1) {
                objNameComp = Component.translatable("gui.examplemod.item_list", objs.size());
            } else {
                objNameComp = objs.get(0).getDisplayName();
            }
        } else if (SpeedrunRoulette.pendingVictoryObjectiveName != null) {
            objNameComp = Component.literal(SpeedrunRoulette.pendingVictoryObjectiveName);
        }

        g.drawCenteredString(this.font, objNameComp, centerX, currentY, 0xFFFFFFFF);
        currentY += 16;

        String time = SpeedrunRoulette.pendingVictoryTime;
        if (time == null) time = "--:--";

        float timeScale = 1.75f;
        g.pose().translate((float) centerX, (float) (currentY + 5));
        g.pose().scale(timeScale, timeScale);
        g.drawCenteredString(this.font, time, 0, 0, 0xFF55FF55);
        g.pose().scale(1.0f / timeScale, 1.0f / timeScale);
        g.pose().translate(-(float) centerX, -(float) (currentY + 5));
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
    }
}
