// the events framework (events.md, plan step 2): the registry of quickevents, one tick loop,
// one running event per player, and the scheduler.

package com.xservernull.archivist.events;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.events.library.EndPortalDetector;
import com.xservernull.archivist.events.library.LibraryEntry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class Events {
    private Events() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final Map<String, QuickEvent> REGISTRY = new LinkedHashMap<>();

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Archivist.MOD_ID);

    private static final Codec<EventState.Memory> MEMORY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(m -> Map.copyOf(m.cooldowns())),
            Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(m -> List.copyOf(m.seen())),
            Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(m -> List.copyOf(m.flags()))
    ).apply(i, (cooldowns, seen, flags) -> {
        EventState.Memory memory = new EventState.Memory();
        memory.restore(cooldowns, seen, flags);
        return memory;
    }));

    private static final Supplier<AttachmentType<EventState.Memory>> MEMORY = ATTACHMENTS.register("event_memory",
            () -> AttachmentType.builder(EventState.Memory::new).serialize(MEMORY_CODEC).copyOnDeath().build());

    private static final Supplier<AttachmentType<EventState.Running>> RUNNING = ATTACHMENTS.register("event_running",
            () -> AttachmentType.builder(EventState.Running::new).build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        FriendSwarm.register(modBus);
        LibraryCorruption.register(modBus);
        TheEndTalk.register(modBus);
        add(new GreyscalePulse());
        add(new EchoChamberTina());
        add(new ToastIntro());
        add(new MansionHunt());
        add(new QuietPlaceHunt());
        add(new HerobrineStalk());
        add(new StatueWalk());
        add(new InfdevEntry.Patch());
        add(new InfdevEntry.Fall());
        add(new InfdevPyramid.Waves());
        add(new LibraryCorruption());
        add(new MissingPlayer());
        add(new M4kbLagSpike());
        add(new M4kbTiny());
        add(new XorMirror());
        add(new XorFlip());
        add(new MarkerHunt("m4kb_pixel_hunt", net.minecraft.world.level.block.Blocks.GLOWSTONE,
                "a pixel doesn't belong. find it.", "pixel found.",
                p -> Gifts.give(p, Gifts.M4KB_NOTES)));
        add(new MarkerHunt("xor_bit_hunt", net.minecraft.world.level.block.Blocks.SEA_LANTERN,
                "§7one bit is set. find it.", "§7cleared.",
                p -> Gifts.give(p, Gifts.XOR_NOTES)));
        add(new M4kbJumpQuota());
        add(new M4kbLowPoly());
        add(new XorBitMath());
        add(new XorZeroCall());
        add(new BlockTrail("xor_path", CubeBlocks::xor));
        add(new BlockTowers("xor_towers", CubeBlocks::xor));
        add(new BlockTowers("m4kb_towers", CubeBlocks::m4kb));
        add(new OldNetherReactorCall());
        add(new OldNetherKeepMoving());
        add(new OldNetherPiglinToll());
        add(new DimensionProgress.Summon());
        add(new TheEndTalk());
        MansionWorld.listen();
        EchoChamberTina.listen();
        QuietPlaceHunt.listen();
        QuietPlaceShrine.listen();
        HerobrineStalk.listen();
        AetherPortal.listen();
        AetherEntry.listen();
        ChildhoodHome.listen();
        DimensionEntries.listen();
        OldNetherEntry.listen();
        OldNetherPiglins.listen();
        M4kbPath.listen();
        OldNetherReactor.listen();
        RespawnRules.listen();
        FireRules.listen();
        ArchivistCommands.listen();
        EndPortalDetector.listen();
        LibraryEntry.register();
        DimensionMobs.listen();
        DogTalk.register(modBus);
        ChildhoodHomePlacer.listen();
        FarLandsTemple.listen();
        GlobalPrayer.listen();
        ThemSpawner.listen();
        ThemDrops.listen();
        HimEncounter.listen();
        BedrockAmbience.listen();
        CopySpawner.listen();
        DinnerboneSpawn.listen();
        FriendSwarm.listen();
        RetroMovement.listen();
        InfdevWorld.listen();
        InfdevEntry.listen();
        InfdevPyramid.listen();
        LibraryCorruption.listen();
        RubyDung.listen();
        WorldQuirks.listen();
        DragonFreeze.listen();
        TheEnd.listen();
        TheEndTalk.listen();
        EndingBedHandler.listen();
        ObjectiveHud.listen();
        DimensionProgress.listen();
        AdminPanelGrant.listen();
        ScheduleLoader.register();
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> stopIfPlayer(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> stopIfPlayer(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> stopIfPlayer(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> stopIfPlayer(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> registerCommand(e.getDispatcher()));
    }

    private static void add(QuickEvent event) {
        if (REGISTRY.putIfAbsent(event.id(), event) != null) LOG.error("[events] two events are called '{}'", event.id());
    }

    public static boolean has(String id) { return REGISTRY.containsKey(id); }

    public static List<String> ids() { return List.copyOf(REGISTRY.keySet()); }

    // ---- per-player state ----

    public static EventState.Memory memory(ServerPlayer player) { return player.getData(MEMORY); }

    public static String running(ServerPlayer player) {
        return player.hasData(RUNNING) ? player.getData(RUNNING).id : null;
    }

    // ---- starting and stopping ----

    public static boolean start(ServerPlayer player, String id) {
        return begin(player, id, 0);
    }

    public static void stop(ServerPlayer player) {
        if (!player.hasData(RUNNING)) return;
        EventState.Running running = player.getData(RUNNING);
        if (running.id == null) return;
        QuickEvent event = REGISTRY.get(running.id);
        running.id = null;
        running.age = 0;
        if (event == null) return;
        try {
            event.stop(player);
        } catch (RuntimeException e) {
            LOG.error("[events] {} failed in stop", event.id(), e);
        }
    }

    private static void stopIfPlayer(Object entity) {
        if (entity instanceof ServerPlayer player) stop(player);
    }

    private static boolean begin(ServerPlayer player, String id, int cooldownTicks) {
        QuickEvent event = REGISTRY.get(id);
        if (event == null) return false;
        stop(player);
        EventState.Running running = player.getData(RUNNING);
        EventState.Memory memory = memory(player);
        running.id = id;
        running.age = 0;
        memory.markSeen(id);
        if (cooldownTicks > 0) memory.setCooldown(id, player.level().getGameTime() + cooldownTicks);
        try {
            event.start(player);
        } catch (RuntimeException e) {
            LOG.error("[events] {} failed in start", id, e);
            running.id = null;
            return false;
        }
        LOG.info("[events] {} started '{}'", player.getName().getString(), id);
        return true;
    }

    // ---- the tick loop and the scheduler ----

    private static void tick(ServerPlayer player) {
        EventState.Running running = player.getData(RUNNING);
        if (running.id != null) {
            QuickEvent event = REGISTRY.get(running.id);
            if (event == null || !player.isAlive()) {
                stop(player);
                return;
            }
            running.age++;
            boolean over;
            try {
                over = event.tick(player, running.age);
            } catch (RuntimeException e) {
                LOG.error("[events] {} failed in tick, stopping it", event.id(), e);
                over = true;
            }
            if (over) stop(player);
            return;
        }
        Schedule schedule = ScheduleLoader.schedule();
        if (schedule.isEmpty() || !player.isAlive() || player.isSpectator()) return;
        long now = player.level().getGameTime();
        if (running.nextRoll == 0) running.nextRoll = now + interval(player, schedule);
        if (now < running.nextRoll) return;
        running.nextRoll = now + interval(player, schedule);
        roll(player, schedule, now);
    }

    private static int interval(ServerPlayer player, Schedule schedule) {
        return schedule.minTicks() + player.getRandom().nextInt(schedule.maxTicks() - schedule.minTicks() + 1);
    }

    private static void roll(ServerPlayer player, Schedule schedule, long now) {
        EventState.Memory memory = memory(player);
        List<Schedule.Entry> eligible = new ArrayList<>();
        int total = 0;
        for (Schedule.Entry entry : schedule.forDimension(player.level().dimension().location().toString())) {
            if (!REGISTRY.containsKey(entry.event()) || memory.cooldownUntil(entry.event()) > now) continue;
            eligible.add(entry);
            total += entry.weight();
        }
        if (total <= 0) return;
        int pick = player.getRandom().nextInt(total);
        for (Schedule.Entry entry : eligible) {
            pick -= entry.weight();
            if (pick < 0) {
                if (player.getRandom().nextDouble() < entry.chance()) begin(player, entry.event(), entry.cooldownTicks());
                return;
            }
        }
    }

    // ---- test command ----

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Archivist.MOD_ID).then(Commands.literal("event")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal("events: " + String.join(", ", ids())), false);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            EventState.Memory memory = memory(player);
                            String running = running(player);
                            context.getSource().sendSuccess(() -> Component.literal("running: " + (running == null ? "nothing" : running)
                                    + ", seen: " + memory.seen() + ", flags: " + memory.flags()), false);
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            stop(player);
                            EventNet.stopFx(player);
                            return 1;
                        }))
                        .then(Commands.literal("run").then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(REGISTRY.keySet(), builder))
                                .executes(context -> {
                                    String id = StringArgumentType.getString(context, "id");
                                    if (start(context.getSource().getPlayerOrException(), id)) return 1;
                                    context.getSource().sendFailure(Component.literal("no event '" + id + "' (known: " + String.join(", ", ids()) + ")"));
                                    return 0;
                                }))))
                .then(Commands.literal("video")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("stop").executes(context -> {
                            EventNet.stopVideo(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.argument("clip", StringArgumentType.word()).executes(context -> {
                            EventNet.video(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "clip"));
                            return 1;
                        }))));
    }
}