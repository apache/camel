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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;

/**
 * The AI-assisted route descriptions of a project's {@link IntegrationSummary#FILE_NAME}, for the tabs that show a
 * route's description (Routes, Diagram) when the route has none of its own. They are shown in the
 * {@link Theme#aiAssisted()} style with the {@link #MARK} so the user tells them apart from what the source and the
 * runtime say (CAMEL-25143).
 * <p/>
 * Read from disk at most every few seconds per project, and not at all when the AI Overview setting is off: the tabs
 * ask on every render.
 */
final class IntegrationSummaryHints {

    /** Put before an AI-assisted text shown among facts. */
    static final String MARK = "✦ ";

    private static final long RECHECK_MS = 3000;
    private static final long MODE_RECHECK_MS = 2000;

    private record Entry(long checkedAt, long modified, Map<String, String> descriptions, Map<String, Note> notes,
            List<IntegrationSummary.StepLabel> steps) {
    }

    /** A route's note: the longer explanation beside its short description, from the AI or from the source. */
    record Note(String text, boolean ai) {
    }

    private static final Entry EMPTY = new Entry(0, 0, Map.of(), Map.of(), List.of());

    private static final Map<Path, Entry> CACHE = new ConcurrentHashMap<>();
    private static volatile long modeCheckedAt;
    private static volatile boolean enabled = true;
    /** The Diagram tab's ai view setting: off shows what the sources and the runtime say, nothing an AI added. */
    private static volatile boolean shown = true;

    private IntegrationSummaryHints() {
    }

    /** The AI description of a route, or null when there is none or the hints are off. */
    static String description(Path dir, String routeId) {
        if (dir == null || routeId == null || !enabled()) {
            return null;
        }
        Map<String, String> descriptions = entry(dir.toAbsolutePath().normalize()).descriptions();
        String d = descriptions.get(routeId);
        return d != null ? d : descriptions.get(RouteKeys.sourceKey(dir, routeId));
    }

    /** The AI descriptions of a project by route id; empty when there are none or the hints are off. */
    static Map<String, String> descriptionsIfEnabled(Path dir) {
        if (dir == null || !enabled()) {
            return Map.of();
        }
        Map<String, String> descriptions = entry(dir.toAbsolutePath().normalize()).descriptions();
        Map<String, String> running = RouteKeys.runningIds(dir);
        if (running.isEmpty()) {
            return descriptions;
        }
        // also under the running id of a source route without one, as the topology names it
        Map<String, String> answer = new HashMap<>(descriptions);
        running.forEach((source, id) -> {
            String d = descriptions.get(source);
            if (d != null) {
                answer.putIfAbsent(id, d);
            }
        });
        return answer;
    }

    /**
     * The note of a route, which the runtime does not report: the one in its source as the summary lists it, else the
     * one the AI wrote (only while the hints are on); null when there is none.
     */
    static Note note(Path dir, String routeId) {
        if (dir == null || routeId == null) {
            return null;
        }
        Map<String, Note> notes = entry(dir.toAbsolutePath().normalize()).notes();
        Note n = notes.containsKey(routeId) ? notes.get(routeId) : notes.get(RouteKeys.sourceKey(dir, routeId));
        return n == null || n.ai() && !enabled() ? null : n;
    }

    /**
     * What the AI wrote about a decision point of a route (CAMEL-25161), by its path in the route; null when there is
     * nothing or the hints are off.
     */
    static IntegrationSummary.StepLabel step(Path dir, String routeId, String path) {
        if (dir == null || routeId == null || path == null || !enabled()) {
            return null;
        }
        List<IntegrationSummary.StepLabel> steps = entry(dir.toAbsolutePath().normalize()).steps();
        if (steps.isEmpty()) {
            return null;
        }
        String sourceKey = RouteKeys.sourceKey(dir, routeId);
        for (IntegrationSummary.StepLabel st : steps) {
            if (st.path().equals(path) && (st.route().equals(routeId) || st.route().equals(sourceKey))) {
                return st;
            }
        }
        return null;
    }

    /** Whether the project has an integration summary, whatever the settings. */
    static boolean hasSummary(Path dir) {
        return dir != null && entry(dir.toAbsolutePath().normalize()).modified() > 0;
    }

    static Map<String, String> descriptions(Path dir) {
        return entry(dir).descriptions();
    }

    private static Entry entry(Path dir) {
        long now = System.currentTimeMillis();
        Entry e = CACHE.get(dir);
        if (e != null && now - e.checkedAt() < RECHECK_MS) {
            return e;
        }
        long modified = modified(dir.resolve(IntegrationSummary.FILE_NAME));
        if (e != null && e.modified() == modified) {
            e = new Entry(now, modified, e.descriptions(), e.notes(), e.steps());
            CACHE.put(dir, e);
            return e;
        }
        Entry fresh = EMPTY;
        if (modified > 0) {
            IntegrationSummary.Summary summary = IntegrationSummary.read(dir);
            if (summary != null) {
                Map<String, Note> notes = new HashMap<>();
                summary.notes().forEach((k, v) -> notes.put(k, new Note(v, true)));
                summary.sourceNotes().forEach((k, v) -> notes.put(k, new Note(v, false)));
                fresh = new Entry(
                        now, modified, Map.copyOf(summary.descriptions()), Map.copyOf(notes),
                        List.copyOf(summary.steps()));
            }
        }
        fresh = new Entry(now, modified, fresh.descriptions(), fresh.notes(), fresh.steps());
        CACHE.put(dir, fresh);
        return fresh;
    }

    /** Forgets what was read for a project, after the summary was written. */
    static void invalidate(Path dir) {
        if (dir != null) {
            CACHE.remove(dir.toAbsolutePath().normalize());
        }
    }

    /** Whether AI-assisted hints are shown: the AI overview setting is not off, and the ai view setting is on. */
    static boolean enabled() {
        return settingEnabled() && shown;
    }

    /** Whether the AI overview setting is not off, whatever the ai view setting. */
    static boolean settingEnabled() {
        long now = System.currentTimeMillis();
        if (now - modeCheckedAt > MODE_RECHECK_MS) {
            modeCheckedAt = now;
            enabled = !ProjectOverviewAssist.MODE_OFF.equals(ProjectOverviewAssist.mode());
        }
        return enabled;
    }

    /** The ai view setting (a in the Diagram tab): whether what an AI added is shown, or only the facts. */
    static boolean isShown() {
        return shown;
    }

    static void setShown(boolean show) {
        shown = show;
    }

    /** For tests: the setting is read again on the next call. */
    static void resetForTesting() {
        CACHE.clear();
        modeCheckedAt = 0;
        shown = true;
    }

    private static long modified(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
        } catch (IOException e) {
            return 0;
        }
    }
}
