// the library after the erase (old librarycorruptiondialogue, #193).

package com.xservernull.archivist.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Texts;
import com.xservernull.archivist.events.neverforgetyou.NfyNet;
import com.xservernull.archivist.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
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
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class LibraryCorruption implements QuickEvent {
    private static final Logger LOG = LogUtils.getLogger();

    public static final String ID = "library_corruption";
    static final String DONE = "library_corruption_done";

    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "events/library_corruption.json");
    private static final int GREY_TICKS = 12000, MIN_DELAY = 50, MAX_DELAY = 160;

    private record Line(String speaker, String text, boolean giveMap) {}

    private record Step(List<Line> lines, List<String> options, List<List<Line>> replies) {
        boolean asks() { return options != null; }
    }

    private record Script(int startDelay, List<Step> steps) {}

    private static final class Talk {
        int step, line, reply = -1;
        long nextAt;
        boolean waiting;
    }

    private static volatile Script script;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Archivist.MOD_ID);
    private static final Supplier<AttachmentType<Talk>> TALK =
            ATTACHMENTS.register("library_corruption_talk", () -> AttachmentType.builder(Talk::new).build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static void listen() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new Loader()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> script = null);
    }

    @Override public String id() { return ID; }

    @Override
    public void start(ServerPlayer player) {
        Talk talk = player.getData(TALK);
        talk.step = 0;
        talk.line = 0;
        talk.reply = -1;
        talk.nextAt = 0;
        talk.waiting = false;
        EventNet.fx(player, "greyscale", GREY_TICKS);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        Script current = script;
        if (current == null || !Archivist.LIBRARY.equals(player.level().dimension())) return true;
        Talk talk = player.getData(TALK);
        long now = player.level().getGameTime();
        if (age < current.startDelay() || talk.waiting || now < talk.nextAt) return false;
        if (talk.step >= current.steps().size()) {
            Events.memory(player).setFlag(DONE);
            ObjectiveHud.show(player);
            LOG.info("[rubydung] {} finished the library dialogue", player.getName().getString());
            return true;
        }
        Step step = current.steps().get(talk.step);
        if (step.asks() && talk.reply < 0) {
            talk.waiting = true;
            NfyNet.ask(player, step.options());
            return false;
        }
        List<Line> lines = step.asks() ? step.replies().get(talk.reply) : step.lines();
        if (talk.line >= lines.size()) {
            talk.step++;
            talk.line = 0;
            talk.reply = -1;
            return false;
        }
        Line line = lines.get(talk.line++);
        if (line.giveMap()) giveMap(player);
        int delay = delayFor(Texts.english(line.text()));
        NfyNet.say(player, line.speaker(), line.text(), delay);
        talk.nextAt = now + delay;
        return false;
    }

    @Override
    public void stop(ServerPlayer player) {
        player.getData(TALK).waiting = false;
        if (player.connection == null) return;
        NfyNet.clear(player);
        EventNet.stopFx(player);
    }

    public static void pick(ServerPlayer player, int index) {
        Script current = script;
        if (current == null || !ID.equals(Events.running(player))) return;
        Talk talk = player.getData(TALK);
        if (!talk.waiting || talk.step >= current.steps().size()) return;
        Step step = current.steps().get(talk.step);
        if (!step.asks() || index < 0 || index >= step.options().size()) return;
        talk.waiting = false;
        talk.reply = index;
        talk.line = 0;
        String said = step.options().get(index);
        int delay = delayFor(Texts.english(said));
        NfyNet.say(player, "you", said, delay);
        talk.nextAt = player.level().getGameTime() + delay;
    }

    private static int delayFor(String text) {
        return Math.max(MIN_DELAY, Math.min(MAX_DELAY, 30 + 2 * text.length()));
    }

    private static void giveMap(ServerPlayer player) {
        ItemStack map = new ItemStack(ModItems.DETECTOR_MAP.get());
        if (!player.addItem(map)) player.drop(map, false);
    }

    // ---------------------------------------------------------------- the script

    private static final class Loader extends SimplePreparableReloadListener<Script> {
        @Override
        protected Script prepare(ResourceManager manager, net.minecraft.util.profiling.ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(FILE);
            if (resource.isEmpty()) {
                LOG.error("[rubydung] {} not found", FILE);
                return null;
            }
            try (Reader reader = resource.get().openAsReader()) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException | RuntimeException e) {
                LOG.error("[rubydung] {} is not valid: {}", FILE, e.toString());
                return null;
            }
        }

        @Override
        protected void apply(Script loaded, ResourceManager manager, ProfilerFiller profiler) {
            script = loaded;
            if (loaded == null) LOG.error("[rubydung] no dialogue loaded: the library corruption will do nothing");
            else LOG.info("[rubydung] dialogue loaded ({} steps)", loaded.steps().size());
        }
    }

    private static Script parse(JsonObject root) {
        List<Step> steps = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("steps")) {
            JsonObject step = element.getAsJsonObject();
            if (step.has("ask")) {
                List<String> options = new ArrayList<>();
                for (JsonElement button : step.getAsJsonArray("ask")) options.add(button.getAsString());
                List<List<Line>> replies = new ArrayList<>();
                for (JsonElement reply : step.getAsJsonArray("replies")) replies.add(lines(reply.getAsJsonArray()));
                if (options.isEmpty() || options.size() > 3 || replies.size() != options.size()) {
                    throw new IllegalArgumentException("a question needs 1 to 3 buttons and one reply list each");
                }
                steps.add(new Step(null, options, replies));
            } else {
                steps.add(new Step(lines(step.getAsJsonArray("lines")), null, null));
            }
        }
        return new Script(root.has("start_delay") ? root.get("start_delay").getAsInt() : 100, steps);
    }

    private static List<Line> lines(JsonArray array) {
        List<Line> lines = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject line = element.getAsJsonObject();
            lines.add(new Line(line.has("s") ? line.get("s").getAsString() : "", line.get("t").getAsString(),
                    line.has("give") && line.get("give").getAsString().equals("detector_map")));
        }
        return lines;
    }
}
