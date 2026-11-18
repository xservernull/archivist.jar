package com.xservernull.archivist;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The mod's own sound events.
 */
public final class Sounds {
    private Sounds() {}

    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Archivist.MOD_ID);

    public static final Supplier<SoundEvent> FUNNY_AUDIO = sound("funnyaudio");
    public static final Supplier<SoundEvent> INTERMISSION = sound("intermission");
    public static final Supplier<SoundEvent> ALARM = sound("alarm");
    public static final Supplier<SoundEvent> CHASEMUSIC1 = sound("chasemusic1");
    public static final Supplier<SoundEvent> CHASEMUSIC2 = sound("chasemusic2");
    public static final Supplier<SoundEvent> BREATHING = sound("breathing");
    public static final Supplier<SoundEvent> FOOTSTEPS = sound("footsteps");
    public static final Supplier<SoundEvent> NEAR = sound("near");
    public static final Supplier<SoundEvent> HEARTBEAT = sound("heartbeat");
    public static final Supplier<SoundEvent> DIE = sound("die");
    public static final Supplier<SoundEvent> GLITCH = sound("glitch");
    public static final Supplier<SoundEvent> SCREAM1 = sound("scream1");
    public static final Supplier<SoundEvent> FRIENDSOUND = sound("friendsound");
    public static final Supplier<SoundEvent> NECKSNAP = sound("necksnap");
    public static final Supplier<SoundEvent> ARCHIVING = sound("archiving");
    public static final Supplier<SoundEvent> BROKENROCKS = sound("brokenrocks");
    public static final Supplier<SoundEvent> DARKFOG = sound("darkfog");
    public static final Supplier<SoundEvent> ECHO_MUSIC = sound("echowithoutechofuckaudacity");
    public static final Supplier<SoundEvent> V1 = sound("v1");
    public static final Supplier<SoundEvent> V2 = sound("v2");
    public static final Supplier<SoundEvent> VILLAGE_THEME = sound("villagetheme");
    public static final Supplier<SoundEvent> WELCOME_TO_HELL = sound("welcometohell");
    public static final Supplier<SoundEvent> WE_USED_TO_PLAY_THIS_TOGETHER = sound("weusedtoplaythistogether");
    public static final Supplier<SoundEvent> YOU_REMEMBER_THIS_PLACE = sound("yourememberthisplace");
    public static final Supplier<SoundEvent> TERMINAL = sound("terminal");
    public static final Supplier<SoundEvent> A_WEIRD_PLACE = sound("aweirdplace");
    public static final Supplier<SoundEvent> HEAVEN = sound("heaven");
    public static final Supplier<SoundEvent> MENU2 = sound("menu2");
    public static final Supplier<SoundEvent> HELLO = sound("hello");
    public static final Supplier<SoundEvent> TINASTEP = sound("tinastep");
    public static final Supplier<SoundEvent> TINAAMBIENT = sound("tinaambient");
    public static final Supplier<SoundEvent> BYETINA = sound("byetina");
    public static final Supplier<SoundEvent> DINNERBONE_AMBIENT = sound("dinnerbone/ambient");
    public static final Supplier<SoundEvent> DINNERBONE_HURT = sound("dinnerbone/hurt");
    public static final Supplier<SoundEvent> DINNERBONE_DEATH = sound("dinnerbone/death");
    public static final Supplier<SoundEvent> DINNERBONE_NEIGH = sound("dinnerbone/neigh");
    public static final Supplier<SoundEvent> DINNERBONE_HOOFSTEP = sound("dinnerbone/hoofstep");
    private static final Supplier<SoundEvent> VIDEO_SHADOW = sound("video/video_shadow");
    private static final Supplier<SoundEvent> VIDEO_SMALLER = sound("video/video_smaller");
    private static final Supplier<SoundEvent> VIDEO_HURT = sound("video/video_hurt");
    private static final Supplier<SoundEvent> VIDEO_FANTASY = sound("video/video_fantasy");
    private static final Supplier<SoundEvent> VIDEO_IDENTITY = sound("video/video_identity");
    private static final Supplier<SoundEvent> VIDEO_LOST = sound("video/video_lost");
    private static final Supplier<SoundEvent> VIDEO_NAME = sound("video/video_name");
    private static final Supplier<SoundEvent> VIDEO_FINAL = sound("video/video_final");


    private static final Map<String, Supplier<SoundEvent>> VOICE = new HashMap<>();

    static {
        for (int block = 1; block <= 9; block++) VOICE.put("block_" + block, sound("voiceacting/block_" + block));
        for (String ask : new String[]{"name", "shadow", "smaller", "hurt", "fantasy", "identity", "lost"}) {
            for (int variant = 1; variant <= 3; variant++) {
                VOICE.put("resp_" + ask + "_" + variant, sound("voiceacting/resp_" + ask + "_" + variant));
            }
        }
        for (String ending : new String[]{"builder", "mosaic", "player"}) {
            VOICE.put("resp_final_" + ending, sound("voiceacting/resp_final_" + ending));
        }
    }

    public static SoundEvent voice(String name) {
        Supplier<SoundEvent> found = VOICE.get(name);
        return found == null ? null : found.get();
    }

    public static Supplier<SoundEvent> video(String clip) {
        return switch (clip) {
            case "shadow" -> VIDEO_SHADOW;
            case "smaller" -> VIDEO_SMALLER;
            case "hurt" -> VIDEO_HURT;
            case "fantasy" -> VIDEO_FANTASY;
            case "identity" -> VIDEO_IDENTITY;
            case "lost" -> VIDEO_LOST;
            case "name" -> VIDEO_NAME;
            case "final" -> VIDEO_FINAL;
            default -> null;
        };
    }

    private static Supplier<SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, id)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}