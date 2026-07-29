package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.ChatFormatting;

/**
 * Shown in Challenge/VS mode when another player finishes the shared objectives first.
 */
public class LoseScreen extends Screen {
    private final String winnerName;
    private final String finishTime;
    private boolean showOptions = false;

    public LoseScreen(String winnerName, String finishTime) {
        super(Component.translatable("gui.examplemod.defeat"));
        this.winnerName = winnerName != null && !winnerName.isEmpty() ? winnerName : "?";
        this.finishTime = finishTime != null && !finishTime.isEmpty() ? finishTime : "--:--";
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
        int startY = this.height - 40;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.continue"), (btn) -> {
            showOptions = true;
            rebuildWidgets();
        }).bounds(this.width / 2 - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());
    }

    private void initOptionButtons() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24;
        int buttonCount = 3;
        int startY = this.height - (buttonCount * buttonHeight + (buttonCount - 1) * spacing) - 15;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.retry_new_seed"), (btn) -> {
            if (isTransitionPending()) return;
            btn.active = false;
            SpeedrunState.saveRunInfo(false);
            SpeedrunState.beginRetryNewSeedAndDisconnect();
        }).bounds(this.width / 2 - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.main_menu"), (btn) -> {
            if (isTransitionPending()) return;
            btn.active = false;
            SpeedrunState.saveRunInfo(false);
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
        g.fill(0, 0, this.width, this.height, (bgAlpha << 24) | 0x220000);

        super.render(g, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int currentY = 30;

        g.drawCenteredString(this.font,
                Component.translatable("gui.examplemod.defeat_title").withStyle(ChatFormatting.BOLD, ChatFormatting.RED),
                centerX, currentY, 0xFFFF5555);
        currentY += 30;

        g.drawCenteredString(this.font,
                Component.translatable("gui.examplemod.defeat_subtitle"),
                centerX, currentY, 0xFFFFFFFF);
        currentY += 25;

        g.drawCenteredString(this.font,
                Component.translatable("gui.examplemod.winner_label", winnerName).withStyle(ChatFormatting.GOLD),
                centerX, currentY, 0xFFFFD700);
        currentY += 20;

        float timeScale = 1.75f;
        g.pose().translate((float) centerX, (float) (currentY + 5));
        g.pose().scale(timeScale, timeScale);
        g.drawCenteredString(this.font, finishTime, 0, 0, 0xFFFF5555);
        g.pose().scale(1.0f / timeScale, 1.0f / timeScale);
        g.pose().translate(-(float) centerX, -(float) (currentY + 5));
        currentY += 35;

        // Objectives summary
        java.util.List<Objective> objs = SpeedrunState.getObjectives();
        if (objs != null && !objs.isEmpty()) {
            Component objName;
            if (objs.size() > 1) {
                objName = Component.translatable("gui.examplemod.item_list", objs.size());
            } else {
                objName = objs.get(0).getDisplayName();
            }
            g.drawCenteredString(this.font,
                    Component.translatable("gui.examplemod.objective_label").append(" ").append(objName),
                    centerX, currentY, 0xFFAAAAAA);
            currentY += 20;
        }

        g.drawCenteredString(this.font,
                Component.translatable("gui.examplemod.mode.challenge").withStyle(ChatFormatting.DARK_RED),
                centerX, currentY, 0xFFFFAAAA);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        // Handled in render()
    }
}
