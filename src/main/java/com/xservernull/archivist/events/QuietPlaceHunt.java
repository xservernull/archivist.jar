// the quiet place hunt (old herobrinequietplaceevent), one per player instead of one global
// state.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.entity.Herobrine;
import com.xservernull.archivist.entity.Mobs;
import com.xservernull.archivist.item.DetectorMapItem;
import com.xservernull.archivist.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

final class QuietPlaceHunt implements QuickEvent {
    static final String ID = "quietplace_hunt";
    static final String DONE = "quietplace_done";

    private static final Logger LOG = LogUtils.getLogger();

    private static final String PIECE = "qp_piece:";
    private static final String TAG = "archivist_qp_";
    private static final String PLACED = "qp_placed", MUSIC_AT = "qp_music";
    private static final String GOLD_N = "qp_gold", ROCK_N = "qp_rock", TORCH_N = "qp_torch";

    private static final int PLACE_FROM = 60, PLACE_EVERY = 40, PLACE_UNTIL = 460;
    private static final int ALARM_AT = 380, CHASE_AT = 500, MUSIC_EVERY = 160;
    private static final int GOLD = 9, ROCK = 1, TORCHES = 4, TOTAL = GOLD + ROCK + TORCHES;
    private static final double MIN_DIST = 50.0, MAX_DIST = 100.0;
    private static final float ALARM_VOLUME = 6.0F, INTERMISSION_VOLUME = 6.0F, MUSIC_VOLUME = 0.1F;

    @Override public String id() { return ID; }

    static void listen() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, (PlayerEvent.PlayerChangedDimensionEvent e) -> begin(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> begin(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, (PlayerEvent.PlayerRespawnEvent e) -> begin(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(QuietPlaceHunt::onBreak);
        NeoForge.EVENT_BUS.addListener(QuietPlaceHunt::onJoin);
        NeoForge.EVENT_BUS.addListener(QuietPlaceHunt::onUseDetector);
    }

    private static void begin(Player entity) {
        if (!(entity instanceof ServerPlayer player) || !player.level().dimension().equals(Archivist.QUIET_PLACE)) return;
        if (!Events.memory(player).hasFlag(DONE)) Events.start(player, ID);
    }

    // ---------------------------------------------------------------- in and out

    static void enter(ServerPlayer player) {
        if (Warp.toQuietPlace(player)) LOG.info("[quietplace] {} entered", player.getGameProfile().getName());
    }

    static void complete(ServerPlayer player) {
        Events.memory(player).setFlag(DONE);
        Events.stop(player);
        LOG.info("[quietplace] {} finished the shrine", player.getGameProfile().getName());
        Warp.toOverworld(player);
    }

    // ---------------------------------------------------------------- the event

    @Override
    public void start(ServerPlayer player) {
        clear(player);
        giveKit(player);
        ServerLevel level = player.serverLevel();
        spawnHerobrine(level, player, 3.0);
        player.playNotifySound(Sounds.INTERMISSION.get(), SoundSource.MASTER, INTERMISSION_VOLUME, 1.0F);
        LOG.info("[quietplace] hunt started for {}", player.getGameProfile().getName());
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(Archivist.QUIET_PLACE)) return true;
        EventState.Memory memory = Events.memory(player);
        if (age >= PLACE_FROM && age <= PLACE_UNTIL && age % PLACE_EVERY == 0 && memory.cooldownUntil(PLACED) < TOTAL) {
            placePieces(level, player, memory);
        }
        if (age == ALARM_AT) player.playNotifySound(Sounds.ALARM.get(), SoundSource.MASTER, ALARM_VOLUME, 1.0F);
        Herobrine hb = find(level, player);
        if (age == CHASE_AT) {
            hb = hb != null && hb.distanceToSqr(player) < 40.0 * 40.0 ? hb : relocate(level, player, hb);
            memory.setCooldown(MUSIC_AT, 0);
        }
        if (age >= CHASE_AT) {
            if (hb == null && age % 100 == 0) hb = spawnHerobrine(level, player, 20.0);
            if (hb != null) chase(level, player, hb, memory);
        }
        if (age % 20 == 0) {
            hud(player, hb, age);
            if (DetectorMapItem.isHeld(player)) EventNet.detector(player, pieces(memory));
        }
        return false;
    }

    @Override
    public void stop(ServerPlayer player) {
        clear(player);
    }

    private static void chase(ServerLevel level, ServerPlayer player, Herobrine hb, EventState.Memory memory) {
        if (hb.isOnFire()) { // on fire he stands still (old: paused by fire)
            if (!hb.isNoAi()) {
                hb.setNoAi(true);
                hb.setTarget(null);
            }
            return;
        }
        if (hb.isNoAi()) hb.setNoAi(false);
        if (hb.getTarget() != player) hb.setTarget(player);
        long now = level.getGameTime();
        if (now >= memory.cooldownUntil(MUSIC_AT)) {
            SoundEvent track = pieces(memory).isEmpty() ? Sounds.CHASEMUSIC2.get() : Sounds.CHASEMUSIC1.get();
            player.playNotifySound(track, SoundSource.MASTER, MUSIC_VOLUME, 1.0F);
            memory.setCooldown(MUSIC_AT, now + MUSIC_EVERY);
        }
    }

    // ---------------------------------------------------------------- Herobrine

    private static String tag(ServerPlayer player) { return TAG + player.getUUID(); }

    private static Herobrine find(ServerLevel level, ServerPlayer player) {
        String tag = tag(player);
        List<? extends Herobrine> found = level.getEntities(EntityTypeTest.forClass(Herobrine.class), h -> h.getTags().contains(tag));
        return found.isEmpty() ? null : found.get(0);
    }

    private static Herobrine spawnHerobrine(ServerLevel level, ServerPlayer player, double dist) {
        Herobrine hb = Mobs.HEROBRINE.get().create(level);
        if (hb == null) return null;
        Vec3 at = spot(level, player, dist);
        hb.moveTo(at.x, at.y, at.z, player.getYRot() + 180.0F, 0.0F);
        hb.setInvulnerable(true);
        hb.setNoAi(true);
        hb.addTag(tag(player));
        level.addFreshEntity(hb);
        return hb;
    }

    private static Herobrine relocate(ServerLevel level, ServerPlayer player, Herobrine hb) {
        if (hb == null) return spawnHerobrine(level, player, 20.0);
        Vec3 at = spot(level, player, 20.0);
        hb.teleportTo(at.x, at.y, at.z);
        return hb;
    }

    private static Vec3 spot(ServerLevel level, ServerPlayer player, double dist) {
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            if (!Ground.isLoaded(level, x, z)) continue;
            var y = Ground.standY(level, x, player.getBlockY() + 3, z);
            if (y.isPresent()) return new Vec3(x + 0.5, y.getAsInt(), z + 0.5);
        }
        return player.position().add(3.0, 0.0, 3.0);
    }

    // ---------------------------------------------------------------- shrine pieces

    private static void placePieces(ServerLevel level, ServerPlayer player, EventState.Memory memory) {
        int placed = (int) memory.cooldownUntil(PLACED);
        RandomSource random = player.getRandom();
        while (placed < TOTAL) {
            BlockState state = placed < GOLD ? Blocks.GOLD_BLOCK.defaultBlockState()
                    : placed < GOLD + ROCK ? Blocks.NETHERRACK.defaultBlockState()
                      : Blocks.REDSTONE_TORCH.defaultBlockState();
            BlockPos at = pieceSpot(level, player, random);
            if (at == null) break; // chunks not loaded yet, try again in 2 s
            level.setBlock(at, state, 3);
            memory.setFlag(key(at));
            memory.setCooldown(PLACED, ++placed);
        }
        if (placed >= TOTAL) {
            LOG.info("[quietplace] {} shrine pieces placed for {}", TOTAL, player.getGameProfile().getName());
            player.sendSystemMessage(Texts.tr("archivist.msg.hold_map", "\u00a77Hold the detector map."));
        }
    }

    private static BlockPos pieceSpot(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = MIN_DIST + random.nextDouble() * (MAX_DIST - MIN_DIST);
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            if (!Ground.isLoaded(level, x, z)) continue;
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            BlockPos below = at.below();
            if (!level.getBlockState(at).isAir() || level.getBlockState(below).liquid()
                    || !level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) continue;
            return at;
        }
        return null;
    }

    private static String key(BlockPos pos) { return PIECE + pos.getX() + "," + pos.getY() + "," + pos.getZ(); }

    private static List<BlockPos> pieces(EventState.Memory memory) {
        List<BlockPos> out = new ArrayList<>();
        for (String flag : memory.flags()) {
            if (!flag.startsWith(PIECE)) continue;
            String[] xyz = flag.substring(PIECE.length()).split(",");
            try {
                out.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
            } catch (RuntimeException e) {
                LOG.warn("[quietplace] bad piece flag '{}'", flag);
            }
        }
        return out;
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer player) || !player.level().dimension().equals(Archivist.QUIET_PLACE)) return;
        EventState.Memory memory = Events.memory(player);
        if (!memory.hasFlag(key(e.getPos()))) return;
        memory.clearFlag(key(e.getPos()));
        count(player, memory, e.getState());
        if (pieces(memory).isEmpty() && ID.equals(Events.running(player))) {
            player.sendSystemMessage(Texts.tr("archivist.msg.collected", "You collected everything! Build the shrine now and right-click it."));
            memory.setCooldown(MUSIC_AT, 0); // the building track starts at once
        }
    }

    private static void count(ServerPlayer player, EventState.Memory memory, BlockState state) {
        String key = state.is(Blocks.GOLD_BLOCK) ? GOLD_N : state.is(Blocks.NETHERRACK) ? ROCK_N
                : state.is(Blocks.REDSTONE_TORCH) ? TORCH_N : null;
        if (key == null) return;
        memory.setCooldown(key, memory.cooldownUntil(key) + 1);
        sendProgress(player, memory);
    }

    private static void sendProgress(ServerPlayer player, EventState.Memory memory) {
        if (player.connection == null) return; // stop() can run on a player who is logging out
        EventNet.shrine(player, (int) memory.cooldownUntil(GOLD_N), (int) memory.cooldownUntil(ROCK_N),
                (int) memory.cooldownUntil(TORCH_N));
    }

    // ---------------------------------------------------------------- HUD (action bar) and the detector

    private static void hud(ServerPlayer player, Herobrine hb, int age) {
        if (age < CHASE_AT) {
            EventNet.herobrine(player, 1, 0);
            return;
        }
        int bars = hb == null ? 0 : Mth.clamp((int) (9 - hb.distanceTo(player) / 8.0), 1, 8);
        EventNet.herobrine(player, 2, bars);
    }

    private static void onUseDetector(PlayerInteractEvent.RightClickItem e) {
        if (e.getLevel().isClientSide() || !(e.getItemStack().getItem() instanceof DetectorMapItem)) return;
        e.getEntity().sendSystemMessage(Texts.tr("archivist.msg.shrine_hint", "\u00a76The shrine: \u00a7r9 gold blocks in a 3x3 square. A netherrack on the middle one. "
                + "A torch on each of the four side ones, not the corners. Set the netherrack on fire."));
    }

    private static void giveKit(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        if (!has(inventory, stack -> stack.getItem() instanceof DetectorMapItem)) {
            give(player, new ItemStack(ModItems.DETECTOR_MAP.get()));
            give(player, new ItemStack(Items.FLINT_AND_STEEL));
        }
        if (!has(inventory, stack -> stack.is(Items.NETHERITE_PICKAXE))) give(player, new ItemStack(Items.NETHERITE_PICKAXE));
    }

    private static boolean has(Inventory inventory, Predicate<ItemStack> test) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (test.test(inventory.getItem(slot))) return true;
        }
        return false;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    // ---------------------------------------------------------------- cleanup

    private static void clear(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        ServerLevel level = player.getServer() == null ? null : player.getServer().getLevel(Archivist.QUIET_PLACE);
        if (level != null) {
            String tag = tag(player);
            for (Herobrine hb : level.getEntities(EntityTypeTest.forClass(Herobrine.class), h -> h.getTags().contains(tag))) hb.discard();
            for (BlockPos pos : pieces(memory)) {
                if (!level.isLoaded(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.NETHERRACK) || state.is(Blocks.REDSTONE_TORCH)) level.removeBlock(pos, false);
            }
        }
        for (BlockPos pos : pieces(memory)) memory.clearFlag(key(pos));
        memory.setCooldown(PLACED, 0);
        memory.setCooldown(MUSIC_AT, 0);
        memory.setCooldown(GOLD_N, 0);
        memory.setCooldown(ROCK_N, 0);
        memory.setCooldown(TORCH_N, 0);
        sendProgress(player, memory);
        if (player.connection != null) {
            EventNet.detector(player, List.of());
            EventNet.herobrine(player, 0, 0);
        }
    }

    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Herobrine hb)) return;
        for (String tag : hb.getTags()) {
            if (!tag.startsWith(TAG)) continue;
            ServerPlayer owner = null;
            try {
                owner = e.getLevel().getServer().getPlayerList().getPlayer(UUID.fromString(tag.substring(TAG.length())));
            } catch (RuntimeException ignored) {
                // not one of ours
            }
            if (owner == null || !ID.equals(Events.running(owner))) e.setCanceled(true);
            return;
        }
    }
}