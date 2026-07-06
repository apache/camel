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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.dsl.jbang.core.common.XmlHelper;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenGav;

/**
 * Shared dependency discovery for the TUI. Reads declared Maven dependencies from pom.xml (for exported Spring Boot /
 * Quarkus apps) or from .camel-jbang/camel-jbang-run.properties (for JBang mode). Filters out internal infrastructure
 * JARs so only the user's actual dependencies are returned.
 * <p/>
 * Used by both {@link MavenDependenciesTab} and {@link CveAuditTab} to avoid scanning the full JVM classpath which
 * would include internal bootstrap JARs like camel-kamelet-main, jansi, and log4j.
 */
final class DependencyLoader {

    private DependencyLoader() {
    }

    record DepEntry(String groupId, String artifactId, String version, boolean transitive, String parent) {

        DepEntry(String groupId, String artifactId, String version) {
            this(groupId, artifactId, version, false, null);
        }

        String display() {
            if (version != null) {
                return groupId + ":" + artifactId + ":" + version;
            }
            return groupId + ":" + artifactId;
        }

        boolean isCamel() {
            return groupId != null && groupId.startsWith("org.apache.camel");
        }
    }

    record LoadResult(List<DepEntry> entries, String source, String error) {
    }

    static LoadResult loadDependencies(IntegrationInfo info) {
        List<Path> candidates = new ArrayList<>();
        Path sourceDir = FilesBrowser.resolveSourceDirectory(info);
        if (sourceDir != null) {
            candidates.add(sourceDir);
        }
        if (info.directory != null && !info.directory.isEmpty()) {
            Path infoDir = Path.of(info.directory);
            if (!candidates.contains(infoDir)) {
                candidates.add(infoDir);
            }
        }
        if (candidates.isEmpty()) {
            return new LoadResult(Collections.emptyList(), null, "Cannot determine integration directory");
        }

        boolean jbangMode = "JBang".equals(info.platform) || "Camel".equals(info.platform);

        if (jbangMode) {
            for (Path dir : candidates) {
                Path propsFile = dir.resolve(".camel-jbang").resolve("camel-jbang-run.properties");
                if (Files.exists(propsFile)) {
                    List<DepEntry> deps = loadFromRunProperties(propsFile);
                    return new LoadResult(deps, "jbang", deps.isEmpty() ? "No dependencies in run properties" : null);
                }
            }
        }

        for (Path dir : candidates) {
            Path pomFile = dir.resolve("pom.xml");
            if (Files.exists(pomFile)) {
                List<DepEntry> deps = loadFromPomXml(pomFile);
                return new LoadResult(deps, "pom.xml", deps.isEmpty() ? "No compile dependencies in pom.xml" : null);
            }
        }

        if (!jbangMode) {
            for (Path dir : candidates) {
                Path propsFile = dir.resolve(".camel-jbang").resolve("camel-jbang-run.properties");
                if (Files.exists(propsFile)) {
                    List<DepEntry> deps = loadFromRunProperties(propsFile);
                    return new LoadResult(deps, "jbang", deps.isEmpty() ? "No dependencies in run properties" : null);
                }
            }
        }

        return new LoadResult(Collections.emptyList(), null, "No dependency information found");
    }

    static List<DepEntry> resolveTransitives(List<DepEntry> directDeps) {
        if (directDeps.isEmpty()) {
            return Collections.emptyList();
        }

        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();

            Set<String> directKeys = new HashSet<>();
            for (DepEntry d : directDeps) {
                directKeys.add(d.groupId() + ":" + d.artifactId());
            }

            boolean hasSnapshot = directDeps.stream()
                    .anyMatch(d -> d.version() != null && d.version().contains("SNAPSHOT"));

            Map<String, DepEntry> transitiveMap = new HashMap<>();
            for (DepEntry direct : directDeps) {
                if (direct.version() == null) {
                    continue;
                }
                String gav = direct.groupId() + ":" + direct.artifactId() + ":" + direct.version();
                String parentKey = direct.groupId() + ":" + direct.artifactId();
                try {
                    List<MavenArtifact> resolved = downloader.resolveArtifacts(
                            List.of(gav), Set.of(), true, hasSnapshot);
                    for (MavenArtifact ma : resolved) {
                        MavenGav g = ma.getGav();
                        if (skipArtifact(g.getGroupId(), g.getArtifactId())) {
                            continue;
                        }
                        String key = g.getGroupId() + ":" + g.getArtifactId();
                        if (!directKeys.contains(key) && !transitiveMap.containsKey(key)) {
                            transitiveMap.put(key, new DepEntry(
                                    g.getGroupId(), g.getArtifactId(), g.getVersion(), true, parentKey));
                        }
                    }
                } catch (Exception e) {
                    // skip this dependency
                }
            }

            List<DepEntry> entries = new ArrayList<>(transitiveMap.values());
            entries.sort(Comparator.comparing(DepEntry::display, String.CASE_INSENSITIVE_ORDER));
            return entries;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    static List<DepEntry> loadFromPomXml(Path pomFile) {
        try {
            DocumentBuilderFactory dbf = XmlHelper.createDocumentBuilderFactory();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom;
            try (InputStream is = Files.newInputStream(pomFile)) {
                dom = db.parse(is);
            }

            Map<String, String> properties = new HashMap<>();
            NodeList propsList = dom.getElementsByTagName("properties");
            if (propsList.getLength() > 0) {
                Element propsEl = (Element) propsList.item(0);
                for (int i = 0; i < propsEl.getChildNodes().getLength(); i++) {
                    if (propsEl.getChildNodes().item(i) instanceof Element prop) {
                        properties.put(prop.getTagName(), prop.getTextContent().trim());
                    }
                }
            }

            String camelVersion = null;
            String springBootVersion = null;
            String quarkusVersion = null;

            NodeList parentList = dom.getElementsByTagName("parent");
            if (parentList.getLength() > 0) {
                Element parentEl = (Element) parentList.item(0);
                String pg = textContent(parentEl, "groupId");
                String pa = textContent(parentEl, "artifactId");
                String pv = textContent(parentEl, "version");
                pv = resolveProperty(pv, properties);
                if ("org.springframework.boot".equals(pg)
                        && ("spring-boot-starter-parent".equals(pa) || "spring-boot-dependencies".equals(pa))) {
                    springBootVersion = pv;
                }
                if ("org.apache.camel.springboot".equals(pg) && "camel-spring-boot-bom".equals(pa)) {
                    camelVersion = pv;
                }
            }

            NodeList nl = dom.getElementsByTagName("dependency");
            List<DepEntry> deps = new ArrayList<>();

            for (int i = 0; i < nl.getLength(); i++) {
                Element node = (Element) nl.item(i);

                String p = node.getParentNode().getNodeName();
                String p2 = node.getParentNode().getParentNode().getNodeName();
                boolean accept = ("dependencyManagement".equals(p2) || "project".equals(p2))
                        && "dependencies".equals(p);
                if (!accept) {
                    continue;
                }

                String g = node.getElementsByTagName("groupId").item(0).getTextContent();
                String a = node.getElementsByTagName("artifactId").item(0).getTextContent();
                String v = null;
                NodeList vl = node.getElementsByTagName("version");
                if (vl.getLength() > 0) {
                    v = vl.item(0).getTextContent();
                }

                v = resolveProperty(v, properties);

                if ("org.apache.camel".equals(g) && "camel-bom".equals(a)) {
                    camelVersion = v;
                    continue;
                }
                if ("org.apache.camel.springboot".equals(g) && "camel-spring-boot-bom".equals(a)) {
                    camelVersion = v;
                    continue;
                }
                if ("org.springframework.boot".equals(g) && "spring-boot-dependencies".equals(a)) {
                    springBootVersion = v;
                    continue;
                }
                if (("${quarkus.platform.group-id}".equals(g) || "io.quarkus.platform".equals(g))
                        && ("${quarkus.platform.artifact-id}".equals(a) || "quarkus-bom".equals(a))) {
                    if ("${quarkus.platform.version}".equals(v)) {
                        NodeList qvl = dom.getElementsByTagName("quarkus.platform.version");
                        if (qvl.getLength() > 0) {
                            quarkusVersion = qvl.item(0).getTextContent();
                        }
                    } else {
                        quarkusVersion = v;
                    }
                    continue;
                }

                String scope = null;
                NodeList sl = node.getElementsByTagName("scope");
                if (sl.getLength() > 0) {
                    scope = sl.item(0).getTextContent();
                }
                if ("test".equals(scope) || "import".equals(scope)) {
                    continue;
                }

                if (v == null && "org.apache.camel".equals(g)) {
                    v = camelVersion;
                }
                if (v == null && "org.apache.camel.springboot".equals(g)) {
                    v = camelVersion;
                }
                if (v == null && "org.springframework.boot".equals(g)) {
                    v = springBootVersion;
                }
                if (v == null && ("io.quarkus".equals(g) || "org.apache.camel.quarkus".equals(g))) {
                    v = quarkusVersion;
                }

                g = resolveProperty(g, properties);
                a = resolveProperty(a, properties);
                v = resolveProperty(v, properties);

                if (skipArtifact(g, a)) {
                    continue;
                }

                deps.add(new DepEntry(g, a, v));
            }

            deps.sort(Comparator.comparing(DepEntry::display, String.CASE_INSENSITIVE_ORDER));
            return deps;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    static List<DepEntry> loadFromRunProperties(Path propsFile) {
        try {
            List<String> lines = Files.readAllLines(propsFile);
            List<DepEntry> deps = new ArrayList<>();

            for (String line : lines) {
                line = line.trim();
                if (!line.startsWith("dependency=")) {
                    continue;
                }
                String value = line.substring("dependency=".length()).trim();
                value = value.replace("\\:", ":");

                if (value.startsWith("mvn:") || value.startsWith("mvn\\:")) {
                    String gav = value.startsWith("mvn:") ? value.substring(4) : value.substring(5);
                    MavenGav parsed = MavenGav.parseGav(gav);
                    if (parsed.getGroupId() != null && parsed.getArtifactId() != null) {
                        if (!skipArtifact(parsed.getGroupId(), parsed.getArtifactId())) {
                            deps.add(new DepEntry(parsed.getGroupId(), parsed.getArtifactId(), parsed.getVersion()));
                        }
                    }
                }
            }

            deps.sort(Comparator.comparing(DepEntry::display, String.CASE_INSENSITIVE_ORDER));
            return deps;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    static boolean skipArtifact(String groupId, String artifactId) {
        if ("org.fusesource.jansi".equals(groupId)) {
            return true;
        }
        if ("org.apache.logging.log4j".equals(groupId)) {
            return true;
        }
        if ("org.apache.camel".equals(groupId) && "camel-kamelet-main".equals(artifactId)) {
            return true;
        }
        if ("org.apache.camel".equals(groupId) && "camel-cli-connector".equals(artifactId)) {
            return true;
        }
        return false;
    }

    static String shortArtifact(String ga) {
        if (ga == null) {
            return "";
        }
        int colon = ga.indexOf(':');
        return colon >= 0 ? ga.substring(colon + 1) : ga;
    }

    static String resolveProperty(String value, Map<String, String> properties) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String key = value.substring(2, value.length() - 1);
            String resolved = properties.get(key);
            if (resolved != null) {
                return resolved;
            }
        }
        return value;
    }

    static String textContent(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : null;
    }
}
