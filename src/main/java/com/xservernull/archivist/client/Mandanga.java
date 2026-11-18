package com.xservernull.archivist.client;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.ArchivistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Mandanga texture swap (client only).YO TOMO MANDANGA TODOS LOS DIAS TODAS LAS NOCHES CONT U HERMANAAAAA
 */
public final class Mandanga {
    private Mandanga() {}

    private static final Logger LOG = LogUtils.getLogger();
    public static final String LANGUAGE = "mandanga";
    private static final int FACES = 5;
    /** mandanga images */
    private static byte[][][] faces;

    public static boolean active() {
        try {
            return LANGUAGE.equals(ArchivistConfig.language());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** recursos para mandanga ou yeah */
    public static Resource wrap(ResourceLocation id, Resource res) {
        if (!"minecraft".equals(id.getNamespace())) return res;
        String p = id.getPath();
        if (!p.endsWith(".png")) return res;
        int size;
        if (p.startsWith("textures/block/") || p.startsWith("textures/item/") || p.startsWith("textures/particle/")) size = 0;
        else if (p.startsWith("textures/entity/") || p.startsWith("textures/painting/") || p.startsWith("textures/models/armor/")) size = 1;
        else return res;
        byte[][][] f = load();
        byte[] data = f[Math.floorMod(p.hashCode(), FACES)][size];
        if (data == null) return res;
        return new Resource(res.source(), () -> new ByteArrayInputStream(data));
    }

    private static synchronized byte[][][] load() {
        if (faces != null) return faces;
        byte[][][] out = new byte[FACES][2][];
        int[] px = {32, 128};
        for (int i = 0; i < FACES; i++) {
            for (int s = 0; s < 2; s++) {
                String path = "/assets/archivist/mandanga/face" + (i + 1) + "_" + px[s] + ".png";
                try (InputStream in = Mandanga.class.getResourceAsStream(path)) {
                    if (in == null) LOG.error("[mandanga] missing {}", path);
                    else out[i][s] = in.readAllBytes();
                } catch (Exception e) {
                    LOG.error("[mandanga] could not read {}: {}", path, e.toString());
                }
            }
        }
        faces = out;
        LOG.info("[mandanga] faces loaded");
        return out;
    }

    // ---------------------------------------------------------------- sonido

    private static final SoundEvent SHOTO = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("archivist", "shoto"));

    /**sonidos q no se reemplazan*/
    private static final String[] REPEATING = {
            ".step", ".swim", ".splash", ".hit", "rain", ".ambient", "block.water", "block.lava", "block.fire",
            "experience_orb", "minecart", "elytra", "block.sand", "block.gravel"
    };

    public static boolean shouldSwap(SoundInstance s) {
        ResourceLocation id = s.getLocation();
        if (!"minecraft".equals(id.getNamespace()) || s.isLooping()) return false;
        SoundSource src = s.getSource();
        if (src == SoundSource.MUSIC || src == SoundSource.RECORDS || src == SoundSource.AMBIENT
                || src == SoundSource.WEATHER || src == SoundSource.VOICE) return false;
        String p = id.getPath();
        for (String part : REPEATING) {
            if (p.contains(part)) return false;
        }
        return true;
    }

    /**  LA PUTA MADRE SHOTO */
    public static SoundInstance shoto(SoundInstance s) {
        float volume = 1.0F, pitch = 1.0F;
        try {
            s.resolve(Minecraft.getInstance().getSoundManager());
            volume = s.getVolume();
            pitch = s.getPitch();
        } catch (RuntimeException e) {
            LOG.warn("[mandanga] could not read volume/pitch of {}: {}", s.getLocation(), e.toString());
        }
        if (s.isRelative()) return SimpleSoundInstance.forUI(SHOTO, pitch, volume);
        return new SimpleSoundInstance(SHOTO, s.getSource(), volume, pitch, RandomSource.create(),
                s.getX(), s.getY(), s.getZ());
    }
}