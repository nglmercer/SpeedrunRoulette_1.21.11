package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class SpeedrunConfigScreen extends Screen {
    private final Screen parent;

    public SpeedrunConfigScreen(Screen parent) {
        super(Component.translatable("gui.examplemod.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = this.height / 6;
        int x = this.width / 2 - 100;
        int w = 200;
        int h = 20;
        int gap = 24;

        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.examplemod.config.auto_open", (Config.AUTO_OPEN_WHEEL.get() ? Component.translatable("gui.examplemod.on") : Component.translatable("gui.examplemod.off"))),
            (btn) -> {
                Config.AUTO_OPEN_WHEEL.set(!Config.AUTO_OPEN_WHEEL.get());
                btn.setMessage(Component.translatable("gui.examplemod.config.auto_open", (Config.AUTO_OPEN_WHEEL.get() ? Component.translatable("gui.examplemod.on") : Component.translatable("gui.examplemod.off"))));
            }
        ).bounds(x, y, w, h).build());

        y += gap;
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.examplemod.config.auto_start", (Config.AUTO_START.get() ? Component.translatable("gui.examplemod.on") : Component.translatable("gui.examplemod.off"))),
            (btn) -> {
                Config.AUTO_START.set(!Config.AUTO_START.get());
                btn.setMessage(Component.translatable("gui.examplemod.config.auto_start", (Config.AUTO_START.get() ? Component.translatable("gui.examplemod.on") : Component.translatable("gui.examplemod.off"))));
            }
        ).bounds(x, y, w, h).build());

        y += gap;
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.examplemod.config.objective_count", Config.OBJECTIVE_COUNT.get()),
            (btn) -> {
                int current = Config.OBJECTIVE_COUNT.get();
                int next = current + 1;
                if (next > 10) next = 1;
                Config.OBJECTIVE_COUNT.set(next);
                btn.setMessage(Component.translatable("gui.examplemod.config.objective_count", next));
            }
        ).bounds(x, y, w, h).build());

        y += gap;
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.examplemod.config.pool_config"),
            (btn) -> {
                this.minecraft.setScreen(new PoolConfigScreen(this));
            }
        ).bounds(x, y, w, h).build());

        y += gap;
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.examplemod.config.hud_config"),
            (btn) -> {
                this.minecraft.setScreen(new HudConfigScreen(this));
            }
        ).bounds(x, y, w, h).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (btn) -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
