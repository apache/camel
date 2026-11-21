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
package org.apache.camel.dsl.jbang.core.commands;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder;
import org.apache.camel.dsl.jbang.core.common.outdated.CamelRelease;
import org.apache.camel.dsl.jbang.core.common.outdated.DependencyCheck;
import org.apache.camel.dsl.jbang.core.common.outdated.DependencyRow;
import org.apache.camel.dsl.jbang.core.common.outdated.Versions;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Lists the dependencies of a project, or of a running integration, that are worth a look (CAMEL-27351): a version
 * other than the one the Camel dependencies bring, an artifact loaded twice or in another version than declared, and
 * newer releases. The same check as the Maven Dependencies screen of the TUI.
 */
@CommandDefinition(name = "outdated",
                   description = "Lists the dependencies with newer versions, or a version other than the one Camel brings",
                   generateHelp = true,
                   footer = {
                           "STATUS: override = the project declares another version than the one the Camel dependencies bring,",
                           "conflict = loaded twice or in another version than declared, mixed = a Camel JAR of another Camel version.",
                           "NEWER is the latest release of the same major.minor line, LATEST the latest release.",
                           "",
                           "Exits 1 when a dependency is outdated (see --fail-on), so a CI job fails.",
                           "",
                           "Examples:",
                           "  camel dependency outdated",
                           "  camel dependency outdated --transitive",
                           "  camel dependency outdated --integration=my-app",
                           "  camel dependency outdated --offline --fail-on=status",
                           "  camel dependency outdated --json" })
public class DependencyOutdated extends DependencyList {

    static final List<String> FAIL_ON = List.of("any", "patch", "status", "none");

    @Option(name = "integration",
            description = "Name or pid of a running integration: checks the JARs it loaded, instead of the dependencies of the project files")
    String integration;

    @Option(name = "transitive", hasValue = false,
            description = "Also the transitive dependencies, with the dependency of the project that brings each in (VIA)")
    boolean transitive;

    @Option(name = "all", hasValue = false,
            description = "Lists all dependencies, not only those with something to report")
    boolean all;

    @Option(name = "offline", hasValue = false,
            description = "Does not look up newer versions in the Maven repositories: only the versions Camel brings")
    boolean offline;

    @Option(name = "json", hasValue = false, description = "Output in JSON Format")
    boolean json;

    @Option(name = "fail-on", defaultValue = "any",
            description = "Exits 1 when: any = a status to act on or any newer release, patch = a status to act on or a newer release of the same line, status = a status to act on (override, conflict, mixed), none = never")
    String failOn = "any";

    // the direct dependencies of the project, as DependencyList finds them
    private final List<MavenGav> directDeps = new ArrayList<>();

    @Override
    public Integer doCall() throws Exception {
        failOn = failOn != null ? failOn.toLowerCase(Locale.ROOT) : "any";
        if (!FAIL_ON.contains(failOn)) {
            throw CliException.usage("Unknown --fail-on value: " + failOn + " (use " + String.join(", ", FAIL_ON) + ")");
        }
        if (integration != null && !integration.isBlank()) {
            return checkIntegration();
        }
        return super.doCall();
    }

    @Override
    protected Integer export() throws Exception {
        directDeps.clear();
        Integer answer = super.export();
        if (answer != 0) {
            return answer;
        }
        return checkProject();
    }

    @Override
    protected void outputGav(MavenGav gav, int index, int total) {
        // collected, not printed: checked when all are known
        directDeps.add(gav);
    }

    /** The dependencies of the project files, optionally with the transitive ones. */
    private Integer checkProject() throws Exception {
        List<DependencyRow> rows = new ArrayList<>();
        Set<String> camelGavs = new LinkedHashSet<>();
        Map<String, String> direct = new LinkedHashMap<>();
        for (MavenGav gav : directDeps) {
            if (gav.getVersion() == null || skipArtifact(gav.getGroupId(), gav.getArtifactId(), gav.getVersion())) {
                continue;
            }
            String ga = gav.getGroupId() + ":" + gav.getArtifactId();
            if (direct.putIfAbsent(ga, gav.getVersion()) == null) {
                rows.add(new DependencyRow(gav.getGroupId(), gav.getArtifactId(), gav.getVersion(), false, null));
                if (Versions.isCamel(gav.getGroupId())) {
                    camelGavs.add(ga + ":" + gav.getVersion());
                }
            }
        }
        String camelVersion = camelVersionOf(rows);
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();
            if (transitive) {
                rows.addAll(transitives(downloader, rows));
            }
            Map<String, String> brings = DependencyCheck.camelBrings(downloader, camelGavs);
            DependencyCheck.classify(rows, camelVersion, brings, null);
            rows.sort(Comparator.comparing(DependencyRow::transitive)
                    .thenComparing(DependencyRow::ga, String.CASE_INSENSITIVE_ORDER));
            return report("project", camelVersion, rows, downloader);
        }
    }

    /**
     * The transitive dependencies, each with the first dependency of the project that brings it in. The versions are
     * those of the dependencies resolved together, as Maven picks one version of each.
     */
    private List<DependencyRow> transitives(MavenDownloaderImpl downloader, List<DependencyRow> declared) {
        List<String> gavs = declared.stream().map(DependencyRow::gav).toList();
        boolean snapshot = gavs.stream().anyMatch(g -> g.contains("SNAPSHOT"));
        Map<String, String> versions = new LinkedHashMap<>();
        try {
            for (MavenArtifact ma : downloader.resolveArtifacts(gavs, Set.of(), true, snapshot)) {
                MavenGav g = ma.getGav();
                versions.putIfAbsent(g.getGroupId() + ":" + g.getArtifactId(), g.getVersion());
            }
        } catch (Exception e) {
            outPrinter().printWarn("The transitive dependencies could not be resolved: " + e.getMessage());
            return List.of();
        }
        Set<String> declaredGas = new LinkedHashSet<>();
        declared.forEach(d -> declaredGas.add(d.ga()));
        Map<String, String> via = new LinkedHashMap<>();
        for (DependencyRow d : declared) {
            try {
                for (MavenArtifact ma : downloader.resolveArtifacts(List.of(d.gav()), Set.of(), true, snapshot)) {
                    MavenGav g = ma.getGav();
                    via.putIfAbsent(g.getGroupId() + ":" + g.getArtifactId(), d.artifactId());
                }
            } catch (Exception e) {
                // this one tells no VIA
            }
        }
        List<DependencyRow> result = new ArrayList<>();
        for (Map.Entry<String, String> e : versions.entrySet()) {
            String ga = e.getKey();
            int colon = ga.indexOf(':');
            String g = ga.substring(0, colon);
            String a = ga.substring(colon + 1);
            if (declaredGas.contains(ga) || skipArtifact(g, a, e.getValue())) {
                continue;
            }
            result.add(new DependencyRow(g, a, e.getValue(), true, via.get(ga)));
        }
        return result;
    }

    /** The JARs a running integration loaded, with the dependencies it declares. */
    private Integer checkIntegration() throws Exception {
        ProcessFinder.CamelProcess process = ProcessFinder.requireOne(integration);
        ActionClient.ActionResult result = ActionClient.execute(process, "jvm", o -> {
        }, 10000);
        List<String> jvmClasspath = result.isOk() ? jsonList(result.reply(), "classpath") : List.of();
        List<String> appClasspath = result.isOk() ? jsonList(result.reply(), "applicationClasspath") : List.of();
        if (jvmClasspath.isEmpty() && appClasspath.isEmpty()) {
            throw CliException.failure("The classpath of " + process.name() + " (" + process.pid()
                                       + ") could not be read: "
                                       + (result.error() != null ? result.error() : "the integration did not answer"));
        }
        List<String> classpath = new ArrayList<>(jvmClasspath);
        classpath.addAll(appClasspath);
        Map<String, Set<String>> loaded = DependencyCheck.loadedVersions(classpath);
        // camel run: the JARs of the integration are those it downloaded, beside the JARs of the CLI itself
        Set<String> listed = appClasspath.isEmpty() ? null : DependencyCheck.loadedVersions(appClasspath).keySet();
        Map<String, String> declared = declaredVersions(process.status());
        String camelVersion = contextVersion(process.status());

        List<DependencyRow> rows = new ArrayList<>();
        Set<String> camelGavs = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> e : loaded.entrySet()) {
            String ga = e.getKey();
            int colon = ga.indexOf(':');
            String g = ga.substring(0, colon);
            String a = ga.substring(colon + 1);
            String version = versionOf(ga, declared, e.getValue(), camelVersion);
            if (skipArtifact(g, a, version) || (listed != null && !listed.contains(ga) && !Versions.isCamel(g))) {
                continue;
            }
            // a JAR the integration does not declare came in with another one
            rows.add(new DependencyRow(g, a, version, !declared.containsKey(ga), null));
        }
        rows.sort((x, y) -> x.ga().compareToIgnoreCase(y.ga()));
        if (camelVersion == null) {
            camelVersion = camelVersionOf(rows);
        }
        // the libraries Camel brings are those of the Camel JARs of the version in use, not of a mixed-in one
        for (DependencyRow r : rows) {
            if (Versions.isCamel(r.groupId()) && (camelVersion == null || !"org.apache.camel".equals(r.groupId())
                    || camelVersion.equals(r.version()))) {
                camelGavs.add(r.gav());
            }
        }
        String name = process.name() != null && !process.name().contains(" ")
                ? process.name() + " (" + process.pid() + ")" : String.valueOf(process.pid());
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();
            Map<String, String> brings = Map.of();
            try {
                brings = DependencyCheck.camelBrings(downloader, camelGavs);
            } catch (Exception e) {
                outPrinter().printWarn("The dependencies of the Camel JARs could not be resolved: " + e.getMessage());
            }
            DependencyCheck.classify(rows, camelVersion, brings, loaded);
            return report("integration " + name, camelVersion, rows, downloader);
        }
    }

    /** The declared version, else the Camel version for a Camel JAR loaded in it, else the highest loaded. */
    static String versionOf(String ga, Map<String, String> declared, Set<String> loaded, String camelVersion) {
        if (declared.containsKey(ga)) {
            return declared.get(ga);
        }
        if (camelVersion != null && Versions.isCamel(ga.substring(0, ga.indexOf(':'))) && loaded.contains(camelVersion)) {
            return camelVersion;
        }
        return loaded.stream().max(Versions::compare).orElse(null);
    }

    private static List<String> jsonList(JsonObject reply, String key) {
        List<String> result = new ArrayList<>();
        if (reply != null && reply.get(key) instanceof JsonArray arr) {
            arr.forEach(item -> result.add(String.valueOf(item)));
        }
        return result;
    }

    /** The {@code groupId:artifactId} to version of camel.jbang.dependencies in the status of an integration. */
    static Map<String, String> declaredVersions(JsonObject status) {
        Map<String, String> result = new LinkedHashMap<>();
        JsonObject properties = status != null ? status.getMap("properties") : null;
        JsonArray list = properties != null ? (JsonArray) properties.get("properties") : null;
        if (list == null) {
            return result;
        }
        for (Object o : list) {
            if (o instanceof JsonObject p && "camel.jbang.dependencies".equals(p.getString("key"))
                    && p.getString("value") != null) {
                for (String dep : p.getString("value").split(",")) {
                    String gav = dep.trim();
                    if (gav.startsWith("mvn:")) {
                        gav = gav.substring(4);
                    }
                    String[] parts = gav.split(":");
                    if (parts.length >= 3) {
                        result.put(parts[0] + ":" + parts[1], parts[2]);
                    }
                }
            }
        }
        return result;
    }

    private static String contextVersion(JsonObject status) {
        JsonObject context = status != null ? status.getMap("context") : null;
        return context != null ? context.getString("version") : null;
    }

    /** The Camel version: the one of camel-core (or another org.apache.camel JAR) among the dependencies. */
    static String camelVersionOf(List<DependencyRow> rows) {
        String any = null;
        for (DependencyRow r : rows) {
            if ("org.apache.camel".equals(r.groupId()) || "org.apache.camel.springboot".equals(r.groupId())) {
                if (r.artifactId().startsWith("camel-core") || r.artifactId().equals("camel-api")) {
                    return r.version();
                }
                if (any == null) {
                    any = r.version();
                }
            }
        }
        return any;
    }

    private Integer report(String source, String camelVersion, List<DependencyRow> rows, MavenDownloaderImpl downloader) {
        String lookupError = null;
        if (!offline) {
            List<String> gas = rows.stream().filter(r -> !r.camel() && r.version() != null)
                    .map(DependencyRow::ga).distinct().toList();
            AtomicReference<String> error = new AtomicReference<>();
            DependencyCheck.applyAvailable(rows, DependencyCheck.availableVersions(downloader, gas, error));
            lookupError = error.get();
        }
        CamelCatalog catalog = new DefaultCamelCatalog();
        CamelRelease release = runtime == RuntimeType.quarkus
                ? null
                : CamelRelease.of(camelVersion, catalog.camelReleases(), LocalDate.now());
        List<DependencyRow> listed = all ? rows : rows.stream().filter(r -> r.outdated(true)).toList();
        boolean failed = failed(rows);
        if (json) {
            JsonOutput.print(outPrinter(), jsonReport(source, release, rows, listed, failed, lookupError));
            return failed ? CliExitCode.FAILURE : CliExitCode.OK;
        }
        if (lookupError != null) {
            outPrinter().printWarn("Some newer versions could not be looked up: " + lookupError);
        }
        if (release != null) {
            outPrinter().println(release.summary());
            outPrinter().println();
        }
        long outdated = rows.stream().filter(r -> r.outdated(true)).count();
        if (listed.isEmpty()) {
            outPrinter().println("All " + rows.size() + " dependencies of the " + source + " are up to date"
                                 + (offline ? " with the versions Camel brings (newer versions not looked up)" : ""));
        } else {
            outPrinter().println(table(listed));
            outPrinter().println();
            outPrinter().println(outdated + " of " + rows.size() + " dependencies of the " + source
                                 + " to look at" + counts(rows) + (offline ? " (newer versions not looked up)" : ""));
        }
        return failed ? CliExitCode.FAILURE : CliExitCode.OK;
    }

    /** Whether the command exits with 1, by --fail-on. */
    boolean failed(List<DependencyRow> rows) {
        return switch (failOn) {
            case "none" -> false;
            case "status" -> rows.stream().anyMatch(r -> r.status().attention());
            case "patch" -> rows.stream().anyMatch(r -> r.outdated(false));
            default -> rows.stream().anyMatch(r -> r.outdated(true));
        };
    }

    JsonObject jsonReport(
            String source, CamelRelease release, List<DependencyRow> rows, List<DependencyRow> listed, boolean failed,
            String lookupError) {
        JsonObject doc = new JsonObject();
        doc.put("source", source);
        if (release != null) {
            doc.put("camel", release.toJson());
        }
        doc.put("dependencies", rows.size());
        doc.put("outdated", rows.stream().filter(r -> r.outdated(true)).count());
        doc.put("failOn", failOn);
        doc.put("failed", failed);
        if (offline) {
            doc.put("offline", true);
        }
        if (lookupError != null) {
            doc.put("lookupError", lookupError);
        }
        JsonArray arr = new JsonArray();
        listed.forEach(r -> arr.add(r.toJson()));
        doc.put("rows", arr);
        return doc;
    }

    private static String counts(List<DependencyRow> rows) {
        List<String> parts = new ArrayList<>();
        for (DependencyRow.Status s : List.of(DependencyRow.Status.CONFLICT, DependencyRow.Status.MIXED,
                DependencyRow.Status.OVERRIDE)) {
            long n = rows.stream().filter(r -> r.status() == s).count();
            if (n > 0) {
                parts.add(n + " " + s.label());
            }
        }
        long newer = rows.stream().filter(r -> r.newer() != null).count();
        if (newer > 0) {
            parts.add(newer + " with a newer patch release");
        }
        long latest = rows.stream().filter(r -> r.newer() == null && r.latest() != null).count();
        if (latest > 0) {
            parts.add(latest + " with a newer release");
        }
        return parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")";
    }

    static String table(List<DependencyRow> rows) {
        boolean anyVia = rows.stream().anyMatch(r -> r.via() != null);
        List<ColumnData<DependencyRow>> columns = new ArrayList<>();
        columns.add(new Column().header("ARTIFACT").dataAlign(HorizontalAlign.LEFT)
                .maxWidth(60, OverflowBehaviour.ELLIPSIS_LEFT).with(DependencyRow::ga));
        columns.add(new Column().header("VERSION").dataAlign(HorizontalAlign.LEFT).with(r -> orEmpty(r.version())));
        columns.add(new Column().header("CAMEL").dataAlign(HorizontalAlign.LEFT)
                .with(r -> r.camel() ? "" : orEmpty(r.camelVersion())));
        columns.add(new Column().header("STATUS").dataAlign(HorizontalAlign.LEFT).with(DependencyOutdated::statusText));
        columns.add(new Column().header("NEWER").dataAlign(HorizontalAlign.LEFT).with(r -> orEmpty(r.newer())));
        columns.add(new Column().header("LATEST").dataAlign(HorizontalAlign.LEFT).with(DependencyOutdated::latestText));
        if (anyVia) {
            columns.add(new Column().header("VIA").dataAlign(HorizontalAlign.LEFT)
                    .maxWidth(40, OverflowBehaviour.ELLIPSIS_LEFT).with(r -> orEmpty(r.via())));
        }
        return AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, columns);
    }

    /** The latest release, when not the same as NEWER, marked when it is another major version. */
    static String latestText(DependencyRow r) {
        if (r.latest() == null || r.latest().equals(r.newer())) {
            return "";
        }
        return r.latest() + (r.latestIsMajor() ? " (major)" : "");
    }

    /** The status, with the versions loaded of a conflict. */
    static String statusText(DependencyRow r) {
        if (r.status() == DependencyRow.Status.CONFLICT) {
            return "conflict (" + String.join(", ", r.loadedVersions()) + ")";
        }
        return r.status().label();
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }
}
