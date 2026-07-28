package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ReminderScreen extends Screen {
    private boolean showRetryConfirm = false;

    public ReminderScreen() {
        super(Component.translatable("gui.examplemod.objectives_reminder"));
    }

    @Override
    protected void init() {
        if (showRetryConfirm) {
            initRetryConfirmButtons();
        } else {
            initMainButtons();
        }
    }

    private void initMainButtons() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = this.height - 80;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.retry"), (button) -> {
            showRetryConfirm = true;
            rebuildWidgets();
        }).bounds(this.width / 2 - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.give_up"), (button) -> {
            if (isTransitionPending()) return;
            button.active = false;
            SpeedrunRoulette.LOGGER.info("New Objectives clicked.");
            SpeedrunState.beginNewRunAndDisconnect();
        }).bounds(this.width / 2 - buttonWidth / 2, startY + 24, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, (button) -> {
            this.onClose();
        }).bounds(this.width / 2 - buttonWidth / 2, startY + 48, buttonWidth, buttonHeight).build());
    }

    private void initRetryConfirmButtons() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = this.height - 80;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.retry_same_seed"), (button) -> {
            if (isTransitionPending()) return;
            button.active = false;
            SpeedrunRoulette.LOGGER.info("Retry Same Seed clicked.");
            SpeedrunState.beginRetryAndDisconnect();
        }).bounds(this.width / 2 - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.retry_new_seed"), (button) -> {
            if (isTransitionPending()) return;
            button.active = false;
            SpeedrunRoulette.LOGGER.info("Retry New Seed clicked.");
            SpeedrunState.beginRetryNewSeedAndDisconnect();
        }).bounds(this.width / 2 - buttonWidth / 2, startY + 24, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, (button) -> {
            showRetryConfirm = false;
            rebuildWidgets();
        }).bounds(this.width / 2 - buttonWidth / 2, startY + 48, buttonWidth, buttonHeight).build());
    }

    private static boolean isTransitionPending() {
        return SpeedrunRoulette.pendingGiveUp || SpeedrunRoulette.pendingNewRun
            || SpeedrunRoulette.pendingReplay || SpeedrunRoulette.pendingRetryNewSeed
            || SpeedrunRoulette.pendingReset || SpeedrunRoulette.pendingMainMenu;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        if (keyEvent.key() == 256) {
            if (showRetryConfirm) {
                showRetryConfirm = false;
                rebuildWidgets();
                return true;
            }
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xCC000000);

        Component titleText = showRetryConfirm
            ? Component.translatable("gui.examplemod.retry_confirm_title")
            : this.title;
        guiGraphics.drawCenteredString(this.font, titleText, this.width / 2, 20, 0xFFFFFFFF);

        List<Objective> objectives = SpeedrunState.getObjectives();
        if (!objectives.isEmpty()) {
            int centerY = this.height / 2 - 20;
            int itemSize = 64;
            int spacing = 32;
            int count = objectives.size();
            int row1Count = Math.min(count, 5);
            int row2Count = count > 5 ? count - 5 : 0;
            int row1Y = (row2Count > 0) ? centerY - 60 : centerY;
            int row2Y = centerY + 80;

            renderRow(guiGraphics, objectives, 0, row1Count, row1Y, itemSize, spacing);
            if (row2Count > 0) {
                renderRow(guiGraphics, objectives, 5, row2Count, row2Y, itemSize, spacing);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphics guiGraphics, List<Objective> objectives, int startIndex, int count, int y, int itemSize, int spacing) {
        int totalRowWidth = count * itemSize + (count - 1) * spacing;
        int startX = (this.width - totalRowWidth) / 2;

        for (int i = 0; i < count; i++) {
            Objective obj = objectives.get(startIndex + i);
            int x = startX + i * (itemSize + spacing);

            float cx = x + itemSize / 2.0f;
            float cy = y;

            guiGraphics.pose().translate(cx, cy);
            guiGraphics.pose().scale(3.0f, 3.0f);
            guiGraphics.renderItem(obj.getIcon(), -8, -8);
            guiGraphics.pose().scale(1 / 3.0f, 1 / 3.0f);
            guiGraphics.pose().translate(-cx, -cy);

            Component name = obj.getDisplayName();
            int nameY = y + 35;
            int nameWidth = this.font.width(name);
            guiGraphics.drawString(this.font, name, x + itemSize / 2 - nameWidth / 2, nameY, 0xFFFFFFFF, true);
        }
    }
}
