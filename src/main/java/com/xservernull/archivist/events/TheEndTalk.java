// the archivist's speech in the end (old archivistdialogue).

package com.xservernull.archivist.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.Texts;
import com.xservernull.archivist.events.neverforgetyou.NfyNet;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class TheEndTalk implements QuickEvent {
    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "events/the_end.json");
    private static final int CHOICE_HOLD = 200, FIRST_DELAY = 40;
    private static final int[] BLOCK_BASE = {64, 67, 55, 55, 58, 52, 53, 50, 58, 58};

    private record Item(String kind, String text, String ask) {}

    private record Option(String text, String score) {}

    private record Ask(List<Option> options, List<List<String>> replies) {}

    private record Script(List<Item> items, Map<String, Ask> asks) {}

    private static final class Talk {
        int index, block = 1, replyIndex, pos, neu, neg, nameChoice;
        long nextAt;
        boolean waiting, finished, video, voiced;
        String ask = "", replySound;
        List<String> replies;
        final Set<String> seen = new HashSet<>();
    }

    private static volatile Script script;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Archivist.MOD_ID);
    private static final Supplier<AttachmentType<Talk>> TALK =
            ATTACHMENTS.register("the_end_talk", () -> AttachmentType.builder(Talk::new).build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static void listen() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new Loader()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> script = null);
    }

    @Override public String id() { return TheEnd.TALK; }

    @Override
    public void start(ServerPlayer player) {
        Talk talk = new Talk();
        talk.nextAt = player.level().getGameTime() + FIRST_DELAY;
        player.setData(TALK, talk);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        Script current = script;
        if (current == null || player.level().dimension() != Level.END) return true;
        Talk talk = player.getData(TALK);
        long now = player.level().getGameTime();
        if (talk.waiting || now < talk.nextAt) return false;
        if (talk.replies != null) {
            if (talk.replyIndex < talk.replies.size()) {
                boolean first = talk.replyIndex == 0;
                String text = talk.replies.get(talk.replyIndex++);
                int delay = replyDelay(Texts.english(text));
                NfyNet.say(player, "archivist", text, delay);
                if (first) {
                    voice(player, talk.replySound);
                    talk.replySound = null;
                }
                talk.nextAt = now + delay;
                return false;
            }
            talk.replies = null;
        }
        if (talk.index >= current.items().size()) {
            talk.finished = true;
            EventNet.stopVideo(player);
            NfyNet.clear(player);
            TheEnd.finish(player, ending(talk));
            return true;
        }
        Item item = current.items().get(talk.index++);
        switch (item.kind()) {
            case "n" -> {
                talk.block++;
                talk.voiced = false;
            }
            case "a" -> {
                Ask ask = current.asks().get(item.ask());
                talk.waiting = true;
                talk.ask = item.ask();
                talk.block++;
                talk.voiced = false;
                if (talk.video) {
                    talk.video = false;
                    EventNet.stopVideo(player);
                }
                List<String> buttons = new ArrayList<>();
                for (Option option : ask.options()) buttons.add(option.text());
                NfyNet.askHud(player, buttons); // HUD choices, not a screen: the player can still look around
            }
            default -> {
                int delay = lineDelay(Texts.english(item.text()), talk.block, talk.seen);
                if ("q".equals(item.kind())) {
                    if (!talk.video) {
                        talk.video = true;
                        stopVoice(player);
                        NfyNet.clear(player);
                        String clip = nextAsk(current, talk.index - 1);
                        if (clip != null) EventNet.video(player, clip);
                    }
                } else {
                    if (talk.video) {
                        talk.video = false;
                        EventNet.stopVideo(player);
                    }
                    NfyNet.say(player, "archivist", item.text(), delay);
                    String name = talk.replySound;
                    if (name == null && !talk.voiced) name = "block_" + talk.block;
                    talk.replySound = null;
                    talk.voiced = true;
                    voice(player, name);
                }
                talk.nextAt = now + delay;
            }
        }
        return false;
    }

    @Override
    public void stop(ServerPlayer player) {
        Talk talk = player.getData(TALK);
        talk.waiting = false;
        if (!talk.finished) TheEnd.interrupted(player);
        if (player.connection == null) return;
        NfyNet.clear(player);
        EventNet.stopVideo(player);
        stopVoice(player);
    }

    private static void voice(ServerPlayer player, String name) {
        if (name == null) return;
        SoundEvent sound = Sounds.voice(name);
        if (sound != null) player.playNotifySound(sound, SoundSource.VOICE, 1.0F, 1.0F);
    }

    private static void stopVoice(ServerPlayer player) {
        player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.VOICE));
    }

    public static void pick(ServerPlayer player, int index) {
        Script current = script;
        if (current == null || !TheEnd.TALK.equals(Events.running(player))) return;
        Talk talk = player.getData(TALK);
        if (!talk.waiting) return;
        Ask ask = current.asks().get(talk.ask);
        if (ask == null || index < 0 || index >= ask.options().size()) return;
        Option option = ask.options().get(index);
        switch (option.score()) {
            case "pos" -> talk.pos++;
            case "neg" -> talk.neg++;
            default -> talk.neu++;
        }
        if ("name".equals(talk.ask)) talk.nameChoice = index + 1;
        talk.waiting = false;
        talk.replySound = "final".equals(talk.ask)
                ? "resp_final_" + switch (talk.nameChoice) {
            case 1 -> "builder";
            case 2 -> "mosaic";
            default -> "player";
        }
                : "resp_" + talk.ask + "_" + (index + 1);
        talk.replies = ask.replies().get(index);
        talk.replyIndex = 0;
        NfyNet.say(player, "you", option.text(), CHOICE_HOLD);
        talk.nextAt = player.level().getGameTime() + CHOICE_HOLD;
    }

    private static TheEnd.Ending ending(Talk talk) {
        if (talk.pos > talk.neg && talk.pos > talk.neu) return TheEnd.Ending.POSITIVE;
        if (talk.neg > talk.pos && talk.neg > talk.neu) return TheEnd.Ending.NEGATIVE;
        return TheEnd.Ending.NEUTRAL;
    }

    private static String nextAsk(Script script, int from) {
        for (int i = from; i < script.items().size(); i++) {
            if ("a".equals(script.items().get(i).kind())) return script.items().get(i).ask();
        }
        return null;
    }

    private static int words(String text) {
        int count = 0;
        for (String token : text.trim().split("\\s+")) {
            if (token.matches(".*[a-zA-Z].*")) count++;
        }
        return count;
    }

    private static int lineDelay(String text, int block, Set<String> seenSingle) {
        int words = words(text);
        double mult;
        if (words == 1) {
            String key = text.trim().replaceAll("[^a-zA-Z']", "").toLowerCase();
            if (!key.isEmpty() && seenSingle.contains(key)) {
                mult = 0.5;
            } else {
                mult = 0.6;
                if (!key.isEmpty()) seenSingle.add(key);
            }
        } else {
            mult = 0.6 + (Math.min(Math.max(words, 1), 10) - 1) * 0.04;
        }
        int base = block >= 1 && block < BLOCK_BASE.length ? BLOCK_BASE[block] : 64;
        return Math.max(1, Math.round((float) (base * mult)));
    }

    private static int replyDelay(String text) {
        int words = words(text);
        if (words <= 2) return 14;
        if (words == 3) return 24;
        if (words == 4) return 34;
        if (words <= 6) return 48;
        return 70;
    }

    // ---------------------------------------------------------------- the script

    private static final class Loader extends SimplePreparableReloadListener<Script> {
        @Override
        protected Script prepare(ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(FILE);
            if (resource.isEmpty()) {
                LOG.error("[the_end] {} not found", FILE);
                return null;
            }
            try (Reader reader = resource.get().openAsReader()) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException | RuntimeException e) {
                LOG.error("[the_end] {} is not valid: {}", FILE, e.toString());
                return null;
            }
        }

        @Override
        protected void apply(Script loaded, ResourceManager manager, ProfilerFiller profiler) {
            script = loaded;
            if (loaded == null) LOG.error("[the_end] no dialogue loaded: the Archivist will say nothing");
            else LOG.info("[the_end] dialogue loaded ({} lines, {} asks)", loaded.items().size(), loaded.asks().size());
        }
    }

    private static Script parse(JsonObject root) {
        Map<String, Ask> asks = new HashMap<>();
        JsonObject askRoot = root.getAsJsonObject("asks");
        for (String id : askRoot.keySet()) {
            JsonObject ask = askRoot.getAsJsonObject(id);
            List<Option> options = new ArrayList<>();
            for (JsonElement element : ask.getAsJsonArray("options")) {
                JsonObject option = element.getAsJsonObject();
                options.add(new Option(option.get("t").getAsString(), option.get("score").getAsString()));
            }
            List<List<String>> replies = new ArrayList<>();
            for (JsonElement element : ask.getAsJsonArray("replies")) {
                List<String> lines = new ArrayList<>();
                for (JsonElement line : element.getAsJsonArray()) lines.add(line.getAsString());
                replies.add(lines);
            }
            if (options.isEmpty() || options.size() > 3 || replies.size() != options.size()) {
                throw new IllegalArgumentException("ask '" + id + "' needs 1 to 3 buttons and one reply list each");
            }
            asks.put(id, new Ask(options, replies));
        }
        List<Item> items = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("lines")) {
            JsonObject line = element.getAsJsonObject();
            String kind = line.get("k").getAsString();
            String ask = line.has("a") ? line.get("a").getAsString() : null;
            if ("a".equals(kind) && !asks.containsKey(ask)) throw new IllegalArgumentException("unknown ask '" + ask + "'");
            items.add(new Item(kind, line.has("t") ? line.get("t").getAsString() : null, ask));
        }
        return new Script(items, asks);
    }
}