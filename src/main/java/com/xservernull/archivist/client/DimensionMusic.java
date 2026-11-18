package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.client.shader.Fx;
import com.xservernull.archivist.entity.ArchivistDragon;
import com.xservernull.archivist.entity.Somehow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Looping music, one track per dimension and, at the main menu,
 * one of two menu tracks picked at random each time the menu shows.
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class DimensionMusic {
    private DimensionMusic() {}

    private static final float VOLUME = 0.3F, MENU_VOLUME = 0.405F;
    private static final int MENU_DELAY_TICKS = 20;

    private static final ResourceLocation MENU = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "menu");


    private static final Map<ResourceLocation, Supplier<SoundEvent>> MUSIC = Map.ofEntries(
            entry("library", Sounds.ARCHIVING),
            entry("bedrock_far_lands", Sounds.BROKENROCKS),
            entry("far_lands", Sounds.DARKFOG),
            entry("echo_chamber", Sounds.ECHO_MUSIC),
            entry("xor_world", Sounds.V1),
            entry("m4kb_world", Sounds.V2),
            entry("villllage", Sounds.VILLAGE_THEME),
            entry("old_nether", Sounds.WELCOME_TO_HELL),
            entry("neverforgetyou", Sounds.WE_USED_TO_PLAY_THIS_TOGETHER),
            entry("childhoodhome", Sounds.YOU_REMEMBER_THIS_PLACE),
            entry("rd132011", Sounds.TERMINAL),
            entry("infdev20100227", Sounds.A_WEIRD_PLACE),
            entry("aether", Sounds.HEAVEN));

    private static Map.Entry<ResourceLocation, Supplier<SoundEvent>> entry(String dimension, Supplier<SoundEvent> sound) {
        return Map.entry(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, dimension), sound);
    }
    private static ResourceLocation seen;
    private static SoundInstance current;
    private static Supplier<SoundEvent> menuTrack;
    private static int menuDelay;

    private static boolean somehowNear, dragonNear;
    public static boolean isDragonNear() { return dragonNear; }
    private static volatile boolean friendEvent;
    private static boolean wasMuted;
    private static int scanTicks;
    private static final int SCAN_INTERVAL_TICKS = 10;

    public static void setFriendEvent(boolean active) {
        friendEvent = active;
    }
    private static boolean muted() {
        return somehowNear || dragonNear || friendEvent || Fx.isRunning("prayer");
    }
    private static void scan(Minecraft minecraft) {
        if (minecraft.level == null) {
            somehowNear = dragonNear = false;
            return;
        }
        if (++scanTicks < SCAN_INTERVAL_TICKS) return;
        scanTicks = 0;
        boolean somehow = false, dragon = false;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof Somehow) somehow = true;
            else if (entity instanceof ArchivistDragon) dragon = true;
        }
        somehowNear = somehow;
        dragonNear = dragon;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        SoundManager sounds = minecraft.getSoundManager();
        scan(minecraft);
        if (muted()) {
            if (!wasMuted) {
                wasMuted = true;
                stop(sounds);
                sounds.stop(null, SoundSource.MUSIC); // vanilla music too
            }
            seen = null; // so the right track starts again when the flags clear
            return;
        }
        wasMuted = false;
        if (minecraft.level == null) {
            tickMenu(sounds);
            return;
        }
        ResourceLocation dimension = minecraft.level.dimension().location();
        if (dimension.equals(seen)) return;
        seen = dimension;
        stop(sounds);
        if (!blocksVanilla(dimension)) return;
        sounds.stop(null, SoundSource.MUSIC); // vanilla music that is still playing
        Supplier<SoundEvent> track = MUSIC.get(dimension);
        if (track != null) start(sounds, track.get(), VOLUME);
    }
    private static void tickMenu(SoundManager sounds) {
        if (!MENU.equals(seen)) {
            stop(sounds);
            seen = MENU;
            menuTrack = RandomSource.create().nextBoolean() ? Sounds.MENU2 : Sounds.HELLO;
            menuDelay = MENU_DELAY_TICKS;
        }
        if (current != null) return;
        if (menuDelay > 0) {
            menuDelay--;
            return;
        }
        sounds.stop(null, SoundSource.MUSIC); // the vanilla menu track
        start(sounds, menuTrack.get(), MENU_VOLUME);
    }

    private static void start(SoundManager sounds, SoundEvent sound, float volume) {
        current = new SimpleSoundInstance(sound.getLocation(), SoundSource.MUSIC, volume, 1.0F,
                RandomSource.create(), true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        sounds.play(current);
    }

    private static boolean blocksVanilla(ResourceLocation dimension) {
        return Archivist.MOD_ID.equals(dimension.getNamespace())
                || dimension.equals(Level.OVERWORLD.location())
                || dimension.equals(Level.NETHER.location());
    }
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) return;
        String path = sound.getLocation().getPath();
        if (dragonNear && !(path.startsWith("voiceacting/") || path.startsWith("video/"))) {
            event.setSound(null);
            return;
        }
        if (friendEvent && !(path.contains("friend") || path.contains("necksnap") || path.startsWith("voiceacting/"))) {
            event.setSound(null);
            return;
        }
        if (sound.getSource() != SoundSource.MUSIC || !"minecraft".equals(sound.getLocation().getNamespace())) return;
        var level = Minecraft.getInstance().level;
        if (muted() || level == null || blocksVanilla(level.dimension().location())) event.setSound(null);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop(Minecraft.getInstance().getSoundManager());
        seen = null;
        somehowNear = dragonNear = false;
        friendEvent = false;
    }

    private static void stop(SoundManager sounds) {
        if (current != null) sounds.stop(current);
        current = null;
    }
}
