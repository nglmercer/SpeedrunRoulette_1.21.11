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
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public HudConfigScreen(Screen parent) {
        super(Component.translatable("gui.examplemod.hud_config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 150;
        int h = 20;
        int x = 20;
        int y = 35 - scrollOffset;
        int gap = 24;

        y = sectionHeader(x, y, w, "gui.examplemod.hud_config.section.hud");
        y += gap;

        addScaleRow(x, y, w, h, "gui.examplemod.hud_config.timer_scale", Config.HUD_TIMER_SCALE); y += gap;
        addScaleRow(x, y, w, h, "gui.examplemod.hud_config.item_scale", Config.HUD_ITEM_SCALE); y += gap;
        addScaleRow(x, y, w, h, "gui.examplemod.hud_config.text_scale", Config.HUD_TEXT_SCALE); y += gap;

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

        addOpacitySlider(x, y, w, h); y += gap;
        addScaleRow(x, y, w, h, "gui.examplemod.hud_config.offset_x", Config.HUD_OFFSET_X, -200, 200, 5); y += gap;
        addScaleRow(x, y, w, h, "gui.examplemod.hud_config.offset_y", Config.HUD_OFFSET_Y, -200, 200, 5); y += gap + 4;

        this.addRenderableWidget(toggleButton(x, y, w, h, "gui.examplemod.hud_config.show_background", Config.HUD_SHOW_BACKGROUND)); y += gap;
        this.addRenderableWidget(toggleButton(x, y, w, h, "gui.examplemod.hud_config.show_border", Config.HUD_SHOW_BORDER)); y += gap;
        this.addRenderableWidget(toggleButton(x, y, w, h, "gui.examplemod.hud_config.show_objectives", Config.HUD_SHOW_OBJECTIVES)); y += gap;
        this.addRenderableWidget(toggleButton(x, y, w, h, "gui.examplemod.hud_config.show_stats", Config.HUD_SHOW_STATS)); y += gap + 4;

        addColorHueSlider(x, y, w, h, "gui.examplemod.hud_config.text_color", Config.HUD_TEXT_COLOR); y += gap;
        addColorHueSlider(x, y, w, h, "gui.examplemod.hud_config.timer_color", Config.HUD_TIMER_COLOR); y += gap;
        addColorHueSlider(x, y, w, h, "gui.examplemod.hud_config.stats_color", Config.HUD_STATS_COLOR); y += gap;
        addColorHueSlider(x, y, w, h, "gui.examplemod.hud_config.completed_color", Config.HUD_COMPLETED_COLOR); y += gap + 8;

        y = sectionHeader(x, y, w, "gui.examplemod.hud_config.section.end_screens");
        y += gap;

        this.addRenderableWidget(toggleButton(x, y, w, h, "gui.examplemod.end_config.show_stats", Config.END_SHOW_STATS)); y += gap;
        this.addRenderableWidget(toggleButton(x, y, w, h, "gui.examplemod.end_config.show_splits", Config.END_SHOW_SPLITS)); y += gap;
        this.addRenderableWidget(toggleButton(x, y, w, h, "gui.examplemod.end_config.show_icon", Config.END_SHOW_ICON)); y += gap;

        this.addRenderableWidget(new ConfigSlider(x, y, w, h,
            "gui.examplemod.end_config.bg_opacity",
            Config.END_BG_OPACITY.get(), 0.0, 1.0, (val) -> {
                Config.END_BG_OPACITY.set(Math.round(val * 100.0) / 100.0);
            }) {
            @Override protected Component buildLabel() {
                return Component.translatable("gui.examplemod.end_config.bg_opacity", String.format("%.0f%%", Config.END_BG_OPACITY.get() * 100));
            }
        }); y += gap;

        maxScroll = Math.max(0, (y + scrollOffset) - (this.height - 40));

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (btn) -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private int sectionHeader(int x, int y, int w, String key) {
        this.addRenderableWidget(Button.builder(
            Component.translatable(key).withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.GOLD),
            (btn) -> {}
        ).bounds(x, y, w, 16).build());
        return y + 18;
    }

    private void addOpacitySlider(int x, int y, int w, int h) {
        this.addRenderableWidget(new ConfigSlider(x, y, w, h,
            "gui.examplemod.hud_config.bg_opacity",
            Config.HUD_BG_OPACITY.get(), 0.0, 1.0, (val) -> {
                Config.HUD_BG_OPACITY.set(Math.round(val * 100.0) / 100.0);
            }) {
            @Override protected Component buildLabel() {
                return Component.translatable("gui.examplemod.hud_config.bg_opacity", String.format("%.0f%%", Config.HUD_BG_OPACITY.get() * 100));
            }
        });
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
            config.set(Math.max(min, config.get() - step));
            this.rebuildWidgets();
        }).bounds(x, y, 20, h).build());
        this.addRenderableWidget(Button.builder(Component.translatable(labelKey, config.get()), (btn) -> {
        }).bounds(x + 25, y, w - 50, h).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.increase"), (btn) -> {
            config.set(Math.min(max, config.get() + step));
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
            if (val < 0.1) { rgb = 0xFFFFFF; } else {
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int)(scrollY * 20);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        this.rebuildWidgets();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int previewX = this.width / 2 + 20;
        g.drawString(this.font, Component.translatable("gui.examplemod.hud_config.preview"), previewX, 35, 0xFFFFFF);
        SpeedrunHud.renderPreviewHud(g, this.width, 10);

        int endY = 200;
        g.drawString(this.font, Component.translatable("gui.examplemod.end_config.preview"), previewX, endY - 12, 0xFFFFFF);
        renderEndScreenPreview(g, previewX, endY, this.width - previewX - 10, 120);

        if (maxScroll > 0) {
            g.drawString(this.font, Component.translatable("gui.examplemod.hud_config.scroll_hint"), 20, this.height - 38, 0xFF888888);
        }
    }

    private void renderEndScreenPreview(GuiGraphics g, int x, int y, int w, int h) {
        int bgAlpha = (int)(Config.END_BG_OPACITY.get() * 255) & 0xFF;
        g.fill(x, y, x + w, y + h, (bgAlpha << 24));
        g.renderOutline(x, y, w, h, 0xFFFFFFFF);

        int cx = x + w / 2;
        int cy = y + 10;

        g.drawCenteredString(this.font, Component.translatable("gui.examplemod.victory_title").withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.GOLD), cx, cy, 0xFFD700);
        cy += 14;

        if (Config.END_SHOW_ICON.get()) {
            g.pose().translate(cx, cy + 8);
            g.pose().scale(2.0f, 2.0f);
            g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD), -8, -8);
            g.pose().scale(0.5f, 0.5f);
            g.pose().translate(-cx, -(cy + 8));
            cy += 22;
        }

        g.drawCenteredString(this.font, Component.translatable("gui.examplemod.preview_emerald"), cx, cy, 0xFFFFFFFF);
        cy += 12;

        g.drawCenteredString(this.font, "00:42.000", cx, cy, 0xFF55FF55);
        cy += 14;

        if (Config.END_SHOW_STATS.get()) {
            g.drawCenteredString(this.font, Component.translatable("gui.examplemod.deaths", 3), cx, cy, 0xFFFFFFFF);
            cy += 10;
            g.drawCenteredString(this.font, Component.translatable("gui.examplemod.distance", 512), cx, cy, 0xFFFFFFFF);
            cy += 10;
        }

        if (Config.END_SHOW_SPLITS.get()) {
            g.drawCenteredString(this.font, Component.translatable("gui.examplemod.splits").withStyle(net.minecraft.ChatFormatting.UNDERLINE), cx, cy, 0xFFAAAAAA);
            cy += 10;
            g.drawCenteredString(this.font, "Nether: 02:15", cx, cy, 0xFFDDDDDD);
        }
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

        @Override protected void updateMessage() { this.setMessage(this.label); }
        @Override protected void applyValue() { this.onChange.accept(this.value); }

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

    abstract static class ConfigSlider extends AbstractSliderButton {
        private final String labelKey;
        private final double minVal;
        private final double maxVal;
        private final java.util.function.Consumer<Double> onChange;

        public ConfigSlider(int x, int y, int width, int height, String labelKey, double currentValue, double minVal, double maxVal, java.util.function.Consumer<Double> onChange) {
            super(x, y, width, height, Component.empty(), (currentValue - minVal) / (maxVal - minVal));
            this.labelKey = labelKey;
            this.minVal = minVal;
            this.maxVal = maxVal;
            this.onChange = onChange;
            this.updateMessage();
        }

        protected Component buildLabel() {
            double actual = minVal + this.value * (maxVal - minVal);
            return Component.translatable(labelKey, String.format("%.0f%%", actual * 100));
        }

        @Override protected void updateMessage() { this.setMessage(buildLabel()); }

        @Override
        protected void applyValue() {
            double actual = minVal + this.value * (maxVal - minVal);
            this.onChange.accept(actual);
            this.updateMessage();
        }
    }
}
