/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.tamboui.style.Style;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.KameletDefinitions;
import org.apache.camel.dsl.jbang.core.commands.ai.KameletInput;
import org.apache.camel.util.json.JsonObject;

/**
 * The data type each Kamelet a step calls turns the body into (CAMEL-27475), for the views: postgresql-sink takes json,
 * and its transformer writes the Map of a row as JSON, so a {@code LinkedHashMap} at the step is fine.
 * <p>
 * The Kamelets are looked up in the project's own Kamelet files and else the Kamelet catalog, which may have to be
 * loaded first: that happens on a thread of its own, and a view shows the data type from the next refresh on. A Kamelet
 * is looked up again after a while, so a Kamelet file that is edited is picked up.
 */
final class KameletInputs {

    /** How long a Kamelet that was looked up is kept before it is looked up again. */
    private static final long KEEP_MILLIS = 30_000;

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CamelTuiKameletInputs");
        t.setDaemon(true);
        return t;
    });
    private static volatile CamelCatalog catalog;

    /** A Kamelet that was looked up (input null when it has no default in data type, or is not known). */
    private record Entry(KameletInput input, long time, boolean loading) {
    }

    private KameletInputs() {
    }

    /**
     * Sets, on each processor of the integration that calls a Kamelet, the data type the Kamelet turns the body into,
     * as far as it has been looked up.
     */
    static void attach(IntegrationInfo info) {
        Path directory = null;
        boolean resolved = false;
        for (RouteInfo route : info.routes) {
            for (ProcessorInfo p : route.processors) {
                String name = KameletInput.kameletName(p.uri);
                if (name == null) {
                    continue;
                }
                if (!resolved) {
                    directory = directory(info);
                    resolved = true;
                }
                p.kameletInput = get(name, directory, info.kameletsVersion);
            }
        }
    }

    /**
     * The data type the Kamelet turns the body into, or null while it is looked up, or when it has no default in data
     * type.
     */
    static KameletInput get(String name, Path directory, String kameletsVersion) {
        String key = directory + "|" + kameletsVersion + "|" + name;
        long now = MonotonicClock.millis();
        Entry entry = CACHE.get(key);
        if (entry == null || !entry.loading() && now - entry.time() > KEEP_MILLIS) {
            Entry loading = new Entry(entry != null ? entry.input() : null, now, true);
            boolean mine = entry == null ? CACHE.putIfAbsent(key, loading) == null : CACHE.replace(key, entry, loading);
            if (mine) {
                LOADER.execute(() -> CACHE.put(key,
                        new Entry(load(name, directory, kameletsVersion), MonotonicClock.millis(), false)));
            }
            return loading.input();
        }
        return entry.input();
    }

    /**
     * Looks the Kamelet up now: the project's own Kamelets, else the catalog of the Kamelets the integration runs
     * (camel run --kamelets-version), which may not be the one of the Camel CLI.
     */
    static KameletInput load(String name, Path directory, String kameletsVersion) {
        try {
            KameletDefinitions.Definition def = KameletDefinitions.find(name, directory, kameletsVersion);
            return def != null ? KameletInput.of(def, catalog()) : null;
        } catch (Exception e) {
            // a Kamelet that cannot be read shows no data type
            return null;
        }
    }

    private static CamelCatalog catalog() {
        CamelCatalog answer = catalog;
        if (answer == null) {
            answer = new DefaultCamelCatalog(true);
            catalog = answer;
        }
        return answer;
    }

    private static Path directory(IntegrationInfo info) {
        try {
            return info.phantom && info.sourceDir != null
                    ? Path.of(info.sourceDir) : FilesBrowser.resolveSourceDirectory(info);
        } catch (Exception e) {
            return null;
        }
    }

    /** Package-private for tests: forgets what was looked up. */
    static void clearForTesting() {
        CACHE.clear();
    }

    /**
     * The data type after the body types: " → json", with why it needs attention when the transformer does not take the
     * body; empty without one.
     */
    static String arrow(KameletInput in, Boolean takes) {
        return in == null ? "" : " " + KameletInput.arrowText(in.dataType(), takes, in.transformer());
    }

    /**
     * The body column of a step in a table: its body types and the data type of the Kamelet it calls, "⚠ String |
     * LinkedHashMap → json", or "→ json" before a message has reached it.
     */
    static String shortLabel(ProcessorInfo p) {
        String types = BodyTypes.shortLabel(p.bodyTypes, p.bodyKinds, p.bodyFormats);
        if (p.kameletInput == null) {
            return types;
        }
        Boolean takes = p.kameletInput.takes(p.bodyTypes, p.bodyKinds);
        String arrow = arrow(p.kameletInput, takes).strip();
        if (Boolean.FALSE.equals(takes) && !BodyTypes.marked(p.bodyKinds, p.bodyFormats)) {
            types = BodyTypes.MARK + " " + types;
        }
        return types.isEmpty() ? arrow : types + " " + arrow;
    }

    /** The style of the body column: a warning when it needs attention, muted otherwise. */
    static Style style(ProcessorInfo p) {
        boolean notTaken = p.kameletInput != null
                && Boolean.FALSE.equals(p.kameletInput.takes(p.bodyTypes, p.bodyKinds));
        return notTaken ? Theme.warning() : BodyTypes.style(p.bodyKinds, p.bodyFormats);
    }

    /** The kameletInput of a line of the route structure, for the diagram (RouteDiagramHelper reads it). */
    static JsonObject toJson(ProcessorInfo p) {
        return p.kameletInput != null ? p.kameletInput.toJson(p.kameletInput.takes(p.bodyTypes, p.bodyKinds)) : null;
    }

    /**
     * The quick documentation of a step that calls a Kamelet: what it takes and why a Map is fine, in the words a Camel
     * developer knows; the Kamelets come from event-driven and serverless integration, where the data is JSON.
     */
    static String doc(KameletInput in, List<String> types, List<String> kinds) {
        StringBuilder sb = new StringBuilder();
        sb.append("The Kamelet ").append(in.kamelet()).append(" takes ").append(in.dataType());
        if (in.description() != null) {
            sb.append(": ").append(in.description());
            if (!in.description().endsWith(".")) {
                sb.append('.');
            }
        } else {
            sb.append('.');
        }
        String words = in.carriersInWords();
        if (words != null) {
            sb.append(" Camel turns the body into it before the Kamelet sees it, with the data type transformer ")
                    .append(in.transformer()).append(", which takes ").append(words).append('.');
        }
        Boolean takes = in.takes(types, kinds);
        if (Boolean.FALSE.equals(takes)) {
            sb.append(' ').append(BodyTypes.MARK).append(" It does not take ").append(BodyTypes.names(types))
                    .append(": convert the body first.");
        }
        return sb.toString();
    }
}
