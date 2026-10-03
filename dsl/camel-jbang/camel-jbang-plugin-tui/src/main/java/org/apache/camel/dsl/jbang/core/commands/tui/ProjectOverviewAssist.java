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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteDescriptions;
import org.apache.camel.util.json.JsonObject;

/**
 * The AI project overview in the TUI (CAMEL-25143): the {@code /overview} command of the AI panel, the
 * {@code camel.tui.ai.overview} setting, and the AI-assisted route descriptions the tabs show.
 * <p/>
 * The facts come from the route sources ({@link ProjectOverview}); the model only adds what they cannot say: an
 * overview, the capabilities, and descriptions of routes that have none. It gets one plain request with the facts and
 * the route excerpts, no tools, so a small local model does it as well as a hosted one. The answer is saved in the
 * project's {@link IntegrationSummary#FILE_NAME}, marked as AI-assisted; putting a description into a route source is a
 * separate step ({@code /overview apply}) that goes through the same confirm dialog, with a diff, as any AI write.
 */
final class ProjectOverviewAssist {

    static final String MODE_MANUAL = "manual";
    static final String MODE_AUTO = "auto";
    static final String MODE_OFF = "off";

    static final String USAGE = "[refresh|show|apply]";

    /** Where the panel shows what happens. */
    interface Sink {
        void add(AiRole role, String text);
    }

    private static volatile CamelCatalog catalog;

    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread worker;
    private volatile boolean stopRequested;
    /** The projects auto mode already explained in this session, by directory and fingerprint. */
    private final Set<String> autoDone = ConcurrentHashMap.newKeySet();

    static String normalizeMode(String mode) {
        if (mode == null) {
            return MODE_MANUAL;
        }
        return switch (mode.strip().toLowerCase(Locale.ROOT)) {
            case MODE_AUTO -> MODE_AUTO;
            case MODE_OFF, "false" -> MODE_OFF;
            default -> MODE_MANUAL;
        };
    }

    static String mode() {
        return normalizeMode(TuiSettings.load().getAiOverview());
    }

    boolean isRunning() {
        return running.get();
    }

    /**
     * Runs {@code /overview}: without arguments shows the summary when it is up to date and explains the project
     * otherwise; {@code refresh} explains it again, {@code show} shows the summary file as it is, {@code apply} puts
     * the AI descriptions into the route sources.
     *
     * @return what to show at once; the explaining runs in the background and reports to the sink
     */
    String command(String arguments, Path dir, LlmClient client, McpFacade facade, Sink sink) {
        if (MODE_OFF.equals(mode())) {
            return "The AI project overview is off: F2 Settings, AI Overview.";
        }
        if (dir == null) {
            return "No project directory: select an integration, or start the TUI in a project folder.";
        }
        String arg = arguments == null ? "" : arguments.strip().toLowerCase(Locale.ROOT);
        if (running.get()) {
            return "The project overview is still being written.";
        }
        ProjectOverview.Overview overview = ProjectOverview.analyze(dir, catalog());
        if (overview.routes().isEmpty()) {
            return "No routes found in " + dir + ". Select the integration of the project in the Overview tab, or"
                   + " start the TUI in the project folder.";
        }
        IntegrationSummary.Summary summary = IntegrationSummary.read(dir);
        switch (arg) {
            case "show" -> {
                return summary == null
                        ? "No " + IntegrationSummary.FILE_NAME + " yet: /overview writes one."
                        : readSummary(dir);
            }
            case "apply" -> {
                return apply(dir, overview, summary, facade, sink);
            }
            case "", "refresh" -> {
                boolean upToDate = summary != null && !summary.ai().isEmpty()
                        && overview.fingerprint().equals(summary.fingerprint());
                if ("".equals(arg) && upToDate) {
                    return describe(overview, summary);
                }
                if (client == null) {
                    return "No LLM client available. Press Ctrl+P to pick a provider.";
                }
                explain(dir, overview, summary, client, sink);
                return "Explaining " + overview.flows().size() + " routes with " + client.model()
                       + " in the background; the answer is saved in " + IntegrationSummary.FILE_NAME + ".";
            }
            default -> {
                return "Usage: /overview " + USAGE;
            }
        }
    }

    /**
     * In auto mode, explains the project when its summary is missing or out of date, once per project state and
     * session.
     *
     * @return true when it started
     */
    boolean autoExplain(Path dir, LlmClient client, McpFacade facade, Sink sink) {
        return autoExplain(dir, client, facade, sink, () -> true);
    }

    /**
     * As {@link #autoExplain(Path, LlmClient, McpFacade, Sink)}, but starts only once the panel is idle: a local model
     * answers one request at a time, so a question asked right after the panel opens would otherwise wait for the whole
     * summary. Blocks the calling (background) thread until then.
     */
    boolean autoExplain(Path dir, LlmClient client, McpFacade facade, Sink sink, BooleanSupplier idle) {
        if (!MODE_AUTO.equals(mode()) || dir == null || client == null || running.get()) {
            return false;
        }
        ProjectOverview.Overview overview = ProjectOverview.analyze(dir, catalog());
        if (overview.flows().isEmpty() || !autoDone.add(dir + "|" + overview.fingerprint())) {
            return false;
        }
        IntegrationSummary.Summary summary = IntegrationSummary.read(dir);
        if (summary != null && !summary.ai().isEmpty() && overview.fingerprint().equals(summary.fingerprint())) {
            return false;
        }
        if (!awaitIdle(idle, IDLE_WAIT_MS) || running.get()) {
            return false;
        }
        sink.add(AiRole.SYSTEM, "The project summary is " + (summary == null ? "missing" : "out of date")
                                + ": explaining " + overview.flows().size() + " routes with " + client.model()
                                + " in the background (AI Overview: auto).");
        explain(dir, overview, summary, client, sink);
        return true;
    }

    /** How long the summary waits for the panel to become idle before it gives up for this session. */
    static final long IDLE_WAIT_MS = 10 * 60_000;

    /** Waits until {@code idle} holds, checking a few times a second; false when it never did in time. */
    static boolean awaitIdle(BooleanSupplier idle, long maxWaitMs) {
        long until = System.currentTimeMillis() + maxWaitMs;
        while (!idle.getAsBoolean()) {
            if (System.currentTimeMillis() >= until) {
                return false;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /**
     * Stops the summary being written, so a question waiting behind it on a local model goes first.
     *
     * @return true when one was running
     */
    boolean stop() {
        Thread w = worker;
        if (!running.get() || w == null) {
            return false;
        }
        stopRequested = true;
        w.interrupt();
        return true;
    }

    private void explain(
            Path dir, ProjectOverview.Overview overview, IntegrationSummary.Summary previous, LlmClient client, Sink sink) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        stopRequested = false;
        Thread t = new Thread(() -> {
            try {
                String answer = ask(client, IntegrationSummary.systemPrompt(),
                        IntegrationSummary.userPrompt(overview, null));
                IntegrationSummary.AiContent fresh = IntegrationSummary.parseAnswer(answer, overview);
                if (fresh.isEmpty()) {
                    sink.add(AiRole.ERROR, "The model's answer did not follow the format; nothing was saved.\n\n"
                                           + (answer == null ? "(no answer)" : answer));
                    return;
                }
                IntegrationSummary.AiContent merged
                        = IntegrationSummary.merge(overview, previous != null ? previous.ai() : null, fresh);
                IntegrationSummary.write(overview, merged, overview.fingerprint(), client.model());
                IntegrationSummaryHints.invalidate(dir);
                IntegrationSummary.Summary saved = IntegrationSummary.read(dir);
                sink.add(AiRole.ASSISTANT, describe(overview, saved != null
                        ? saved
                        : new IntegrationSummary.Summary(
                                overview.fingerprint(), client.model(), null, merged.overview(),
                                merged.capabilities(), merged.descriptions())));
            } catch (Exception e) {
                if (!stopRequested) {
                    sink.add(AiRole.ERROR, "Explaining the project failed: " + e.getMessage());
                }
            } finally {
                worker = null;
                running.set(false);
            }
        }, "tui-ai-overview");
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    /** One plain request without tools; the answer text. */
    static String ask(LlmClient client, String system, String user) {
        LlmClient.ChatResponse response = client.chatWithTools(system, List.of(LlmClient.Message.user(user)), List.of());
        return response != null ? response.text() : null;
    }

    /**
     * The summary as the panel shows it: the AI-assisted parts under a marked heading, then what the sources say in
     * numbers, then how to use it.
     */
    static String describe(ProjectOverview.Overview overview, IntegrationSummary.Summary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Project overview ").append(IntegrationSummary.AI_MARK);
        if (summary.model() != null) {
            sb.append(" (").append(summary.model()).append(')');
        }
        sb.append("\n\n");
        if (summary.overview() != null) {
            sb.append(summary.overview()).append("\n\n");
        }
        for (IntegrationSummary.Capability c : summary.capabilities()) {
            sb.append("- **").append(c.name()).append("**");
            if (!c.routes().isEmpty()) {
                sb.append(" (").append(String.join(", ", c.routes())).append(')');
            }
            if (c.text() != null && !c.text().isBlank()) {
                sb.append(": ").append(c.text());
            }
            sb.append('\n');
        }
        if (!summary.descriptions().isEmpty() || !summary.notes().isEmpty()) {
            sb.append("\n**Suggested route descriptions** ").append(IntegrationSummary.AI_MARK).append("\n\n");
            Set<String> keys = new LinkedHashSet<>(summary.descriptions().keySet());
            keys.addAll(summary.notes().keySet());
            for (String k : keys) {
                sb.append("- `").append(k).append('`');
                String label = summary.descriptions().get(k);
                String note = summary.notes().get(k);
                if (label != null) {
                    sb.append(" **").append(label).append("**");
                }
                if (note != null) {
                    sb.append(label != null ? ": " : " ").append(note);
                }
                sb.append('\n');
            }
        }
        sb.append("\n---\n");
        sb.append("From the sources: ").append(overview.flows().size()).append(" routes, ")
                .append(overview.entryPoints().size()).append(" entry points, ").append(overview.links().size())
                .append(" links, ").append(overview.systems().stream().map(ProjectOverview.SystemUse::uri).distinct().count())
                .append(" external endpoints");
        long warnings = overview.findings().stream().filter(f -> !"info".equals(f.level())).count();
        if (warnings > 0) {
            sb.append(", ").append(warnings).append(" warnings");
        }
        sb.append(".\n");
        if (!overview.fingerprint().equals(summary.fingerprint())) {
            sb.append("\nThe routes changed after this was written: /overview refresh explains them again.\n");
        }
        sb.append("\nThe Diagram tab shows the capabilities as a map: press v in the topology.\n");
        sb.append("\nSaved in `").append(IntegrationSummary.FILE_NAME).append("`; /overview show prints it");
        if (!summary.descriptions().isEmpty() || !summary.notes().isEmpty()) {
            sb.append(", /overview apply puts the suggested descriptions into the routes (you confirm each file)");
        }
        sb.append('.');
        return sb.toString();
    }

    private static String readSummary(Path dir) {
        try {
            return Files.readString(dir.resolve(IntegrationSummary.FILE_NAME), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Cannot read " + IntegrationSummary.FILE_NAME + ": " + e.getMessage();
        }
    }

    /**
     * Puts the AI descriptions into the route sources through the TUI's write path: a confirm dialog with the diff for
     * every file, whatever the write mode, since these are route sources.
     */
    private String apply(
            Path dir, ProjectOverview.Overview overview, IntegrationSummary.Summary summary, McpFacade facade,
            Sink sink) {
        if (summary == null || summary.descriptions().isEmpty() && summary.notes().isEmpty()) {
            return "No suggested route descriptions in " + IntegrationSummary.FILE_NAME + ": /overview writes them.";
        }
        String name = facade != null ? facade.getSelectedIntegrationName() : null;
        if (facade == null || name == null || !dir.equals(facade.getSelectedSourceDirectory())) {
            return "Select the integration of " + dir + " to change its route sources.";
        }
        RouteDescriptions.Plan plan = RouteDescriptions.plan(overview, summary.descriptions(), summary.notes());
        if (plan.changes().isEmpty()) {
            return "No route could be given its description: " + skippedText(plan);
        }
        if (!running.compareAndSet(false, true)) {
            return "The project overview is still being written.";
        }
        Thread t = new Thread(() -> {
            StringBuilder report = new StringBuilder();
            try {
                int applied = 0;
                for (RouteDescriptions.Change change : plan.changes()) {
                    JsonObject result = facade.writeFile(name, change.file(), change.newContent(), true);
                    String status = result.getString("status");
                    report.append("- ").append(change.file()).append(": ").append(status != null ? status : "?")
                            .append(" (").append(String.join(", ", change.routes())).append(")\n");
                    if ("overwritten".equals(status) || "created".equals(status) || "written".equals(status)) {
                        applied++;
                    }
                }
                if (applied > 0) {
                    // descriptions in the source are facts now: the summary keeps only the ones still missing
                    ProjectOverview.Overview after = ProjectOverview.analyze(dir, catalog());
                    IntegrationSummary.AiContent rest = IntegrationSummary.merge(after, summary.ai(),
                            new IntegrationSummary.AiContent(null, List.of(), Map.of()));
                    IntegrationSummary.write(after, rest, after.fingerprint(), summary.model());
                    IntegrationSummaryHints.invalidate(dir);
                }
                if (!plan.skipped().isEmpty()) {
                    report.append("\nNot placed: ").append(skippedText(plan));
                }
                sink.add(AiRole.SYSTEM, "Route descriptions:\n" + report);
            } catch (Exception e) {
                sink.add(AiRole.ERROR, "Applying the descriptions failed: " + e.getMessage());
            } finally {
                running.set(false);
            }
        }, "tui-ai-overview-apply");
        t.setDaemon(true);
        t.start();
        return "Putting " + plan.changes().stream().mapToInt(c -> c.routes().size()).sum() + " descriptions into "
               + plan.changes().size() + " files: confirm each change.";
    }

    private static String skippedText(RouteDescriptions.Plan plan) {
        StringBuilder sb = new StringBuilder();
        for (RouteDescriptions.Skipped s : plan.skipped()) {
            sb.append(sb.isEmpty() ? "" : "; ").append(s.route()).append(" (").append(s.reason()).append(')');
        }
        return sb.toString();
    }

    /**
     * The catalog the overview names and categorizes components with: the CLI's own, loaded once. The titles and labels
     * hardly change between versions, and loading the selected integration's version could mean a download on the UI
     * thread.
     */
    static CamelCatalog catalog() {
        CamelCatalog c = catalog;
        if (c == null) {
            c = new DefaultCamelCatalog();
            catalog = c;
        }
        return c;
    }
}
