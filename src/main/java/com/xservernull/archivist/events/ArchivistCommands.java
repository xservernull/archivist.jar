// two more subcommands of /archivist for testing (permission level 2), next to event and video
// in events: /archivist tp : moves you to a dimension's landing spot, the same landing the

package com.xservernull.archivist.events;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiFunction;

final class ArchivistCommands {
    private ArchivistCommands() {}

    private record At(int x, int z, OptionalInt y) {}

    private record Place(String dimension, BiFunction<MinecraftServer, ServerPlayer, Optional<At>> where) {}

    private static final Map<String, Place> PLACES = new LinkedHashMap<>();

    static {
        PLACES.put("far_lands_temple", new Place("far_lands", (server, player) -> {
            if (!FarLandsTemple.built(server)) return Optional.empty();
            BlockPos at = BlockPos.containing(FarLandsTemple.centre());
            return Optional.of(new At(at.getX(), at.getZ(), OptionalInt.of(at.getY())));
        }));
        PLACES.put("childhoodhome", new Place("childhoodhome", (server, player) ->
                ChildhoodHomePlacer.spot(server).map(at -> new At(at.getX(), at.getZ(), OptionalInt.empty()))));
        PLACES.put("infdev_pyramid", new Place("infdev20100227", (server, player) ->
                InfdevPyramid.chest(server).map(at -> new At(at.getX(), at.getZ(), OptionalInt.of(at.getY())))));
        PLACES.put("quietplace_house", new Place("quietplace", (server, player) -> quiet(server, player, false)));
        PLACES.put("quietplace_farm", new Place("quietplace", (server, player) -> quiet(server, player, true)));
    }

    private static Optional<At> quiet(MinecraftServer server, ServerPlayer player, boolean farm) {
        ServerLevel level = server.getLevel(Archivist.QUIET_PLACE);
        if (level == null) return Optional.empty();
        BlockPos from = player.level().dimension().equals(Archivist.QUIET_PLACE) ? player.blockPosition() : level.getSharedSpawnPos();
        ChunkPos chunk = QuietPlaceStructures.nearest(farm, from);
        return chunk == null ? Optional.empty() : Optional.of(new At(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), OptionalInt.empty()));
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> register(e.getDispatcher()));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Archivist.MOD_ID)
                .then(Commands.literal("tp")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("dimension", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(AdminPanelNet.DIMENSIONS, builder))
                                .executes(ArchivistCommands::tp)))
                .then(Commands.literal("locate")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("structure", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(PLACES.keySet(), builder))
                                .executes(context -> locate(context, false))
                                .then(Commands.literal("tp").executes(context -> locate(context, true))))));
    }

    private static int tp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(context, "dimension");
        if (!AdminPanelNet.DIMENSIONS.contains(id)) {
            context.getSource().sendFailure(Component.literal("no dimension '" + id + "' (known: " + String.join(", ", AdminPanelNet.DIMENSIONS) + ")"));
            return 0;
        }
        if (!AdminPanelNet.warp(player, id)) {
            context.getSource().sendFailure(Component.literal("dimension " + id + " is not loaded"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("moved to " + id), true);
        return 1;
    }

    private static int locate(CommandContext<CommandSourceStack> context, boolean teleport) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String id = StringArgumentType.getString(context, "structure");
        Place place = PLACES.get(id);
        if (place == null) {
            source.sendFailure(Component.literal("no structure '" + id + "' (known: " + String.join(", ", PLACES.keySet()) + ")"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, place.dimension())));
        Optional<At> found = level == null ? Optional.empty() : place.where().apply(server, player);
        if (level == null || found.isEmpty()) {
            source.sendFailure(Component.literal(id + " is not there yet: it is built when a player first arrives in " + place.dimension()));
            return 0;
        }
        At at = found.get();
        int y;
        if (at.y().isPresent()) {
            y = at.y().getAsInt();
        } else {
            level.getChunk(at.x() >> 4, at.z() >> 4);
            y = Ground.surface(level, at.x(), at.z()).getY() + 1;
        }
        if (teleport) {
            Warp.place(player, level, at.x() + 0.5, y, at.z() + 0.5);
            source.sendSuccess(() -> Component.literal("moved next to " + id), true);
            return 1;
        }
        MutableComponent message = Component.literal(id + " in " + place.dimension() + ": " + at.x() + " " + y + " " + at.z() + " ")
                .append(Component.literal("[tp]").withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/archivist locate " + id + " tp"))));
        source.sendSuccess(() -> message, false);
        return 1;
    }
}