// loads the neverforgetyou script json on datapack reload and holds the one loaded script

package com.xservernull.archivist.events.neverforgetyou;

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

final class NfyLoader {
    private NfyLoader() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceLocation SCRIPT_FILE =
            ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "events/neverforgetyou.json");

    private static volatile NfyScript script;

    static NfyScript script() { return script; }

    static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new Loader()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> script = null);
    }

    private static final class Loader extends SimplePreparableReloadListener<NfyScript.Result> {
        @Override
        protected NfyScript.Result prepare(ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(SCRIPT_FILE);
            if (resource.isEmpty()) {
                return new NfyScript.Result(null, List.of(new NfyScript.Issue(
                        NfyScript.Severity.ERROR, SCRIPT_FILE.toString(), "file not found")));
            }
            try (Reader reader = resource.get().openAsReader()) {
                return NfyScript.parse(SCRIPT_FILE.toString(), reader);
            } catch (IOException e) {
                return new NfyScript.Result(null, List.of(new NfyScript.Issue(
                        NfyScript.Severity.ERROR, SCRIPT_FILE.toString(), "could not read: " + e.getMessage())));
            }
        }

        @Override
        protected void apply(NfyScript.Result result, ResourceManager manager, ProfilerFiller profiler) {
            for (NfyScript.Issue issue : result.issues()) {
                if (issue.severity() == NfyScript.Severity.ERROR) LOG.error("[nfy] {}", issue);
            }
            script = result.script();
        }
    }
}
