// loads the qa books and the qa_lang overlays from datapacks and keeps them by speaker
// the map is swapped whole on reload and emptied when the server stops
package com.xservernull.archivist.talk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.qa.QaBook;
import com.xservernull.archivist.qa.QaLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QaBooks extends SimplePreparableReloadListener<List<QaBooks.Loaded>> {
    private static final Logger LOG = LogUtils.getLogger();
    private static volatile Map<String, QaBook> books = Map.of();

    record Loaded(String key, QaLoader.Result result) {}

    public static QaBook get(String speaker) { return books.get(speaker); }

    public static void clear() { books = Map.of(); }

    @Override
    protected List<Loaded> prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<Loaded> results = new ArrayList<>();
        Map<String, JsonObject> bases = new HashMap<>();
        manager.listResources("qa", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                String text = readAll(reader);
                results.add(new Loaded(null, QaLoader.load(id.toString(), new StringReader(text))));
                try {
                    JsonObject o = JsonParser.parseString(text).getAsJsonObject();
                    if (o.has("speaker")) bases.put(o.get("speaker").getAsString(), o);
                } catch (RuntimeException ignored) {
                }
            } catch (IOException e) {
                LOG.error("could not read qa {}", id, e);
            }
        });

        manager.listResources("qa_lang", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                JsonObject overlay = JsonParser.parseString(readAll(reader)).getAsJsonObject();
                String speaker = overlay.has("speaker") ? overlay.get("speaker").getAsString() : "";
                String lang = overlay.has("lang") ? overlay.get("lang").getAsString() : "";
                JsonObject base = bases.get(speaker);
                if (base == null || lang.isEmpty()) {
                    throw new IllegalArgumentException("overlay needs a loaded speaker and a lang");
                }
                List<String> problems = new ArrayList<>();
                JsonObject merged = QaOverlay.merge(base, overlay, lang, problems);
                results.add(new Loaded(speaker + "_" + lang, QaLoader.load(id.toString(), new StringReader(merged.toString()))));
            } catch (IOException | RuntimeException e) {
                LOG.error("could not read qa overlay {}", id, e);
            }
        });
        return results;
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        for (int n; (n = reader.read(buf)) > 0; ) sb.append(buf, 0, n);
        return sb.toString();
    }

    @Override
    protected void apply(List<Loaded> loadedResults, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, QaBook> loaded = new HashMap<>();
        for (Loaded l : loadedResults) {
            QaLoader.Result r = l.result();
            for (QaLoader.Issue issue : r.issues()) {
                if (issue.severity() == QaLoader.Severity.ERROR) LOG.error("qa {}", issue);
            }
            if (r.book() == null) continue;
            String key = l.key() != null ? l.key() : r.book().speaker();
            loaded.putIfAbsent(key, r.book());
        }
        books = Map.copyOf(loaded);
    }
}
