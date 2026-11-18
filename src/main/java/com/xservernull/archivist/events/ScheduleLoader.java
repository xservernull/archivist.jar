// loads data/archivist/events/schedule.json through schedule whenever datapacks (re)load, logs
// every problem as [events] ...

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Optional;

final class ScheduleLoader {
    private ScheduleLoader() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "events/schedule.json");

    private static volatile Schedule schedule = Schedule.EMPTY;

    static Schedule schedule() { return schedule; }

    static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new Loader()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> schedule = Schedule.EMPTY);
    }

    private static final class Loader extends SimplePreparableReloadListener<Schedule.Result> {
        @Override
        protected Schedule.Result prepare(ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(FILE);
            if (resource.isEmpty()) {
                return new Schedule.Result(Schedule.EMPTY, List.of(FILE + ": file not found, nothing is scheduled"));
            }
            try (Reader reader = resource.get().openAsReader()) {
                return Schedule.parse(FILE.toString(), reader);
            } catch (IOException e) {
                LOG.error("[events] could not read {}", FILE, e);
                return new Schedule.Result(Schedule.EMPTY, List.of(FILE + ": could not read: " + e.getMessage()));
            }
        }

        @Override
        protected void apply(Schedule.Result result, ResourceManager manager, ProfilerFiller profiler) {
            for (String issue : result.issues()) LOG.warn("[events] {}", issue);
            for (var entries : result.schedule().dimensions().entrySet()) {
                for (Schedule.Entry entry : entries.getValue()) {
                    if (!Events.has(entry.event())) {
                        LOG.warn("[events] {}: no event called '{}' (known: {})", entries.getKey(), entry.event(), Events.ids());
                    }
                }
            }
            schedule = result.schedule();
            LOG.info("[events] schedule loaded: {} dimension(s), roll every {} to {} s, {} issue(s)",
                    schedule.dimensions().size(), schedule.minTicks() / 20, schedule.maxTicks() / 20, result.issues().size());
        }
    }
}
