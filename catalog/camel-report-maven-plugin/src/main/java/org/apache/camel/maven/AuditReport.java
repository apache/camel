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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.catalog.cve.CveFinding;
import org.apache.camel.catalog.cve.CveScan;
import org.apache.camel.catalog.cve.CvssScore;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The parts of {@code camel-report:audit} that need no Maven project: the suppressions, the console table, and the JSON
 * and SARIF reports.
 */
final class AuditReport {

    static final List<String> LEVELS = List.of("critical", "high", "medium", "low");

    private AuditReport() {
    }

    /**
     * The suppressed vulnerabilities of a suppression file, by id (a CVE or GHSA id), with the reason: one per line, as
     * {@code CVE-2022-1471 not reachable: the YAML comes from our own config only}; blank lines and lines starting with
     * {@code #} are left out.
     */
    static Map<String, String> readSuppressions(Path file) throws IOException {
        Map<String, String> answer = new LinkedHashMap<>();
        if (file == null || !Files.isRegularFile(file)) {
            return answer;
        }
        for (String line : Files.readAllLines(file)) {
            String text = line.trim();
            if (text.isEmpty() || text.startsWith("#")) {
                continue;
            }
            String[] parts = text.split("\\s+", 2);
            answer.put(parts[0].toUpperCase(Locale.ROOT), parts.length > 1 ? parts[1] : "");
        }
        return answer;
    }

    /** The suppression of a finding, by its id or one of its aliases; null when it is not suppressed. */
    static String suppression(CveFinding finding, Map<String, String> suppressions) {
        String reason = suppressions.get(finding.canonicalId().toUpperCase(Locale.ROOT));
        if (reason == null) {
            for (String alias : finding.aliases()) {
                reason = suppressions.get(alias.toUpperCase(Locale.ROOT));
                if (reason != null) {
                    break;
                }
            }
        }
        return reason;
    }

    /**
     * Whether a finding is at or above the severity level; a severity OSV.dev does not give counts as the lowest one.
     */
    static boolean atOrAbove(CveFinding finding, String level) {
        int index = LEVELS.indexOf(level);
        if (index < 0) {
            return false;
        }
        return CveScan.severityIndex(finding.severity()) <= index || index == LEVELS.size() - 1;
    }

    /** The lines of the console table: severity, score, id, artifact, fix and the dependency that brings it in. */
    static List<String> table(List<CveFinding> findings) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "SEVERITY", "SCORE", "CVE", "ARTIFACT", "FIX", "VIA", "SUMMARY" });
        for (CveFinding f : findings) {
            for (String gav : f.affectedGavs()) {
                String fix = f.fixFor().get(gav);
                String via = f.gavVia().get(gav);
                String summary = f.summary() != null ? f.summary() : "";
                if (summary.length() > 60) {
                    summary = summary.substring(0, 59) + "…";
                }
                rows.add(new String[] {
                        f.severity(), CvssScore.format(f.score()), f.canonicalId(), gav, fix != null ? fix : "none",
                        via != null ? via : "", summary });
            }
        }
        int[] width = new int[rows.get(0).length];
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                width[i] = Math.max(width[i], row[i].length());
            }
        }
        List<String> lines = new ArrayList<>();
        for (String[] row : rows) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                sb.append(i == 1 ? pad(row[i], width[i], true) : pad(row[i], width[i], false));
                if (i < row.length - 1) {
                    sb.append("  ");
                }
            }
            lines.add(sb.toString().stripTrailing());
        }
        return lines;
    }

    private static String pad(String text, int width, boolean right) {
        String spaces = " ".repeat(Math.max(0, width - text.length()));
        return right ? spaces + text : text + spaces;
    }

    /** The JSON report, in the shape of {@code camel dependency audit --json}, with the suppressed findings. */
    static JsonObject json(
            String project, int scanned, List<CveFinding> findings, Map<CveFinding, String> suppressed, String failOn,
            boolean failed, String osvError) {
        JsonObject doc = new JsonObject();
        doc.put("source", project);
        doc.put("scanned", scanned);
        doc.put("vulnerabilities", findings.size());
        doc.put("suppressed", suppressed.size());
        doc.put("failOn", failOn);
        doc.put("failed", failed);
        if (osvError != null) {
            doc.put("osvError", osvError);
        }
        JsonArray rows = new JsonArray();
        findings.forEach(f -> rows.add(f.toJson()));
        doc.put("findings", rows);
        JsonArray hidden = new JsonArray();
        suppressed.forEach((f, reason) -> {
            JsonObject row = f.toJson();
            row.put("suppression", reason);
            hidden.add(row);
        });
        doc.put("suppressedFindings", hidden);
        return doc;
    }

    /**
     * The SARIF 2.1.0 report, for GitHub code scanning and other tools: one rule per vulnerability, and one result per
     * vulnerable artifact, located at the POM of the project.
     *
     * @param pom the path of the POM, relative to the root of the repository
     */
    static JsonObject sarif(List<CveFinding> findings, String pom, String version) {
        JsonArray rules = new JsonArray();
        JsonArray results = new JsonArray();
        for (CveFinding f : findings) {
            JsonObject rule = new JsonObject();
            rule.put("id", f.canonicalId());
            rule.put("name", f.canonicalId());
            rule.put("shortDescription", text(f.summary() != null ? f.summary() : f.canonicalId()));
            if (f.details() != null) {
                rule.put("fullDescription", text(f.details()));
            }
            rule.put("helpUri", helpUri(f));
            JsonObject props = new JsonObject();
            JsonArray tags = new JsonArray();
            tags.add("security");
            tags.add("vulnerability");
            props.put("tags", tags);
            if (f.score() != null) {
                // the severity of GitHub code scanning is taken from this score
                props.put("security-severity", CvssScore.format(f.score()));
            }
            rule.put("properties", props);
            rules.add(rule);

            for (String gav : f.affectedGavs()) {
                String fix = f.fixFor().get(gav);
                String via = f.gavVia().get(gav);
                JsonObject result = new JsonObject();
                result.put("ruleId", f.canonicalId());
                result.put("level", level(f.severity()));
                result.put("message", text(gav + " has " + f.canonicalId() + " (" + f.severity() + ")"
                                           + (fix != null ? ", fixed in " + fix : ", with no fix yet")
                                           + (via != null ? ", brought in by " + via : "")
                                           + (f.summary() != null ? ": " + f.summary() : "")));
                JsonObject location = new JsonObject();
                JsonObject physical = new JsonObject();
                JsonObject artifact = new JsonObject();
                artifact.put("uri", pom);
                physical.put("artifactLocation", artifact);
                location.put("physicalLocation", physical);
                JsonArray locations = new JsonArray();
                locations.add(location);
                result.put("locations", locations);
                JsonObject fingerprints = new JsonObject();
                fingerprints.put("camelAudit/v1", f.canonicalId() + "|" + gav);
                result.put("partialFingerprints", fingerprints);
                results.add(result);
            }
        }
        JsonObject driver = new JsonObject();
        driver.put("name", "camel-report:audit");
        driver.put("informationUri", "https://camel.apache.org/manual/camel-report-maven-plugin.html");
        driver.put("version", version);
        driver.put("rules", rules);
        JsonObject tool = new JsonObject();
        tool.put("driver", driver);
        JsonObject run = new JsonObject();
        run.put("tool", tool);
        run.put("results", results);
        JsonArray runs = new JsonArray();
        runs.add(run);
        JsonObject doc = new JsonObject();
        doc.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        doc.put("version", "2.1.0");
        doc.put("runs", runs);
        return doc;
    }

    private static String helpUri(CveFinding f) {
        if (f.camelAdvisory() != null && f.camelAdvisory().url() != null) {
            return f.camelAdvisory().url();
        }
        return "https://osv.dev/vulnerability/" + f.canonicalId();
    }

    private static String level(String severity) {
        return switch (severity != null ? severity : "") {
            case "CRITICAL", "HIGH" -> "error";
            case "MEDIUM" -> "warning";
            default -> "note";
        };
    }

    private static JsonObject text(String text) {
        JsonObject o = new JsonObject();
        o.put("text", text);
        return o;
    }
}
