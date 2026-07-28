package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class SpeedrunConfigScreen extends Screen {
    private final Screen parent;
    private static final String[] LANGUAGES = {"", "en_us", "de_de", "es_es", "fr_fr", "it_it", "pt_br", "ru_ru", "zh_cn"};
    private Button objectiveCountLabel;
    private Button autoOpenLabel;
    private Button autoStartLabel;
    private Button gameModeLabel;
    private Button languageLabel;

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

        // Auto Open Wheel toggle
        autoOpenLabel = this.addRenderableWidget(Button.builder(
            Component.translatable("gui.examplemod.config.auto_open", (Config.AUTO_OPEN_WHEEL.get() ? Component.translatable("gui.examplemod.on") : Component.translatable("gui.examplemod.off"))),
            (btn) -> {
                Config.AUTO_OPEN_WHEEL.set(!Config.AUTO_OPEN_WHEEL.get());
                btn.setMessage(Component.translatable("gui.examplemod.config.auto_open", (Config.AUTO_OPEN_WHEEL.get() ? Component.translatable("gui.examplemod.on") : Component.translatable("gui.examplemod.off"))));
            }
        ).bounds(x, y, w, h).build());

        y += gap;
        // Auto Start Timer toggle
        autoStartLabel = this.addRenderableWidget(Button.builder(
            Component.translatable("gui.examplemod.config.auto_start", (Config.AUTO_START.get() ? Component.translatable("gui.examplemod.on") : Component.translatable("gui.examplemod.off"))),
            (btn) -> {
                Config.AUTO_START.set(!Config.AUTO_START.get());
                btn.setMessage(Component.translatable("gui.examplemod.config.auto_start", (Config.AUTO_START.get() ? Component.translatable("gui.examplemod.on") : Component.translatable("gui.examplemod.off"))));
            }
        ).bounds(x, y, w, h).build());

        y += gap;
        // Multiplayer game mode: Cooperative (default) / Challenge (VS)
        gameModeLabel = this.addRenderableWidget(Button.builder(
            Component.translatable("gui.examplemod.config.game_mode", Config.getGameMode().displayName()),
            (btn) -> {
                SpeedrunGameMode next = Config.getGameMode().next();
                Config.setGameMode(next);
                btn.setMessage(Component.translatable("gui.examplemod.config.game_mode", next.displayName()));
                // If currently in a world, push mode to server so multiplayer stays in sync
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.level != null) {
                    SpeedrunState.setActiveGameMode(next);
                    net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
                    if (server != null) {
                        SpeedrunWorldData data = SpeedrunWorldData.get(server);
                        data.setGameMode(next);
                        SpeedrunNetwork.broadcastRunState(server);
                    } else {
                        SpeedrunNetwork.sendToServer(new SpeedrunNetwork.SetGameModePacket(next));
                    }
                }
            }
        ).bounds(x, y, w, h).build());

        y += gap;
        // Objective Count with selector arrows
        this.addRenderableWidget(Button.builder(
            Component.literal("-"),
            (btn) -> {
                int current = Config.OBJECTIVE_COUNT.get();
                if (current > 1) {
                    Config.OBJECTIVE_COUNT.set(current - 1);
                    updateCountLabel();
                }
            }
        ).bounds(x, y, 20, h).build());

        objectiveCountLabel = this.addRenderableWidget(Button.builder(
            Component.translatable("gui.examplemod.config.objective_count", Config.OBJECTIVE_COUNT.get()),
            (btn) -> {
                int current = Config.OBJECTIVE_COUNT.get();
                int next = current + 1;
                if (next > 10) next = 1;
                Config.OBJECTIVE_COUNT.set(next);
                btn.setMessage(Component.translatable("gui.examplemod.config.objective_count", next));
            }
        ).bounds(x + 25, y, w - 50, h).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("+"),
            (btn) -> {
                int current = Config.OBJECTIVE_COUNT.get();
                if (current < 10) {
                    Config.OBJECTIVE_COUNT.set(current + 1);
                    updateCountLabel();
                }
            }
        ).bounds(x + w - 20, y, 20, h).build());

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

        y += gap;
        // Language selector with arrows
        this.addRenderableWidget(Button.builder(
            Component.literal("<"),
            (btn) -> {
                cycleLanguage(-1);
            }
        ).bounds(x, y, 20, h).build());

        languageLabel = this.addRenderableWidget(Button.builder(
            Component.translatable("gui.examplemod.config.language", getLanguageDisplayName()),
            (btn) -> {
                cycleLanguage(1);
            }
        ).bounds(x + 25, y, w - 50, h).build());

        this.addRenderableWidget(Button.builder(
            Component.literal(">"),
            (btn) -> {
                cycleLanguage(1);
            }
        ).bounds(x + w - 20, y, 20, h).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (btn) -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }

    private void updateCountLabel() {
        if (objectiveCountLabel != null) {
            objectiveCountLabel.setMessage(Component.translatable("gui.examplemod.config.objective_count", Config.OBJECTIVE_COUNT.get()));
        }
    }

    private void cycleLanguage(int direction) {
        String current = Config.FORCED_LANGUAGE.get();
        int idx = 0;
        for (int i = 0; i < LANGUAGES.length; i++) {
            if (LANGUAGES[i].equals(current)) { idx = i; break; }
        }
        int next = (idx + direction + LANGUAGES.length) % LANGUAGES.length;
        Config.FORCED_LANGUAGE.set(LANGUAGES[next]);
        if (languageLabel != null) {
            languageLabel.setMessage(Component.translatable("gui.examplemod.config.language", getLanguageDisplayName()));
        }
        applyForcedLanguage();
    }

    private String getLanguageDisplayName() {
        String lang = Config.FORCED_LANGUAGE.get();
        if (lang == null || lang.isEmpty()) return Component.translatable("gui.examplemod.config.language_default").getString();
        return lang.toUpperCase();
    }

    public static void applyForcedLanguage() {
        Minecraft mc = Minecraft.getInstance();
        String forced = Config.FORCED_LANGUAGE.get();
        if (forced != null && !forced.isEmpty()) {
            mc.getLanguageManager().setSelected(forced);
            mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
