// hooks the qa engine up to players so toast can answer in chat, on right click and with /archivist toast
// per player memory lives in attachments, server side only
package com.xservernull.archivist.talk;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.ArchivistConfig;
import com.xservernull.archivist.entity.Toast;
import com.xservernull.archivist.qa.QaBook;
import com.xservernull.archivist.qa.QaInput;
import com.xservernull.archivist.qa.QaState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class Talk {
    private Talk() {}

    private static final String TOAST = "toast";

    private static final int MAX_MESSAGE_LENGTH = 80;

    private static final int COOLDOWN_TICKS = 100;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Archivist.MOD_ID);

    private static final Codec<QaState> STATE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("flags").forGetter(s -> List.copyOf(s.flags())),
            Codec.STRING.listOf().fieldOf("recent").forGetter(s -> List.copyOf(s.recentReplies())),
            Codec.STRING.optionalFieldOf("topic").forGetter(s -> Optional.ofNullable(s.topic())),
            Codec.INT.fieldOf("topic_turns").forGetter(QaState::topicTurnsLeft)
    ).apply(i, (flags, recent, topic, turns) -> {
        QaState s = new QaState();
        s.restore(flags, recent, topic.orElse(null), turns);
        return s;
    }));

    private static final Supplier<AttachmentType<QaState>> STATE = ATTACHMENTS.register("qa_state",
            () -> AttachmentType.builder(QaState::new).serialize(STATE_CODEC).copyOnDeath().build());

    private static final Supplier<AttachmentType<Integer>> VISITS = ATTACHMENTS.register("visits",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    private static final Supplier<AttachmentType<Long>> LAST_REPLY = ATTACHMENTS.register("last_reply",
            () -> AttachmentType.builder(() -> Long.MIN_VALUE / 2).build());

    private static final Supplier<AttachmentType<String>> LANG = ATTACHMENTS.register("language",
            () -> AttachmentType.builder(() -> "en").serialize(Codec.STRING).copyOnDeath().build());

    static void setLanguage(ServerPlayer player, String code) {
        if (ArchivistConfig.LANGUAGES.contains(code)) player.setData(LANG, code);
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(new QaBooks());
            e.addListener(new Blocklist());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            QaBooks.clear();
            Blocklist.clear();
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> registerCommand(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(Talk::onChat);
    }

    public static boolean speak(ServerPlayer player, String message) {
        String text = reply(player, message);
        if (text == null) return false;
        player.sendSystemMessage(Component.literal("[Toast]: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
        return true;
    }

    private static QaBook bookFor(String speaker, String code) {
        if ("mandanga".equals(code)) code = "es";
        QaBook translated = "en".equals(code) ? null : QaBooks.get(speaker + "_" + code);
        return translated != null ? translated : QaBooks.get(speaker);
    }

    private static String reply(ServerPlayer player, String message) {
        QaBook book = bookFor(TOAST, player.getData(LANG));
        if (book == null) return null;
        if (inDanger(message)) return dangerLine(player.getData(LANG));
        if (message != null && (message.length() > MAX_MESSAGE_LENGTH || Blocklist.blocks(message))) return "...";

        QaState state = player.getData(STATE);
        String name = player.getGameProfile().getName();
        Random rng = ThreadLocalRandom.current();

        long now = player.level().getGameTime();
        if (now - player.getData(LAST_REPLY) < COOLDOWN_TICKS) {
            return book.quiet(new QaInput().playerName(name), state, rng).text();
        }
        player.setData(LAST_REPLY, now);

        boolean approach = message == null || message.isBlank();
        int visits = player.getData(VISITS) + (approach ? 1 : 0);
        if (approach) player.setData(VISITS, visits);

        QaInput input = new QaInput()
                .message(message)
                .playerName(name)
                .stat("deaths", player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)))
                .stat("visits", visits)
                .stat("hour", LocalTime.now().getHour());
        return book.respond(input, state, rng).text();
    }

    private static final List<String> DANGER = List.of(
            "kill myself",
            "killing myself",
            "want to die",
            "wanna die",
            "end my life",
            "suicide",
            "suicidal",
            "hurt myself",
            "harm myself",
            "cut myself",
            "better off dead",
            "don't want to live",
            "dont want to live",
            "matarme",
            "quiero morir",
            "suicid",
            "hacerme da\u00f1o",
            "hacerme dano",
            "cortarme",
            "no quiero vivir",
            "me matar",
            "quero morrer",
            "suic\u00eddio",
            "me machucar",
            "me cortar",
            "n\u00e3o quero viver",
            "nao quero viver",
            "\u0443\u0431\u0438\u0442\u044c \u0441\u0435\u0431\u044f",
            "\u0445\u043e\u0447\u0443 \u0443\u043c\u0435\u0440\u0435\u0442\u044c",
            "\u0441\u0443\u0438\u0446\u0438\u0434",
            "\u0441\u0430\u043c\u043e\u0443\u0431\u0438\u0439\u0441\u0442\u0432",
            "\u043d\u0435 \u0445\u043e\u0447\u0443 \u0436\u0438\u0442\u044c",
            "\u043f\u0440\u0438\u0447\u0438\u043d\u0438\u0442\u044c \u0441\u0435\u0431\u0435 \u0432\u0440\u0435\u0434");

    private static String dangerLine(String code) {
        return switch ("mandanga".equals(code) ? "es" : code) {
            case "es" -> "solo soy un conejo. por favor, d\u00edselo ahora a alguien real cerca de ti, o llama a tu n\u00famero de emergencias. le importas a alguien. aqu\u00ed estoy.";
            case "pt" -> "sou s\u00f3 um coelho. por favor, conte agora a uma pessoa de verdade perto de voc\u00ea, ou ligue para o n\u00famero de emerg\u00eancia da sua regi\u00e3o. voc\u00ea importa para algu\u00e9m. estou aqui.";
            case "ru" -> "\u044f \u0432\u0441\u0435\u0433\u043e \u043b\u0438\u0448\u044c \u043a\u0440\u043e\u043b\u0438\u043a. \u043f\u043e\u0436\u0430\u043b\u0443\u0439\u0441\u0442\u0430, \u0441\u043a\u0430\u0436\u0438 \u043f\u0440\u044f\u043c\u043e \u0441\u0435\u0439\u0447\u0430\u0441 \u0436\u0438\u0432\u043e\u043c\u0443 \u0447\u0435\u043b\u043e\u0432\u0435\u043a\u0443 \u0440\u044f\u0434\u043e\u043c \u0438\u043b\u0438 \u043f\u043e\u0437\u0432\u043e\u043d\u0438 \u043d\u0430 \u043c\u0435\u0441\u0442\u043d\u044b\u0439 \u043d\u043e\u043c\u0435\u0440 \u044d\u043a\u0441\u0442\u0440\u0435\u043d\u043d\u043e\u0439 \u043f\u043e\u043c\u043e\u0449\u0438. \u0442\u044b \u043a\u043e\u043c\u0443-\u0442\u043e \u0434\u043e\u0440\u043e\u0433. \u044f \u0437\u0434\u0435\u0441\u044c.";
            default -> "i'm only a rabbit. please tell a real person near you right now, or call your local emergency number. you matter to someone. i'm here.";
        };
    }

    private static boolean inDanger(String message) {
        if (message == null) return false;
        String text = message.toLowerCase(java.util.Locale.ROOT);
        for (String phrase : DANGER) {
            if (text.contains(phrase)) return true;
        }
        return false;
    }

    private static final double CHAT_RANGE = 16.0;

    private static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String text = event.getRawText();
        player.server.execute(() -> chatToToast(player, text));
    }

    private static void chatToToast(ServerPlayer player, String text) {
        Toast toast = Toast.ownedBy(player);
        if (toast == null || (player.level().dimension() != Archivist.LIBRARY
                && toast.distanceToSqr(player) > CHAT_RANGE * CHAT_RANGE)) {
            toast = player.serverLevel().getEntitiesOfClass(Toast.class,
                    player.getBoundingBox().inflate(CHAT_RANGE)).stream()
                    .min(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(player))).orElse(null);
        }
        if (toast == null) return;
        toast.getLookControl().setLookAt(player, 30f, 30f);
        speak(player, text);
    }

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Archivist.MOD_ID).then(Commands.literal(TOAST)
                .executes(ctx -> say(ctx.getSource(), null))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> say(ctx.getSource(), StringArgumentType.getString(ctx, "message"))))));
    }

    private static int say(CommandSourceStack source, String message) throws CommandSyntaxException {
        if (speak(source.getPlayerOrException(), message)) return 1;
        source.sendFailure(Component.literal("toast has no book loaded (check the log for [qa] lines)"));
        return 0;
    }
}
