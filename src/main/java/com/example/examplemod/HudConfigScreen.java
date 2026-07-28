package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class HudConfigScreen extends Screen {
    private final Screen parent;
    private EditBox textColorBox;
    private EditBox timerColorBox;

    public HudConfigScreen(Screen parent) {
        super(Component.translatable("gui.examplemod.hud_config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 150;
        int h = 20;
        int x = 20;
        int y = 40;
        int gap = 24;

        // Timer Scale
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.decrease"), (btn) -> {
            adjustDouble(Config.HUD_TIMER_SCALE, -0.1);
        }).bounds(x, y, 20, h).build());
        
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.hud_config.timer_scale", String.format("%.1f", Config.HUD_TIMER_SCALE.get())), (btn) -> {
            // Reset or cycle? Maybe just display.
        }).bounds(x + 25, y, w - 50, h).build()); 

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.increase"), (btn) -> {
            adjustDouble(Config.HUD_TIMER_SCALE, 0.1);
        }).bounds(x + w - 20, y, 20, h).build());

        y += gap;

        // Item Scale
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.decrease"), (btn) -> {
            adjustDouble(Config.HUD_ITEM_SCALE, -0.1);
        }).bounds(x, y, 20, h).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.hud_config.item_scale", String.format("%.1f", Config.HUD_ITEM_SCALE.get())), (btn) -> {
        }).bounds(x + 25, y, w - 50, h).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.increase"), (btn) -> {
            adjustDouble(Config.HUD_ITEM_SCALE, 0.1);
        }).bounds(x + w - 20, y, 20, h).build());

        y += gap;

        // Text Scale
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.decrease"), (btn) -> {
            adjustDouble(Config.HUD_TEXT_SCALE, -0.1);
        }).bounds(x, y, 20, h).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.hud_config.text_scale", String.format("%.1f", Config.HUD_TEXT_SCALE.get())), (btn) -> {
        }).bounds(x + 25, y, w - 50, h).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.increase"), (btn) -> {
            adjustDouble(Config.HUD_TEXT_SCALE, 0.1);
        }).bounds(x + w - 20, y, 20, h).build());

        y += gap;
        
        // Text Color (Hue Slider)
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.hud_config.text_color"), (btn) -> {}).bounds(x, y, w, h).build()); 
        y += h + 2;
        
        int textColor = parseColor(Config.HUD_TEXT_COLOR.get(), 0xFFFFFFFF);
        // We need to guess hue from RGB. Or just default to 0 if white.
        float[] hsb = java.awt.Color.RGBtoHSB((textColor >> 16) & 0xFF, (textColor >> 8) & 0xFF, textColor & 0xFF, null);
        // If saturation is low, we assume it's white/gray mode (slider value < 0.1)
        double initialHueValue = (hsb[1] < 0.1) ? 0.05 : (0.1 + hsb[0] * 0.9);
        
        this.addRenderableWidget(new HueSlider(x, y, w, h, Component.translatable("gui.examplemod.hud_config.hue"), initialHueValue, (val) -> {
            int rgb;
            if (val < 0.1) {
                rgb = 0xFFFFFFFF; // White
            } else {
                float hue = (float)((val - 0.1) / 0.9);
                rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
            }
            String hex = String.format("#FF%06X", (0xFFFFFF & rgb));
            Config.HUD_TEXT_COLOR.set(hex);
        }));
        
        y += gap + 5;

        // Timer Color (Hue Slider)
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.hud_config.timer_color"), (btn) -> {}).bounds(x, y, w, h).build());
        y += h + 2;
        
        String timerColorStr = Config.HUD_TIMER_COLOR.get();
        boolean hasTimerColor = !timerColorStr.isEmpty();
        
        // Toggle Custom Color
        this.addRenderableWidget(Button.builder(hasTimerColor ? Component.translatable("gui.examplemod.hud_config.custom_color.on") : Component.translatable("gui.examplemod.hud_config.custom_color.off"), (btn) -> {
             if (hasTimerColor) {
                 Config.HUD_TIMER_COLOR.set(""); // Disable
             } else {
                 Config.HUD_TIMER_COLOR.set("#FFFFFFFF"); // Enable default white
             }
             this.rebuildWidgets();
        }).bounds(x, y, w, h).build());
        
        y += h + 2;
        
        if (hasTimerColor) {
            int timerColor = parseColor(timerColorStr, 0xFFFFFFFF);
            float[] timerHsb = java.awt.Color.RGBtoHSB((timerColor >> 16) & 0xFF, (timerColor >> 8) & 0xFF, timerColor & 0xFF, null);
            double timerInitialHue = (timerHsb[1] < 0.1) ? 0.05 : (0.1 + timerHsb[0] * 0.9);

            this.addRenderableWidget(new HueSlider(x, y, w, h, Component.translatable("gui.examplemod.hud_config.hue"), timerInitialHue, (val) -> {
                int rgb;
                if (val < 0.1) {
                    rgb = 0xFFFFFFFF; // White
                } else {
                    float hue = (float)((val - 0.1) / 0.9);
                    rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
                }
                String hex = String.format("#FF%06X", (0xFFFFFF & rgb));
                Config.HUD_TIMER_COLOR.set(hex);
            }));
        }

        // Done
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (btn) -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }
    
    // ... parseColor ...
    
    private int parseColor(String colorStr, int defaultColor) {
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
            // No percentage text needed, handled by preview
            this.setMessage(this.label);
        }

        @Override
        protected void applyValue() {
            this.onChange.accept(this.value);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            
            // Draw background (Spectrum)
            int startX = this.getX() + 4;
            int endX = this.getX() + this.getWidth() - 4;
            int innerWidth = endX - startX;
            int height = this.getHeight();
            
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + height, 0xFF000000);
            guiGraphics.renderOutline(this.getX(), this.getY(), this.getWidth(), height, 0xFFFFFFFF);
            
            // Draw White section (first 10%)
            int whiteWidth = (int)(innerWidth * 0.1);
            guiGraphics.fill(startX, this.getY() + 2, startX + whiteWidth, this.getY() + height - 2, 0xFFFFFFFF);
            
            // Draw Rainbow section
            int rainbowStart = startX + whiteWidth;
            int rainbowWidth = innerWidth - whiteWidth;
            
            for (int i = 0; i < rainbowWidth; i++) {
                float hue = (float)i / (float)rainbowWidth;
                int color = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
                guiGraphics.fill(rainbowStart + i, this.getY() + 2, rainbowStart + i + 1, this.getY() + height - 2, color | 0xFF000000);
            }
            
            // Draw handle
            int handleX = startX + (int)(this.value * innerWidth);
            guiGraphics.fill(handleX - 2, this.getY(), handleX + 2, this.getY() + height, 0xFF888888);
            guiGraphics.renderOutline(handleX - 2, this.getY(), 4, height, 0xFFFFFFFF);
            
            // Label
            guiGraphics.drawCenteredString(minecraft.font, this.getMessage(), this.getX() + this.getWidth() / 2, this.getY() + (this.getHeight() - 8) / 2, 0xFFFFFFFF);
        }
    }

    private void adjustDouble(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue config, double delta) {
        double current = config.get();
        double next = Math.round((current + delta) * 10.0) / 10.0;
        if (next < 0.5) next = 0.5;
        if (next > 5.0) next = 5.0;
        config.set(next);
        this.rebuildWidgets(); // Refresh labels
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        
        // Preview Label
        guiGraphics.drawString(this.font, Component.translatable("gui.examplemod.hud_config.preview"), this.width / 2 + 20, 40, 0xFFFFFF);
        
        // Render HUD Preview
        // Use SpeedrunState.renderPreviewHud
        // We can pass the full width and use margin to position it relative to the right side of the screen
        // Or we can simulate a smaller screen area?
        // Let's just use the current screen size.
        
        SpeedrunState.renderPreviewHud(guiGraphics, this.width, 10);
    }
}
