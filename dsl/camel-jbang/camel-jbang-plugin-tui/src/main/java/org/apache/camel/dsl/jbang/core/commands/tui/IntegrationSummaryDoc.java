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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;

/**
 * The project's integration summary as a document, opened like a README in the markdown viewer (CAMEL-25143): from
 * {@code /overview show}, and with {@code s} from the architecture, the topology and a route.
 * <p/>
 * The comments that fence the AI sections are for tools and are left out; the {@code ✦ AI-assisted} headings stay, so
 * the marking is still there. A project without a summary gets the facts derived from its sources, saying how to add
 * the AI part; one whose routes changed since the AI wrote its part says it is out of date in the title.
 */
final class IntegrationSummaryDoc {

    static final String ARCHITECTURE = "Architecture";
    static final String ROUTES = "Routes";

    private IntegrationSummaryDoc() {
    }

    /** What the viewer shows: a title and the markdown. */
    record Doc(String title, String markdown) {
    }

    /** The summary of a project for the viewer, or null when there is no project or it has no routes. */
    static Doc load(Path dir) {
        if (dir == null) {
            return null;
        }
        ProjectOverview.Overview overview = ProjectOverview.analyze(dir, ProjectOverviewAssist.catalog());
        String name = dir.getFileName() != null ? dir.getFileName().toString() : dir.toString();
        Path file = dir.resolve(IntegrationSummary.FILE_NAME);
        if (Files.isRegularFile(file)) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                IntegrationSummary.Summary summary = IntegrationSummary.parse(text);
                String title = IntegrationSummary.FILE_NAME + " · " + name;
                boolean hasAi = !summary.ai().isEmpty();
                if (hasAi && !overview.fingerprint().equals(summary.fingerprint())) {
                    title += "  " + IntegrationSummaryHints.MARK + "out of date: /overview refresh";
                }
                return new Doc(title, forViewing(text));
            } catch (IOException e) {
                // fall through to the facts
            }
        }
        if (overview.flows().isEmpty()) {
            return null;
        }
        String facts = IntegrationSummary.render(overview, null, null, null, null)
                .replace("> Derived from the route sources. No AI-assisted sections yet.",
                        "> Derived from the route sources. Not saved yet: /overview in the AI panel explains the"
                                                                                           + " project and saves it as "
                                                                                           + IntegrationSummary.FILE_NAME
                                                                                           + ".");
        return new Doc("Integration summary · " + name + " (not saved)", forViewing(facts));
    }

    /**
     * The markdown for the viewer: without the comments that fence the AI sections and carry the header (they are for
     * tools), and with the routes table as a list, since the viewer gives table columns equal widths and would cut off
     * the notes.
     */
    static String forViewing(String markdown) {
        StringBuilder sb = new StringBuilder();
        boolean routes = false;
        for (String line : markdown.split("\\R", -1)) {
            String t = line.strip();
            if (t.startsWith("<!--") && t.endsWith("-->")) {
                continue;
            }
            if (t.startsWith("## ")) {
                routes = t.equals("## " + ROUTES);
            }
            if (routes && t.startsWith("|")) {
                String item = routeItem(cells(t));
                if (item != null) {
                    sb.append(item).append('\n');
                }
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * A row of the routes table (Route | From | Description | Note | Source) as a list item; null for the header and
     * separator rows.
     */
    private static String routeItem(List<String> cells) {
        if (cells.size() < 4 || !cells.get(0).startsWith("`")) {
            return null;
        }
        boolean withNote = cells.size() >= 5;
        String label = viewText(cells.get(2));
        String note = withNote ? viewText(cells.get(3)) : "";
        String source = cells.get(withNote ? 4 : 3);
        StringBuilder sb = new StringBuilder("- **").append(cells.get(0).replace("`", "")).append("**");
        if (!label.isEmpty()) {
            sb.append(" \u2014 ").append(label);
        }
        if (!note.isEmpty()) {
            sb.append(label.isEmpty() ? " \u2014 " : ": ").append(note);
        }
        sb.append(" *(").append(cells.get(1).isEmpty() ? "" : cells.get(1) + ", ").append(source).append(")*");
        return sb.toString();
    }

    /** A cell as the viewer shows it: the AI prefix shortened to the mark. */
    private static String viewText(String cell) {
        String t = cell.strip();
        return t.startsWith(IntegrationSummary.AI_PREFIX)
                ? IntegrationSummaryHints.MARK + t.substring(IntegrationSummary.AI_PREFIX.length()) : t;
    }

    /** The cells of a markdown table row; an escaped pipe stays in its cell. */
    private static List<String> cells(String row) {
        List<String> out = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        String inner = row.substring(1, row.endsWith("|") && row.length() > 1 ? row.length() - 1 : row.length());
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\\' && i + 1 < inner.length() && inner.charAt(i + 1) == '|') {
                cell.append("\\|");
                i++;
            } else if (c == '|') {
                out.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        out.add(cell.toString().strip());
        return out;
    }

    /**
     * Opens the summary of a project in the markdown viewer, scrolled to a section.
     *
     * @param  heading the section to scroll to, such as {@link #ARCHITECTURE}, or null for the top
     * @return         why it could not be opened, or null when it opened
     */
    static String open(MonitorContext ctx, Path dir, String heading) {
        if (ctx == null || ctx.openMarkdownAtCallback == null) {
            return "The document viewer is not available";
        }
        Doc doc = load(dir);
        if (doc == null) {
            return dir == null
                    ? "No source directory for the selected integration"
                    : "No routes found in " + dir;
        }
        ctx.openMarkdownAtCallback.open(doc.title(), doc.markdown(), heading);
        return null;
    }
}
