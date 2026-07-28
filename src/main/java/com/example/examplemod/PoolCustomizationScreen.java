package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PoolCustomizationScreen extends Screen {
    private final Screen parent;
    private final boolean enableItems;
    private final boolean enableBlocks;
    private final boolean enableAdvancements;
    private ObjectiveList objectiveList;
    private EditBox searchBox;
    private List<String> currentBlacklist;
    private List<Objective> allObjectives;
    
    // Mouse tracking for nested widgets
    public int currentMouseX;
    public int currentMouseY;
    
    // Filters
    private Objective.Type currentTypeFilter = null; // null = ALL
    private DimensionFilter currentDimensionFilter = DimensionFilter.ALL;

    private enum DimensionFilter {
        ALL, OVERWORLD, NETHER, END
    }

    public PoolCustomizationScreen(Screen parent, boolean enableItems, boolean enableBlocks, boolean enableAdvancements) {
        super(Component.translatable("gui.examplemod.pool_config.customize.title"));
        this.parent = parent;
        this.enableItems = enableItems;
        this.enableBlocks = enableBlocks;
        this.enableAdvancements = enableAdvancements;
        this.currentBlacklist = new ArrayList<>(Config.BLACKLIST.get());
        this.allObjectives = ObjectivePoolHelper.getAllCandidates(true, enableItems, enableBlocks, enableAdvancements);
    }

    @Override
    protected void init() {
        int midX = this.width / 2;
        
        // Search Box
        this.searchBox = new EditBox(this.font, midX - 100, 22, 200, 20, Component.translatable("gui.examplemod.pool_config.search"));
        this.searchBox.setResponder(this::updateFilter);
        this.addRenderableWidget(this.searchBox);

        // Type Filter Button
        this.addRenderableWidget(Button.builder(getTypeFilterText(), (btn) -> {
            if (currentTypeFilter == null) currentTypeFilter = Objective.Type.ITEM;
            else if (currentTypeFilter == Objective.Type.ITEM) currentTypeFilter = Objective.Type.BLOCK;
            else if (currentTypeFilter == Objective.Type.BLOCK) currentTypeFilter = Objective.Type.ADVANCEMENT;
            else currentTypeFilter = null;
            btn.setMessage(getTypeFilterText());
            this.updateFilter(this.searchBox.getValue());
        }).bounds(midX - 155, 45, 100, 20).build());

        // Dimension Filter Button
        this.addRenderableWidget(Button.builder(getDimensionFilterText(), (btn) -> {
            int nextOrd = (currentDimensionFilter.ordinal() + 1) % DimensionFilter.values().length;
            currentDimensionFilter = DimensionFilter.values()[nextOrd];
            btn.setMessage(getDimensionFilterText());
            this.updateFilter(this.searchBox.getValue());
        }).bounds(midX + 55, 45, 100, 20).build());

        // Enable All Visible Button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.enable_all"), (btn) -> {
            setAllVisible(true);
        }).bounds(midX - 52, 45, 50, 20).tooltip(Tooltip.create(Component.translatable("gui.examplemod.pool_config.enable_all"))).build());

        // Disable All Visible Button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.disable_all"), (btn) -> {
            setAllVisible(false);
        }).bounds(midX + 2, 45, 50, 20).tooltip(Tooltip.create(Component.translatable("gui.examplemod.pool_config.disable_all"))).build());

        // List
        this.objectiveList = new ObjectiveList(this.minecraft, this.width, this.height - 105, 70, 40);
        this.addRenderableWidget(this.objectiveList);
        this.updateFilter(this.searchBox.getValue());

        // Save Button
        this.addRenderableWidget(Button.builder(Component.translatable("gui.examplemod.pool_config.save_quit"), (btn) -> {
            Config.BLACKLIST.set(this.currentBlacklist);
            Config.SPEC.save();
            this.onClose();
        }).bounds(midX - 100, this.height - 25, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;
        super.render(guiGraphics, mouseX, mouseY, partialTick); // Handles background and children
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        
        if (this.objectiveList.children().isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.examplemod.pool_config.no_objectives"), this.width / 2, this.height / 2, 0xFFAAAAAA);
        }
    }

    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (this.objectiveList == null) return false;

        // Don't intercept clicks below the list viewport (where Save button lives)
        if (mouseY >= objectiveList.getViewportBottom()) {
            return false;
        }

        // Manual hit test for widgets in the list
        {
            int listTop = objectiveList.getViewportTop();
            int listBottom = objectiveList.getViewportBottom();
            int rowWidth = objectiveList.getRowWidth();
            int cols = 3;
            int gap = 10;
            int totalGapWidth = gap * (cols - 1);
            int itemWidth = (rowWidth - totalGapWidth) / cols;
            int itemHeight = 40;
            int rowLeft = objectiveList.getRowLeft();

            for (ObjectiveList.ObjectiveRowEntry entry : this.objectiveList.children()) {
                int index = objectiveList.children().indexOf(entry);
                if (index < 0) continue;
                int rowTop = objectiveList.getRowTop(index);
                int rowBottom = rowTop + itemHeight;
                if (rowBottom <= listTop || rowTop >= listBottom) continue;

                for (int i = 0; i < entry.widgets.size(); i++) {
                    ObjectiveWidget widget = entry.widgets.get(i);
                    int itemX = rowLeft + i * (itemWidth + gap);
                    widget.setX(itemX);
                    widget.setY(rowTop);
                    widget.setWidth(itemWidth);
                    widget.setHeight(itemHeight);

                    if (widget.isMouseOver(mouseX, mouseY)) {
                        widget.playDownSound(Minecraft.getInstance().getSoundManager());
                        String id = widget.objective.getId();
                        if (currentBlacklist.contains(id)) {
                            currentBlacklist.remove(id);
                        } else {
                            currentBlacklist.add(id);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        if (this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }

    private Component getTypeFilterText() {
        Component typeText;
        if (currentTypeFilter == null) typeText = Component.translatable("gui.examplemod.pool_config.filter.type.all");
        else if (currentTypeFilter == Objective.Type.ITEM) typeText = Component.translatable("gui.examplemod.pool_config.filter.type.items");
        else if (currentTypeFilter == Objective.Type.BLOCK) typeText = Component.translatable("gui.examplemod.pool_config.filter.type.blocks");
        else if (currentTypeFilter == Objective.Type.ADVANCEMENT) typeText = Component.translatable("gui.examplemod.pool_config.filter.type.advancements");
        else typeText = Component.translatable("gui.examplemod.unknown");

        return Component.translatable("gui.examplemod.pool_config.filter.type", typeText);
    }

    private Component getDimensionFilterText() {
        Component dimText;
        switch (currentDimensionFilter) {
            case ALL: dimText = Component.translatable("gui.examplemod.pool_config.filter.dimension.all"); break;
            case OVERWORLD: dimText = Component.translatable("gui.examplemod.pool_config.filter.dimension.overworld"); break;
            case NETHER: dimText = Component.translatable("gui.examplemod.pool_config.filter.dimension.nether"); break;
            case END: dimText = Component.translatable("gui.examplemod.pool_config.filter.dimension.end"); break;
            default: dimText = Component.translatable("gui.examplemod.unknown");
        }
        return Component.translatable("gui.examplemod.pool_config.filter.dimension", dimText);
    }

    private void updateFilter(String filter) {
        String lowerFilter = filter.toLowerCase();
        List<Objective> filtered = this.allObjectives.stream()
            .filter(obj -> {
                // Type Filter
                if (currentTypeFilter != null && obj.getType() != currentTypeFilter) return false;
                
                // Text Filter
                if (!lowerFilter.isEmpty() && !obj.getDisplayName().getString().toLowerCase().contains(lowerFilter) 
                    && !obj.getId().toLowerCase().contains(lowerFilter)) return false;
                
                // Dimension Filter
                if (currentDimensionFilter != DimensionFilter.ALL) {
                    if (!matchesDimension(obj, currentDimensionFilter)) return false;
                }
                
                return true;
            })
            .collect(Collectors.toList());
        
        if (this.objectiveList != null) {
            this.objectiveList.updateEntries(filtered);
        }
    }

    private void setAllVisible(boolean visible) {
        if (this.objectiveList == null) return;
        
        for (ObjectiveList.ObjectiveRowEntry entry : this.objectiveList.children()) {
            for (ObjectiveWidget widget : entry.widgets) {
                String id = widget.objective.getId();
                if (visible) {
                    currentBlacklist.remove(id);
                } else {
                    if (!currentBlacklist.contains(id)) {
                        currentBlacklist.add(id);
                    }
                }
            }
        }
    }

    private boolean matchesDimension(Objective obj, DimensionFilter dim) {
        String id = obj.getId().toLowerCase();
        
        // Advancements often have specific prefixes
        boolean isAdvancement = obj.getType() == Objective.Type.ADVANCEMENT;
        if (isAdvancement) {
            if (id.startsWith("minecraft:nether/") || id.contains("nether")) return dim == DimensionFilter.NETHER;
            if (id.startsWith("minecraft:end/") || id.contains("end")) return dim == DimensionFilter.END;
            // Most other advancements are Overworld or general
            if (dim == DimensionFilter.OVERWORLD) return !id.contains("nether") && !id.contains("end");
        }
        
        boolean isNether = id.contains("nether") || id.contains("soul") || id.contains("crimson") || id.contains("warped") || 
                           id.contains("piglin") || id.contains("hoglin") || id.contains("blaze") || id.contains("ghast") || 
                           id.contains("wither") || id.contains("quartz") || id.contains("glowstone") || id.contains("basalt") || 
                           id.contains("blackstone") || id.contains("debris") || id.contains("fungus") || id.contains("shroomlight") || id.contains("magma") ||
                           id.contains("strider") || id.contains("fortress") || id.contains("bastion");
                           
        boolean isEnd = id.contains("end") || id.contains("purpur") || id.contains("chorus") || id.contains("shulker") || 
                        id.contains("elytra") || id.contains("dragon") || id.contains("city") || id.contains("levitate");

        switch (dim) {
            case NETHER: return isNether;
            case END: return isEnd;
            case OVERWORLD: return !isNether && !isEnd;
            default: return true;
        }
    }

    // --- Inner Classes ---

    class ObjectiveList extends ContainerObjectSelectionList<ObjectiveList.ObjectiveRowEntry> {
        private final int viewportTop;
        private final int viewportBottom;

        public ObjectiveList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
            this.viewportTop = top;
            this.viewportBottom = top + height;
        }

        public int getViewportTop() {
            return viewportTop;
        }

        public int getViewportBottom() {
            return viewportBottom;
        }

        @Override
        public int getRowWidth() {
            return Math.min(600, this.width - 50);
        }

        public void updateEntries(List<Objective> objectives) {
            this.clearEntries();
            
            int cols = 3;
            List<ObjectiveWidget> currentRowWidgets = new ArrayList<>();
            
            for (Objective obj : objectives) {
                currentRowWidgets.add(new ObjectiveWidget(obj));
                
                if (currentRowWidgets.size() == cols) {
                    this.addEntry(new ObjectiveRowEntry(new ArrayList<>(currentRowWidgets)));
                    currentRowWidgets.clear();
                }
            }
            
            if (!currentRowWidgets.isEmpty()) {
                this.addEntry(new ObjectiveRowEntry(new ArrayList<>(currentRowWidgets)));
            }
        }

        class ObjectiveRowEntry extends ContainerObjectSelectionList.Entry<ObjectiveRowEntry> {
            public final List<ObjectiveWidget> widgets;

            public ObjectiveRowEntry(List<ObjectiveWidget> widgets) {
                this.widgets = widgets;
            }
            
            @Override
            public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
                int index = ObjectiveList.this.children().indexOf(this);
                if (index < 0) return;
                
                int rowTop = ObjectiveList.this.getRowTop(index);
                int rowLeft = ObjectiveList.this.getRowLeft();
                int rowWidth = ObjectiveList.this.getRowWidth();
                
                int cols = 3;
                int gap = 10;
                int totalGapWidth = gap * (cols - 1);
                int itemWidth = (rowWidth - totalGapWidth) / cols;
                int itemHeight = 40;

                for (int i = 0; i < widgets.size(); i++) {
                    ObjectiveWidget widget = widgets.get(i);
                    int itemX = rowLeft + i * (itemWidth + gap);
                    int itemY = rowTop;
                    
                    widget.setX(itemX);
                    widget.setY(itemY);
                    widget.setWidth(itemWidth);
                    widget.setHeight(itemHeight);
                    
                    widget.render(guiGraphics, mouseX, mouseY, partialTick);
                }
            }
            
            @Override
            public List<? extends GuiEventListener> children() {
                return this.widgets;
            }
            
            @Override
            public List<? extends NarratableEntry> narratables() {
                return widgets;
            }
        }
    }

    class ObjectiveWidget extends AbstractWidget {
        final Objective objective;

        public ObjectiveWidget(Objective objective) {
            super(0, 0, 0, 0, Component.empty());
            this.objective = objective;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean isBlacklisted = currentBlacklist.contains(objective.getId());
            boolean hovered = this.isHovered();
            
            int borderColor = isBlacklisted ? 0xFFFF0000 : 0xFF00FF00;
            int bgColor = isBlacklisted ? 0xAA550000 : 0xAA000000;
            
            if (hovered) {
                bgColor = isBlacklisted ? 0xCC772222 : 0xCC222222;
                borderColor = isBlacklisted ? 0xFFFF5555 : 0xFF55FF55;
            }

            // Background
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            
            // Border
            guiGraphics.renderOutline(getX(), getY(), width, height, borderColor);

            // Icon
            guiGraphics.renderItem(objective.getIcon(), getX() + 5, getY() + (height - 16) / 2);

            // Text
            Component name = objective.getDisplayName();
            int textX = getX() + 25;
            int maxTextWidth = width - 30;
            
            // Text Wrapping Logic
            List<FormattedCharSequence> lines = font.split(name, maxTextWidth);
            int totalTextHeight = lines.size() * font.lineHeight;
            int startY = getY() + (height - totalTextHeight) / 2;

            for (int i = 0; i < lines.size(); i++) {
                guiGraphics.drawString(font, lines.get(i), textX, startY + (i * font.lineHeight), 0xFFFFFFFF, false);
            }

            // Tooltip (Name + Description)
            if (hovered) {
                if (objective.getDescription() != null && !objective.getDescription().getString().isEmpty()) {
                     this.setTooltip(Tooltip.create(Component.empty().append(name).append(Component.translatable("gui.examplemod.tooltip_newline")).append(objective.getDescription())));
                } else {
                     this.setTooltip(Tooltip.create(name));
                }
            } else {
                this.setTooltip(null);
            }

            // Status Indicator (disabled)
            if (isBlacklisted) {
                guiGraphics.drawCenteredString(font, Component.translatable("gui.examplemod.disabled_indicator").getString(), getX() + width - 10, getY() + 5, 0xFFFF0000);
            }
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false; // Handled by RowEntry
        }
    }
}
