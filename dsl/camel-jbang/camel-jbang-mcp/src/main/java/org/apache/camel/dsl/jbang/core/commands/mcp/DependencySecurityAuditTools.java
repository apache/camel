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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * MCP Tool for CVE analysis of dependencies in a Camel project.
 * <p>
 * Parses a pom.xml to extract all dependencies (not just Camel ones), queries the Sonatype OSS Index API for known
 * vulnerabilities, and returns findings with severity, affected artifact, and whether the dependency is a direct Camel
 * dependency or a transitive one.
 * <p>
 * Distinct from {@code camel_dependency_check} (which checks dependency resolution and conflicts) and
 * {@code camel_security_advisories} (which lists Camel-specific CVEs from the catalog).
 */
@ApplicationScoped
public class DependencySecurityAuditTools {

    static final String OSS_INDEX_API = "https://ossindex.sonatype.org/api/v3/component-report";
    static final int MAX_COORDINATES_PER_REQUEST = 128;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    @Inject
    DependencyData dependencyData;

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = true),
          description = "Scan a Camel project's dependencies for known CVEs using the Sonatype OSS Index. "
                        + "Parses the pom.xml to extract all dependencies (not just Camel ones), "
                        + "queries the OSS Index for known vulnerabilities, and returns findings "
                        + "with CVE IDs, severity scores, affected artifacts, and whether the "
                        + "dependency is a direct Camel dependency or a transitive one. "
                        + "Requires network access to https://ossindex.sonatype.org. "
                        + "Distinct from camel_dependency_check (dependency resolution) and "
                        + "camel_security_advisories (Camel-specific CVEs).")
    public AuditResult camel_dependency_security_audit(
            @ToolArg(description = "The pom.xml content to analyze") String pomContent,
            @ToolArg(description = "Whether to sanitize the pom.xml to mask secrets before processing (default: true)") Boolean sanitizePom) {

        if (pomContent == null || pomContent.isBlank()) {
            throw new ToolCallException("pom.xml content is required", null);
        }

        try {
            PomSanitizer.ProcessedPom processed = PomSanitizer.process(pomContent, sanitizePom);
            List<Dependency> dependencies = parseDependencies(processed.content());

            if (dependencies.isEmpty()) {
                return new AuditResult(
                        "https://ossindex.sonatype.org",
                        List.of(), 0, new SeverityCounts(0, 0, 0, 0, 0),
                        dependencies.size(), "No dependencies found in pom.xml");
            }

            List<VulnerabilityFinding> findings = queryOssIndex(dependencies);

            int critical = (int) findings.stream().filter(f -> f.cvssScore() >= 9.0).count();
            int high = (int) findings.stream().filter(f -> f.cvssScore() >= 7.0 && f.cvssScore() < 9.0).count();
            int medium = (int) findings.stream().filter(f -> f.cvssScore() >= 4.0 && f.cvssScore() < 7.0).count();
            int low = (int) findings.stream().filter(f -> f.cvssScore() > 0 && f.cvssScore() < 4.0).count();
            int none = (int) findings.stream().filter(f -> f.cvssScore() <= 0).count();

            findings.sort((a, b) -> Double.compare(b.cvssScore(), a.cvssScore()));

            return new AuditResult(
                    "https://ossindex.sonatype.org",
                    findings, findings.size(),
                    new SeverityCounts(critical, high, medium, low, none),
                    dependencies.size(), null);
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to audit dependencies (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    List<Dependency> parseDependencies(String pomContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(pomContent.getBytes(StandardCharsets.UTF_8)));

        Map<String, String> properties = extractProperties(doc);

        List<Dependency> deps = new ArrayList<>();
        NodeList depNodes = doc.getElementsByTagName("dependency");

        for (int i = 0; i < depNodes.getLength(); i++) {
            Node node = depNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element elem = (Element) node;

            if (isInsideDependencyManagement(elem)) {
                continue;
            }

            String groupId = resolveProperty(getElementText(elem, "groupId"), properties);
            String artifactId = resolveProperty(getElementText(elem, "artifactId"), properties);
            String version = resolveProperty(getElementText(elem, "version"), properties);
            String scope = getElementText(elem, "scope");

            if (groupId == null || artifactId == null) {
                continue;
            }
            if ("test".equalsIgnoreCase(scope)) {
                continue;
            }

            boolean isCamelDep = artifactId.startsWith("camel-")
                    || "org.apache.camel".equals(groupId)
                    || groupId != null && groupId.startsWith("org.apache.camel");
            boolean isCoreTransitive = dependencyData.isCoreTransitive(artifactId);

            deps.add(new Dependency(groupId, artifactId, version, isCamelDep, isCoreTransitive));
        }
        return deps;
    }

    List<VulnerabilityFinding> queryOssIndex(List<Dependency> dependencies) throws Exception {
        List<Dependency> withVersion = dependencies.stream()
                .filter(d -> d.version() != null && !d.version().isBlank() && !d.version().startsWith("$"))
                .toList();

        if (withVersion.isEmpty()) {
            return List.of();
        }

        List<VulnerabilityFinding> allFindings = new ArrayList<>();

        for (int batch = 0; batch < withVersion.size(); batch += MAX_COORDINATES_PER_REQUEST) {
            List<Dependency> chunk = withVersion.subList(
                    batch, Math.min(batch + MAX_COORDINATES_PER_REQUEST, withVersion.size()));

            List<VulnerabilityFinding> batchFindings = queryOssIndexBatch(chunk);
            allFindings.addAll(batchFindings);
        }

        return allFindings;
    }

    private List<VulnerabilityFinding> queryOssIndexBatch(List<Dependency> deps) throws Exception {
        JsonArray coordinates = new JsonArray();
        Map<String, Dependency> coordToDep = new LinkedHashMap<>();
        for (Dependency dep : deps) {
            String purl = "pkg:maven/" + dep.groupId() + "/" + dep.artifactId() + "@" + dep.version();
            coordinates.add(purl);
            coordToDep.put(purl, dep);
        }

        JsonObject requestBody = new JsonObject();
        requestBody.put("coordinates", coordinates);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OSS_INDEX_API))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Jsoner.serialize(requestBody)))
                .timeout(HTTP_TIMEOUT)
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new ToolCallException(
                    "OSS Index API returned HTTP " + response.statusCode() + ": " + truncate(response.body(), 200),
                    null);
        }

        return parseOssIndexResponse(response.body(), coordToDep);
    }

    @SuppressWarnings("unchecked")
    List<VulnerabilityFinding> parseOssIndexResponse(
            String responseBody, Map<String, Dependency> coordToDep)
            throws Exception {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        Object parsed = Jsoner.deserialize(responseBody);
        if (!(parsed instanceof JsonArray components)) {
            return findings;
        }

        for (Object item : components) {
            if (!(item instanceof JsonObject component)) {
                continue;
            }
            String coordinates = (String) component.get("coordinates");
            Object vulnsObj = component.get("vulnerabilities");
            if (!(vulnsObj instanceof JsonArray vulns) || vulns.isEmpty()) {
                continue;
            }

            Dependency dep = coordToDep.get(coordinates);
            if (dep == null) {
                continue;
            }

            for (Object vulnObj : vulns) {
                if (!(vulnObj instanceof JsonObject vuln)) {
                    continue;
                }

                String id = (String) vuln.get("id");
                String displayName = (String) vuln.get("displayName");
                String title = (String) vuln.get("title");
                String description = (String) vuln.get("description");
                double cvssScore = vuln.get("cvssScore") instanceof Number n ? n.doubleValue() : 0.0;
                String cvssVector = (String) vuln.get("cvssVector");
                String cve = (String) vuln.get("cve");
                String cwe = (String) vuln.get("cwe");
                String reference = (String) vuln.get("reference");

                String severity = scoreSeverity(cvssScore);

                findings.add(new VulnerabilityFinding(
                        cve != null ? cve : displayName,
                        title,
                        description != null ? truncate(description, 300) : null,
                        severity,
                        cvssScore,
                        cvssVector,
                        cwe,
                        reference,
                        dep.groupId(),
                        dep.artifactId(),
                        dep.version(),
                        dep.isCamelDependency(),
                        dep.isCoreTransitive()));
            }
        }

        return findings;
    }

    private static Map<String, String> extractProperties(Document doc) {
        Map<String, String> properties = new LinkedHashMap<>();
        NodeList propsNodes = doc.getElementsByTagName("properties");
        if (propsNodes.getLength() > 0) {
            NodeList children = propsNodes.item(0).getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    String value = child.getTextContent();
                    if (value != null) {
                        properties.put(child.getNodeName(), value.trim());
                    }
                }
            }
        }
        return properties;
    }

    private static String resolveProperty(String value, Map<String, String> properties) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        String result = value;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private static boolean isInsideDependencyManagement(Element elem) {
        Node parent = elem.getParentNode();
        while (parent != null) {
            if (parent instanceof Element e && "dependencyManagement".equals(e.getTagName())) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    private static String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }

    static String scoreSeverity(double cvssScore) {
        if (cvssScore >= 9.0) {
            return "critical";
        }
        if (cvssScore >= 7.0) {
            return "high";
        }
        if (cvssScore >= 4.0) {
            return "medium";
        }
        if (cvssScore > 0) {
            return "low";
        }
        return "none";
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    // Result records

    public record AuditResult(
            String source, List<VulnerabilityFinding> vulnerabilities,
            int totalVulnerabilities, SeverityCounts severityCounts,
            int dependenciesScanned, String note) {
    }

    public record VulnerabilityFinding(
            String cve, String title, String description,
            String severity, double cvssScore, String cvssVector,
            String cwe, String reference,
            String groupId, String artifactId, String version,
            boolean isCamelDependency, boolean isCoreTransitive) {
    }

    public record SeverityCounts(int critical, int high, int medium, int low, int none) {
    }

    public record Dependency(
            String groupId, String artifactId, String version,
            boolean isCamelDependency, boolean isCoreTransitive) {
    }
}
