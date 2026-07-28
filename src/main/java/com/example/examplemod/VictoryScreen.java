package com.example.examplemod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;

public class VictoryScreen extends Screen {
    public VictoryScreen() {
        super(Component.translatable("gui.examplemod.victory"));
    }

    @Override
    protected void init() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24;
        int startY = this.height - 110; // Moved up to fit 4th button

        // Rejouer (Même Objectif)
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.play_again"), (btn) -> {
            SpeedrunState.saveRunInfo(true);
            SpeedrunState.beginRetryAndDisconnect();
        }).bounds(this.width / 2 - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        // Nouvelle Run (Nouveaux Objectifs)
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.new_run"), (btn) -> {
            SpeedrunState.saveRunInfo(true);
            SpeedrunState.beginNewRunAndDisconnect();
        }).bounds(this.width / 2 - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight).build());

        // Menu Principal
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.main_menu"), (btn) -> {
            SpeedrunState.saveRunInfo(true);
            SpeedrunState.beginGiveUpAndDisconnect();
        }).bounds(this.width / 2 - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight).build());

        // Rester en Jeu (Fermer Menu)
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.stay_in_game"), (btn) -> {
            this.onClose();
        }).bounds(this.width / 2 - buttonWidth / 2, startY + spacing * 3, buttonWidth, buttonHeight).build());
    }


    
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        if (keyEvent.key() == 256) { // ESCAPE
            this.onClose();
            return true;
        }
        return false;
    }
    
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1. Background
        this.renderTransparentBackground(g);
        g.fill(0, 0, this.width, this.height, 0xAA000000); // Darken background
        
        // 2. Render Buttons (via super)
        super.render(g, mouseX, mouseY, partialTick);
        
        // 3. Render Victory Content
        int centerX = this.width / 2;
        int currentY = 20;

        // Title
        g.drawCenteredString(this.font, Component.translatable("gui.examplemod.victory_title").withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.GOLD), centerX, currentY, 0xFFD700);
        currentY += 25;

        // Retrieve Objective Data
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

        // Render BIG Item Icon (Scale 4.0 = 64x64)
        if (!icon.isEmpty()) {
            float scale = 4.0f;
            // Manual Pose Stack (No push/pop available)
            g.pose().translate((float)centerX, (float)(currentY + 32));
            g.pose().scale(scale, scale);
            g.pose().translate(-8.0f, -8.0f);
            
            g.renderItem(icon, 0, 0);
            
            // Reverse
            g.pose().translate(8.0f, 8.0f);
            g.pose().scale(1.0f/scale, 1.0f/scale);
            g.pose().translate(-(float)centerX, -(float)(currentY + 32));
            
            currentY += 70; // 64 + padding
        } else {
            currentY += 10;
        }

        // Render Objective Name (WHITE)
        g.drawCenteredString(this.font, objNameComp, centerX, currentY, 0xFFFFFFFF);
        currentY += 15;

        // Render Time (GREEN)
        String time = SpeedrunRoulette.pendingVictoryTime;
        if (time == null) time = "--:--";
        
        float timeScale = 2.0f;
        g.pose().translate((float)centerX, (float)(currentY + 5));
        g.pose().scale(timeScale, timeScale);
        g.drawCenteredString(this.font, time, 0, 0, 0xFF55FF55);
        g.pose().scale(1.0f/timeScale, 1.0f/timeScale);
        g.pose().translate(-(float)centerX, -(float)(currentY + 5));
        
        currentY += 30;

        // Render Stats (Morts, Distance, Jours)
        g.drawCenteredString(this.font, Component.translatable("gui.examplemod.statistics").withStyle(net.minecraft.ChatFormatting.UNDERLINE), centerX, currentY, 0xFFAAAAAA);
        currentY += 15;
        
        Component deathStr = Component.translatable("gui.examplemod.deaths", SpeedrunState.getDeathCount());
        Component distStr = Component.translatable("gui.examplemod.distance", (int)SpeedrunState.getTraveledMeters());
        Component daysStr = Component.translatable("gui.examplemod.days", SpeedrunState.getDaysPlayed());
        
        g.drawCenteredString(this.font, deathStr, centerX, currentY, 0xFFFFFFFF);
        currentY += 12;
        g.drawCenteredString(this.font, distStr, centerX, currentY, 0xFFFFFFFF);
        currentY += 12;
        g.drawCenteredString(this.font, daysStr, centerX, currentY, 0xFFFFFFFF);
        
        // Render Splits (Optional/Secondary)
        currentY += 20;
        java.util.Map<String, String> splits = SpeedrunState.getSplits();
        if (!splits.isEmpty()) {
             g.drawCenteredString(this.font, Component.translatable("gui.examplemod.splits").withStyle(net.minecraft.ChatFormatting.UNDERLINE), centerX, currentY, 0xFFAAAAAA);
             currentY += 15;
             for (java.util.Map.Entry<String, String> entry : splits.entrySet()) {
                 g.drawCenteredString(this.font, entry.getKey() + ": " + entry.getValue(), centerX, currentY, 0xFFDDDDDD);
                 currentY += 12;
             }
        }
    }
    
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        // Do nothing, we handle background in render()
    }
}
