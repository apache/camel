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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.commons.text.similarity.LevenshteinDistance;

/**
 * Shared holder for migration reference data used by {@link MigrationTools}, {@link MigrationWildflyKarafTools}, and
 * {@link MigrationResources}.
 * <p>
 * Contains migration guide metadata, WildFly/Karaf detection markers, and POM parsing utilities. Component-level
 * migration details (renames, discontinued components, API changes) are intentionally not included here — the LLM
 * should consult the migration guides directly for that information.
 */
@ApplicationScoped
public class MigrationData {

    // Migration guides

    private static final List<MigrationGuide> MIGRATION_GUIDES = Arrays.asList(
            new MigrationGuide(
                    "migration-and-upgrade",
                    "Migration and Upgrade",
                    "https://camel.apache.org/manual/migration-and-upgrade.html",
                    "Overview of Camel migration and upgrade options across all versions."),
            new MigrationGuide(
                    "camel-3-migration",
                    "Camel 3 Migration Guide",
                    "https://camel.apache.org/manual/camel-3-migration-guide.html",
                    "Guide for migrating from Camel 2.x to Camel 3.0."),
            new MigrationGuide(
                    "camel-4-migration",
                    "Camel 4 Migration Guide",
                    "https://camel.apache.org/manual/camel-4-migration-guide.html",
                    "Guide for migrating from Camel 3.x to Camel 4.0."),
            new MigrationGuide(
                    "camel-3x-upgrade",
                    "Camel 3.x Upgrade Guide",
                    "https://camel.apache.org/manual/camel-3x-upgrade-guide.html",
                    "Detailed upgrade notes for Camel 3.x minor version upgrades."),
            new MigrationGuide(
                    "camel-4x-upgrade",
                    "Camel 4.x Upgrade Guide",
                    "https://camel.apache.org/manual/camel-4x-upgrade-guide.html",
                    "Detailed upgrade notes for Camel 4.x minor version upgrades."));

    // WildFly/Karaf detection markers

    private static final List<String> WILDFLY_MARKERS = Arrays.asList(
            "org.wildfly.camel", "org.wildfly",
            "org.wildfly.plugins",
            "javax.ejb", "jakarta.ejb",
            "camel-cdi", "maven-war-plugin");

    private static final List<String> KARAF_MARKERS = Arrays.asList(
            "org.apache.karaf", "camel-blueprint",
            "org.ops4j.pax", "camel-core-osgi",
            "maven-bundle-plugin");

    // Accessors

    public List<MigrationGuide> getMigrationGuides() {
        return MIGRATION_GUIDES;
    }

    public MigrationGuide getMigrationGuide(String name) {
        return MIGRATION_GUIDES.stream()
                .filter(g -> g.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public List<String> getWildflyMarkers() {
        return WILDFLY_MARKERS;
    }

    public List<String> getKarafMarkers() {
        return KARAF_MARKERS;
    }

    /**
     * Get relevant migration guides based on the detected Camel major version.
     */
    public List<MigrationGuide> getGuidesForVersion(int majorVersion) {
        List<MigrationGuide> guides = new ArrayList<>();
        guides.add(getMigrationGuide("migration-and-upgrade"));
        if (majorVersion <= 2) {
            guides.add(getMigrationGuide("camel-3-migration"));
            guides.add(getMigrationGuide("camel-4-migration"));
        } else if (majorVersion == 3) {
            guides.add(getMigrationGuide("camel-3x-upgrade"));
            guides.add(getMigrationGuide("camel-4-migration"));
        } else {
            guides.add(getMigrationGuide("camel-4x-upgrade"));
        }
        return guides;
    }

    // POM parsing

    /**
     * Parse pom.xml content and extract project analysis data.
     */
    public static PomAnalysis parsePomContent(String pomContent) throws Exception {
        DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(pomContent.getBytes(StandardCharsets.UTF_8)));

        String camelVersion = null;
        String springBootVersion = null;
        String quarkusVersion = null;
        String javaVersion = null;
        boolean isWildfly = false;
        boolean isKaraf = false;
        List<String> dependencies = new ArrayList<>();

        // Detect packaging type (war packaging is a strong WildFly/app-server signal)
        String packaging = getElementText(doc.getDocumentElement(), "packaging");
        if ("war".equalsIgnoreCase(packaging)) {
            isWildfly = true;
        }

        // Extract properties
        NodeList propertiesNodes = doc.getElementsByTagName("properties");
        if (propertiesNodes.getLength() > 0) {
            Element properties = (Element) propertiesNodes.item(0);
            camelVersion = getElementText(properties, "camel.version");
            if (camelVersion == null) {
                camelVersion = getElementText(properties, "camel-version");
            }
            springBootVersion = getElementText(properties, "spring-boot.version");
            if (springBootVersion == null) {
                springBootVersion = getElementText(properties, "spring-boot-version");
            }
            quarkusVersion = getElementText(properties, "quarkus.platform.version");
            if (quarkusVersion == null) {
                quarkusVersion = getElementText(properties, "quarkus-plugin.version");
            }
            javaVersion = getElementText(properties, "maven.compiler.release");
            if (javaVersion == null) {
                javaVersion = getElementText(properties, "maven.compiler.source");
            }
            if (javaVersion == null) {
                javaVersion = getElementText(properties, "maven.compiler.target");
            }
        }

        // Scan dependencyManagement and dependencies for version and runtime detection
        NodeList allDeps = doc.getElementsByTagName("dependency");
        for (int i = 0; i < allDeps.getLength(); i++) {
            Element dep = (Element) allDeps.item(i);
            String groupId = getElementText(dep, "groupId");
            String artifactId = getElementText(dep, "artifactId");
            String version = getElementText(dep, "version");

            if (groupId == null || artifactId == null) {
                continue;
            }

            // Detect Camel version from BOM
            if (camelVersion == null && version != null && !version.startsWith("$")) {
                if ("camel-bom".equals(artifactId) || "camel-spring-boot-bom".equals(artifactId)
                        || "camel-quarkus-bom".equals(artifactId)) {
                    camelVersion = version;
                }
            }

            // Detect Spring Boot
            if (springBootVersion == null && version != null && !version.startsWith("$")) {
                if ("spring-boot-dependencies".equals(artifactId)
                        || "spring-boot-starter-parent".equals(artifactId)) {
                    springBootVersion = version;
                }
            }

            // Detect Quarkus
            if (quarkusVersion == null && version != null && !version.startsWith("$")) {
                if ("quarkus-bom".equals(artifactId)) {
                    quarkusVersion = version;
                }
            }

            // Detect WildFly
            for (String marker : WILDFLY_MARKERS) {
                if (groupId.contains(marker) || artifactId.contains(marker)) {
                    isWildfly = true;
                }
            }

            // Detect Karaf
            for (String marker : KARAF_MARKERS) {
                if (groupId.contains(marker) || artifactId.contains(marker)) {
                    isKaraf = true;
                }
            }

            // Collect Camel dependencies
            if (artifactId.startsWith("camel-")) {
                dependencies.add(artifactId);
            }
        }

        return new PomAnalysis(
                camelVersion, springBootVersion, quarkusVersion, javaVersion,
                dependencies, isWildfly, isKaraf);
    }

    // XML helper

    private static DocumentBuilderFactory createSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        } catch (ParserConfigurationException e) {
            // ignore
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (ParserConfigurationException e) {
            // ignore
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
            // ignore
        }
        return factory;
    }

    private static String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    // Guide search

    private static final String GUIDES_RESOURCE_DIR = "migration-guides/";
    private static final String GUIDES_INDEX_FILE = GUIDES_RESOURCE_DIR + "index.txt";

    private volatile List<GuideSection> guideIndex;

    /**
     * Get the guide index, loading lazily on first access.
     */
    public synchronized List<GuideSection> getGuideIndex() {
        if (guideIndex == null) {
            guideIndex = loadGuideSections();
        }
        return guideIndex;
    }

    /**
     * Search migration guides for sections matching the query using fuzzy matching.
     * <p>
     * Scoring: +10 if the full query appears verbatim in the section content. Per query token: +3 exact token match, +2
     * fuzzy match (Levenshtein distance &le; max(1, minTokenLen/4)), +1 substring fallback. Results are sorted by
     * descending score.
     */
    public List<GuideSection> searchGuides(String query, int maxResults) {
        List<String> queryTokens = tokenize(query);
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        List<GuideSection> index = getGuideIndex();
        List<ScoredSection> scored = new ArrayList<>();

        for (GuideSection section : index) {
            double score = scoreSection(section, queryTokens, lowerQuery);
            if (score > 0) {
                scored.add(new ScoredSection(section, score));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredSection::score).reversed());

        return scored.stream()
                .limit(maxResults)
                .map(ScoredSection::section)
                .collect(Collectors.toList());
    }

    private double scoreSection(GuideSection section, List<String> queryTokens, String lowerQuery) {
        double score = 0;
        String lowerContent = section.content().toLowerCase(Locale.ROOT);

        // Bonus for exact substring match of the full query
        if (lowerContent.contains(lowerQuery)) {
            score += 10;
        }

        // Token-level scoring
        for (String qt : queryTokens) {
            boolean exactFound = false;
            boolean fuzzyFound = false;
            boolean substringFound = false;

            for (String dt : section.tokens()) {
                if (dt.equals(qt)) {
                    exactFound = true;
                    break;
                }
                int maxDist = Math.max(1, Math.min(qt.length(), dt.length()) / 4);
                if (levenshteinDistance(qt, dt) <= maxDist) {
                    fuzzyFound = true;
                }
            }

            if (!exactFound && !fuzzyFound && lowerContent.contains(qt)) {
                substringFound = true;
            }

            if (exactFound) {
                score += 3;
            } else if (fuzzyFound) {
                score += 2;
            } else if (substringFound) {
                score += 1;
            }
        }

        return score;
    }

    private List<GuideSection> loadGuideSections() {
        List<GuideSection> sections = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        // Read index.txt to discover guide filenames
        List<String> guideFiles = new ArrayList<>();
        InputStream indexStream = cl.getResourceAsStream(GUIDES_INDEX_FILE);
        if (indexStream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(indexStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && trimmed.endsWith(".adoc")) {
                        guideFiles.add(trimmed);
                    }
                }
            } catch (IOException e) {
                // fall through with empty list
            }
        }

        for (String filename : guideFiles) {
            InputStream is = cl.getResourceAsStream(GUIDES_RESOURCE_DIR + filename);
            if (is == null) {
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String content = reader.lines().collect(Collectors.joining("\n"));
                String guideName = guideNameFromFilename(filename);
                String version = versionFromFilename(filename);
                String url = urlFromFilename(filename);

                splitIntoSections(content, guideName, version, url, sections);
            } catch (IOException e) {
                // skip unreadable files
            }
        }

        return sections;
    }

    private void splitIntoSections(
            String content, String guideName, String version, String url,
            List<GuideSection> sections) {
        String[] lines = content.split("\n");
        StringBuilder currentContent = new StringBuilder();
        String currentTitle = guideName;

        for (String line : lines) {
            if (line.startsWith("== ") && !line.startsWith("===")) {
                // Flush previous section
                if (currentContent.length() > 0) {
                    String sectionText = currentContent.toString().trim();
                    if (!sectionText.isEmpty()) {
                        sections.add(new GuideSection(
                                guideName, version, currentTitle, sectionText, url,
                                tokenize(sectionText)));
                    }
                }
                currentTitle = line.substring(3).trim();
                currentContent = new StringBuilder();
            } else {
                currentContent.append(line).append("\n");
            }
        }

        // Flush last section
        if (currentContent.length() > 0) {
            String sectionText = currentContent.toString().trim();
            if (!sectionText.isEmpty()) {
                sections.add(new GuideSection(
                        guideName, version, currentTitle, sectionText, url,
                        tokenize(sectionText)));
            }
        }
    }

    /**
     * Tokenize text into lowercase words, preserving hyphens within words (e.g., direct-vm).
     */
    static List<String> tokenize(String text) {
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9\\-]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank() && part.length() > 1) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static final LevenshteinDistance LEVENSHTEIN = LevenshteinDistance.getDefaultInstance();

    static int levenshteinDistance(String a, String b) {
        return LEVENSHTEIN.apply(a, b);
    }

    private static String guideNameFromFilename(String filename) {
        String name = filename.replace(".adoc", "");
        if ("camel-3-migration-guide".equals(name)) {
            return "Camel 3 Migration Guide (2.x to 3.0)";
        }
        if ("camel-4-migration-guide".equals(name)) {
            return "Camel 4 Migration Guide (3.x to 4.0)";
        }
        String version = versionFromFilename(filename);
        return "Camel " + version + " Upgrade Guide";
    }

    private static String versionFromFilename(String filename) {
        String name = filename.replace(".adoc", "");
        if ("camel-3-migration-guide".equals(name)) {
            return "3.0";
        }
        if ("camel-4-migration-guide".equals(name)) {
            return "4.0";
        }
        int lastDash = name.lastIndexOf('-');
        if (lastDash > 0) {
            return name.substring(lastDash + 1).replace('_', '.');
        }
        return name;
    }

    private static String urlFromFilename(String filename) {
        String name = filename.replace(".adoc", "");
        return "https://camel.apache.org/manual/" + name + ".html";
    }

    // Records

    public record MigrationGuide(String name, String title, String url, String summary) {
    }

    public record GuideSection(
            String guide,
            String version,
            String sectionTitle,
            String content,
            String url,
            List<String> tokens) {
    }

    private record ScoredSection(GuideSection section, double score) {
    }

    public record PomAnalysis(
            String camelVersion,
            String springBootVersion,
            String quarkusVersion,
            String javaVersion,
            List<String> dependencies,
            boolean isWildfly,
            boolean isKaraf) {

        /**
         * Determine the runtime type from the POM analysis.
         */
        public String runtimeType() {
            if (isWildfly) {
                return "wildfly";
            }
            if (isKaraf) {
                return "karaf";
            }
            if (springBootVersion != null || dependencies.stream().anyMatch(d -> d.contains("spring-boot"))) {
                return "spring-boot";
            }
            if (quarkusVersion != null || dependencies.stream().anyMatch(d -> d.contains("quarkus"))) {
                return "quarkus";
            }
            return "main";
        }

        /**
         * Get the major version number from the Camel version string.
         */
        public int majorVersion() {
            if (camelVersion == null) {
                return 0;
            }
            try {
                return Integer.parseInt(camelVersion.split("\\.")[0]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
