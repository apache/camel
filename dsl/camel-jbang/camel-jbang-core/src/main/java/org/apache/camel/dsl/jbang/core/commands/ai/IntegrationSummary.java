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
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.EntryPoint;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Finding;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Link;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Overview;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.SystemUse;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Route;
import org.apache.camel.util.SensitiveUtils;

/**
 * The integration summary of a project: a Markdown file ({@value #FILE_NAME}) in the project directory that explains
 * what the integrations do. It is meant to be read by people, committed with the routes, and read back by tools.
 * <p/>
 * The file mixes two kinds of content, and keeps them apart so a reader can tell which is which:
 * <ul>
 * <li>facts derived from the sources by {@link ProjectOverview}: entry points, routes, the flows between them, external
 * systems, findings;</li>
 * <li>AI-assisted content: an overview, the business capabilities, and descriptions of routes that have none in the
 * source. These sections are headed with {@value #AI_MARK}, fenced with {@code <!-- ai:begin -->} and
 * {@code <!-- ai:end -->}, and an AI description is prefixed with {@value #AI_PREFIX}.</li>
 * </ul>
 * The header records the fingerprint of the route files the AI saw, so a tool can tell when the AI sections no longer
 * match the routes. Nothing is written that the sources do not already show to anyone reading them: endpoint URIs lose
 * their query (which may carry credentials), and property placeholders stay unresolved.
 */
public final class IntegrationSummary {

    public static final String FILE_NAME = "camel-summary.md";
    /** Marks an AI-assisted section heading. */
    public static final String AI_MARK = "\u2726 AI-assisted";
    /** Prefixes an AI-written route description. */
    public static final String AI_PREFIX = "\u2726 AI: ";

    static final String AI_BEGIN = "<!-- ai:begin -->";
    static final String AI_END = "<!-- ai:end -->";
    private static final Pattern HEADER = Pattern.compile("<!--\\s*camel-summary\\b([^>]*)-->");
    private static final Pattern HEADER_ATTR = Pattern.compile("(\\w+)=\"([^\"]*)\"");
    private static final Pattern CAPABILITY_LINE = Pattern.compile(
            "^\\s*[-*]\\s+\\*\\*(.+?)\\*\\*\\s*(?:\\(routes?:\\s*([^)]*)\\))?\\s*[:\\-\u2013\u2014]?\\s*(.*)$");
    /** Most of a route's source that goes into the prompt. */
    static final int MAX_EXCERPT_LINES = 40;
    /** Most source text in one prompt, so a small local model still has room to answer. */
    static final int MAX_PROMPT_SOURCE = 14_000;

    private IntegrationSummary() {
    }

    /** A business capability: a name, the routes that implement it and one sentence about it. */
    public record Capability(String name, List<String> routes, String text) {
    }

    /**
     * What an AI wrote about a project: an overview, the capabilities, and descriptions of the routes that have none,
     * by route key.
     */
    public record AiContent(String overview, List<Capability> capabilities, Map<String, String> descriptions,
            List<String> utility, Map<String, String> notes) {

        public AiContent(String overview, List<Capability> capabilities, Map<String, String> descriptions) {
            this(overview, capabilities, descriptions, List.of(), Map.of());
        }

        public AiContent(String overview, List<Capability> capabilities, Map<String, String> descriptions,
                         List<String> utility) {
            this(overview, capabilities, descriptions, utility, Map.of());
        }

        public boolean isEmpty() {
            return (overview == null || overview.isBlank()) && capabilities.isEmpty() && descriptions.isEmpty()
                    && utility.isEmpty() && notes.isEmpty();
        }
    }

    /**
     * A summary file read back: the AI content with the fingerprint of the routes it was written for, the model and the
     * date. {@code descriptions} and {@code notes} are what the AI wrote for routes without them; {@code sourceNotes}
     * are the notes the routes have in their source, which the runtime does not report.
     */
    public record Summary(String fingerprint, String model, String date, String overview, List<Capability> capabilities,
            Map<String, String> descriptions, List<String> utility, Map<String, String> notes,
            Map<String, String> sourceNotes) {

        public Summary(String fingerprint, String model, String date, String overview, List<Capability> capabilities,
                       Map<String, String> descriptions) {
            this(fingerprint, model, date, overview, capabilities, descriptions, List.of(), Map.of(), Map.of());
        }

        public AiContent ai() {
            return new AiContent(overview, capabilities, descriptions, utility, notes);
        }
    }

    // ---- reading ----

    /** The summary file of a directory, or null when there is none or it cannot be read. */
    public static Summary read(Path dir) {
        if (dir == null) {
            return null;
        }
        Path file = dir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /** Reads the AI content and header of a summary file's text. */
    public static Summary parse(String text) {
        Map<String, String> header = new LinkedHashMap<>();
        Matcher h = HEADER.matcher(text);
        if (h.find()) {
            Matcher a = HEADER_ATTR.matcher(h.group(1));
            while (a.find()) {
                header.put(a.group(1), a.group(2));
            }
        }
        String overview = null;
        List<Capability> capabilities = new ArrayList<>();
        Map<String, String> descriptions = new LinkedHashMap<>();
        Map<String, String> notes = new LinkedHashMap<>();
        Map<String, String> sourceNotes = new LinkedHashMap<>();
        List<String> utility = new ArrayList<>();
        String section = null;
        boolean inAi = false;
        StringBuilder ai = new StringBuilder();
        for (String line : text.split("\\R")) {
            String t = line.strip();
            if (t.startsWith("## ")) {
                section = t.substring(3).replace(AI_MARK, "").strip().toLowerCase(Locale.ROOT);
                continue;
            }
            if (AI_BEGIN.equals(t)) {
                inAi = true;
                ai.setLength(0);
                continue;
            }
            if (AI_END.equals(t)) {
                inAi = false;
                if ("overview".equals(section)) {
                    overview = ai.toString().strip();
                } else if ("capabilities".equals(section)) {
                    for (String c : ai.toString().split("\n")) {
                        Capability cap = capability(c);
                        if (cap != null) {
                            capabilities.add(cap);
                        }
                    }
                } else if ("utility routes".equals(section)) {
                    for (String u : ai.toString().split("\n")) {
                        String key = u.strip().replaceFirst("^[-*]\\s*", "").replace("`", "").strip();
                        if (!key.isEmpty()) {
                            utility.add(key);
                        }
                    }
                }
                continue;
            }
            if (inAi) {
                ai.append(line).append('\n');
            } else if ("routes".equals(section) && t.startsWith("|")) {
                String[] cells = splitRow(t);
                if (cells.length >= 3 && cells[0].startsWith("`")) {
                    String key = unescapeCell(cells[0].replace("`", ""));
                    boolean withNotes = cells.length >= 5;
                    if (cells[2].startsWith(AI_PREFIX)) {
                        String d = unescapeCell(cells[2].substring(AI_PREFIX.length()).strip());
                        // a file from before labels and notes has sentences here: a long one is a note
                        if (withNotes || d.split("\\s+").length <= MAX_LABEL_WORDS) {
                            descriptions.put(key, d);
                        } else {
                            notes.put(key, d);
                        }
                    }
                    // Route | From | Description | Note | Source; an older file has no Note column
                    String note = withNotes ? cells[3] : "";
                    if (note.startsWith(AI_PREFIX)) {
                        notes.put(key, unescapeCell(note.substring(AI_PREFIX.length()).strip()));
                    } else if (!note.isBlank()) {
                        sourceNotes.put(key, unescapeCell(note.strip()));
                    }
                }
            }
        }
        return new Summary(
                header.get("fingerprint"), header.get("model"), header.get("date"),
                overview == null || overview.isBlank() ? null : overview, capabilities, descriptions, utility, notes,
                sourceNotes);
    }

    private static Capability capability(String line) {
        Matcher m = CAPABILITY_LINE.matcher(line);
        if (!m.matches()) {
            return null;
        }
        List<String> routes = new ArrayList<>();
        if (m.group(2) != null) {
            for (String r : m.group(2).split(",")) {
                String key = r.replace("`", "").strip();
                if (!key.isEmpty()) {
                    routes.add(key);
                }
            }
        }
        return new Capability(m.group(1).strip(), routes, m.group(3).strip());
    }

    /** The cells of a Markdown table row; an escaped pipe stays inside its cell. */
    private static String[] splitRow(String row) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        String inner = row.substring(1, row.endsWith("|") && row.length() > 1 ? row.length() - 1 : row.length());
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\\' && i + 1 < inner.length() && inner.charAt(i + 1) == '|') {
                cell.append("\\|");
                i++;
            } else if (c == '|') {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().strip());
        return cells.toArray(new String[0]);
    }

    // ---- writing ----

    /**
     * The summary file for an overview. The facts are always those of the overview; the AI content is kept as given
     * together with the fingerprint and model it was written with (the fingerprint of the overview when the AI content
     * is new).
     *
     * @param ai          what an AI wrote, or null for a summary of the facts alone
     * @param fingerprint the fingerprint of the routes the AI content was written for
     * @param model       the model that wrote it, may be null
     * @param date        when it was written (yyyy-mm-dd), may be null for today
     */
    public static String render(Overview overview, AiContent ai, String fingerprint, String model, String date) {
        boolean hasAi = ai != null && !ai.isEmpty();
        String aiFingerprint = hasAi && fingerprint != null ? fingerprint : overview.fingerprint();
        String when = date != null ? date : LocalDate.now().toString();
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- camel-summary fingerprint=\"").append(aiFingerprint).append('"');
        if (hasAi && model != null && !model.isBlank()) {
            sb.append(" model=\"").append(model.replace("\"", "'")).append('"');
        }
        sb.append(" date=\"").append(when).append("\" -->\n");
        String name = overview.directory() != null && overview.directory().getFileName() != null
                ? overview.directory().getFileName().toString() : "project";
        sb.append("# Integration summary: ").append(name).append("\n\n");
        if (hasAi) {
            sb.append("> Sections marked ").append(AI_MARK).append(" were written by an AI")
                    .append(model != null && !model.isBlank() ? " (" + model + ")" : "")
                    .append(" from the route sources on ").append(when)
                    .append(". Review them before relying on them. The other sections are derived from the sources.\n");
            if (!aiFingerprint.equals(overview.fingerprint())) {
                sb.append(">\n> The routes changed after the AI sections were written; they may be out of date.\n");
            }
            sb.append('\n');
        } else {
            sb.append("> Derived from the route sources. No AI-assisted sections yet.\n\n");
        }

        if (hasAi && ai.overview() != null && !ai.overview().isBlank()) {
            sb.append("## Overview ").append(AI_MARK).append("\n\n").append(AI_BEGIN).append('\n')
                    .append(ai.overview().strip()).append('\n').append(AI_END).append("\n\n");
        }
        // the capabilities as the map places their routes: a route grouped by the source, shared by several groups or
        // plumbing is not in the capability the AI named, and a capability left without routes is not listed, so the
        // AI section and the map below never disagree
        ProjectCapabilities.Capabilities caps = ProjectCapabilities.build(overview, hasAi ? ai : null);
        List<Capability> placed = new ArrayList<>();
        if (hasAi) {
            for (Capability c : ai.capabilities()) {
                ProjectCapabilities.Group g = caps.group("capability:" + c.name());
                if (g != null && !g.routes().isEmpty()) {
                    placed.add(new Capability(c.name(), g.routes(), c.text()));
                }
            }
        }
        if (!placed.isEmpty()) {
            sb.append("## Capabilities ").append(AI_MARK).append("\n\n").append(AI_BEGIN).append('\n');
            for (Capability c : placed) {
                sb.append("- **").append(c.name()).append("**");
                if (!c.routes().isEmpty()) {
                    sb.append(" (routes: ").append(String.join(", ", c.routes().stream().map(r -> "`" + r + "`").toList()))
                            .append(')');
                }
                if (c.text() != null && !c.text().isBlank()) {
                    sb.append(": ").append(c.text().strip());
                }
                sb.append('\n');
            }
            sb.append(AI_END).append("\n\n");
        }

        if (hasAi && !ai.utility().isEmpty()) {
            sb.append("## Utility routes ").append(AI_MARK).append("\n\n").append(AI_BEGIN).append('\n');
            for (String u : ai.utility()) {
                sb.append("- `").append(u).append("`\n");
            }
            sb.append(AI_END).append("\n\n");
        }

        renderArchitecture(sb, caps);

        sb.append("## Entry points\n\n");
        if (overview.entryPoints().isEmpty()) {
            sb.append("None found: every route is called by another route or from code.\n\n");
        } else {
            for (EntryPoint e : overview.entryPoints()) {
                sb.append("- ").append(entryKind(e)).append(": `").append(e.label()).append('`');
                if (e.route() != null) {
                    sb.append(" \u2192 `").append(e.route()).append('`');
                }
                sb.append('\n');
            }
            sb.append('\n');
        }

        sb.append("## Routes\n\n");
        List<Route> flows = overview.flows();
        if (flows.isEmpty()) {
            sb.append("No routes found.\n\n");
        } else {
            sb.append("| Route | From | Description | Note | Source |\n|---|---|---|---|---|\n");
            for (Route r : flows) {
                String description;
                if (r.hasDescription()) {
                    description = r.description();
                } else if (hasAi && ai.descriptions().containsKey(r.key())) {
                    description = AI_PREFIX + ai.descriptions().get(r.key());
                } else {
                    description = "";
                }
                String note;
                if (r.hasNote()) {
                    note = r.note();
                } else if (hasAi && ai.notes().containsKey(r.key())) {
                    note = AI_PREFIX + ai.notes().get(r.key());
                } else {
                    note = "";
                }
                sb.append("| `").append(cell(r.key())).append("` | ")
                        .append(r.from() != null ? "`" + cell(r.from().uri()) + "`" : kindLabel(r)).append(" | ")
                        .append(cell(description)).append(" | ")
                        .append(cell(note)).append(" | ")
                        .append(cell(r.file() + ":" + r.line())).append(" |\n");
            }
            sb.append('\n');
        }

        if (!overview.links().isEmpty()) {
            sb.append("## Flows between routes\n\n");
            for (Link l : overview.links()) {
                sb.append("- `").append(l
                        .from()).append("` ").append(linkVerb(l.kind())).append(" `").append(l.to())
                        .append("` over `").append(l.endpoint()).append("`\n");
            }
            sb.append('\n');
        }

        if (!overview.systems().isEmpty()) {
            sb.append("## External systems\n\n");
            Map<String, Map<String, Set<String>[]>> byCategory = new TreeMap<>();
            for (SystemUse s : overview.systems()) {
                @SuppressWarnings("unchecked")
                Set<String>[] dirs = byCategory.computeIfAbsent(s.category(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(s.name() + "|" + s.uri(),
                                k -> new Set[] { new LinkedHashSet<>(), new LinkedHashSet<>() });
                dirs["in".equals(s.direction()) ? 0 : 1].add("`" + s.route() + "`");
            }
            byCategory.forEach((category, uses) -> {
                sb.append("- **").append(categoryLabel(category)).append("**\n");
                uses.forEach((key, dirs) -> {
                    String[] parts = key.split("\\|", 2);
                    sb.append("  - ").append(parts[0]).append(" `").append(parts[1]).append('`');
                    if (!dirs[0].isEmpty()) {
                        sb.append(", read by ").append(String.join(", ", dirs[0]));
                    }
                    if (!dirs[1].isEmpty()) {
                        sb.append(", written by ").append(String.join(", ", dirs[1]));
                    }
                    sb.append('\n');
                });
            });
            sb.append('\n');
        }

        if (!overview.findings().isEmpty()) {
            sb.append("## Findings\n\n");
            for (Finding f : overview.findings()) {
                sb.append("- ").append(f.level()).append(": ").append(f.message()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * The architecture: the routes grouped by what they do, with the facts of each group. A group an AI decided is
     * marked; a group from the source's route group is not. Utility routes are listed last, briefly.
     */
    static void renderArchitecture(StringBuilder sb, ProjectCapabilities.Capabilities caps) {
        if (caps.groups().isEmpty()) {
            return;
        }
        sb.append("## Architecture\n\n");
        if (caps.hasAi()) {
            sb.append("Groups marked ").append(AI_MARK).append(" were proposed by an AI; the others come from the"
                                                               + " route groups and links in the sources.\n\n");
        }
        for (ProjectCapabilities.Group g : caps.groups()) {
            sb.append("- **").append(g.name()).append("**");
            if (g.ai()) {
                sb.append(" \u2726");
            }
            sb.append(" (").append(groupKind(g.kind())).append("): ")
                    .append(String.join(", ", g.routes().stream().map(r -> "`" + r + "`").toList()));
            if (!g.entryPoints().isEmpty()) {
                sb.append("; entry: ").append(String.join(", ", g.entryPoints()));
            }
            if (!g.systems().isEmpty()) {
                sb.append("; systems: ").append(String.join(", ", g.systems()));
            }
            if (g.warnings() > 0) {
                sb.append("; ").append(g.warnings()).append(g.warnings() == 1 ? " warning" : " warnings");
            }
            List<String> to = new ArrayList<>();
            for (ProjectCapabilities.GroupLink l : caps.links()) {
                if (l.from().equals(g.id())) {
                    ProjectCapabilities.Group target = caps.group(l.to());
                    to.add(linkVerb(l.kind()) + " " + (target != null ? target.name() : l.to()));
                }
            }
            if (!to.isEmpty()) {
                sb.append("; ").append(String.join(", ", to));
            }
            sb.append('\n');
        }
        sb.append('\n');
    }

    private static String groupKind(String kind) {
        return switch (kind) {
            case "group" -> "route group";
            case "capability" -> "capability";
            case ProjectCapabilities.SHARED -> "used by several";
            case ProjectCapabilities.UTILITY -> "plumbing";
            default -> "not grouped";
        };
    }

    /** Writes the summary file into the overview's directory and returns its path. */
    public static Path write(Overview overview, AiContent ai, String fingerprint, String model) throws IOException {
        Path file = overview.directory().resolve(FILE_NAME);
        Files.writeString(file, render(overview, ai, fingerprint, model, null), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Merges newly written AI content over what a summary already holds: a new overview or capability list replaces the
     * old one, descriptions are added per route. Descriptions of routes that are gone or now have one in the source are
     * dropped.
     */
    public static AiContent merge(Overview overview, AiContent previous, AiContent fresh) {
        String text = fresh.overview() != null && !fresh.overview().isBlank() ? fresh.overview()
                : previous != null ? previous.overview() : null;
        List<Capability> caps = !fresh.capabilities().isEmpty() ? fresh.capabilities()
                : previous != null ? previous.capabilities() : List.of();
        Map<String, String> descriptions = new LinkedHashMap<>();
        if (previous != null) {
            descriptions.putAll(previous.descriptions());
        }
        descriptions.putAll(fresh.descriptions());
        Set<String> undescribed = new LinkedHashSet<>();
        overview.undescribed().forEach(r -> undescribed.add(r.key()));
        descriptions.keySet().retainAll(undescribed);
        Set<String> keys = new LinkedHashSet<>();
        overview.flows().forEach(r -> keys.add(r.key()));
        List<String> utility = new ArrayList<>(
                !fresh.utility().isEmpty() ? fresh.utility()
                        : previous != null ? previous.utility() : List.of());
        utility.retainAll(keys);
        Map<String, String> notes = new LinkedHashMap<>();
        if (previous != null) {
            notes.putAll(previous.notes());
        }
        notes.putAll(fresh.notes());
        Set<String> unnoted = new LinkedHashSet<>();
        overview.flows().stream().filter(r -> !r.hasNote()).forEach(r -> unnoted.add(r.key()));
        notes.keySet().retainAll(unnoted);
        return new AiContent(text, caps, descriptions, utility, notes);
    }

    /**
     * How an entry point is listed; one that is not remote (per the catalog, such as a timer) starts work inside the
     * integration and is marked internal, the others are driven by remote systems.
     */
    private static String entryKind(EntryPoint e) {
        String kind = switch (e.kind()) {
            case "schedule" -> "Schedule";
            case "http" -> "HTTP";
            default -> "Consumes";
        };
        return ProjectOverview.isInternal(e) ? kind + " (internal)" : kind;
    }

    private static String kindLabel(Route r) {
        return switch (r.kind()) {
            case "templatedRoute" -> "(from a template)";
            case "routeTemplate" -> "(template)";
            default -> "";
        };
    }

    private static String linkVerb(String kind) {
        return switch (kind) {
            case "call" -> "calls";
            case "async" -> "hands off to";
            case "event" -> "publishes events to";
            default -> "shares data with";
        };
    }

    static String categoryLabel(String category) {
        return switch (category) {
            case "ai" -> "AI";
            case "database" -> "Databases";
            case "messaging" -> "Messaging";
            case "file" -> "Files and storage";
            case "email" -> "Email";
            case "apps" -> "Apps and SaaS";
            case "http" -> "HTTP and APIs";
            case "cloud" -> "Cloud services";
            default -> "Other";
        };
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("\r", " ").replace("\n", " ").replace("|", "\\|");
    }

    private static String unescapeCell(String text) {
        return text.replace("\\|", "|");
    }

    // ---- the AI request ----

    /** The system prompt that asks a model for the AI content of a summary, in a form {@link #parseAnswer} reads. */
    public static String systemPrompt() {
        return """
                You are an Apache Camel integration expert. You explain the integrations of a project to people who do \
                not read code: architects, operators, business owners. You are given facts derived from the route \
                sources and excerpts of the routes. Only state what the facts and excerpts show; do not invent systems, \
                routes or behaviour. Keep it short and plain.

                Answer in exactly this format and nothing else:

                OVERVIEW:
                <two to four sentences: what the project does as a whole and for whom>

                CAPABILITIES:
                - <capability name>: <route ids, comma separated> | <one sentence on what it achieves>

                UTILITY:
                - <route id>

                DESCRIPTIONS:
                - <route id>: <label: two to five words, a title and not a sentence> | <one sentence, at most 25 words, on what the route does and why>

                Group the routes listed as needing grouping into two to six business capabilities, each route in one. \
                A route that is plumbing with little business meaning (logging, dead letter, error handling, retries, \
                housekeeping) goes under UTILITY instead of a capability. Routes already grouped by the source keep \
                their group: leave them out. Write a description only for the routes listed as needing one: the label is \
                what a diagram box shows (such as "Order intake & validation"), the sentence explains it. Use the \
                route ids exactly as given.""";
    }

    /**
     * The user prompt: the facts of the overview and excerpts of the routes, secrets masked. Excerpts go to the routes
     * that need a description first, within {@link #MAX_PROMPT_SOURCE} characters.
     *
     * @param sources the content of the route files by relative path; null reads them from the directory
     */
    public static String userPrompt(Overview overview, Map<String, String> sources) {
        StringBuilder sb = new StringBuilder();
        String name = overview.directory() != null && overview.directory().getFileName() != null
                ? overview.directory().getFileName().toString() : "project";
        sb.append("Project: ").append(name).append("\n\n");
        sb.append("Routes:\n");
        for (Route r : overview.flows()) {
            sb.append("- ").append(r.key());
            if (!"route".equals(r.kind())) {
                sb.append(" (").append(r.kind()).append(')');
            }
            if (r.group() != null) {
                sb.append(" [group ").append(r.group()).append(']');
            }
            if (r.from() != null) {
                sb.append(" from ").append(r.from().uri());
                if (r.from().label() != null) {
                    sb.append(" (").append(r.from().label()).append(')');
                }
            }
            if (!r.produces().isEmpty()) {
                sb.append(" to ").append(String.join(", ", r.produces().stream().map(e -> e.uri()).distinct().toList()));
            }
            if (r.hasDescription()) {
                sb.append(" - described as: ").append(r.description());
            }
            if (r.hasNote()) {
                sb.append(" - note: ").append(r.note());
            }
            sb.append('\n');
        }
        if (!overview.entryPoints().isEmpty()) {
            sb.append("\nEntry points:\n");
            for (EntryPoint e : overview.entryPoints()) {
                sb.append("- ").append(e.kind()).append(ProjectOverview.isInternal(e) ? " (internal)" : " (remote)")
                        .append(' ').append(e.label())
                        .append(e.route() != null ? " -> " + e.route() : "").append('\n');
            }
        }
        if (!overview.links().isEmpty()) {
            sb.append("\nFlows between routes:\n");
            for (Link l : overview.links()) {
                sb.append("- ").append(l.from()).append(' ').append(linkVerb(l.kind())).append(' ').append(l.to())
                        .append('\n');
            }
        }
        if (!overview.systems().isEmpty()) {
            sb.append("\nExternal systems:\n");
            Set<String> seen = new LinkedHashSet<>();
            for (SystemUse s : overview.systems()) {
                if (seen.add(s.uri())) {
                    sb.append("- ").append(categoryLabel(s.category())).append(": ").append(s.name()).append(' ')
                            .append(s.uri()).append('\n');
                }
            }
        }
        List<Route> grouped = overview.flows().stream().filter(r -> r.group() != null).toList();
        if (!grouped.isEmpty()) {
            sb.append("\nRoutes already grouped by the source: ")
                    .append(String.join(", ", grouped.stream().map(r -> r.key() + " [" + r.group() + "]").toList()))
                    .append('\n');
        }
        List<Route> ungrouped = overview.flows().stream().filter(r -> r.group() == null).toList();
        sb.append("\nRoutes needing grouping: ")
                .append(ungrouped.isEmpty() ? "none" : String.join(", ", ungrouped.stream().map(Route::key).toList()))
                .append('\n');
        List<Route> undescribed = overview.flows().stream().filter(r -> !r.hasDescription() || !r.hasNote()).toList();
        sb.append("\nRoutes needing a description: ");
        sb.append(undescribed.isEmpty() ? "none" : String.join(", ", undescribed.stream().map(Route::key).toList()))
                .append('\n');

        Map<String, String> files = sources != null ? sources : readSources(overview);
        if (!files.isEmpty()) {
            sb.append("\nRoute excerpts:\n");
            int budget = MAX_PROMPT_SOURCE;
            List<Route> order = new ArrayList<>(undescribed);
            overview.flows().stream().filter(r -> !order.contains(r)).forEach(order::add);
            for (Route r : order) {
                String excerpt = excerpt(overview, files, r);
                if (excerpt == null || excerpt.length() > budget) {
                    continue;
                }
                budget -= excerpt.length();
                sb.append("\n").append(r.key()).append(" (").append(r.file()).append("):\n```\n").append(excerpt)
                        .append("```\n");
            }
        }
        return sb.toString();
    }

    private static Map<String, String> readSources(Overview overview) {
        Map<String, String> files = new LinkedHashMap<>();
        if (overview.directory() == null) {
            return files;
        }
        for (String f : overview.files()) {
            try {
                files.put(f, Files.readString(overview.directory().resolve(f), StandardCharsets.UTF_8));
            } catch (IOException e) {
                // left out
            }
        }
        return files;
    }

    /** The lines of a route: from its start to the next route in the same file, capped, secrets masked. */
    static String excerpt(Overview overview, Map<String, String> files, Route r) {
        String content = files.get(r.file());
        if (content == null) {
            return null;
        }
        String[] lines = content.split("\\R", -1);
        int start = Math.max(0, r.line() - 1);
        int end = lines.length;
        for (Route other : overview.routes()) {
            if (other != r && other.file().equals(r.file()) && other.line() > r.line()) {
                end = Math.min(end, other.line() - 1);
            }
        }
        end = Math.min(end, start + MAX_EXCERPT_LINES);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(maskLine(lines[i])).append('\n');
        }
        return sb.toString();
    }

    private static final Pattern KEY_VALUE = Pattern.compile("^(\\s*-?\\s*[\"']?)([\\w.-]+)([\"']?\\s*[:=]\\s*)(.+)$");
    private static final Pattern XML_ATTR = Pattern.compile("([\\w.-]+)(\\s*=\\s*)\"([^\"]*)\"");

    /**
     * A source line with the values of sensitive keys masked (password, token, secret, access key, ...), whether they
     * stand as a YAML/properties key, an XML attribute or a URI query parameter. A placeholder stays, it names no
     * secret.
     */
    static String maskLine(String line) {
        String masked = ProjectRoutes.safeUri(line);
        Matcher m = KEY_VALUE.matcher(masked);
        if (m.matches() && SensitiveUtils.containsSensitive(m.group(2)) && !m.group(4).strip().startsWith("\"{{")
                && !m.group(4).strip().startsWith("{{")) {
            return m.group(1) + m.group(2) + m.group(3) + "xxxxxx";
        }
        Matcher a = XML_ATTR.matcher(masked);
        StringBuilder sb = new StringBuilder();
        while (a.find()) {
            String value = SensitiveUtils.containsSensitive(a.group(1)) && !a.group(3).startsWith("{{")
                    ? "xxxxxx" : a.group(3);
            a.appendReplacement(sb, Matcher.quoteReplacement(a.group(1) + a.group(2) + "\"" + value + "\""));
        }
        a.appendTail(sb);
        return sb.toString();
    }

    private static final Pattern SECTION = Pattern.compile(
            "^\\s*(?:#+\\s*)?\\**\\s*(OVERVIEW|CAPABILITIES|UTILITY|DESCRIPTIONS)\\s*\\**\\s*(?::|$)\\s*\\**\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM = Pattern.compile("^\\s*(?:[-*\u2022]|\\d+[.)])\\s+(.*)$");

    /**
     * Reads a model's answer to {@link #systemPrompt()}. Tolerant of Markdown around the section names and the items; a
     * description of a route that does not need one, or is not in the project, is dropped, as are unknown routes in a
     * capability.
     */
    public static AiContent parseAnswer(String answer, Overview overview) {
        if (answer == null) {
            return new AiContent(null, List.of(), Map.of());
        }
        Set<String> unlabelled = new LinkedHashSet<>();
        overview.flows().stream().filter(r -> !r.hasDescription()).forEach(r -> unlabelled.add(r.key()));
        Set<String> unnoted = new LinkedHashSet<>();
        overview.flows().stream().filter(r -> !r.hasNote()).forEach(r -> unnoted.add(r.key()));

        Set<String> ungrouped = new LinkedHashSet<>();
        overview.flows().stream().filter(r -> r.group() == null).forEach(r -> ungrouped.add(r.key()));
        List<String> keys = overview.flows().stream().map(Route::key).toList();

        StringBuilder text = new StringBuilder();
        List<Capability> capabilities = new ArrayList<>();
        Map<String, String> descriptions = new LinkedHashMap<>();
        Map<String, String> notes = new LinkedHashMap<>();
        List<String> utility = new ArrayList<>();
        String section = null;
        for (String raw : answer.split("\\R")) {
            if (raw.strip().startsWith("```")) {
                continue;
            }
            Matcher s = SECTION.matcher(raw);
            if (s.matches()) {
                section = s.group(1).toUpperCase(Locale.ROOT);
                String rest = s.group(2).strip();
                if ("OVERVIEW".equals(section) && !rest.isEmpty()) {
                    text.append(rest).append(' ');
                }
                continue;
            }
            if (section == null || raw.isBlank()) {
                continue;
            }
            switch (section) {
                case "OVERVIEW" -> text.append(raw.strip()).append(' ');
                case "CAPABILITIES" -> {
                    Capability c = answerCapability(raw, ungrouped);
                    if (c != null) {
                        capabilities.add(c);
                    }
                }
                case "UTILITY" -> {
                    Matcher item = ITEM.matcher(raw);
                    String body = item.matches() ? item.group(1) : raw.strip();
                    // "- id" or "- id: why"
                    String key = keyAndRest(body, keys)[0];
                    if (ungrouped.contains(key) && !utility.contains(key)) {
                        utility.add(key);
                    }
                }
                default -> {
                    Matcher item = ITEM.matcher(raw);
                    String body = item.matches() ? item.group(1) : raw.strip();
                    String[] keyAndRest = keyAndRest(body, keys);
                    if (keyAndRest[1] != null) {
                        String key = keyAndRest[0];
                        String[] parts = labelAndNote(keyAndRest[1]);
                        if (parts[0] != null && unlabelled.contains(key)) {
                            descriptions.put(key, parts[0]);
                        }
                        if (parts[1] != null && unnoted.contains(key)) {
                            notes.put(key, parts[1]);
                        }
                    }
                }
            }
        }
        String overviewText = text.toString().strip();
        return new AiContent(overviewText.isEmpty() ? null : overviewText, capabilities, descriptions, utility, notes);
    }

    /** The most words a description is taken as a label; a longer one without a label is taken as the note. */
    static final int MAX_LABEL_WORDS = 8;

    /**
     * A description as the model wrote it: {@code label | sentence}, or one text, a label when short and a note when
     * longer, so a model that ignores the format still gives something usable.
     *
     * @return the label and the note, either may be null
     */
    static String[] labelAndNote(String text) {
        String t = clean(text);
        int bar = t.indexOf('|');
        if (bar >= 0) {
            String label = clean(t.substring(0, bar));
            String note = clean(t.substring(bar + 1));
            if (label.split("\\s+").length > MAX_LABEL_WORDS) {
                // a sentence where a label belongs: it explains, so it is the note when there is none
                if (note.isEmpty()) {
                    note = label;
                }
                label = "";
            }
            return new String[] { label.isEmpty() ? null : label, note.isEmpty() ? null : note };
        }
        if (t.isEmpty()) {
            return new String[] { null, null };
        }
        return t.split("\\s+").length <= MAX_LABEL_WORDS ? new String[] { t, null } : new String[] { null, t };
    }

    private static Capability answerCapability(String raw, Set<String> keys) {
        Matcher item = ITEM.matcher(raw);
        if (!item.matches()) {
            return null;
        }
        String body = item.group(1);
        int colon = body.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String name = clean(body.substring(0, colon));
        String rest = body.substring(colon + 1);
        String sentence = "";
        int bar = rest.indexOf('|');
        if (bar >= 0) {
            sentence = clean(rest.substring(bar + 1));
            rest = rest.substring(0, bar);
        }
        List<String> routes = new ArrayList<>();
        for (String r : rest.split(",")) {
            String key = clean(r);
            if (keys.contains(key) && !routes.contains(key)) {
                routes.add(key);
            }
        }
        if (name.isEmpty() || routes.isEmpty() && sentence.isEmpty()) {
            return null;
        }
        return new Capability(name, routes, sentence);
    }

    /** Strips Markdown emphasis, code ticks and quotes around a value. */
    /**
     * The route a line of the answer is about, and what follows its colon (null when there is no colon). A route
     * without an id has a key with a colon of its own ({@code src/main/java/OrderRoute.java:32}), so the key is the
     * longest one of the project the line starts with, or its short form ({@code OrderRoute.java:32}) when that names
     * one route only; else the line up to its first colon.
     */
    static String[] keyAndRest(String body, List<String> keys) {
        String line = body.strip().replaceAll("^[*_`\"']+", "");
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort((a, b) -> b.length() - a.length());
        for (String key : sorted) {
            List<String> forms = new ArrayList<>(List.of(key));
            String shortForm = shortKey(key);
            if (!shortForm.equals(key) && keys.stream().filter(k -> shortKey(k).equals(shortForm)).count() == 1) {
                forms.add(shortForm);
            }
            for (String form : forms) {
                if (line.startsWith(form)) {
                    String after = line.substring(form.length()).replaceAll("^[*_`\"']+", "").strip();
                    if (after.isEmpty()) {
                        return new String[] { key, null };
                    }
                    if (after.startsWith(":")) {
                        return new String[] { key, after.substring(1) };
                    }
                }
            }
        }
        int colon = body.indexOf(':');
        return colon > 0
                ? new String[] { clean(body.substring(0, colon)), body.substring(colon + 1) }
                : new String[] { clean(body), null };
    }

    /** {@code src/main/java/OrderRoute.java:32} as {@code OrderRoute.java:32}; other keys as they are. */
    static String shortKey(String key) {
        int slash = key.lastIndexOf('/');
        return slash >= 0 && key.matches(".*:\\d+$") ? key.substring(slash + 1) : key;
    }

    private static String clean(String s) {
        String t = s.strip();
        t = t.replaceAll("^[*_`\"']+|[*_`\"']+$", "").strip();
        return t;
    }
}
