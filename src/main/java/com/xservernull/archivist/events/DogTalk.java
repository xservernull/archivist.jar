// the chat with the childhoodhome dog (old dogdialogue), shown like hope's: one line in the
// upper third of the screen (nfynet.say, speaker in yellow, the player's own pick in grey) and

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.Dog;
import com.xservernull.archivist.item.ModItems;
import net.minecraft.world.item.ItemStack;
import com.xservernull.archivist.events.neverforgetyou.NfyNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

final class DogTalk {
    private DogTalk() {}

    private static final String PHOTO_FLAG = "dog_photo_received";
    private static final int MAX_DISTANCE = 16;
    private static final int MIN_TICKS = 50, MAX_TICKS = 160, GAP_TICKS = 12, FIRST_DELAY = 10, YOU_TICKS = 40;

    // ---- the script ----

    private sealed interface Node permits Line, Choice, Photo {}
    private record Line(String text) implements Node {}
    private record Photo() implements Node {}
    private record Choice(List<Option> options) implements Node {}
    private record Option(String label, List<Node> then) {}

    private static Node say(String text) { return new Line(text); }

    private static Option option(String label, Node... then) { return new Option(label, List.of(then)); }

    private static Node choice(Option... options) { return new Choice(List.of(options)); }

    private static final List<Node> SCRIPT = List.of(
            say("archivist.dog.1|oh... hey"),
            say("archivist.dog.2|didn't think i would see you after so long"),
            choice(
                    option("archivist.dog.3|what's that next to you?",
                            say("archivist.dog.4|im tired of pretending this fake player roaming everywhere is you"),
                            say("archivist.dog.5|this is the closest thing to an actual memory i have"),
                            say("archivist.dog.6|take care of it"),
                            new Photo(),
                            choice(
                                    option("archivist.dog.7|thank you",
                                            say("archivist.dog.8|please kill it"),
                                            say("archivist.dog.9|and stay"),
                                            say("archivist.dog.10|please stay")),
                                    option("archivist.dog.11|..."))),
                    option("archivist.dog.12|who are you?",
                            say("archivist.dog.13|a lost childhood friend")),
                    option("archivist.dog.14|how do i leave this place?",
                            say("archivist.dog.15|i wish i knew"),
                            choice(
                                    option("archivist.dog.16|help me please"),
                                    option("archivist.dog.17|we can leave together",
                                            say("archivist.dog.18|you already left me behind once"),
                                            say("archivist.dog.19|but i believe we can start again"),
                                            say("archivist.dog.20|just dont leave me alone again."))))));

    // ---- per-player state ----

    static final class Talk {
        boolean active;
        boolean waiting;
        long nextAt;
        Vec3 origin = Vec3.ZERO;
        UUID dog;
        final Deque<Node> queue = new ArrayDeque<>();
        List<Option> options = List.of();
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Archivist.MOD_ID);
    private static final Supplier<AttachmentType<Talk>> TALK =
            ATTACHMENTS.register("dog_talk", () -> AttachmentType.builder(Talk::new).build());

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) stop(player);
        });
    }

    // ---- flow ----

    static void start(ServerPlayer player, Dog dog) {
        Talk talk = player.getData(TALK);
        if (talk.active) return;
        talk.active = true;
        talk.waiting = false;
        talk.queue.clear();
        talk.queue.addAll(SCRIPT);
        talk.options = List.of();
        talk.origin = player.position();
        talk.dog = dog.getUUID();
        talk.nextAt = player.level().getGameTime() + FIRST_DELAY;
    }

    private static void stop(ServerPlayer player) {
        if (!player.hasData(TALK)) return;
        Talk talk = player.getData(TALK);
        if (!talk.active) return;
        reset(talk);
        PacketDistributor.sendToPlayer(player, new NfyNet.Clear());
    }

    private static void reset(Talk talk) {
        talk.active = false;
        talk.waiting = false;
        talk.queue.clear();
        talk.options = List.of();
    }

    private static void tick(ServerPlayer player) {
        if (!player.hasData(TALK)) return;
        Talk talk = player.getData(TALK);
        if (!talk.active) return;
        if (!player.isAlive() || player.level().dimension() != ChildhoodHome.CHILDHOOD_HOME
                || player.position().distanceToSqr(talk.origin) > MAX_DISTANCE * MAX_DISTANCE) {
            stop(player);
            return;
        }
        if (talk.waiting || player.level().getGameTime() < talk.nextAt) return;

        Node node = talk.queue.poll();
        if (node == null) {
            Events.memory(player).setFlag(ChildhoodHome.TALKED);
            ChildhoodHome.standDogUp(player);
            reset(talk);
            PacketDistributor.sendToPlayer(player, new NfyNet.Clear());
            ChildhoodNet.openInterrogation(player, 0);
        } else if (node instanceof Photo) {
            EventState.Memory memory = Events.memory(player);
            if (!memory.hasFlag(PHOTO_FLAG)) {
                memory.setFlag(PHOTO_FLAG);
                player.addItem(new ItemStack(ModItems.DOG_PHOTO.get()));
            }
        } else if (node instanceof Line line) {
            int ticks = Math.max(MIN_TICKS, Math.min(MAX_TICKS, 30 + 2 * Texts.english(line.text()).length()));
            PacketDistributor.sendToPlayer(player, new NfyNet.Say("dog", line.text(), ticks));
            talk.nextAt = player.level().getGameTime() + ticks + GAP_TICKS;
        } else if (node instanceof Choice choice) {
            talk.options = choice.options();
            talk.waiting = true;
            List<String> labels = new ArrayList<>();
            for (Option option : choice.options()) labels.add(option.label());
            ChildhoodNet.ask(player, labels);
        }
    }

    static void pick(ServerPlayer player, int index) {
        if (!player.hasData(TALK)) return;
        Talk talk = player.getData(TALK);
        if (!talk.active || !talk.waiting || index < 0 || index >= talk.options.size()) return;
        if (player.level().dimension() != ChildhoodHome.CHILDHOOD_HOME) return;
        Option chosen = talk.options.get(index);
        talk.waiting = false;
        talk.options = List.of();
        // the chosen answer's lines go first, then whatever was left (nothing: a choice is always last)
        List<Node> next = new ArrayList<>(chosen.then());
        next.addAll(talk.queue);
        talk.queue.clear();
        talk.queue.addAll(next);
        PacketDistributor.sendToPlayer(player, new NfyNet.Say("you", chosen.label(), YOU_TICKS));
        talk.nextAt = player.level().getGameTime() + YOU_TICKS + GAP_TICKS;
    }
}