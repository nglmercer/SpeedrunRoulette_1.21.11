package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class HudConfigScreen extends Screen {
    private final Screen parent;

    public HudConfigScreen(Screen parent) {
        super(Component.translatable("gui.examplemod.hud_config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 150;
        int h = 20;
        int x = 20;
        int y = 35;
        int gap = 24;

        addScaleRow(x, y, w, h, "gui.examplemod.hud_config.timer_scale", Config.HUD_TIMER_SCALE);
        y += gap;
        addScaleRow(x, y, w, h, "gui.examplemod.hud_config.item_scale", Config.HUD_ITEM_SCALE);
        y += gap;
        addScaleRow(x, y, w, h, "gui.examplemod.hud_config.text_scale", Config.HUD_TEXT_SCALE);
        y += gap;

        String posLabel = switch (Config.HUD_POSITION.get()) {
            case "top_left" -> "gui.examplemod.hud_config.pos.top_left";
            case "bottom_right" -> "gui.examplemod.hud_config.pos.bottom_right";
            case "bottom_left" -> "gui.examplemod.hud_config.pos.bottom_left";
            default -> "gui.examplemod.hud_config.pos.top_right";
        };
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.hud_config.position", Component.translatable(posLabel).getString()), (btn) -> {
            String cur = Config.HUD_POSITION.get();
            Config.HUD_POSITION.set(switch (cur) {
                case "top_right" -> "top_left";
                case "top_left" -> "bottom_left";
                case "bottom_left" -> "bottom_right";
                default -> "top_right";
            });
            this.rebuildWidgets();
        }).bounds(x, y, w, h).build());
        y += gap;

        this.addRenderableWidget(new ConfigSlider(x, y, w, h,
            Component.translatable("gui.examplemod.hud_config.bg_opacity", String.format("%.0f%%", Config.HUD_BG_OPACITY.get() * 100)),
            Config.HUD_BG_OPACITY.get(), 0.0, 1.0, (val) -> {
                Config.HUD_BG_OPACITY.set(Math.round(val * 100.0) / 100.0);
            }));
        y += gap;

        addScaleRow(x, y, w, h, "gui.examplemod.hud_config.offset_x", Config.HUD_OFFSET_X, -200, 200, 5);
        y += gap;
        addScaleRow(x, y, w, h, "gui.examplemod.hud_config.offset_y", Config.HUD_OFFSET_Y, -200, 200, 5);
        y += gap + 4;

        this.addRenderableWidget(toggleButton(x, y, w, h, "gui.examplemod.hud_config.show_background", Config.HUD_SHOW_BACKGROUND));
        y += gap;
        this.addRenderableWidget(toggleButton(x, y, w, h, "gui.examplemod.hud_config.show_border", Config.HUD_SHOW_BORDER));
        y += gap;
        this.addRenderableWidget(toggleButton(x, y, w, h, "gui.examplemod.hud_config.show_objectives", Config.HUD_SHOW_OBJECTIVES));
        y += gap;
        this.addRenderableWidget(toggleButton(x, y, w, h, "gui.examplemod.hud_config.show_stats", Config.HUD_SHOW_STATS));
        y += gap + 4;

        addColorHueSlider(x, y, w, h, "gui.examplemod.hud_config.text_color", Config.HUD_TEXT_COLOR);
        y += gap;
        addColorHueSlider(x, y, w, h, "gui.examplemod.hud_config.timer_color", Config.HUD_TIMER_COLOR);
        y += gap;
        addColorHueSlider(x, y, w, h, "gui.examplemod.hud_config.stats_color", Config.HUD_STATS_COLOR);
        y += gap;
        addColorHueSlider(x, y, w, h, "gui.examplemod.hud_config.completed_color", Config.HUD_COMPLETED_COLOR);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (btn) -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void addScaleRow(int x, int y, int w, int h, String labelKey, Config.DoubleValue config) {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.decrease"), (btn) -> {
            adjustDouble(config, -0.1, 0.5, 5.0);
        }).bounds(x, y, 20, h).build());

        this.addRenderableWidget(Button.builder(Component.translatable(labelKey, String.format("%.1f", config.get())), (btn) -> {
        }).bounds(x + 25, y, w - 50, h).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.increase"), (btn) -> {
            adjustDouble(config, 0.1, 0.5, 5.0);
        }).bounds(x + w - 20, y, 20, h).build());
    }

    private void addScaleRow(int x, int y, int w, int h, String labelKey, Config.IntValue config, int min, int max, int step) {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.decrease"), (btn) -> {
            int next = config.get() - step;
            config.set(Math.max(min, next));
            this.rebuildWidgets();
        }).bounds(x, y, 20, h).build());

        this.addRenderableWidget(Button.builder(Component.translatable(labelKey, config.get()), (btn) -> {
        }).bounds(x + 25, y, w - 50, h).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.increase"), (btn) -> {
            int next = config.get() + step;
            config.set(Math.min(max, next));
            this.rebuildWidgets();
        }).bounds(x + w - 20, y, 20, h).build());
    }

    private Button toggleButton(int x, int y, int w, int h, String labelKey, Config.BooleanValue config) {
        return Button.builder(Component.translatable(labelKey, config.get() ? Component.translatable("gui.examplemod.on") : Component.translatable("gui.examplemod.off")), (btn) -> {
            config.set(!config.get());
            this.rebuildWidgets();
        }).bounds(x, y, w, h).build();
    }

    private void addColorHueSlider(int x, int y, int w, int h, String labelKey, Config.StringValue config) {
        int color = SpeedrunHud.parseColor(config.get(), 0xFFFFFFFF);
        float[] hsb = java.awt.Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        double initial = (hsb[1] < 0.1) ? 0.05 : (0.1 + hsb[0] * 0.9);

        this.addRenderableWidget(new HueSlider(x, y, w, h, Component.translatable(labelKey), initial, (val) -> {
            int rgb;
            if (val < 0.1) {
                rgb = 0xFFFFFF;
            } else {
                float hue = (float)((val - 0.1) / 0.9);
                rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
            }
            config.set(String.format("#FF%06X", (0xFFFFFF & rgb)));
        }));
    }

    private void adjustDouble(Config.DoubleValue config, double delta, double min, double max) {
        double next = Math.round((config.get() + delta) * 10.0) / 10.0;
        config.set(Math.max(min, Math.min(max, next)));
        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        guiGraphics.drawString(this.font, Component.translatable("gui.examplemod.hud_config.preview"), this.width / 2 + 20, 35, 0xFFFFFF);
        SpeedrunHud.renderPreviewHud(guiGraphics, this.width, 10);
    }

    private class HueSlider extends AbstractSliderButton {
        private final Component label;
        private final java.util.function.Consumer<Double> onChange;

        public HueSlider(int x, int y, int width, int height, Component label, double initialValue, java.util.function.Consumer<Double> onChange) {
            super(x, y, width, height, label, initialValue);
            this.label = label;
            this.onChange = onChange;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(this.label);
        }

        @Override
        protected void applyValue() {
            this.onChange.accept(this.value);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int startX = this.getX() + 4;
            int endX = this.getX() + this.getWidth() - 4;
            int innerWidth = endX - startX;
            int height = this.getHeight();

            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + height, 0xFF000000);
            guiGraphics.renderOutline(this.getX(), this.getY(), this.getWidth(), height, 0xFFFFFFFF);

            int whiteWidth = (int)(innerWidth * 0.1);
            guiGraphics.fill(startX, this.getY() + 2, startX + whiteWidth, this.getY() + height - 2, 0xFFFFFFFF);

            int rainbowStart = startX + whiteWidth;
            int rainbowWidth = innerWidth - whiteWidth;
            for (int i = 0; i < rainbowWidth; i++) {
                float hue = (float)i / (float)rainbowWidth;
                int color = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
                guiGraphics.fill(rainbowStart + i, this.getY() + 2, rainbowStart + i + 1, this.getY() + height - 2, color | 0xFF000000);
            }

            int handleX = startX + (int)(this.value * innerWidth);
            guiGraphics.fill(handleX - 2, this.getY(), handleX + 2, this.getY() + height, 0xFF888888);
            guiGraphics.renderOutline(handleX - 2, this.getY(), 4, height, 0xFFFFFFFF);

            guiGraphics.drawCenteredString(minecraft.font, this.getMessage(), this.getX() + this.getWidth() / 2, this.getY() + (this.getHeight() - 8) / 2, 0xFFFFFFFF);
        }
    }

    private class ConfigSlider extends AbstractSliderButton {
        private final Component label;
        private final double minVal;
        private final double maxVal;
        private final java.util.function.Consumer<Double> onChange;

        public ConfigSlider(int x, int y, int width, int height, Component label, double currentValue, double minVal, double maxVal, java.util.function.Consumer<Double> onChange) {
            super(x, y, width, height, label, (currentValue - minVal) / (maxVal - minVal));
            this.label = label;
            this.minVal = minVal;
            this.maxVal = maxVal;
            this.onChange = onChange;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(this.label);
        }

        @Override
        protected void applyValue() {
            double actual = minVal + this.value * (maxVal - minVal);
            this.onChange.accept(actual);
        }
    }
}
