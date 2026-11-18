// runs hope's conversation with a player: lines, choices and waiting for button picks state
// lives in the player's nfystate.dialogue so nothing is kept in here

package com.xservernull.archivist.events.neverforgetyou;

import com.xservernull.archivist.Texts;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.Hope;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

final class NfyDialogue {
    private NfyDialogue() {}

    static final int LINE_DELAY = 50;
    private static final int MAX_DELAY = 160;

    static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
    }

    static Hope findHope(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(512.0);
        List<Hope> found = player.level().getEntitiesOfClass(Hope.class, box, h -> h.isOwnedBy(player.getUUID()));
        return found.isEmpty() ? null : found.get(0);
    }

    static int delayFor(String text) {
        return Math.max(LINE_DELAY, Math.min(MAX_DELAY, 30 + 2 * text.length()));
    }

    private static void tick(ServerPlayer player) {
        NfyState.Dialogue dialogue = NfyEvent.dialogueOrNull(player);
        if (dialogue == null || !dialogue.inSession) return;
        if (!player.isAlive() || !player.level().dimension().equals(Archivist.NEVER_FORGET_YOU)) {
            NfyEvent.cleanup(player);
            NfyNet.clear(player);
            return;
        }
        NfyScript current = NfyLoader.script();
        if (current == null) return;
        if (dialogue.messageOrbsOn && player.tickCount % NfyMessageOrbs.CHECK_INTERVAL == 0) {
            NfyMessageOrbs.spawnDue(player, player.serverLevel(), dialogue, current);
        }
        if (dialogue.sequence == NfyState.Dialogue.NONE || dialogue.waiting) return;

        long now = player.level().getGameTime();
        if (now - dialogue.lastLineTime < dialogue.lineDelay) return;
        List<NfyScript.Line> lines = dialogue.answering ? replies(current, dialogue) : linesOf(current, dialogue.sequence);
        int index = dialogue.answering ? dialogue.answerLine : dialogue.line;
        if (index >= lines.size()) {
            if (dialogue.answering) {
                dialogue.answering = false;
                dialogue.answer = "";
                dialogue.answerLine = 0;
            } else {
                finish(player, dialogue);
            }
            return;
        }
        NfyScript.Line line = lines.get(index);
        if (dialogue.answering) dialogue.answerLine++;
        else dialogue.line++;

        if (!dialogue.answering && dialogue.sequence == NfyState.Dialogue.ENTRY && dialogue.line == 1) faceHope(player);
        speak(player, dialogue, current, line, now);
    }

    private static void faceHope(ServerPlayer player) {
        Hope hope = findHope(player);
        if (hope == null || player.connection == null) return;
        Vec3 d = hope.position().add(0, hope.getBbHeight() / 2.0, 0).subtract(player.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0 / Math.PI));
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
    }

    private static void speak(ServerPlayer player, NfyState.Dialogue dialogue, NfyScript current, NfyScript.Line line, long now) {
        dialogue.lastLineTime = now;
        switch (line.kind()) {
            case HOPE -> {
                dialogue.lineDelay = delayFor(Texts.english(line.text()));
                NfyNet.say(player, "hope", line.text(), dialogue.lineDelay);
            }
            case RAW -> {
                dialogue.lineDelay = delayFor(Texts.english(line.text()));
                NfyNet.say(player, "", line.text(), dialogue.lineDelay);
            }
            case YOU -> {
                dialogue.waiting = true;
                dialogue.pendingChoice = "";
                dialogue.pendingLine = line.text();
                NfyNet.ask(player, List.of(line.text()));
            }
            case CHOICE -> {
                NfyScript.Choice choice = current.choice(line.text());
                if (choice == null) {
                    dialogue.lineDelay = 0;
                    return;
                }
                dialogue.waiting = true;
                dialogue.pendingChoice = choice.name();
                dialogue.pendingLine = "";
                NfyNet.ask(player, choice.options().stream().map(NfyScript.Option::button).toList());
            }
            case ORB_TOAST -> {
                dialogue.lineDelay = delayFor(Texts.english(line.text()));
                player.sendSystemMessage(Texts.component(line.text()).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
            }
        }
    }

    static void pick(ServerPlayer player, int index) {
        NfyState.Dialogue dialogue = NfyEvent.dialogueOrNull(player);
        NfyScript current = NfyLoader.script();
        if (dialogue == null || current == null || !dialogue.inSession || !dialogue.waiting) return;

        String said;
        if (dialogue.pendingChoice.isEmpty()) {
            if (index != 0) return;
            said = dialogue.pendingLine;
        } else {
            NfyScript.Choice choice = current.choice(dialogue.pendingChoice);
            if (choice == null || index < 0 || index >= choice.options().size()) return;
            NfyScript.Option option = choice.options().get(index);
            said = option.label();
            dialogue.answer = option.key();
            dialogue.answering = true;
            dialogue.answerLine = 0;
        }
        dialogue.waiting = false;
        dialogue.pendingChoice = "";
        dialogue.pendingLine = "";
        dialogue.lastLineTime = player.level().getGameTime();
        dialogue.lineDelay = delayFor(Texts.english(said));
        NfyNet.say(player, "you", said, dialogue.lineDelay);
    }

    private static List<NfyScript.Line> linesOf(NfyScript current, int sequence) {
        if (sequence == NfyState.Dialogue.FINAL) return current.finalLines();
        int orb = sequence - NfyState.Dialogue.ORB_BASE;
        if (orb >= 0 && orb < NfyScript.ORB_COUNT) return current.orb(orb);
        return current.entry();
    }

    private static List<NfyScript.Line> replies(NfyScript current, NfyState.Dialogue dialogue) {
        for (NfyScript.Choice choice : current.choices().values()) {
            List<NfyScript.Line> lines = choice.responses().get(dialogue.answer);
            if (lines != null) return lines;
        }
        return List.of();
    }

    private static void finish(ServerPlayer player, NfyState.Dialogue dialogue) {
        if (dialogue.sequence == NfyState.Dialogue.FINAL) {
            dialogue.sequence = NfyState.Dialogue.NONE;
            return;
        }
        int orb = dialogue.orbOfSequence();
        dialogue.sequence = NfyState.Dialogue.NONE;
        Hope hope = findHope(player);
        if (orb < 0) {
            dialogue.entryDone = true;
            if (hope != null) hope.startGuide();
        } else if (orb == NfyScript.ORB_COUNT - 1) {
            dialogue.finished = true;
            NfyMessageOrbs.start(player, dialogue);
            if (hope != null) hope.startBookMode();
        } else if (hope != null) {
            hope.orbDone(orb);
        }
    }
}
