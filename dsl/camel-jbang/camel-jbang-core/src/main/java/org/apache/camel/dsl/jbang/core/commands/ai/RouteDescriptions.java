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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Overview;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Route;

/**
 * Puts route descriptions and notes (typically ones an AI suggested) into the route sources, so they become part of the
 * routes: shown by every tool, versioned and reviewed with the code. Nothing is written here: the answer is the new
 * content of each file, for the caller to show as a diff and write once the user accepts it.
 * <p/>
 * The description is the short label a diagram box shows, the note the longer explanation. They are added where the DSL
 * keeps them: {@code description:} and {@code note:} beside the route's {@code id:} in YAML, {@code description} and
 * {@code note} attributes on the {@code <route>} element in XML, {@code .routeDescription("...")} and
 * {@code .routeNote("...")} after {@code .routeId("...")} in Java. What a route already has is left alone, as is a
 * route whose place cannot be found (a bare {@code - from:} in YAML, a route without an id in XML or Java).
 */
public final class RouteDescriptions {

    private RouteDescriptions() {
    }

    /** A file to change: its content now and with the descriptions added, and the routes they are for. */
    public record Change(String file, String oldContent, String newContent, List<String> routes) {
    }

    /** A route that was not given its description, and why. */
    public record Skipped(String route, String reason) {
    }

    public record Plan(List<Change> changes, List<Skipped> skipped) {
    }

    /** The changes that add the descriptions (short labels) to the routes of the overview. */
    public static Plan plan(Overview overview, Map<String, String> descriptions) {
        return plan(overview, descriptions, Map.of());
    }

    /**
     * The changes that add the descriptions and notes to the routes of the overview.
     *
     * @param descriptions the description (short label) of each route, by route key
     * @param notes        the note (a sentence or two) of each route, by route key
     */
    public static Plan plan(Overview overview, Map<String, String> descriptions, Map<String, String> notes) {
        Map<String, List<Route>> byFile = new LinkedHashMap<>();
        List<Skipped> skipped = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>(descriptions.keySet());
        keys.addAll(notes.keySet());
        for (String key : keys) {
            Route r = overview.route(key);
            boolean label = !blank(descriptions.get(key)) && r != null && !r.hasDescription();
            boolean note = !blank(notes.get(key)) && r != null && !r.hasNote();
            if (r == null) {
                skipped.add(new Skipped(key, "no such route"));
            } else if (!label && !note) {
                skipped.add(new Skipped(key, "the route has its description and note"));
            } else {
                byFile.computeIfAbsent(r.file(), k -> new ArrayList<>()).add(r);
            }
        }
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<String, List<Route>> e : byFile.entrySet()) {
            String content;
            try {
                content = Files.readString(overview.directory().resolve(e.getKey()), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                e.getValue().forEach(r -> skipped.add(new Skipped(r.key(), "cannot read " + e.getKey())));
                continue;
            }
            Change change = apply(e.getKey(), content, e.getValue(), descriptions, notes, skipped);
            if (change != null) {
                changes.add(change);
            }
        }
        return new Plan(changes, skipped);
    }

    /** The file with the descriptions and notes of the given routes added, or null when none could be placed. */
    static Change apply(
            String file, String content, List<Route> routes, Map<String, String> descriptions, Map<String, String> notes,
            List<Skipped> skipped) {
        String updated = content;
        List<String> done = new ArrayList<>();
        // bottom up, so the lines of the routes above stay where the parser found them
        List<Route> ordered = new ArrayList<>(routes);
        ordered.sort(Comparator.comparingInt(Route::line).reversed());
        for (Route r : ordered) {
            String label = r.hasDescription() ? null : oneLine(descriptions.get(r.key()));
            String note = r.hasNote() ? null : oneLine(notes.get(r.key()));
            String next = switch (r.format()) {
                case "yaml" -> yaml(updated, r, label, note);
                case "xml" -> xml(updated, r, label, note);
                case "java" -> java(updated, r, label, note);
                default -> null;
            };
            if (next == null) {
                skipped.add(new Skipped(r.key(), placeReason(r)));
            } else {
                updated = next;
                done.add(0, r.key());
            }
        }
        return done.isEmpty() ? null : new Change(file, content, updated, done);
    }

    private static String placeReason(Route r) {
        return switch (r.format()) {
            case "yaml" -> "no place for a description (a bare from, or a flow-style mapping)";
            default -> "the route has no id to find it by";
        };
    }

    private static String yaml(String content, Route r, String label, String note) {
        if (r.insertAt() < 0) {
            return null;
        }
        String nl = content.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(List.of(content.split("\\R", -1)));
        if (r.insertAt() > lines.size()) {
            return null;
        }
        String indent = " ".repeat(r.indent());
        int at = r.insertAt();
        if (label != null) {
            lines.add(at++, indent + "description: " + yamlQuote(label));
        }
        if (note != null) {
            lines.add(at, indent + "note: " + yamlQuote(note));
        }
        return String.join(nl, lines);
    }

    private static String xml(String content, Route r, String label, String note) {
        if (r.id() == null) {
            return null;
        }
        String element = "routeTemplate".equals(r.kind()) ? "routeTemplate" : "route";
        Matcher m = Pattern.compile("<(?:\\w+:)?" + element + "\\b[^>]*?\\bid\\s*=\\s*([\"'])" + Pattern.quote(r.id())
                                    + "\\1")
                .matcher(content);
        if (!m.find()) {
            return null;
        }
        int tagEnd = content.indexOf('>', m.end());
        String tag = tagEnd < 0 ? "" : content.substring(m.start(), tagEnd);
        StringBuilder attrs = new StringBuilder();
        if (label != null && !tag.matches("(?s).*\\bdescription\\s*=.*")) {
            attrs.append(" description=\"").append(xmlEscape(label)).append('"');
        }
        if (note != null && !tag.matches("(?s).*\\bnote\\s*=.*")) {
            attrs.append(" note=\"").append(xmlEscape(note)).append('"');
        }
        if (attrs.isEmpty()) {
            return null;
        }
        return content.substring(0, m.end()) + attrs + content.substring(m.end());
    }

    private static String java(String content, Route r, String label, String note) {
        if (r.id() == null) {
            return null;
        }
        Matcher m = Pattern.compile("\\.routeId\\s*\\(\\s*\"" + Pattern.quote(r.id()) + "\"\\s*\\)").matcher(content);
        if (!m.find()) {
            return null;
        }
        StringBuilder calls = new StringBuilder();
        if (label != null) {
            calls.append(".routeDescription(\"").append(javaEscape(label)).append("\")");
        }
        if (note != null) {
            calls.append(".routeNote(\"").append(javaEscape(note)).append("\")");
        }
        return content.substring(0, m.end()) + calls + content.substring(m.end());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String oneLine(String text) {
        return blank(text) ? null : text.replaceAll("\\s+", " ").strip();
    }

    static String yamlQuote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String xmlEscape(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String javaEscape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
