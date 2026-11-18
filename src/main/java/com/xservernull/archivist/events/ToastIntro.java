// toast's opening words (the old shelfportal monologue), as a quickevent.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import com.xservernull.archivist.entity.Toast;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

final class ToastIntro implements QuickEvent {
    static final String ID = "toast_intro";

    private static final List<String> LINES = List.of(
            "archivist.toast.intro.1|oh. its you. im toast, do you remember me?",
            "archivist.toast.intro.2|oh. well. i guess youve got a lot of exploring to do...",
            "archivist.toast.intro.3|talk to me in the chat. i can hear you here.",
            "archivist.toast.intro.4|ill be here if you need anything.");
    private static final int GAP = 60;
    private static final int END = LINES.size() * GAP + 40;

    @Override public String id() { return ID; }

    @Override public void start(ServerPlayer player) {}

    @Override
    public boolean tick(ServerPlayer player, int age) {
        if ((age - 1) % GAP == 0 && (age - 1) / GAP < LINES.size()) {
            player.sendSystemMessage(Component.literal("[Toast] ").append(Texts.component(LINES.get((age - 1) / GAP))));
            Toast toast = nearestToast(player);
            if (toast != null) toast.getLookControl().setLookAt(player, 30f, 30f);
        }
        if (age < END) return false;
        player.displayClientMessage(Texts.tr("archivist.toast.chat_hint", "text toast in the chat"), true);
        Events.memory(player).markSeen(ID);
        return true;
    }

    private static Toast nearestToast(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(Toast.class, player.getBoundingBox().inflate(16))
                .stream().findFirst().orElse(null);
    }
}