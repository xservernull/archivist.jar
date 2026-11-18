package com.xservernull.archivist.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.xservernull.archivist.ArchivistConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * All of the mod's menus in one file
 */
@EventBusSubscriber(modid = "archivist", value = Dist.CLIENT)
public final class ArchivistMenus {
    private ArchivistMenus() {}

    static final int AMBER = 0xFFD4A84B;
    static final int TEXT = 0xFFB8AFA0;
    static final int TEXT_HOT = 0xFFFFE9B0;
    static final int TEXT_DIM = 0xFF7A6F60;
    static final int TEXT_OFF = 0xFF5A5650;

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (next == null) return;
        if (!ArchivistConfig.customMenu()) return;
        if (next.getClass() == TitleScreen.class) {
            event.setNewScreen(new Title());
        } else if (next.getClass() == PauseScreen.class) {
            event.setNewScreen(new Pause());
        }
    }

    @SubscribeEvent
    public static void onInitPost(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof Pause pause) pause.adopt(event);
        Screen screen = event.getScreen();
        boolean vanilla = screen.getClass() == TitleScreen.class || screen.getClass() == PauseScreen.class;
        if (vanilla && !ArchivistConfig.customMenu()) {
            vanillaBtnScreen = screen;
            vanillaBtn = new Btn(4, 4, 80, 20, Lang.text("archivist.menu.archivist|Archivist"),
                    b -> Minecraft.getInstance().setScreen(new Options(screen)));
        } else if (screen == vanillaBtnScreen) {
            vanillaBtn = null;
            vanillaBtnScreen = null;
        }
    }
    private static Btn vanillaBtn;
    private static Screen vanillaBtnScreen;

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (vanillaBtn == null || event.getScreen() != vanillaBtnScreen) return;
        vanillaBtn.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (vanillaBtn == null || event.getScreen() != vanillaBtnScreen) return;
        if (vanillaBtn.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (event.getScreen() == vanillaBtnScreen) {
            vanillaBtn = null;
            vanillaBtnScreen = null;
        }
    }

    // ---------------------------------------------------------------- shared look

    static int lerpColor(int a, int b, float t) {
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >> shift) & 0xFF;
            int cb = (b >> shift) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }

    static final ResourceLocation TILE_BG =
            ResourceLocation.fromNamespaceAndPath("archivist", "textures/gui/background.png");
    static void tiledBackground(GuiGraphics g, int w, int h) {
        g.setColor(1f, 1f, 1f, 1f);
        for (int x = 0; x < w; x += 16) {
            for (int y = 0; y < h; y += 16) {
                g.blit(TILE_BG, x, y, 0, 0, 16, 16, 16, 16);
            }
        }
        g.fillGradient(0, 0, w, h, 0xC0101010, 0xD0101010);
    }
    static void backdrop(GuiGraphics g, int w, int h, boolean inGame) {
        long t = Util.getMillis();
        if (inGame) {
            g.fillGradient(0, 0, w, h, 0xC00A0806, 0xE0030201);
        } else {
            g.fillGradient(0, 0, w, h, 0xFF0C0A08, 0xFF020202);
            int beam = 80;
            int by = (int) ((t / 40L) % (h + beam * 2L)) - beam;
            g.fillGradient(0, by, w, by + beam / 2, 0x00D4A84B, 0x16D4A84B);
            g.fillGradient(0, by + beam / 2, w, by + beam, 0x16D4A84B, 0x00D4A84B);
        }
        for (int y = 0; y < h; y += 3) {
            g.fill(0, y, w, y + 1, 0x16000000);
        }
        long cycle = t % 7000L;
        if (cycle < 120L) {
            int gy = (int) ((t / 7000L * 97L) % Math.max(1, h - 12));
            g.fill(0, gy, w, gy + 3 + (int) (cycle % 5L), 0x26D4A84B);
        }
        for (int i = 0; i < 24; i++) {
            int c = ((int) (0x78 * Math.pow(1.0 - i / 24.0, 2))) << 24;
            g.fill(i * 3, 0, i * 3 + 3, h, c);
            g.fill(w - i * 3 - 3, 0, w - i * 3, h, c);
            g.fill(0, i * 3, w, i * 3 + 3, c);
            g.fill(0, h - i * 3 - 3, w, h - i * 3, c);
        }
    }
    static final class Btn extends Button {
        private float hover;
        private long last = Util.getMillis();

        Btn(int x, int y, int w, int h, Component message, OnPress onPress) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            long now = Util.getMillis();
            float k = 1f - (float) Math.exp(-(now - last) / 70.0);
            last = now;
            boolean on = this.active && this.isHoveredOrFocused();
            hover += ((on ? 1f : 0f) - hover) * k;

            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            RenderSystem.enableBlend();
            g.fill(x, y, x + w, y + h, lerpColor(0x8C000000, 0xD0141008, hover));
            g.fill(x, y, x + Math.round(w * hover), y + h, 0x1CD4A84B);
            int border = this.active ? lerpColor(0x66604830, AMBER, hover) : 0x44303030;
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + h - 1, x + w, y + h, border);
            g.fill(x, y, x + 1, y + h, border);
            g.fill(x + w - 1, y, x + w, y + h, border);
            g.fill(x + 1, y + 1, x + 3, y + h - 1, this.active ? lerpColor(0x00D4A84B, AMBER, hover) : 0x00000000);

            var font = Minecraft.getInstance().font;
            int color = this.active ? lerpColor(TEXT, TEXT_HOT, hover) : TEXT_OFF;
            int ty = y + (h - 8) / 2;
            g.drawString(font, getMessage(), x + 10 + Math.round(hover * 4f), ty, color, false);
            if (hover > 0.05f) {
                int a = Math.round(hover * 255f) << 24;
                g.drawString(font, ">", x + w - 12, ty, (AMBER & 0x00FFFFFF) | a, false);
            }
        }
    }
    abstract static class Slide extends AbstractSliderButton {
        private final String label;

        Slide(int x, int y, int w, int h, String label, double value) {
            super(x, y, w, h, Component.empty(), Mth.clamp(value, 0.0, 1.0));
            this.label = label;
            updateMessage();
        }

        abstract String show(double v);

        /** the player moved the slider. */
        abstract void changed(double v);

        void set(double v) {
            this.value = Mth.clamp(v, 0.0, 1.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(this.label + ": " + show(this.value)));
        }

        @Override
        protected void applyValue() {
            changed(this.value);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean on = this.active && this.isHoveredOrFocused();
            RenderSystem.enableBlend();
            g.fill(x, y, x + w, y + h, 0x8C000000);
            g.fill(x, y, x + Math.round(w * (float) this.value), y + h, this.active ? 0x30D4A84B : 0x16D4A84B);
            int border = this.active ? lerpColor(0x66604830, AMBER, on ? 1f : 0f) : 0x44303030;
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + h - 1, x + w, y + h, border);
            g.fill(x, y, x + 1, y + h, border);
            g.fill(x + w - 1, y, x + w, y + h, border);
            int tx = x + Math.round((w - 3) * (float) this.value);
            g.fill(tx, y, tx + 3, y + h, this.active ? AMBER : 0x66604830);
            int color = !this.active ? TEXT_OFF : (on ? TEXT_HOT : TEXT);
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, color);
        }
    }

    // ---------------------------------------------------------------- title

    static final class Title extends TitleScreen {
        private static final String[] CRYPTIC = {
                "archivist.splash.1|you already know my name",
                "archivist.splash.2|why fear",
                "archivist.splash.3|still here",
                "archivist.splash.4|she had no face you gave her one",
                "archivist.splash.5|he waited",
                "archivist.splash.6|normalcy isn't a constant",
                "archivist.splash.7|find your faith",
                "archivist.splash.8|the door was always there",
                "archivist.splash.9|they always find a way"
        };

        private final List<Btn> buttons = new ArrayList<>();
        private String splash = null;
        private String splashLang = "";
        private final LogoRenderer vanillaLogo = new LogoRenderer(false);
        private long openedAt = 0L;

        @Override
        protected void init() {
            super.init();
            this.clearWidgets();
            this.buttons.clear();
            if (this.openedAt == 0L) this.openedAt = Util.getMillis();
            if (this.splash == null || !ArchivistConfig.language().equals(this.splashLang)) {
                this.splash = pickSplash();
                this.splashLang = ArchivistConfig.language();
            }

            int bw = 190, bh = 20, step = 24;
            int x = this.width / 2 - bw / 2;
            int y = Math.max(84, Math.min(this.height / 4 + 48, this.height - 5 * step - 24));
            add(new Btn(x, y, bw, bh, Lang.text("archivist.menu.singleplayer|Singleplayer"),
                    b -> this.minecraft.setScreen(new SelectWorldScreen(this))));
            add(new Btn(x, y + step, bw, bh, Lang.text("archivist.menu.multiplayer|Multiplayer"),
                    b -> this.minecraft.setScreen(new JoinMultiplayerScreen(this))));
            add(new Btn(x, y + step * 2, bw, bh, Lang.text("archivist.menu.options|Options..."),
                    b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))));
            add(new Btn(x, y + step * 3, bw, bh, Lang.text("archivist.menu.archivist|Archivist"),
                    b -> this.minecraft.setScreen(new Options(this))));
            add(new Btn(x, y + step * 4 + 8, bw, bh, Lang.text("archivist.menu.quit|Quit Game"),
                    b -> this.minecraft.stop()));
        }

        private Btn add(Btn b) {
            this.buttons.add(b);
            return this.addRenderableWidget(b);
        }

        private String pickSplash() {
            String mcName = this.minecraft.getUser().getName();
            String sysName = System.getProperty("user.name");
            int hour = LocalTime.now().getHour();
            java.util.Locale locale = ArchivistConfig.language().equals("mandanga")
                    ? java.util.Locale.ENGLISH : java.util.Locale.forLanguageTag(ArchivistConfig.language());
            String day = LocalDate.now().getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.FULL, locale).toUpperCase(locale);
            Random random = new Random();
            double crypticChance = hour < 6 ? 0.50 : 0.85;
            if (random.nextDouble() < crypticChance) return Lang.get(CRYPTIC[random.nextInt(CRYPTIC.length)]);
            if (hour < 6) return Lang.fmt("archivist.splash.midnight|up past midnight, %s?", mcName);
            if (hour < 12) return Lang.fmt("archivist.splash.morning|mornin, %s!", sysName);
            if (hour < 18) return Lang.fmt("archivist.splash.afternoon|good afternoon, %s!", mcName);
            if (hour < 21) return Lang.fmt("archivist.splash.evening|evenin, %s!", sysName);
            return Lang.fmt("archivist.splash.day|happy %s, %s!", day, mcName);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            long now = Util.getMillis();
            tiledBackground(g, this.width, this.height);
            this.vanillaLogo.renderLogo(g, this.width, 1.0F);
            drawSplash(g, now);
            for (Btn b : this.buttons) b.render(g, mouseX, mouseY, partialTick);
            g.drawString(this.font, Lang.get("archivist.menu.version|archivist.jar - first stable release."), 4, this.height - 12, 0xFF6A6258, false);

            float fade = Mth.clamp((now - this.openedAt) / 900f, 0f, 1f);
            if (fade < 1f) {
                g.fill(0, 0, this.width, this.height, Math.round((1f - fade) * 255f) << 24);
            }
        }

        private void drawSplash(GuiGraphics g, long now) {
            float pulse = 1.8f - Math.abs((float) Math.sin(now / 300.0)) * 0.1f;
            float scale = pulse * 100f / (this.font.width(this.splash) + 32);
            g.pose().pushPose();
            g.pose().translate(this.width / 2f + 96f, 26f + 42f, 0f);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-16f));
            g.pose().scale(scale, scale, 1f);
            g.drawCenteredString(this.font, this.splash, 0, -8, 0xFFFFE070);
            g.pose().popPose();
        }
    }

    // ---------------------------------------------------------------- pause

    static final class Pause extends PauseScreen {
        private static final Set<String> DROP = Set.of(
                "menu.returnToGame", "gui.advancements", "gui.stats", "menu.sendFeedback", "menu.reportBugs",
                "menu.feedback", "menu.options", "menu.playerReporting", "menu.server_links");

        private final List<Btn> buttons = new ArrayList<>();
        private final Map<Btn, Button> extras = new LinkedHashMap<>();
        private Button vanillaLeave;
        private boolean custom;

        Pause() {
            super(true);
        }

        private static String key(Button b) {
            return b.getMessage().getContents() instanceof TranslatableContents tc ? tc.getKey() : null;
        }

        private static boolean isLeave(Button b) {
            String k = key(b);
            return k != null && (k.equals("menu.returnToMenu") || k.equals("menu.disconnect"));
        }

        @Override
        protected void init() {
            super.init();
            this.extras.clear();
            this.vanillaLeave = null;
            List<Button> keep = new ArrayList<>();
            for (GuiEventListener child : this.children()) {
                if (!(child instanceof Button b)) continue;
                if (isLeave(b)) {
                    if (this.vanillaLeave == null) this.vanillaLeave = b;
                } else if (!DROP.contains(key(b))) {
                    keep.add(b);
                }
            }
            this.custom = this.vanillaLeave != null;
            if (!this.custom) return;

            this.clearWidgets();
            this.buttons.clear();
            for (Button orig : keep) mirror(orig);
            build();
        }

        private void mirror(Button orig) {
            Btn copy = new Btn(0, 0, 190, 20, orig.getMessage(), b -> orig.onPress());
            copy.active = orig.active;
            this.extras.put(copy, orig);
        }

        private void build() {
            for (Btn b : this.buttons) this.removeWidget(b);
            this.buttons.clear();
            int bw = 190, bh = 20, step = 24;
            int x = this.width / 2 - bw / 2;
            int rows = 3 + this.extras.size();
            int y = Math.max(20, Math.min(Math.max(64, this.height / 4 + 24), this.height - rows * step - 8 - bh - 10));
            final Button leave = this.vanillaLeave;
            add(new Btn(x, y, bw, bh, Lang.text("archivist.menu.back|Back to game"),
                    b -> this.minecraft.setScreen(null)));
            add(new Btn(x, y + step, bw, bh, Lang.text("archivist.menu.options|Options..."),
                    b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))));
            add(new Btn(x, y + step * 2, bw, bh, Lang.text("archivist.menu.archivist|Archivist"),
                    b -> this.minecraft.setScreen(new Options(this))));
            int row = 3;
            for (Btn copy : this.extras.keySet()) {
                copy.setX(x);
                copy.setY(y + step * row++);
                add(copy);
            }
            add(new Btn(x, y + step * row + 8, bw, bh, leave.getMessage(),
                    b -> leave.onPress()));
        }

        void adopt(ScreenEvent.Init.Post event) {
            if (!this.custom) return;
            boolean added = false;
            for (GuiEventListener child : new ArrayList<>(event.getListenersList())) {
                if (child instanceof Button b && !(child instanceof Btn) && !isLeave(b) && !DROP.contains(key(b))) {
                    mirror(b);
                    event.removeListener(b);
                    added = true;
                }
            }
            if (added) build();
        }

        private Btn add(Btn b) {
            this.buttons.add(b);
            return this.addRenderableWidget(b);
        }

        @Override
        public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (this.custom) backdrop(g, this.width, this.height, true);
            else super.renderBackground(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            for (Map.Entry<Btn, Button> e : this.extras.entrySet()) e.getKey().active = e.getValue().active;
            super.render(g, mouseX, mouseY, partialTick);
        }
    }

    // ---------------------------------------------------------------- options

    static final class Options extends Screen {
        private final Screen parent;
        private final Slide[] custom = new Slide[4];

        Options(Screen parent) {
            super(Lang.text("archivist.menu.archivist|Archivist"));
            this.parent = parent;
        }

        private static Component label(String name, boolean on) {
            return Component.literal(name + ": " + Lang.get(on ? "archivist.menu.on|ON" : "archivist.menu.off|OFF"));
        }


        Options fresh() {
            return new Options(this.parent);
        }
        private static double norm(int v, int min, int max) {
            return (double) (v - min) / (max - min);
        }
        private static int den(double v, int min, int max) {
            return min + (int) Math.round(v * (max - min));
        }

        @Override
        protected void init() {
            int bw = 150, gap = 10, step = 24;
            int cx = this.width / 2;
            int lx = cx - bw - gap / 2;
            int rx = cx + gap / 2;
            int y = Math.max(30, this.height / 2 - 90);

            this.addRenderableWidget(new Btn(lx, y, bw, 20, label(Lang.get("archivist.menu.shaders|Shaders"), ArchivistConfig.shaders()), b -> {
                ArchivistConfig.setShaders(!ArchivistConfig.shaders());
                b.setMessage(label(Lang.get("archivist.menu.shaders|Shaders"), ArchivistConfig.shaders()));
            }));
            this.addRenderableWidget(new Btn(lx, y + step, bw, 20, label(Lang.get("archivist.menu.cannibalism|Animal cannibalism"), ArchivistConfig.cannibalism()), b -> {
                ArchivistConfig.setCannibalism(!ArchivistConfig.cannibalism());
                b.setMessage(label(Lang.get("archivist.menu.cannibalism|Animal cannibalism"), ArchivistConfig.cannibalism()));
            }));
            this.addRenderableWidget(new Slide(lx, y + step * 2, bw, 20, Lang.get("archivist.menu.loud|Loud noises"), ArchivistConfig.loud() / 100.0) {
                @Override String show(double v) { return Math.round(v * 100) + "%"; }
                @Override void changed(double v) { ArchivistConfig.setLoud((int) Math.round(v * 100)); }
            });
            this.addRenderableWidget(new Slide(lx, y + step * 3, bw, 20, Lang.get("archivist.menu.difficulty|Difficulty"), ArchivistConfig.difficulty() / 3.0) {
                @Override String show(double v) { return Lang.get("archivist.menu.difficulty." + (int) Math.round(v * 3)); }
                @Override void changed(double v) {
                    int idx = (int) Math.round(v * 3);
                    set(idx / 3.0);
                    if (idx != ArchivistConfig.difficulty()) {
                        ArchivistConfig.setDifficulty(idx);
                        refreshCustom();
                    }
                }
            });

            custom[0] = new Slide(rx, y, bw, 20, Lang.get("archivist.menu.them_cooldown|Them cooldown"), 0) {
                @Override String show(double v) { return String.format("%.1fs", den(v, 20, 2000) / 20.0); }
                @Override void changed(double v) { ArchivistConfig.setThemCooldown(den(v, 20, 2000)); }
            };
            custom[1] = new Slide(rx, y + step, bw, 20, Lang.get("archivist.menu.max_them|Max Them"), 0) {
                @Override String show(double v) { return String.valueOf(den(v, 1, 64)); }
                @Override void changed(double v) { ArchivistConfig.setMaxThem(den(v, 1, 64)); }
            };
            custom[2] = new Slide(rx, y + step * 2, bw, 20, Lang.get("archivist.menu.mansion_tinas|Mansion Tinas"), 0) {
                @Override String show(double v) { return String.valueOf(den(v, 0, 30)); }
                @Override void changed(double v) { ArchivistConfig.setMansionTinas(den(v, 0, 30)); }
            };
            custom[3] = new Slide(rx, y + step * 3, bw, 20, Lang.get("archivist.menu.echo_tinas|Echo Tinas"), 0) {
                @Override String show(double v) { return String.valueOf(den(v, 0, 10)); }
                @Override void changed(double v) { ArchivistConfig.setEchoTinas(den(v, 0, 10)); }
            };
            for (Slide sl : custom) this.addRenderableWidget(sl);
            refreshCustom();

            this.addRenderableWidget(new Btn(cx - 95, y + step * 4, 190, 20,
                    Component.literal(Lang.get("archivist.menu.language|Language") + ": " + Lang.nameOf(ArchivistConfig.language())),
                    b -> this.minecraft.setScreen(new LanguageScreen(this))));
            this.addRenderableWidget(new Btn(cx - 95, y + step * 5, 190, 20,
                    label(Lang.get("archivist.menu.custom_menu|Custom menu"), ArchivistConfig.customMenu()), b -> {
                ArchivistConfig.setCustomMenu(!ArchivistConfig.customMenu());
                b.setMessage(label(Lang.get("archivist.menu.custom_menu|Custom menu"), ArchivistConfig.customMenu()));
            }));
            this.addRenderableWidget(new Btn(cx - 95, y + step * 6 + 8, 190, 20, Lang.text("archivist.menu.done|Done"), b -> this.onClose()));
        }
        private void refreshCustom() {
            custom[0].set(norm(ArchivistConfig.themCooldown(), 20, 2000));
            custom[1].set(norm(ArchivistConfig.maxThem(), 1, 64));
            custom[2].set(norm(ArchivistConfig.mansionTinas(), 0, 30));
            custom[3].set(norm(ArchivistConfig.echoTinas(), 0, 10));
            boolean unlocked = ArchivistConfig.difficulty() == 3;
            for (Slide sl : custom) sl.active = unlocked;
        }

        @Override
        public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            backdrop(g, this.width, this.height, this.minecraft != null && this.minecraft.level != null);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.render(g, mouseX, mouseY, partialTick);
            int cx = this.width / 2;
            int y = Math.max(30, this.height / 2 - 90);
            g.drawCenteredString(this.font, Lang.get("archivist.menu.custom_hint|Custom difficulty unlocks the four sliders on the right."),
                    cx, y + 24 * 6 + 34, TEXT_DIM);
        }

        @Override
        public void removed() {
            ArchivistConfig.save();
        }

        @Override
        public void onClose() {
            boolean customParent = this.parent instanceof Title || this.parent instanceof Pause;
            boolean vanillaParent = this.parent.getClass() == TitleScreen.class || this.parent.getClass() == PauseScreen.class;
            // custom menu toggled while open rebuild the parent so onOpening picks the right style.
            if ((customParent && !ArchivistConfig.customMenu()) || (vanillaParent && ArchivistConfig.customMenu())) {
                boolean title = this.parent instanceof TitleScreen;
                ArchivistConfig.save();
                this.minecraft.setScreen(title ? new TitleScreen() : new PauseScreen(true));
                return;
            }
            this.minecraft.setScreen(this.parent);
        }
    }

    // ---------------------------------------------------------------- language

    static final class LanguageScreen extends Screen {
        private final Options options;

        LanguageScreen(Options options) {
            super(Lang.text("archivist.menu.language|Language"));
            this.options = options;
        }

        @Override
        protected void init() {
            int bw = 190, step = 24;
            int cx = this.width / 2;
            List<String> codes = ArchivistConfig.LANGUAGES;
            int y = Math.max(50, this.height / 2 - (codes.size() * step + 40) / 2 + 10);
            for (String code : codes) {
                String name = (code.equals(ArchivistConfig.language()) ? "> " : "") + Lang.nameOf(code);
                this.addRenderableWidget(new Btn(cx - bw / 2, y, bw, 20, Component.literal(name), b -> choose(code)));
                y += step;
            }
            this.addRenderableWidget(new Btn(cx - bw / 2, y + 8, bw, 20, Lang.text("archivist.menu.done|Done"), b -> this.onClose()));
        }

        private void choose(String code) {
            String before = ArchivistConfig.language();
            if (code.equals(before)) return;
            ArchivistConfig.setLanguage(code);
            Lang.reload();
            LangSync.send(); // the server answers toast in this language
            if (Mandanga.LANGUAGE.equals(code) || Mandanga.LANGUAGE.equals(before)) this.minecraft.reloadResourcePacks();
            this.rebuildWidgets();
        }

        @Override
        public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            backdrop(g, this.width, this.height, this.minecraft != null && this.minecraft.level != null);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.render(g, mouseX, mouseY, partialTick);
            int cx = this.width / 2;
            int ty = Math.max(50, this.height / 2 - (ArchivistConfig.LANGUAGES.size() * 24 + 40) / 2 + 10) - 24;
            g.drawCenteredString(this.font, Lang.get("archivist.menu.language|Language"), cx, ty, AMBER);
            g.fill(cx - 44, ty + 12, cx + 44, ty + 13, 0x88D4A84B);
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.options.fresh());
        }
    }
}