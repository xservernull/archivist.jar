// runs the reactor fight, three layers of 100 hp with waves of piglins, blazes and ghasts
// a boss bar tracks the layer and winning gives rewards and opens the exit
package com.xservernull.archivist.block;

import com.xservernull.archivist.Texts;
import com.xservernull.archivist.events.OldNetherReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ReactorCoreEntity extends BlockEntity {
    private static final int LAYER_HP = 100, HIT_DAMAGE = 6, HIT_DELAY = 8, LAST_LAYER = 3;
    private static final int PULSE_EVERY = 60, WATCH_RANGE = 48, ABANDON_AFTER = 20 * 30, WAVE_RANGE = 32;
    private static final String WAVE_TAG = "archivist_reactor_wave";
    private static final String[] ROMAN = {"I", "II", "III"};
    private static final BossEvent.BossBarColor[] COLORS = {
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarColor.RED};

    private record Wave(int piglins, int blazes, int ghasts, int cap) {}

    private static final Wave[] WAVES = {new Wave(1, 1, 0, 6), new Wave(2, 2, 1, 10), new Wave(3, 3, 2, 14)};

    private int hp = LAYER_HP;
    private long lastHit = -HIT_DELAY;
    private long lastSeen;
    private ServerBossEvent bar;

    public ReactorCoreEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.REACTOR_CORE_ENTITY.get(), pos, state);
    }

    private int layer() {
        return getBlockState().getValue(ReactorCoreBlock.LAYER);
    }

    private static Component title(int layer) {
        return Texts.tr("archivist.reactor.title", "netherreactorcore, Layer %s", ROMAN[layer - 1]);
    }

    void hit(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (now - lastHit < HIT_DELAY) return;
        lastHit = now;
        lastSeen = now;
        if (bar == null) begin(server, player);
        bar.addPlayer(player);
        hp = Math.max(0, hp - HIT_DAMAGE);
        bar.setProgress(hp / (float) LAYER_HP);
        server.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6F, 0.7F);
        summon(server, player);
        if (hp > 0) {
            setChanged();
        } else if (layer() < LAST_LAYER) {
            nextLayer(server);
        } else {
            finish(server);
        }
    }

    private void begin(ServerLevel server, ServerPlayer player) {
        bar = new ServerBossEvent(title(layer()), COLORS[layer() - 1], BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setProgress(hp / (float) LAYER_HP);
        player.sendSystemMessage(Texts.tr("archivist.reactor.first", "it noticed you."));
        AABB around = new AABB(worldPosition).inflate(16);
        for (ZombifiedPiglin pilgrim : server.getEntitiesOfClass(ZombifiedPiglin.class, around, p -> p.getTags().contains(OldNetherReactor.PILGRIM))) {
            pilgrim.setNoAi(false);
            pilgrim.setInvulnerable(false);
            pilgrim.setSilent(false);
            pilgrim.setTarget(player);
            pilgrim.setPersistentAngerTarget(player.getUUID());
            pilgrim.startPersistentAngerTimer();
        }
    }

    private void nextLayer(ServerLevel server) {
        int layer = layer() + 1;
        hp = LAYER_HP;
        server.setBlock(worldPosition, getBlockState().setValue(ReactorCoreBlock.LAYER, layer), Block.UPDATE_ALL);
        bar.setName(title(layer));
        bar.setColor(COLORS[layer - 1]);
        bar.setProgress(1.0F);
        OldNetherReactor.lights(server, worldPosition, Math.max(0, 6 - 2 * layer));
        server.playSound(null, worldPosition, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5F, 1.2F);
        for (ServerPlayer near : server.players()) {
            if (near.blockPosition().closerThan(worldPosition, 64)) {
                near.sendSystemMessage(Texts.tr("archivist.reactor.layer", "close... layer %s begins.", ROMAN[layer - 1]));
            }
        }
        setChanged();
    }

    private void finish(ServerLevel server) {
        server.setBlock(worldPosition, getBlockState().setValue(ReactorCoreBlock.DONE, true), Block.UPDATE_ALL);
        List<ServerPlayer> fighters = List.copyOf(bar.getPlayers());
        dropBar();
        clearWave(server);
        OldNetherReactor.lights(server, worldPosition, 4);
        OldNetherReactor.openExit(server, worldPosition);
        server.playSound(null, worldPosition, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F);
        for (ServerPlayer player : fighters) reward(player);
        setChanged();
    }

    private static void reward(ServerPlayer player) {
        give(player, new ItemStack(Items.DIAMOND, 5));
        give(player, named(Items.APPLE, 16, "Apple Archive"));
        give(player, named(Items.COOKED_PORKCHOP, 16, "Porkchop Archive"));
        give(player, new ItemStack(Items.GOLDEN_APPLE));
        give(player, new ItemStack(Items.BEDROCK));
        player.giveExperiencePoints(500);
        player.sendSystemMessage(Texts.tr("archivist.reactor.done", "You survived the waves."));
    }

    private static ItemStack named(Item item, int count, String name) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private void summon(ServerLevel server, ServerPlayer player) {
        Wave wave = WAVES[layer() - 1];
        int alive = server.getEntitiesOfClass(Mob.class, new AABB(worldPosition).inflate(WAVE_RANGE), m -> m.getTags().contains(WAVE_TAG)).size();
        if (alive >= wave.cap()) return;
        for (int i = 0; i < wave.piglins(); i++) spawn(server, EntityType.ZOMBIFIED_PIGLIN, player, 2.5, 0.0, 3.0, 0, 2, true);
        for (int i = 0; i < wave.blazes(); i++) spawn(server, EntityType.BLAZE, player, 4.5, 1.0, 4.0, 0, 2, false);
        for (int i = 0; i < wave.ghasts(); i++) spawn(server, EntityType.GHAST, player, 8.5, 4.0, 5.0, 2, 5, false);
    }

    private <T extends Mob> void spawn(ServerLevel server, EntityType<T> type, ServerPlayer player,
                                       double ahead, double up, double spread, int radius, int height, boolean needsFloor) {
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z);
        forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 at = player.position().add(forward.scale(ahead)).add(side.scale((server.random.nextDouble() - 0.5) * spread)).add(0, up, 0);
        BlockPos feet = openSpot(server, BlockPos.containing(at), radius, height, needsFloor);
        if (feet == null) return;
        T mob = type.create(server);
        if (mob == null) return;
        mob.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, server.random.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(server, server.getCurrentDifficultyAt(feet), MobSpawnType.EVENT, null);
        mob.addTag(WAVE_TAG);
        server.addFreshEntity(mob);
        mob.setTarget(player);
        if (mob instanceof NeutralMob angry) {
            angry.setPersistentAngerTarget(player.getUUID());
            angry.startPersistentAngerTimer();
        }
    }

    private static BlockPos openSpot(ServerLevel level, BlockPos base, int radius, int height, boolean needsFloor) {
        for (int i = 0; i <= 6; i++) {
            for (int dir : i == 0 ? new int[] {0} : new int[] {i, -i}) {
                BlockPos feet = base.above(dir);
                if (roomAt(level, feet, radius, height, needsFloor)) return feet;
            }
        }
        return null;
    }

    private static boolean roomAt(ServerLevel level, BlockPos feet, int radius, int height, boolean needsFloor) {
        if (needsFloor && !level.getBlockState(feet.below()).isSolid()) return false;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-radius, 0, -radius), feet.offset(radius, height - 1, radius))) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        }
        return true;
    }

    private void clearWave(ServerLevel server) {
        for (Mob mob : server.getEntitiesOfClass(Mob.class, new AABB(worldPosition).inflate(WAVE_RANGE), m -> m.getTags().contains(WAVE_TAG))) {
            server.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + mob.getBbHeight() / 2.0, mob.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            mob.discard();
        }
    }

    private void dropBar() {
        if (bar == null) return;
        bar.removeAllPlayers();
        bar = null;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ReactorCoreEntity core) {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % PULSE_EVERY != 0) return;
        core.pulse(server);
    }

    private void pulse(ServerLevel server) {
        if (getBlockState().getValue(ReactorCoreBlock.DONE)) return;
        long now = server.getGameTime();
        if (bar != null) {
            for (ServerPlayer fighter : List.copyOf(bar.getPlayers())) {
                if (!fighter.isAlive() || fighter.level() != server || !fighter.blockPosition().closerThan(worldPosition, WATCH_RANGE * 1.5)) {
                    bar.removePlayer(fighter);
                }
            }
            if (!bar.getPlayers().isEmpty()) {
                lastSeen = now;
            } else if (now - lastSeen > ABANDON_AFTER) {
                hp = LAYER_HP;
                dropBar();
                clearWave(server);
                setChanged();
            }
        }
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        Player near = server.getNearestPlayer(x, y, z, WATCH_RANGE, true);
        if (near == null) return;
        server.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.5F + 0.25F * layer());
        server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.8, z, 6, 0.3, 0.3, 0.3, 0.02);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hp = tag.contains("hp") ? tag.getInt("hp") : LAYER_HP;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("hp", hp);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        dropBar();
    }
}