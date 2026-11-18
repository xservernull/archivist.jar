// the rules of archivist:childhoodhome (old worldeventhub.onexplosionstart,
// portaltriggers.onvoidfall and onentitytamed, and the start of the old dog interrogation):

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.Dog;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public final class ChildhoodHome {
    private ChildhoodHome() {}

    public static final ResourceKey<Level> CHILDHOOD_HOME = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "childhoodhome"));
    public static final String DONE = "dog_interrogation_done";
    public static final String TALKED = "dog_talk_done";

    static void listen() {
        NeoForge.EVENT_BUS.addListener(ChildhoodHome::onExplosion);
        NeoForge.EVENT_BUS.addListener(ChildhoodHome::onVoidFall);
        NeoForge.EVENT_BUS.addListener(ChildhoodHome::onTame);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) Dog.followAcross(player, e.getFrom());
        });
    }

    private static void onExplosion(ExplosionEvent.Start e) {
        if (e.getLevel().dimension() == CHILDHOOD_HOME) e.setCanceled(true);
    }

    private static void onTame(AnimalTameEvent e) {
        if (!(e.getTamer() instanceof ServerPlayer player) || !(e.getAnimal() instanceof Wolf) || e.getAnimal() instanceof Dog) return;
        if (player.level().dimension() == CHILDHOOD_HOME) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            if (!player.isRemoved() && player.level().dimension() != CHILDHOOD_HOME) Warp.toChildhoodHome(player);
        });
    }

    private static void onVoidFall(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != CHILDHOOD_HOME) return;
        if (!e.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        e.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        server.execute(() -> Warp.toOverworld(player));
    }

    public static boolean onDogUsed(ServerPlayer player, Dog dog) {
        if (player.level().dimension() != CHILDHOOD_HOME) return false;
        EventState.Memory memory = Events.memory(player);
        if (memory.hasFlag(DONE)) return false;
        if (memory.hasFlag(TALKED)) {
            ChildhoodNet.openInterrogation(player, 0);
            return true;
        }
        DogTalk.start(player, dog);
        return true;
    }

    static void standDogUp(ServerPlayer player) {
        Dog nearest = null;
        double best = Double.MAX_VALUE;
        for (Dog dog : player.serverLevel().getEntitiesOfClass(Dog.class, player.getBoundingBox().inflate(64))) {
            double d = dog.distanceToSqr(player);
            if (d < best) {
                best = d;
                nearest = dog;
            }
        }
        if (nearest != null) nearest.befriend(player);
    }
}