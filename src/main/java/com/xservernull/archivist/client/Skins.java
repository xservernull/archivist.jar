package com.xservernull.archivist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** for copy mobs */
final class Skins {
    static final ResourceLocation STEVE =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private Skins() {}
    static AbstractClientPlayer player(UUID id) {
        ClientLevel level = Minecraft.getInstance().level;
        if (id == null || level == null) return null;
        return level.getPlayerByUUID(id) instanceof AbstractClientPlayer player ? player : null;
    }
    static ResourceLocation texture(UUID id) {
        AbstractClientPlayer player = player(id);
        return player != null ? player.getSkin().texture() : STEVE;
    }
}
