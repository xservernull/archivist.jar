// the copies that wander archivist:bedrock_far_lands (old bedrockfarlandsambience).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.CopyMob;
import com.xservernull.archivist.entity.Mobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.function.Supplier;

public final class BedrockAmbience {
    private BedrockAmbience() {}

    private static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "bedrock_far_lands"));
    private static final String KEY = "bedrock_copies";
    private static final int INTERVAL = 300, MAX_NEAR = 2;
    private static final double MIN_RADIUS = 10.0, MAX_RADIUS = 24.0, DESPAWN_RADIUS = 40.0, HOSTILE_CHANCE = 0.12;

    public static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
    }

    private static void tick(ServerPlayer player) {
        if (!player.level().dimension().equals(DIMENSION)) return;
        if (!player.isAlive() || player.isSpectator() || player.isCreative()) return;
        long now = player.level().getGameTime();
        EventState.Memory memory = Events.memory(player);
        if (now < memory.cooldownUntil(KEY)) return;
        memory.setCooldown(KEY, now + INTERVAL);

        ServerLevel level = player.serverLevel();
        for (CopyMob copy : level.getEntities(EntityTypeTest.forClass(CopyMob.class), c -> level.getNearestPlayer(c, DESPAWN_RADIUS) == null)) {
            copy.discard();
        }
        int near = level.getEntitiesOfClass(CopyMob.class, player.getBoundingBox().inflate(MAX_RADIUS)).size();
        if (near >= MAX_NEAR) return;

        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double dist = MIN_RADIUS + level.random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
        int x = player.blockPosition().getX() + (int) (Math.cos(angle) * dist);
        int z = player.blockPosition().getZ() + (int) (Math.sin(angle) * dist);
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isSolid()) return;

        Supplier<EntityType<CopyMob>> type = level.random.nextDouble() < HOSTILE_CHANCE
                ? List.of(Mobs.COPY_ZOMBIE, Mobs.COPY_COBWEB, Mobs.COPY_ARROW).get(level.random.nextInt(3))
                : Mobs.COPY_FRIENDLY;
        CopyMob copy = type.get().create(level);
        if (copy == null) return;
        copy.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        copy.setTargetPlayer(player);
        level.addFreshEntity(copy);
    }
}