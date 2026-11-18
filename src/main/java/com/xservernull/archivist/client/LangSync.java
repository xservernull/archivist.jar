package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.ArchivistConfig;
import com.xservernull.archivist.talk.LangNet;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends the chosen Archivist language to the server
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class LangSync {
    private LangSync() {}

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        send();
    }
    public static void send() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        PacketDistributor.sendToServer(new LangNet.Set(ArchivistConfig.language()));
    }
}
