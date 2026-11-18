// loads the word blocklist json files from datapacks and merges them into one filter
// the filter gets swapped whole on reload and emptied when the server stops
package com.xservernull.archivist.talk;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.qa.WordFilter;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public final class Blocklist extends SimplePreparableReloadListener<List<WordFilter.Result>> {
    private static final Logger LOG = LogUtils.getLogger();
    private static volatile WordFilter filter = WordFilter.empty();

    public static boolean blocks(String message) { return filter.blocks(message); }

    public static void clear() { filter = WordFilter.empty(); }

    @Override
    protected List<WordFilter.Result> prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<WordFilter.Result> results = new ArrayList<>();
        manager.listResources("filter", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                StringWriter text = new StringWriter();
                reader.transferTo(text);
                results.add(WordFilter.parse(id.toString(), text.toString()));
            } catch (IOException e) {
                LOG.error("could not read filter {}", id, e);
            }
        });
        return results;
    }

    @Override
    protected void apply(List<WordFilter.Result> results, ResourceManager manager, ProfilerFiller profiler) {
        List<WordFilter.Term> terms = new ArrayList<>();
        for (WordFilter.Result r : results) {
            terms.addAll(r.filter().terms());
        }
        filter = WordFilter.of(terms);
    }
}
