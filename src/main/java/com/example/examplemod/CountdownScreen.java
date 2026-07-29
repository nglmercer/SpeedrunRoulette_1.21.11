package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CountdownScreen extends Screen {
    private int countdownTicks = 0;
    private static final int TICKS_PER_NUMBER = 20;
    private static final int TOTAL_TICKS = TICKS_PER_NUMBER * 4;
    private static final String[] COUNTDOWN_TEXTS = {"3", "2", "1"};
    private final Runnable onComplete;
    private double lastMouseX = -1;
    private double lastMouseY = -1;

    public CountdownScreen(Runnable onComplete) {
        super(Component.empty());
        this.onComplete = onComplete;
    }

    @Override
    protected void init() {
    }

    @Override
    public void tick() {
        super.tick();
        countdownTicks++;

        if (countdownTicks >= TOTAL_TICKS) {
            this.onComplete.run();
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (lastMouseX >= 0) {
                double dx = mouseX - lastMouseX;
                double dy = mouseY - lastMouseY;
                if (dx != 0 || dy != 0) {
                    mc.player.turn(dx * 8.0, dy * 8.0);
                }
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        int phase = countdownTicks / TICKS_PER_NUMBER;
        String text;
        int color;

        if (phase < 3) {
            text = COUNTDOWN_TEXTS[phase];
            color = 0xFFFFFFFF;
        } else {
            text = Component.translatable("gui.examplemod.countdown_go").getString();
            color = 0xFF55FF55;
        }

        float scale = 4.0f;
        float x = this.width / 2.0f;
        float y = this.height / 2.0f;

        guiGraphics.pose().translate(x, y);
        guiGraphics.pose().scale(scale, scale);
        guiGraphics.drawCenteredString(this.font, text, 0, -4, color);
        guiGraphics.pose().scale(1.0f / scale, 1.0f / scale);
        guiGraphics.pose().translate(-x, -y);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        if (keyEvent.key() == 256) {
            this.onComplete.run();
            this.onClose();
            return true;
        }
        return true;
    }
}
