// the neverforgetyou event, spawns hope when a player enters the dimension and handles orbs,
// the book and cleanup per player state is stored as attachments, nothing static per player

package com.xservernull.archivist.events.neverforgetyou;

import com.xservernull.archivist.Texts;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.GlowOrb;
import com.xservernull.archivist.entity.Hope;
import com.xservernull.archivist.entity.Mobs;
import com.xservernull.archivist.entity.Owned;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class NfyEvent {
    private NfyEvent() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final int ENTRY_DELAY = 20;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Archivist.MOD_ID);

    private static final Codec<NfyState.Progress> PROGRESS_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.listOf().fieldOf("orbs").forGetter(p -> List.copyOf(p.collected())),
            Codec.BOOL.fieldOf("complete").forGetter(NfyState.Progress::isComplete),
            Codec.INT.listOf().optionalFieldOf("messages", List.of()).forGetter(p -> List.copyOf(p.messages())),
            Codec.INT.listOf().optionalFieldOf("quotes", List.of()).forGetter(p -> List.copyOf(p.quotes()))
    ).apply(i, (orbs, complete, messages, quotes) -> {
        NfyState.Progress progress = new NfyState.Progress();
        progress.restore(orbs, complete, messages, quotes);
        return progress;
    }));

    private static final Supplier<AttachmentType<NfyState.Progress>> PROGRESS = ATTACHMENTS.register("nfy_progress",
            () -> AttachmentType.builder(NfyState.Progress::new).serialize(PROGRESS_CODEC).copyOnDeath().build());

    private static final Supplier<AttachmentType<NfyState.Dialogue>> DIALOGUE = ATTACHMENTS.register("nfy_dialogue",
            () -> AttachmentType.builder(NfyState.Dialogue::new).build());

    public static NfyState.Progress progress(ServerPlayer player) { return player.getData(PROGRESS); }

    static NfyState.Dialogue dialogueOrNull(ServerPlayer player) {
        return player.hasData(DIALOGUE) ? player.getData(DIALOGUE) : null;
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NfyLoader.register();
        NfyNet.register(modBus);
        GlowOrb.setCollector(NfyEvent::onOrbCollected);
        Hope.setBookHandler(NfyEvent::onBookReached);
        NfyDialogue.register();
        NeoForge.EVENT_BUS.addListener(NfyEvent::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(NfyEvent::onLogout);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> registerCommand(e.getDispatcher()));
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getFrom().equals(Archivist.NEVER_FORGET_YOU)) {
            cleanup(player);
            NfyNet.clear(player);
        }
        if (event.getTo().equals(Archivist.NEVER_FORGET_YOU)) start(player);
    }

    private static boolean start(ServerPlayer player) {
        if (NfyLoader.script() == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        discardOwned(level, player.getUUID());
        progress(player).resetOrbs();
        Hope hope = Mobs.HOPE.get().create(level);
        if (hope == null) {
            LOG.error("[nfy] could not create Hope for {}", player.getName().getString());
            return false;
        }
        BlockPos at = player.blockPosition().offset(3, 0, 0);
        hope.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        hope.setOwner(player.getUUID());
        hope.setPersistenceRequired();
        level.addFreshEntity(hope);

        NfyState.Dialogue dialogue = player.getData(DIALOGUE);
        dialogue.clear();
        dialogue.inSession = true;
        dialogue.begin(NfyState.Dialogue.ENTRY, level.getGameTime(), NfyDialogue.LINE_DELAY - ENTRY_DELAY);
        return true;
    }

    public static boolean restart(ServerPlayer player) {
        if (!player.level().dimension().equals(Archivist.NEVER_FORGET_YOU)) return false;
        cleanup(player);
        NfyNet.clear(player);
        return start(player);
    }

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Archivist.MOD_ID).then(Commands.literal("nfy")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("restart").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (restart(player)) return 1;
                    context.getSource().sendFailure(Component.literal(
                            "enter archivist:neverforgetyou first (and check the log for [nfy] problems)"));
                    return 0;
                }))));
    }

    private static void onOrbCollected(ServerPlayer player, GlowOrb orb) {
        int index = orb.index();
        NfyState.Dialogue dialogue = dialogueOrNull(player);
        if (index >= GlowOrb.MESSAGE_BASE) {
            NfyScript script = NfyLoader.script();

            if (script != null && dialogue != null && dialogue.inSession && dialogue.messageOrbsOn) {
                NfyMessageOrbs.collected(player, orb, script);
            }
            return;
        }
        if (index >= NfyScript.ORB_COUNT) return;
        if (NfyLoader.script() == null || dialogue == null || !dialogue.inSession || dialogue.sequence != NfyState.Dialogue.NONE) {
            return;
        }
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD, orb.getX(), orb.getY(), orb.getZ(), 30, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, orb.getX(), orb.getY(), orb.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 0.8F + 0.1F * index);

        NfyState.Progress progress = progress(player);
        progress.collect(index);
        progress.collectQuote(index);
        player.displayClientMessage(Component.literal(progress.count() + " / " + NfyScript.ORB_COUNT), true);
        if (progress.count() >= NfyScript.ORB_COUNT && !progress.isComplete()) {
            progress.markComplete();
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_EMERGE,
                    SoundSource.AMBIENT, 1.0F, 0.5F);
        }
        dialogue.begin(NfyState.Dialogue.ORB_BASE + index, level.getGameTime(), NfyDialogue.LINE_DELAY - ENTRY_DELAY);
    }

    static void sendLog(ServerPlayer player) {
        NfyScript script = NfyLoader.script();
        List<String> lines = new ArrayList<>();
        if (script != null) {
            for (int i : progress(player).quotes()) {
                if (i >= 0 && i < script.orbQuotes().size()) lines.add(script.orbQuotes().get(i));
            }
            for (int i : progress(player).messages()) {
                if (i >= 0 && i < script.messageOrbs().size()) lines.add(script.messageOrbs().get(i));
            }
        }
        NfyNet.log(player, lines);
    }

    private static void onBookReached(ServerPlayer player, Hope hope) {
        NfyScript script = NfyLoader.script();
        NfyState.Dialogue dialogue = dialogueOrNull(player);
        if (script == null || dialogue == null || !dialogue.inSession || dialogue.bookGiven) return;
        dialogue.bookGiven = true;
        give(player, writtenBook(script.book()));
        give(player, new ItemStack(Items.WRITABLE_BOOK));
        dialogue.begin(NfyState.Dialogue.FINAL, player.serverLevel().getGameTime(), NfyDialogue.LINE_DELAY - ENTRY_DELAY);
    }

    private static ItemStack writtenBook(NfyScript.Book book) {
        List<Filterable<Component>> pages = new ArrayList<>();
        if (!book.warningPage().isEmpty()) pages.add(page(book.warningPage(), book.italic()));
        for (String text : book.pages()) pages.add(page(text, book.italic()));
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(book.title()), book.author(), 0, pages, true));
        return stack;
    }

    private static Filterable<Component> page(String text, boolean italic) {
        Component component = Texts.component(text);
        if (italic) component = Texts.component(text).withStyle(ChatFormatting.ITALIC);
        return Filterable.passThrough(component);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cleanup(player);
    }

    static void cleanup(ServerPlayer player) {
        if (player.getServer() != null) {
            ServerLevel level = player.getServer().getLevel(Archivist.NEVER_FORGET_YOU);
            if (level != null) discardOwned(level, player.getUUID());
        }
        NfyState.Dialogue dialogue = dialogueOrNull(player);
        if (dialogue != null) dialogue.clear();
    }

    private static void discardOwned(ServerLevel level, UUID owner) {
        List<Entity> found = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Owned owned && (e instanceof Hope || e instanceof GlowOrb) && owned.isOwnedBy(owner)) found.add(e);
        }
        found.forEach(Entity::discard);
    }
}
