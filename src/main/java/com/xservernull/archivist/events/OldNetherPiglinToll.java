// old_nether piglin toll: a silent pilgrim stands 5 blocks ahead and watches you.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Ground;
import com.xservernull.archivist.Texts;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

import java.util.OptionalInt;

final class OldNetherPiglinToll implements QuickEvent {
    private static final int DURATION = 20 * 40;

    @Override public String id() { return "old_nether_piglin_toll"; }

    private static String tag(ServerPlayer player) {
        return "archivist_toll_" + player.getUUID();
    }

    private static boolean gold(ItemStack stack) {
        return stack.is(Items.GOLD_INGOT) || stack.is(Items.RAW_GOLD) || stack.is(Items.GOLD_BLOCK);
    }

    @Override
    public void start(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        for (int attempt = 0; attempt < 8; attempt++) {
            double turn = (attempt - 4) * 0.45;
            double cos = Math.cos(turn), sin = Math.sin(turn);
            Vec3 dir = new Vec3(flat.x * cos - flat.z * sin, 0, flat.x * sin + flat.z * cos);
            int x = (int) Math.floor(player.getX() + dir.x * 5.0), z = (int) Math.floor(player.getZ() + dir.z * 5.0);
            if (!Ground.isLoaded(level, x, z)) continue;
            OptionalInt y = Ground.standY(level, x, player.getBlockY() + 3, z);
            if (y.isEmpty() || Math.abs(y.getAsInt() - player.getBlockY()) > 3) continue;
            ZombifiedPiglin pilgrim = EntityType.ZOMBIFIED_PIGLIN.create(level);
            if (pilgrim == null) return;
            pilgrim.moveTo(x + 0.5, y.getAsInt(), z + 0.5, 0.0F, 0.0F);
            pilgrim.finalizeSpawn(level, level.getCurrentDifficultyAt(pilgrim.blockPosition()), MobSpawnType.EVENT, null);
            pilgrim.setNoAi(true);
            pilgrim.setInvulnerable(true);
            pilgrim.setSilent(true);
            pilgrim.setPersistenceRequired();
            pilgrim.addTag(tag(player));
            level.addFreshEntity(pilgrim);
            pilgrim.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
            player.sendSystemMessage(Texts.tr("archivist.event.old_nether_piglin_toll.start", "a pilgrim wants gold. drop it at its feet."));
            return;
        }
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        ServerLevel level = player.serverLevel();
        ZombifiedPiglin pilgrim = find(level, player);
        if (pilgrim == null) return true;
        if (age % 5 == 0) pilgrim.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, pilgrim.getBoundingBox().inflate(2.5), i -> gold(i.getItem()))) {
            drop.getItem().shrink(1);
            if (drop.getItem().isEmpty()) drop.discard();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pilgrim.getX(), pilgrim.getY() + 1.2, pilgrim.getZ(), 12, 0.4, 0.5, 0.4, 0.02);
            level.playSound(null, pilgrim.blockPosition(), SoundEvents.PIGLIN_ADMIRING_ITEM, SoundSource.HOSTILE, 1.0F, 0.8F);
            give(player, new ItemStack(Items.QUARTZ, 6));
            give(player, new ItemStack(Items.GLOWSTONE_DUST, 4));
            player.sendSystemMessage(Texts.tr("archivist.event.old_nether_piglin_toll.win", "the pilgrim keeps the gold and nods."));
            return true;
        }
        if (age >= DURATION) {
            player.sendSystemMessage(Texts.tr("archivist.event.old_nether_piglin_toll.fail", "the pilgrim turns away."));
            return true;
        }
        return false;
    }

    @Override
    public void stop(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        String tag = tag(player);
        for (ServerLevel level : server.getAllLevels()) {
            for (ZombifiedPiglin p : level.getEntities(EntityTypeTest.forClass(ZombifiedPiglin.class), e -> e.getTags().contains(tag))) {
                level.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                p.discard();
            }
        }
    }

    private static ZombifiedPiglin find(ServerLevel level, ServerPlayer player) {
        String tag = tag(player);
        return level.getEntitiesOfClass(ZombifiedPiglin.class, player.getBoundingBox().inflate(48.0), p -> p.getTags().contains(tag))
                .stream().findFirst().orElse(null);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
