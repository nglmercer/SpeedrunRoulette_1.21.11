package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WheelScreen extends Screen {
    private enum State { SPINNING, STOPPING, SHOWING_RESULT }
    
    private State state = State.SPINNING;
    private final List<WheelColumn> columns = new ArrayList<>();
    private final List<Objective> winners = new ArrayList<>();
    private Button confirmButton;
    private int spinTicks = 0;
    
    private static final int ITEM_HEIGHT = 60;

    public WheelScreen() {
        super(Component.translatable("gui.examplemod.spin_the_wheel"));
        
        int count = Config.OBJECTIVE_COUNT.get();
        List<Objective> selectedObjectives = ObjectivePoolHelper.getRandomObjectives(count);
        
        if (selectedObjectives.isEmpty()) {
            this.onClose();
            return;
        }
        
        this.winners.addAll(selectedObjectives);
        
        // Create a column for each objective
        for (int i = 0; i < count; i++) {
            columns.add(new WheelColumn(selectedObjectives.get(i), i));
        }
    }

    private void confirmSelection() {
        SpeedrunState.setActiveGameMode(Config.getGameMode());
        SpeedrunState.setObjectives(new ArrayList<>(winners), false);
        SpeedrunTimer.reset();
        this.onClose();
        Minecraft.getInstance().setScreen(new CountdownScreen(() -> {
            SpeedrunState.saveObjectivesToWorld();
            SpeedrunTimer.start();
        }));
    }

    @Override
    protected void init() {
        int w = 200;
        int h = 20;
        int x = this.width / 2 - w / 2;
        int y = this.height - 40;

        this.confirmButton = Button.builder(Component.translatable("gui.examplemod.lets_go"), (button) -> {
            confirmSelection();
        }).bounds(x, y, w, h).build();
        
        this.confirmButton.visible = false;
        this.addRenderableWidget(this.confirmButton);
    }

    @Override
    public void tick() {
        super.tick();
        spinTicks++;
        
        boolean allStopped = true;
        
        if (state == State.SPINNING) {
            // Check if we should start stopping
            if (spinTicks > 60) {
                state = State.STOPPING;
                // Initialize stopping for all columns
                for (WheelColumn col : columns) {
                    col.startStopping();
                }
            } else {
                allStopped = false;
                for (WheelColumn col : columns) {
                    col.tickSpinning();
                }
            }
        } else if (state == State.STOPPING) {
            for (WheelColumn col : columns) {
                col.tickStopping();
                if (!col.isStopped) allStopped = false;
            }
            
            if (allStopped) {
                state = State.SHOWING_RESULT;
                this.confirmButton.visible = true;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Remove default background to allow custom transparency
        // this.renderTransparentBackground(guiGraphics);
        
        // Draw full screen dark background for immersion (translucent)
        // 0xAA000000 is about 66% opacity.
        guiGraphics.fill(0, 0, this.width, this.height, 0xAA000000);
        
        if (state == State.SHOWING_RESULT) {
            renderResults(guiGraphics, mouseX, mouseY, partialTick);
        } else {
            renderColumns(guiGraphics, partialTick);
        }
        
        // Title
        if (state != State.SHOWING_RESULT) {
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        }
    }
    
    private void renderColumns(GuiGraphics guiGraphics, float partialTick) {
        int numCols = columns.size();
        // Calculate column width dynamically, max 150
        int colWidth = Math.min(150, (this.width - 40) / numCols);
        int totalWidth = colWidth * numCols;
        int startX = (this.width - totalWidth) / 2;
        
        int centerY = this.height / 2;
        int boxY = centerY - ITEM_HEIGHT / 2;
        
        // Highlight the selection area
        guiGraphics.fill(0, boxY, this.width, boxY + ITEM_HEIGHT, 0x33FFFFFF);
        
        // Scissor for all columns
        guiGraphics.enableScissor(startX, 0, startX + totalWidth, this.height);
        
        for (int i = 0; i < numCols; i++) {
            WheelColumn col = columns.get(i);
            int colX = startX + i * colWidth;
            
            // Draw column border
            guiGraphics.renderOutline(colX, boxY, colWidth, ITEM_HEIGHT, 0xFFFFFF00);
            
            col.render(guiGraphics, colX, colWidth, centerY, partialTick, this.font);
        }
        
        guiGraphics.disableScissor();
    }
    
    private void renderResults(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No extra background fill here, using the one from render()
        
        int count = winners.size();
        
        // --- ADAPTIVE LAYOUT LOGIC ---
        // Calculate scaling based on item count.
        // Less items = Bigger icons. More items = Smaller icons.
        
        int baseItemSize = 48; 
        float scaleFactor = 1.0f;
        
        if (count <= 1) {
             scaleFactor = 2.5f; // Huge for single item
        } else if (count <= 5) {
             scaleFactor = 1.5f; // Large for few items
        } else if (count <= 10) {
             scaleFactor = 1.0f; // Normal
        } else {
             scaleFactor = 0.8f; // Compact for many
        }
        
        int itemSize = (int)(baseItemSize * scaleFactor);
        int spacing = (int)(10 * scaleFactor);
        int fontSize = (int)(8 * scaleFactor); // Approx font height
        
        // Calculate maxPerRow dynamically based on screen width and item size
        // We want to center them nicely.
        int availableWidth = this.width - 40; // Padding
        int maxPerRow = Math.max(1, availableWidth / (itemSize + spacing));
        
        // If we have few items, try to keep them in one row if possible, or limit row width
        if (count <= 5) maxPerRow = count;
        else if (maxPerRow > 8) maxPerRow = 8; // Cap
        
        int rows = (int) Math.ceil((double)count / maxPerRow);
        
        int rowHeight = (int)(itemSize * 1.6); // Defined earlier now
        
        // Calculate total content height to center vertically
        int titleHeight = 30; // Title + spacing
        int gridHeight = rows * rowHeight;
        int buttonHeight = 30; // Button (20) + spacing (10)
        
        int totalContentHeight = titleHeight + gridHeight + buttonHeight;
        
        // Center Y position
        int startY = (this.height - totalContentHeight) / 2 + titleHeight;
        
        // Vertical compression check if still too tall
        if (totalContentHeight > this.height - 20) {
             // Try to shrink scale
             scaleFactor *= 0.8f;
             itemSize = (int)(baseItemSize * scaleFactor);
             rowHeight = (int)(itemSize * 1.6);
             
             // Recalculate
             gridHeight = rows * rowHeight;
             totalContentHeight = titleHeight + gridHeight + buttonHeight;
             startY = (this.height - totalContentHeight) / 2 + titleHeight;
             
             // If STILL too tall, clamp to top
             if (startY < 40) startY = 40;
        }
        
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.examplemod.selected_objectives"), this.width / 2, startY - 25, 0xFFFF55);
        
        Objective hoveredObjective = null;

        for (int r = 0; r < rows; r++) {
            int rowStartIndex = r * maxPerRow;
            int rowCount = Math.min(maxPerRow, count - rowStartIndex);
            
            int totalRowWidth = rowCount * itemSize + (rowCount - 1) * spacing;
            int startX = (this.width - totalRowWidth) / 2;
            int y = startY + r * rowHeight;
            
            for (int i = 0; i < rowCount; i++) {
                Objective obj = winners.get(rowStartIndex + i);
                int x = startX + i * (itemSize + spacing);
                
                // Draw Icon
                float cx = x + itemSize / 2.0f;
                float cy = y + itemSize / 2.0f; 
                
                // Base scale is 2.0 for 48px (16x2 = 32? No wait, 16x3=48)
                // Let's say itemSize 48 corresponds to scale 3.0 (16*3=48)
                float iconScale = (float)itemSize / 16.0f;
                
                guiGraphics.pose().translate(cx, cy);
                guiGraphics.pose().scale(iconScale, iconScale); 
                guiGraphics.renderItem(obj.getIcon(), -8, -8); // -8 centers 16x16
                guiGraphics.pose().scale(1.0f/iconScale, 1.0f/iconScale);
                guiGraphics.pose().translate(-cx, -cy);
                
                // Draw Name (Wrapped)
                Component name = obj.getDisplayName();
                int textY = y + itemSize + 5;
                int maxTextWidth = itemSize + spacing + 10; 
                
                // Text Scale
                // Normal scale 1.0 is huge for small icons.
                // We want text to be roughly proportional but legible.
                // Min scale 0.5, Max scale 1.2
                float textScale = Math.max(0.6f, Math.min(1.2f, scaleFactor * 0.6f));
                
                // Text Wrapping
                java.util.List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(name, (int)(maxTextWidth / textScale));
                int lineCount = Math.min(lines.size(), 2); // Max 2 lines
                
                for (int l = 0; l < lineCount; l++) {
                    net.minecraft.util.FormattedCharSequence line = lines.get(l);
                    int lineWidth = this.font.width(line);
                    
                    guiGraphics.pose().translate(cx, (float)(textY + l * (9 * textScale)));
                    guiGraphics.pose().scale(textScale, textScale);
                    guiGraphics.drawString(this.font, line, -(int)(lineWidth * textScale / 2), 0, 0xFFFFFFFF, false);
                    guiGraphics.pose().scale(1.0f/textScale, 1.0f/textScale);
                    guiGraphics.pose().translate(-cx, -(float)(textY + l * (9 * textScale)));
                }

                // Check for hover - only over the icon area to prevent overlap with adjacent rows
                if (mouseX >= x && mouseX <= x + itemSize && mouseY >= y && mouseY <= y + itemSize + 4) {
                    hoveredObjective = obj;
                }
            }
        }
        
        // Button - Position relative to grid
        this.confirmButton.setX((this.width - 200) / 2);
        this.confirmButton.setY(startY + rows * rowHeight + 10);
        this.confirmButton.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render Tooltip if hovered
        if (hoveredObjective != null) {
             List<Component> tooltip = new ArrayList<>();
             tooltip.add(hoveredObjective.getDisplayName().copy().withStyle(ChatFormatting.YELLOW));
             
             if (hoveredObjective.getDescription() != null && !hoveredObjective.getDescription().getString().isEmpty()) {
                 tooltip.add(hoveredObjective.getDescription());
             } else {
              // Default description based on type
              if (hoveredObjective.getType() == Objective.Type.ITEM || hoveredObjective.getType() == Objective.Type.BLOCK) {
                  tooltip.add(Component.translatable("gui.examplemod.obtain_item_tooltip"));
              }
             }
             
             renderCustomTooltip(guiGraphics, tooltip, mouseX, mouseY);
         }
     }
    
    private void renderCustomTooltip(GuiGraphics guiGraphics, List<Component> text, int x, int y) {
        if (text.isEmpty()) return;
        
        int maxWidth = 0;
        for (Component c : text) {
            int w = this.font.width(c);
            if (w > maxWidth) maxWidth = w;
        }
        
        int lineHeight = 10;
        int totalHeight = text.size() * lineHeight + 6;
        int totalWidth = maxWidth + 8;
        
        int bx = x + 10;
        int by = y - 5;
        
        // Adjust if off screen
        if (bx + totalWidth > this.width) bx -= (totalWidth + 20);
        if (by + totalHeight > this.height) by -= totalHeight;
        
        // Background
        guiGraphics.fill(bx, by, bx + totalWidth, by + totalHeight, 0xF0100010);
        guiGraphics.renderOutline(bx, by, totalWidth, totalHeight, 0x505000FF); 
        
        for (int i = 0; i < text.size(); i++) {
            guiGraphics.drawString(this.font, text.get(i), bx + 4, by + 4 + (i * lineHeight), 0xFFFFFFFF, false);
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        // Return true to pause game in singleplayer
        // But if user wants to close with Escape, we need to handle it.
        // Screen.shouldCloseOnEsc() default is true.
        // If we want to prevent closing while spinning, we override keyPressed.
        return false;
    }
    
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        int keyCode = keyEvent.key();
        if (keyCode == 256) { // ESCAPE
             if (state == State.SHOWING_RESULT) {
                 confirmSelection();
                 return true;
             }
             return true;
        }
        return false;
    }
    
    private class WheelColumn {
        List<Objective> scrollList = new ArrayList<>();
        Objective winner;
        double scrollPosition = 0;
        double scrollSpeed = 0;
        int targetIndex;
        double targetPosition;
        boolean isStopped = false;
        
        // Constants per column (can be slightly randomized for effect)
        double maxSpeed = 50.0;
        
        public WheelColumn(Objective winner, int colIndex) {
            this.winner = winner;
            
            // Build list
            List<Objective> fillers = ObjectivePoolHelper.getRandomObjectives(50);
            if (fillers.isEmpty()) fillers.add(winner);
            
            Random rand = new Random();
            int totalItemsBeforeWinner = 40 + rand.nextInt(20) + (colIndex * 5); // Stagger lengths slightly
            
            for (int i = 0; i < totalItemsBeforeWinner; i++) {
                scrollList.add(fillers.get(rand.nextInt(fillers.size())));
            }
            
            scrollList.add(winner);
            this.targetIndex = totalItemsBeforeWinner;
            
            for (int i = 0; i < 5; i++) {
                scrollList.add(fillers.get(rand.nextInt(fillers.size())));
            }
        }
        
        public void startStopping() {
            this.targetPosition = this.targetIndex * ITEM_HEIGHT;
        }
        
        public void tickSpinning() {
            if (scrollSpeed < maxSpeed) {
                scrollSpeed += 2.0;
            }
            double oldPos = scrollPosition;
            scrollPosition += scrollSpeed;
            
            // Sound effect while spinning
            int currentItemIndex = (int)(scrollPosition / ITEM_HEIGHT);
            int prevItemIndex = (int)(oldPos / ITEM_HEIGHT);
            if (currentItemIndex != prevItemIndex) {
                 Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 0.5F));
            }
        }
        
        public void tickStopping() {
            if (isStopped) return;
            
            double dist = targetPosition - scrollPosition;
            if (dist > 0) {
                double wantedSpeed = dist * 0.05;
                if (wantedSpeed > maxSpeed) wantedSpeed = maxSpeed;
                if (wantedSpeed < 0.5) wantedSpeed = 0.5;
                
                scrollSpeed = wantedSpeed;
                scrollPosition += scrollSpeed;
                
                if (dist < 1.0) {
                    scrollPosition = targetPosition;
                    isStopped = true;
                    // Click sound on stop
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
            } else {
                scrollPosition = targetPosition;
                isStopped = true;
            }
            
            // Sound effect while scrolling
            if (!isStopped) {
                 int currentItemIndex = (int)(scrollPosition / ITEM_HEIGHT);
                 int prevItemIndex = (int)((scrollPosition - scrollSpeed) / ITEM_HEIGHT);
                 if (currentItemIndex != prevItemIndex) {
                     // Quieter click
                     Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 0.5F));
                 }
            }
        }
        
        public void render(GuiGraphics guiGraphics, int x, int width, int centerY, float partialTick, net.minecraft.client.gui.Font font) {
            double smoothPos = scrollPosition + (isStopped ? 0 : scrollSpeed * partialTick);
            
            int firstIndex = (int)((smoothPos - centerY) / ITEM_HEIGHT) - 2;
            int lastIndex = (int)((smoothPos + centerY + ITEM_HEIGHT) / ITEM_HEIGHT) + 2;
            
            if (firstIndex < 0) firstIndex = 0;
            if (lastIndex >= scrollList.size()) lastIndex = scrollList.size() - 1;
            
            for (int i = firstIndex; i <= lastIndex; i++) {
                Objective obj = scrollList.get(i);
                
                double itemCenterY = centerY + (i * ITEM_HEIGHT) - smoothPos;
                int itemY = (int)(itemCenterY - ITEM_HEIGHT / 2);
                
                // Render Item
                int iconX = x + (width - 16) / 2;
                int iconY = itemY + (ITEM_HEIGHT - 32) / 2; // Offset for text space
                
                // Icon
                 float ix = iconX + 8;
                 float iy = iconY + 8;
                 guiGraphics.pose().translate(ix, iy);
                 guiGraphics.pose().scale(2.0f, 2.0f);
                 guiGraphics.renderItem(obj.getIcon(), -8, -8);
                 guiGraphics.pose().scale(0.5f, 0.5f);
                 guiGraphics.pose().translate(-ix, -iy);
                
                // Render Name (small)
                Component name = obj.getDisplayName();
                int textY = iconY + 20;
                // Truncate if too long
                String nameStr = font.plainSubstrByWidth(name.getString(), width - 4);
                int textX = x + (width - font.width(nameStr)) / 2;
                
                guiGraphics.drawString(font, nameStr, textX, textY, 0xFFFFFF, false);
            }
        }
    }
}
