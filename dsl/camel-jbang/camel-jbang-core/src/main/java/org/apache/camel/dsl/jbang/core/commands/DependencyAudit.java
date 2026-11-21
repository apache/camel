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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.cve.CveArtifact;
import org.apache.camel.catalog.cve.CveAudit;
import org.apache.camel.catalog.cve.CveFinding;
import org.apache.camel.catalog.cve.CveScan;
import org.apache.camel.catalog.cve.CvssScore;
import org.apache.camel.catalog.cve.Dependency;
import org.apache.camel.catalog.cve.HttpProxy;
import org.apache.camel.catalog.cve.OsvClient;
import org.apache.camel.catalog.cve.OsvDatabase;
import org.apache.camel.catalog.cve.VulnerabilitySource;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteComponents;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.OsvProxy;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder;
import org.apache.camel.dsl.jbang.run.common.CamelHome;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Checks the dependencies of a project, or the classpath of a running integration, for known vulnerabilities: OSV.dev
 * for every JAR, and the Apache Camel security advisories of the catalog for the Camel JARs (CAMEL-27340). The same
 * audit as the CVE Audit screen of the TUI, for a script, a CI job or an AI agent.
 */
@CommandDefinition(name = "audit",
                   description = "Checks the dependencies for known vulnerabilities (CVEs) with OSV.dev and the Apache Camel security advisories",
                   generateHelp = true,
                   footer = {
                           "Exits 1 when a vulnerability at or above --fail-on is found (default: any), so a CI job fails.",
                           "",
                           "Examples:",
                           "  camel dependency audit",
                           "  camel dependency audit --by-artifact",
                           "  camel dependency audit --integration=my-app",
                           "  camel dependency audit --severity=high --fail-on=critical",
                           "  camel dependency audit --osv-database",
                           "  camel dependency audit --osv-database-url=https://nexus.corp.com/repository/osv/Maven/all.zip",
                           "  camel dependency audit --json" })
public class DependencyAudit extends DependencyList {

    static final List<String> LEVELS = List.of("critical", "high", "medium", "low");

    @Option(name = "integration",
            description = "Name or pid of a running integration: audits its classpath, instead of the dependencies of the project files")
    String integration;

    @Option(name = "by-artifact", hasValue = false,
            description = "One row per vulnerable JAR, with the version that fixes all its CVEs")
    boolean byArtifact;

    @Option(name = "json", hasValue = false, description = "Output in JSON Format")
    boolean json;

    @Option(name = "severity", defaultValue = "low",
            description = "The lowest severity to list: critical, high, medium or low")
    String severity = "low";

    @Option(name = "fail-on", defaultValue = "low",
            description = "Exits 1 when a vulnerability at or above this severity is found: critical, high, medium, low or none")
    String failOn = "low";

    @Option(name = "offline", hasValue = false,
            description = "Checks the Apache Camel security advisories of the catalog only, without OSV.dev")
    boolean offline;

    @Option(name = "osv-database", hasValue = false,
            description = "Checks with the offline OSV database instead of the OSV.dev API: the export of all the Maven vulnerabilities of OSV.dev,"
                          + " downloaded once a day and matched locally, so no coordinates of the project leave the machine")
    boolean osvDatabase;

    @Option(name = "osv-database-url",
            description = "Where the offline OSV database is downloaded from, such as a mirror on the repository manager of the company,"
                          + " or a ZIP file on disk (default: " + OsvDatabase.DEFAULT_URL + "). Implies --osv-database")
    String osvDatabaseUrl;

    // the direct dependencies of the project, as DependencyList finds them
    private final List<MavenGav> directDeps = new ArrayList<>();
    // the offline OSV database, when it is used
    private OsvDatabase database;

    @Override
    public Integer doCall() throws Exception {
        checkLevel("--severity", severity, false);
        checkLevel("--fail-on", failOn, true);
        severity = severity.toLowerCase(Locale.ROOT);
        failOn = failOn.toLowerCase(Locale.ROOT);
        if (integration != null && !integration.isBlank()) {
            return auditIntegration();
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
        return auditProject();
    }

    @Override
    protected void outputGav(MavenGav gav, int index, int total) {
        // collected, not printed: audited when all are known
        directDeps.add(gav);
    }

    /** The dependencies of the project, with the transitive ones, and the routes of its files. */
    private Integer auditProject() throws Exception {
        List<String> gavs = new ArrayList<>();
        Set<String> declared = new LinkedHashSet<>();
        for (MavenGav gav : directDeps) {
            if (gav.getVersion() != null) {
                gavs.add(gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion());
                declared.add(gav.getGroupId() + ":" + gav.getArtifactId());
            }
        }
        List<Dependency> deps = new ArrayList<>();
        Map<String, Path> jars = new HashMap<>();
        VulnerabilitySource source = null;
        if (!gavs.isEmpty()) {
            boolean snapshot = gavs.stream().anyMatch(g -> g.contains("SNAPSHOT"));
            try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
                downloader.setMavenSettingsLocation(mavenSettings);
                downloader.setMavenSettingsSecurityLocation(mavenSettingsSecurity);
                downloader.build();
                source = vulnerabilitySource(downloader);
                for (MavenArtifact ma : downloader.resolveArtifacts(gavs, Set.of(), true, snapshot)) {
                    MavenGav g = ma.getGav();
                    if (g.getVersion() == null || skipArtifact(g.getGroupId(), g.getArtifactId(), g.getVersion())) {
                        continue;
                    }
                    deps.add(new Dependency(g.getGroupId(), g.getArtifactId(), g.getVersion()));
                    if (ma.getFile() != null) {
                        jars.putIfAbsent(g.getGroupId() + ":" + g.getArtifactId(), ma.getFile().toPath());
                    }
                }
            }
        }
        if (source == null) {
            source = vulnerabilitySource(null);
        }
        CamelCatalog catalog = new DefaultCamelCatalog();
        return report("project", deps, CveAudit.audit(deps, jars, declared, Map.of(), routeComponents(catalog),
                catalog, source));
    }

    /** The classpath of a running integration, and the components of its endpoints. */
    private Integer auditIntegration() throws Exception {
        ProcessFinder.CamelProcess process = ProcessFinder.requireOne(integration);
        ActionClient.ActionResult result = ActionClient.execute(process, "jvm", o -> {
        }, 10000);
        List<String> classpath = result.isOk() ? classpathOf(result.reply()) : List.of();
        if (classpath.isEmpty()) {
            throw CliException.failure("The classpath of " + process.name() + " (" + process.pid()
                                       + ") could not be read: "
                                       + (result.error() != null ? result.error() : "the integration did not answer"));
        }
        List<Dependency> deps = new ArrayList<>();
        Map<String, Path> jars = new HashMap<>();
        for (String item : classpath) {
            Dependency dep = Dependency.fromJarPath(String.valueOf(item));
            if (dep == null || skipArtifact(dep.groupId(), dep.artifactId(), dep.version())) {
                continue;
            }
            deps.add(dep);
            jars.putIfAbsent(dep.ga(), Path.of(String.valueOf(item)));
        }
        CamelCatalog catalog = new DefaultCamelCatalog();
        String name = process.name() != null && !process.name().contains(" ")
                ? process.name() + " (" + process.pid() + ")" : String.valueOf(process.pid());
        return report("integration " + name, deps, CveAudit.audit(deps, jars, declaredDependencies(process.status()),
                Map.of(), endpointComponents(process.status()), catalog, vulnerabilitySource(null)));
    }

    /**
     * Where the vulnerabilities come from: the offline OSV database (--osv-database), the OSV.dev API, or none
     * (--offline). The proxy comes from the Maven settings of the downloader (null to read them), else HTTPS_PROXY.
     */
    VulnerabilitySource vulnerabilitySource(MavenDownloaderImpl downloader) {
        if (offline) {
            return null;
        }
        Map<String, String> env = System.getenv();
        if (osvDatabase || osvDatabaseUrl != null) {
            String url = osvDatabaseUrl != null && !osvDatabaseUrl.isBlank() ? osvDatabaseUrl : OsvDatabase.DEFAULT_URL;
            HttpProxy proxy = !url.startsWith("http://") && !url.startsWith("https://") ? null
                    : downloader != null ? OsvProxy.resolve(downloader, env, url)
                    : OsvProxy.resolve(mavenSettings, mavenSettingsSecurity, env, url);
            database = OsvDatabase.open(url, CamelHome.getCamelDir().resolve("osv"), OsvDatabase.DEFAULT_MAX_AGE, proxy);
            return database;
        }
        return new OsvClient(
                downloader != null
                        ? OsvProxy.resolve(downloader, env) : OsvProxy.resolve(mavenSettings, mavenSettingsSecurity, env));
    }

    /** The components the routes of the project files use; null when there are no route files. */
    private Set<String> routeComponents(CamelCatalog catalog) {
        Set<String> result = new LinkedHashSet<>();
        boolean any = false;
        for (String file : files) {
            Path path = Path.of(file);
            String name = path.getFileName().toString();
            String format = name.endsWith(".yaml") || name.endsWith(".yml") ? "yaml"
                    : name.endsWith(".xml") ? "xml" : name.endsWith(".java") ? "java" : null;
            if (format == null || !Files.isRegularFile(path)) {
                continue;
            }
            try {
                result.addAll(RouteComponents.of(Files.readString(path), format, catalog));
                any = true;
            } catch (Exception e) {
                // a file that cannot be read tells nothing about the routes
            }
        }
        return any ? result : null;
    }

    /**
     * The JARs of the reply of the jvm action: the JVM classpath, and the application class loader that camel run adds
     * the downloaded dependencies to (applicationClasspath), without duplicates.
     */
    static List<String> classpathOf(JsonObject reply) {
        Set<String> paths = new LinkedHashSet<>();
        for (String key : List.of("classpath", "applicationClasspath")) {
            Object cp = reply != null ? reply.get(key) : null;
            if (cp instanceof JsonArray arr) {
                arr.forEach(item -> paths.add(String.valueOf(item)));
            }
        }
        return new ArrayList<>(paths);
    }

    /**
     * The {@code groupId:artifactId} of the dependencies a running integration declares (camel.jbang.dependencies,
     * which also has those of //DEPS and --dep), from the properties of its status.
     */
    static Set<String> declaredDependencies(JsonObject status) {
        Set<String> result = new LinkedHashSet<>();
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
                        result.add(parts[0] + ":" + parts[1]);
                    }
                }
            }
        }
        return result;
    }

    /** The components of the endpoints of a running integration, from its status. */
    static Set<String> endpointComponents(JsonObject status) {
        Set<String> result = new LinkedHashSet<>();
        JsonObject endpoints = status != null ? status.getMap("endpoints") : null;
        JsonArray list = endpoints != null ? (JsonArray) endpoints.get("endpoints") : null;
        if (list == null) {
            return result;
        }
        for (Object o : list) {
            String uri = o instanceof JsonObject ep ? ep.getString("uri") : null;
            int colon = uri != null ? uri.indexOf(':') : -1;
            if (colon > 0) {
                result.add(uri.substring(0, colon).toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private Integer report(String source, List<Dependency> deps, CveAudit.Result result) {
        int lowest = LEVELS.indexOf(severity);
        List<CveFinding> findings = result.findings().stream()
                .filter(f -> CveScan.severityIndex(f.severity()) <= lowest || lowest == LEVELS.size() - 1)
                .toList();
        List<CveArtifact> artifacts = CveScan.artifactRows(findings);
        // the ones below --severity are not listed, but --fail-on applies to them too
        int notListed = result.findings().size() - findings.size();
        if (json) {
            JsonOutput.print(outPrinter(), jsonReport(source, deps.size(), result, findings, artifacts));
        } else {
            if (result.osvError() != null) {
                outPrinter().printWarn(result.osvError() + ": only the Apache Camel security advisories were checked");
            }
            if (database != null && database.warning() != null) {
                outPrinter().printWarn(database.warning());
            }
            String below = notListed > 0 ? " (" + notListed + " more below --severity=" + severity + ")" : "";
            if (findings.isEmpty()) {
                outPrinter().println((notListed > 0
                        ? "No vulnerabilities of " + severity + " severity or worse in "
                        : "No known vulnerabilities in ")
                                     + deps.size() + " dependencies of the " + source
                                     + (offline ? " (Apache Camel security advisories only)" : "") + databaseNote() + below);
            } else {
                outPrinter().println(byArtifact ? artifactTable(artifacts) : findingTable(findings));
                outPrinter().println();
                outPrinter().println(findings.size() + " vulnerabilities in " + artifacts.size() + " of " + deps.size()
                                     + " dependencies of the " + source + counts(findings) + databaseNote() + below);
            }
        }
        return failed(result.findings()) ? CliExitCode.FAILURE : CliExitCode.OK;
    }

    /**
     * The --json document. vulnerabilities counts the ones listed (--severity), totalVulnerabilities all of them, and
     * failed tells whether the command exits with 1 (--fail-on, applied to all of them), so a script or an AI agent
     * gets the same answer from the document as from the exit code.
     */
    JsonObject jsonReport(
            String source, int scanned, CveAudit.Result result, List<CveFinding> findings, List<CveArtifact> artifacts) {
        JsonObject doc = new JsonObject();
        doc.put("source", source);
        doc.put("scanned", scanned);
        doc.put("vulnerabilities", findings.size());
        doc.put("totalVulnerabilities", result.findings().size());
        doc.put("vulnerableArtifacts", artifacts.size());
        doc.put("severity", severity);
        doc.put("failOn", failOn);
        doc.put("failed", failed(result.findings()));
        if (result.osvError() != null) {
            doc.put("osvError", result.osvError());
        }
        if (offline) {
            doc.put("offline", true);
        }
        if (database != null) {
            JsonObject db = new JsonObject();
            db.put("location", database.location());
            if (database.updated() != null) {
                db.put("updated", database.updated().toString());
            }
            if (database.warning() != null) {
                db.put("warning", database.warning());
            }
            doc.put("osvDatabase", db);
        }
        JsonArray rows = new JsonArray();
        if (byArtifact) {
            artifacts.forEach(a -> rows.add(a.toJson()));
            doc.put("artifacts", rows);
        } else {
            findings.forEach(f -> rows.add(f.toJson()));
            doc.put("findings", rows);
        }
        return doc;
    }

    // which offline OSV database was used, such as " (offline OSV database, updated 2026-11-20)"
    private String databaseNote() {
        if (database == null || database.updated() == null) {
            return "";
        }
        return " (offline OSV database, updated " + database.updated().toString().substring(0, 10) + ")";
    }

    /** Whether a vulnerability at or above --fail-on was found (of all of them, not only those listed). */
    boolean failed(List<CveFinding> findings) {
        int level = LEVELS.indexOf(failOn);
        if (level < 0) {
            return false;
        }
        return findings.stream().anyMatch(f -> CveScan.severityIndex(f.severity()) <= level
                || level == LEVELS.size() - 1);
    }

    private static String counts(List<CveFinding> findings) {
        List<String> parts = new ArrayList<>();
        for (String sev : CveScan.SEVERITY_ORDER) {
            long n = findings.stream().filter(f -> sev.equals(f.severity())).count();
            if (n > 0) {
                parts.add(n + " " + sev);
            }
        }
        return parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")";
    }

    static String findingTable(List<CveFinding> findings) {
        return AsciiTable.getTable(AsciiTable.NO_BORDERS, findings, Arrays.asList(
                new Column().header("SEVERITY").dataAlign(HorizontalAlign.LEFT).with(CveFinding::severity),
                new Column().header("SCORE").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(f -> CvssScore.format(f.score())),
                new Column().header("CVE").dataAlign(HorizontalAlign.LEFT).with(CveFinding::canonicalId),
                new Column().header("ARTIFACT").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(50, OverflowBehaviour.ELLIPSIS_LEFT)
                        .with(f -> f.firstGav() + (f.affectedGavs().size() > 1
                                ? " (+" + (f.affectedGavs().size() - 1) + ")" : "")),
                new Column().header("FIX").dataAlign(HorizontalAlign.LEFT)
                        .with(f -> orNone(f.fixFor().get(f.firstGav()))),
                new Column().header("VIA").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(40, OverflowBehaviour.ELLIPSIS_LEFT).with(CveFinding::firstVia),
                new Column().header("USED").dataAlign(HorizontalAlign.LEFT).with(f -> used(f.usedByRoutes())),
                new Column().header("SUMMARY").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(60, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(f -> f.summary() != null ? f.summary() : "")));
    }

    static String artifactTable(List<CveArtifact> artifacts) {
        return AsciiTable.getTable(AsciiTable.NO_BORDERS, artifacts, Arrays.asList(
                new Column().header("SEVERITY").dataAlign(HorizontalAlign.LEFT).with(CveArtifact::severity),
                new Column().header("SCORE").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(a -> CvssScore.format(a.score())),
                new Column().header("ARTIFACT").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(60, OverflowBehaviour.ELLIPSIS_LEFT).with(CveArtifact::gav),
                new Column().header("CVES").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(a -> String.valueOf(a.cves().size())),
                new Column().header("FIXES ALL").dataAlign(HorizontalAlign.LEFT)
                        .with(a -> a.fix() == null ? "none" : a.camelRelease() ? "Camel " + a.fix() : a.fix()),
                new Column().header("VIA").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(40, OverflowBehaviour.ELLIPSIS_LEFT).with(a -> a.via() != null ? a.via() : ""),
                new Column().header("USED").dataAlign(HorizontalAlign.LEFT)
                        .with(a -> used(a.usage() != null ? a.usage().used() : null))));
    }

    private static String orNone(String value) {
        return value != null ? value : "none";
    }

    private static String used(Boolean used) {
        return used == null ? "" : used ? "yes" : "no";
    }

    /** Checks a severity option: exits 2 when it is not one of the levels (or none, when allowed). */
    static void checkLevel(String option, String value, boolean noneAllowed) {
        if (value == null || !(LEVELS.contains(value.toLowerCase(Locale.ROOT))
                || noneAllowed && "none".equalsIgnoreCase(value))) {
            throw CliException.usage(option + " must be critical, high, medium, low"
                                     + (noneAllowed ? " or none" : "") + ", was: " + value);
        }
    }
}
