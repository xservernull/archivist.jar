package com.xservernull.archivist.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Sounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.ColorUtil;
import org.jcodec.scale.Transform;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * plays an mp4 clip as a full-screen overlay
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class VideoPlayer {
    private VideoPlayer() {}

    private static final Logger LOG = LogUtils.getLogger();

    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "dynamic/archivist_video");
    private static final String DIR = "/assets/archivist/video/";
    private static final int QUEUE = 4;
    private static final double FPS = 24.0;
    private static final Set<String> KEYED = Set.of("identity");
    private static final int KEY_R = 27, KEY_G = 52, KEY_B = 31, KEY_TOLERANCE = 60;
    private static final Object LOCK = new Object();
    private static BlockingQueue<NativeImage> frames = new ArrayBlockingQueue<>(QUEUE);
    private static Thread decoder;
    private static volatile boolean stopDecoder;
    private static volatile boolean decoderDone;
    private static volatile int run;
    private static boolean playing, started, wasPaused, warnedSlow;
    private static long startNanos, pausedNanos, pauseBeganNanos, playNanos;
    private static int shownIndex;
    private static String clip = "";
    private static SoundInstance sound;
    private static DynamicTexture texture;
    private static int textureW, textureH;
    private static volatile boolean ready;

    // ---------------------------------------------------------------- control

    public static void play(String name) {
        if (name == null || name.isEmpty()) {
            stop();
            return;
        }
        stop();
        synchronized (LOCK) {
            clip = name;
            playing = true;
            started = false;
            shownIndex = -1;
            warnedSlow = false;
            playNanos = System.nanoTime();
            LOG.info("[video] play {}", name);
            frames = new ArrayBlockingQueue<>(QUEUE);
            stopDecoder = false;
            decoderDone = false;
            int thisRun = ++run;
            BlockingQueue<NativeImage> queue = frames;
            decoder = new Thread(() -> decode(name, thisRun, queue), "archivist-video-decode");
            decoder.setDaemon(true);
            decoder.start();
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            run++;
            stopDecoder = true;
            if (decoder != null) decoder.interrupt();
            decoder = null;
            NativeImage left;
            while ((left = frames.poll()) != null) left.close();
            playing = false;
            started = false;
            ready = false;
            wasPaused = false;
            pausedNanos = 0;
            clip = "";
        }
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
            sound = null;
        }
    }

    static boolean ready() { return ready && texture != null; }

    static int width() { return textureW; }

    static int height() { return textureH; }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            texture = null;
            textureW = textureH = 0;
        }
    }

    // ---------------------------------------------------------------- render thread
    static void tick() {
        if (!playing) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            if (!wasPaused) {
                pauseBeganNanos = System.nanoTime();
                wasPaused = true;
            }
            return;
        }
        if (wasPaused) {
            pausedNanos += System.nanoTime() - pauseBeganNanos;
            wasPaused = false;
        }
        if (!started) {
            NativeImage first = frames.poll();
            if (first == null) {
                if (!warnedSlow && System.nanoTime() - playNanos > 10_000_000_000L) {
                    warnedSlow = true;
                    LOG.warn("[video] {}: no frame after 10 s (is jcodec packed into the mod jar? is the .mp4 H.264? see earlier [video] lines)", clip);
                }
                return;
            }
            show(first);
            started = true;
            shownIndex = 0;
            startNanos = System.nanoTime();
            pausedNanos = 0;
            startSound();
            return;
        }
        long elapsed = System.nanoTime() - startNanos - pausedNanos;
        int target = (int) (elapsed / 1_000_000_000.0 * FPS);
        NativeImage latest = null;
        while (shownIndex < target) {
            NativeImage next = frames.poll();
            if (next == null) break;
            shownIndex++;
            if (latest != null) latest.close();
            latest = next;
        }
        if (latest != null) {
            show(latest);
        } else if (decoderDone && frames.isEmpty()) {
            stop();
        }
    }

    private static void show(NativeImage frame) {
        try {
            if (texture == null || textureW != frame.getWidth() || textureH != frame.getHeight()) {
                if (texture != null) Minecraft.getInstance().getTextureManager().release(TEXTURE);
                textureW = frame.getWidth();
                textureH = frame.getHeight();
                texture = new DynamicTexture(textureW, textureH, true);
                Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
            }
            NativeImage pixels = texture.getPixels();
            if (pixels == null) return;
            pixels.copyFrom(frame);
            texture.upload();
            ready = true;
        } finally {
            frame.close();
        }
    }

    private static void startSound() {
        Supplier<SoundEvent> event = Sounds.video(clip);
        if (event == null) return; // a clip without a sound event plays silent
        if (Minecraft.getInstance().getSoundManager().getSoundEvent(event.get().getLocation()) == null) {
            LOG.warn("[video] {} is silent: assets/archivist/sounds.json has no entry '{}' (and sounds/{}.ogg must exist)",
                    clip, event.get().getLocation().getPath(), event.get().getLocation().getPath());
        }
        sound = SimpleSoundInstance.forUI(event.get(), 1.0F, 1.0F);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    // ---------------------------------------------------------------- decoder thread

    private static void decode(String name, int thisRun, BlockingQueue<NativeImage> queue) {
        Path file = null;
        SeekableByteChannel channel = null;
        try {
            file = extract(name);
            channel = NIOUtils.readableChannel(file.toFile());
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            Picture raw = grab.getNativeFrame();
            if (raw == null) return;
            int width = raw.getWidth(), height = raw.getHeight();
            Transform toRgb = ColorUtil.getTransform(raw.getColor(), ColorSpace.RGB);
            Picture rgb = Picture.create(width, height, ColorSpace.RGB);
            boolean keyed = KEYED.contains(name);
            LOG.info("[video] {} {}x{}{}", name, width, height, keyed ? " (chroma key)" : "");
            while (!stopDecoder && run == thisRun && raw != null) {
                toRgb.transform(raw, rgb);
                NativeImage frame = toImage(rgb, width, height, keyed);
                boolean handed = false;
                try {
                    while (!stopDecoder && run == thisRun) {
                        if (handed = queue.offer(frame, 50, TimeUnit.MILLISECONDS)) break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (!handed) frame.close();
                }
                if (!handed) return;
                raw = grab.getNativeFrame();
            }
            if (!stopDecoder && run == thisRun) decoderDone = true;
        } catch (IOException | JCodecException e) {
            LOG.warn("[video] could not play {}: {}", name, e.getMessage());
            if (run == thisRun) Minecraft.getInstance().execute(() -> {
                if (run == thisRun) stop();
            });
        } catch (RuntimeException | LinkageError e) {
            LOG.error("[video] decoder for {} crashed", name, e);
            if (run == thisRun) Minecraft.getInstance().execute(() -> {
                if (run == thisRun) stop();
            });
        } finally {
            if (channel != null) {
                try { channel.close(); } catch (IOException ignored) {}
            }
            if (file != null) {
                try { Files.deleteIfExists(file); } catch (IOException ignored) {}
            }
        }
    }

    /** jcodec needs a seekable file, so the clip is copied out of the mod's resources to a temp file for the run. */
    private static Path extract(String name) throws IOException {
        String path = DIR + name + ".mp4";
        try (InputStream in = VideoPlayer.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("no file " + path);
            Path temp = Files.createTempFile("archivist_video_", ".mp4");
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            return temp;
        }
    }

    private static NativeImage toImage(Picture rgb, int width, int height, boolean keyed) {
        NativeImage image = new NativeImage(width, height, false);
        byte[] plane = rgb.getPlaneData(0);
        int toleranceSq = KEY_TOLERANCE * KEY_TOLERANCE;
        int p = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (plane[p] & 0xFF) ^ 0x80, g = (plane[p + 1] & 0xFF) ^ 0x80, b = (plane[p + 2] & 0xFF) ^ 0x80;
                p += 3;
                int dr = r - KEY_R, dg = g - KEY_G, db = b - KEY_B;
                if (keyed && dr * dr + dg * dg + db * db < toleranceSq) image.setPixelRGBA(x, y, 0);
                else image.setPixelRGBA(x, y, (0xFF << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return image;
    }
}