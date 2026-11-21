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
package org.apache.camel.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.cve.CveAudit;
import org.apache.camel.catalog.cve.CveFinding;
import org.apache.camel.catalog.cve.CveScan;
import org.apache.camel.catalog.cve.Dependency;
import org.apache.camel.catalog.cve.HttpProxy;
import org.apache.camel.catalog.cve.OnlineAdvisories;
import org.apache.camel.catalog.cve.OsvClient;
import org.apache.camel.catalog.cve.OsvDatabase;
import org.apache.camel.catalog.cve.VulnerabilitySource;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenProxy;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;

/**
 * Checks the dependencies of the project for known vulnerabilities (CVEs) with OSV.dev and the Apache Camel security
 * advisories, and reports them, or fails the build (CAMEL-27345). The same checks as {@code camel dependency audit} of
 * the Camel CLI, for a CI/CD pipeline.
 */
@Mojo(name = "audit", threadSafe = true, defaultPhase = LifecyclePhase.VERIFY,
      requiresDependencyResolution = ResolutionScope.TEST)
public class AuditMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

    private final RepositorySystem repositorySystem;

    /**
     * Skips the audit.
     */
    @Parameter(property = "camel.audit.skip", defaultValue = "false")
    private boolean skip;

    /**
     * The scopes of the dependencies to check, separated by commas.
     */
    @Parameter(property = "camel.audit.scopes", defaultValue = "compile,runtime")
    private String scopes;

    /**
     * The lowest severity to list: critical, high, medium or low.
     */
    @Parameter(property = "camel.audit.severity", defaultValue = "low")
    private String severity;

    /**
     * Fails the build when a vulnerability at or above this severity is found (also when it is below the severity
     * listed): critical, high, medium, low, or none to only report them.
     */
    @Parameter(property = "camel.audit.failOn", defaultValue = "none")
    private String failOn;

    /**
     * Fails the build when the dependencies could not be checked with OSV.dev or the OSV database (not reachable, no
     * database). Otherwise only the Apache Camel security advisories are checked then, with a warning.
     */
    @Parameter(property = "camel.audit.failOnError", defaultValue = "false")
    private boolean failOnError;

    /**
     * Checks the Apache Camel security advisories of the catalog only, without OSV.dev and without checking the
     * advisories online.
     */
    @Parameter(property = "camel.audit.offline", defaultValue = "false")
    private boolean offline;

    /**
     * Checks with the offline OSV database instead of the OSV.dev API: the export of all the Maven vulnerabilities of
     * OSV.dev, downloaded once a day and matched locally, so no coordinates of the project leave the machine.
     */
    @Parameter(property = "camel.audit.osvDatabase", defaultValue = "false")
    private boolean osvDatabase;

    /**
     * Where the offline OSV database is downloaded from, such as a mirror on the repository manager of the company, or
     * a ZIP file on disk (default: https://osv-vulnerabilities.storage.googleapis.com/Maven/all.zip). Implies
     * osvDatabase.
     */
    @Parameter(property = "camel.audit.osvDatabaseUrl")
    private String osvDatabaseUrl;

    /**
     * Where a downloaded OSV database is kept, shared with the Camel CLI.
     */
    @Parameter(property = "camel.audit.osvDatabaseCache", defaultValue = "${user.home}/.camel/osv")
    private File osvDatabaseCache;

    /**
     * Where the Apache Camel security advisories are checked online, once a day, and merged with those of the catalog
     * of the plugin: the GitHub contents API of camel-website (default:
     * https://api.github.com/repos/apache/camel-website/contents/content/security), a mirror of it, or the URL or file
     * of a JSON array in the format of the catalog. Not with offline.
     */
    @Parameter(property = "camel.audit.advisoriesUrl")
    private String advisoriesUrl;

    /**
     * Where the advisories checked online are kept, shared with the Camel CLI.
     */
    @Parameter(property = "camel.audit.advisoriesCache", defaultValue = "${user.home}/.camel/advisories")
    private File advisoriesCache;

    /**
     * The groupIds that are not checked, and not sent to OSV.dev, such as those of the internal libraries of a company,
     * separated by commas; {@code com.acme} also leaves out {@code com.acme.billing}. The modules of the build are
     * never checked.
     */
    @Parameter(property = "camel.audit.excludeGroupIds")
    private String excludeGroupIds;

    /**
     * A file of the vulnerabilities that do not apply, one per line with the reason, such as
     * {@code CVE-2022-1471 the YAML comes from our own configuration only}. They are reported as suppressed, and do not
     * fail the build.
     */
    @Parameter(property = "camel.audit.suppressions", defaultValue = "${project.basedir}/camel-audit-suppressions.txt")
    private File suppressions;

    /**
     * The JSON report, in the shape of {@code camel dependency audit --json}; empty for none.
     */
    @Parameter(property = "camel.audit.jsonReport", defaultValue = "${project.build.directory}/camel-audit.json")
    private File jsonReport;

    /**
     * The SARIF report, for GitHub code scanning and other tools; empty for none.
     */
    @Parameter(property = "camel.audit.sarifReport", defaultValue = "${project.build.directory}/camel-audit.sarif")
    private File sarifReport;

    @Inject
    public AuditMojo(RepositorySystem repositorySystem) {
        this.repositorySystem = repositorySystem;
    }

    /** The dependencies to check, and the chain that brings each transitive one in, by {@code groupId:artifactId}. */
    record Collected(List<Dependency> deps, Map<String, String> via) {
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping the audit as per configuration");
            return;
        }
        String listLevel = level("severity", severity, false);
        String failLevel = level("failOn", failOn, true);

        Set<String> reactor = new LinkedHashSet<>();
        for (MavenProject p : session.getProjects()) {
            reactor.add(p.getGroupId() + ":" + p.getArtifactId());
        }
        Collected collected = collect(project.getArtifacts(), split(scopes), reactor, split(excludeGroupIds));
        String name = project.getGroupId() + ":" + project.getArtifactId();
        if (collected.deps().isEmpty()) {
            getLog().info("No dependencies of " + name + " to audit");
            return;
        }
        Set<String> declared = new LinkedHashSet<>();
        project.getDependencies().forEach(d -> declared.add(d.getGroupId() + ":" + d.getArtifactId()));

        CamelCatalog catalog = new DefaultCamelCatalog();
        OsvDatabase database = null;
        VulnerabilitySource source = null;
        if (!offline) {
            if (osvDatabase || (osvDatabaseUrl != null && !osvDatabaseUrl.isBlank())) {
                String url = osvDatabaseUrl != null && !osvDatabaseUrl.isBlank() ? osvDatabaseUrl : OsvDatabase.DEFAULT_URL;
                database = OsvDatabase.open(url, osvDatabaseCache.toPath(), OsvDatabase.DEFAULT_MAX_AGE, proxy(url));
                source = database;
            } else {
                source = new OsvClient(proxy(OsvClient.API_URL));
            }
        }
        List<SecurityAdvisoryModel> camelAdvisories = CveAudit.advisories(catalog);
        OnlineAdvisories.Result online = null;
        if (!offline) {
            String url = advisoriesUrl != null && !advisoriesUrl.isBlank() ? advisoriesUrl : OnlineAdvisories.DEFAULT_URL;
            online = OnlineAdvisories.load(url, advisoriesCache.toPath(), OnlineAdvisories.DEFAULT_MAX_AGE, proxy(url),
                    camelAdvisories);
            camelAdvisories = online.advisories();
        }
        CveAudit.Result result = CveAudit.audit(collected.deps(), Map.of(), declared, collected.via(), null, catalog,
                camelAdvisories, source);

        Map<String, String> suppressed = readSuppressions();
        Map<CveFinding, String> hidden = new LinkedHashMap<>();
        List<CveFinding> remaining = new ArrayList<>();
        for (CveFinding f : result.findings()) {
            String reason = AuditReport.suppression(f, suppressed);
            if (reason != null) {
                hidden.put(f, reason);
            } else {
                remaining.add(f);
            }
        }
        List<CveFinding> listed = remaining.stream().filter(f -> AuditReport.atOrAbove(f, listLevel)).toList();
        boolean failed = remaining.stream().anyMatch(f -> AuditReport.atOrAbove(f, failLevel));

        if (result.osvError() != null) {
            getLog().warn(result.osvError() + ": only the Apache Camel security advisories were checked");
        }
        if (database != null && database.warning() != null) {
            getLog().warn(database.warning());
        }
        if (online != null && online.warning() != null) {
            getLog().warn(online.warning());
        }
        String note = database != null && database.updated() != null
                ? " (offline OSV database, updated " + database.updated().toString().substring(0, 10) + ")" : "";
        String more = hidden.isEmpty() ? "" : ", " + hidden.size() + " suppressed";
        if (listed.isEmpty()) {
            getLog().info(
                    "No known vulnerabilities" + ("low".equals(listLevel) ? "" : " of " + listLevel + " severity or worse")
                          + " in " + collected.deps().size() + " dependencies of " + name + note + more);
        } else {
            AuditReport.table(listed).forEach(line -> getLog().warn(line));
            getLog().warn(listed.size() + " vulnerabilities in " + CveScan.artifactRows(listed).size() + " of "
                          + collected.deps().size() + " dependencies of " + name + note + more);
        }
        writeReports(name, collected.deps().size(), remaining, hidden, failed, result.osvError(), catalog);

        if (failOnError && result.osvError() != null) {
            throw new MojoFailureException(
                    "The dependencies of " + name + " could not be checked: " + result.osvError());
        }
        if (failed) {
            long count = remaining.stream().filter(f -> AuditReport.atOrAbove(f, failLevel)).count();
            throw new MojoFailureException(
                    count + " vulnerabilities of " + failLevel + " severity or worse in the"
                                           + " dependencies of " + name + " (failOn=" + failLevel + ")");
        }
    }

    /**
     * The dependencies to check: of the given scopes, without the modules of the build and the excluded groupIds, with
     * the chain from the dependency of the project that brings each transitive one in.
     */
    static Collected collect(
            Collection<Artifact> artifacts, Set<String> scopes, Set<String> reactor, Set<String> excludedGroups) {
        List<Dependency> deps = new ArrayList<>();
        Map<String, String> via = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Artifact a : artifacts) {
            String ga = a.getGroupId() + ":" + a.getArtifactId();
            String scope = a.getScope() != null ? a.getScope() : "compile";
            if (!scopes.contains(scope) || reactor.contains(ga) || excluded(a.getGroupId(), excludedGroups)
                    || !seen.add(ga)) {
                continue;
            }
            deps.add(new Dependency(a.getGroupId(), a.getArtifactId(), a.getBaseVersion()));
            List<String> trail = a.getDependencyTrail();
            if (trail != null && trail.size() > 2) {
                // the project, the dependencies that bring it in, and the artifact itself
                List<String> chain = new ArrayList<>();
                for (String id : trail.subList(1, trail.size() - 1)) {
                    String[] parts = id.split(":");
                    chain.add(parts[0] + ":" + parts[1]);
                }
                via.put(ga, CveScan.via(chain));
            }
        }
        return new Collected(deps, via);
    }

    private static boolean excluded(String groupId, Set<String> excludedGroups) {
        for (String g : excludedGroups) {
            if (groupId.equals(g) || groupId.startsWith(g + ".")) {
                return true;
            }
        }
        return false;
    }

    // the proxy of the Maven settings for the URL, else HTTPS_PROXY; null for the proxy settings of the JVM
    private HttpProxy proxy(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null;
        }
        if (repositorySystemSession != null) {
            MavenProxy maven = new MavenDownloaderImpl(repositorySystem, repositorySystemSession).proxyFor(url);
            if (maven != null) {
                return new HttpProxy(
                        maven.host(), maven.port(), maven.username(), maven.password(),
                        HttpProxy.splitHosts(maven.nonProxyHosts()));
            }
        }
        return HttpProxy.fromEnvironment(System.getenv());
    }

    private Map<String, String> readSuppressions() throws MojoExecutionException {
        try {
            return AuditReport.readSuppressions(suppressions != null ? suppressions.toPath() : null);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read the suppressions " + suppressions, e);
        }
    }

    private void writeReports(
            String name, int scanned, List<CveFinding> findings, Map<CveFinding, String> hidden, boolean failed,
            String osvError, CamelCatalog catalog)
            throws MojoExecutionException {
        try {
            if (jsonReport != null && !jsonReport.getPath().isBlank()) {
                write(jsonReport.toPath(),
                        Jsoner.prettyPrint(AuditReport.json(name, scanned, findings, hidden, failOn, failed, osvError)
                                .toJson()));
            }
            if (sarifReport != null && !sarifReport.getPath().isBlank()) {
                write(sarifReport.toPath(), Jsoner.prettyPrint(
                        AuditReport.sarif(findings, pomPath(), catalog.getCatalogVersion()).toJson()));
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot write the audit report", e);
        }
    }

    // the POM of the project, relative to the directory the build runs in (the root of the repository, in CI)
    private String pomPath() {
        File pom = project.getFile();
        if (pom == null) {
            return "pom.xml";
        }
        try {
            Path root = Path.of(session.getExecutionRootDirectory()).toAbsolutePath().normalize();
            return root.relativize(pom.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (RuntimeException e) {
            return "pom.xml";
        }
    }

    private static void write(Path file, String text) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, text);
    }

    private static String level(String option, String value, boolean noneAllowed) throws MojoExecutionException {
        String level = value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
        if (AuditReport.LEVELS.contains(level) || noneAllowed && "none".equals(level)) {
            return level;
        }
        throw new MojoExecutionException(
                option + " must be critical, high, medium, low" + (noneAllowed ? " or none" : "")
                                         + ", was: " + value);
    }

    private static Set<String> split(String text) {
        Set<String> answer = new LinkedHashSet<>();
        if (text != null) {
            for (String s : text.split(",")) {
                if (!s.isBlank()) {
                    answer.add(s.trim());
                }
            }
        }
        return answer;
    }
}
