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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /**
     * Words dropped from search queries: they appear in nearly every migration guide entry (camel, migration, upgrade,
     * version, guide) or carry no meaning on their own (to, from, the, ...), so they only add noise to the ranking.
     */
    static final Set<String> STOP_WORDS = Set.of(
            "camel", "apache", "migration", "migrate", "migrating", "upgrade", "upgrading", "upgraded", "version",
            "versions", "guide", "guides", "release", "change", "changes", "changed",
            "a", "an", "and", "are", "as", "at", "be", "by", "do", "does", "for", "from", "how", "in", "is", "it", "my",
            "of", "on", "or", "the", "this", "to", "what", "when", "which", "with");

    /** BM25 term frequency saturation. */
    private static final double BM25_K1 = 1.2;
    /** BM25 length normalisation: 0 ignores the entry length, 1 fully normalises by it. */
    private static final double BM25_B = 0.5;
    /** Extra weight, in units of the term idf, when a query term appears in the entry heading. */
    private static final double TITLE_BOOST = 1.5;
    /** Weight of a part of an unknown hyphenated query term, e.g. http4 for camel-http4. */
    private static final double PART_WEIGHT = 0.7;
    /** Weight of a vocabulary term matched by Levenshtein distance instead of exactly (typo tolerance). */
    private static final double FUZZY_WEIGHT = 0.7;
    /** Weight of a vocabulary term that only contains the query term as a substring. */
    private static final double SUBSTRING_WEIGHT = 0.3;
    /** Multiplier when the whole query appears verbatim in the entry. */
    private static final double PHRASE_BOOST = 1.25;
    /**
     * Multiplier for the entries of the 2.x to 3.0 and 3.x to 4.0 migration guides in a version-scoped search, so they
     * rank above comparable upgrade guide entries without pushing out strong ones.
     */
    static final double MAJOR_GUIDE_BOOST = 1.5;
    /** Entries scoring below this are considered unrelated to the query and are not returned. */
    static final double MIN_SCORE = 2.5;

    private volatile GuideIndex guideIndex;

    /**
     * Get the guide index entries (one per {@code ===} topic of each release section), loading lazily on first access.
     */
    public List<GuideSection> getGuideIndex() {
        return index().sections();
    }

    private synchronized GuideIndex index() {
        if (guideIndex == null) {
            guideIndex = buildIndex(loadGuideSections());
        }
        return guideIndex;
    }

    /**
     * Search all migration guides, see {@link #search(String, String, String, int)}.
     */
    public List<GuideSection> searchGuides(String query, int maxResults) {
        return search(query, null, null, maxResults).stream().map(SearchHit::section).toList();
    }

    /**
     * Search the migration guides for the entries matching the query.
     * <p>
     * Each entry is one {@code ===} topic (usually one component) of one release section of a guide. Entries are ranked
     * with BM25 over the query terms (stop words such as camel, migration or upgrade removed), with a bonus for terms
     * in the entry heading. A query term that does not occur verbatim also matches vocabulary terms within a small
     * Levenshtein distance (typos) or containing it, at a reduced weight. Entries matching less than half of the query
     * terms or scoring below {@link #MIN_SCORE} are dropped, so an unrelated query returns no results.
     * <p>
     * When {@code currentVersion} and/or {@code targetVersion} are given, only the guides for the releases after
     * {@code currentVersion} up to {@code targetVersion} are searched, and the entries of the major migration guides
     * (2.x to 3.0, 3.x to 4.0) in that range get a higher score.
     *
     * @param  query                    the search query
     * @param  currentVersion           the Camel version migrated from (e.g. 2.25 or 3.20.1), or null
     * @param  targetVersion            the Camel version migrated to (e.g. 4.22), or null
     * @param  maxResults               the maximum number of results
     * @return                          the matching entries, best first
     * @throws IllegalArgumentException if a version cannot be parsed or currentVersion is after targetVersion
     */
    public List<SearchHit> search(String query, String currentVersion, String targetVersion, int maxResults) {
        int[] from = parseVersion(currentVersion, "currentVersion");
        int[] to = parseVersion(targetVersion, "targetVersion");
        if (from != null && to != null && compareMajorMinor(from, to) > 0) {
            throw new IllegalArgumentException(
                    "currentVersion " + currentVersion + " is after targetVersion " + targetVersion);
        }
        boolean ranged = from != null || to != null;

        List<String> queryTerms = queryTerms(query);
        if (queryTerms.isEmpty() || maxResults <= 0) {
            return List.of();
        }

        GuideIndex index = index();
        List<Map<String, Double>> expansions = new ArrayList<>();
        for (String term : queryTerms) {
            expansions.add(expandTerm(term, index));
        }
        String phrase = query.toLowerCase(Locale.ROOT).trim();

        List<SearchHit> hits = new ArrayList<>();
        for (GuideSection section : index.sections()) {
            if (ranged && !inRange(section, from, to)) {
                continue;
            }
            Set<String> matched = new LinkedHashSet<>();
            double score = 0;
            int matchedTerms = 0;
            for (Map<String, Double> expansion : expansions) {
                double best = 0;
                String bestTerm = null;
                for (Map.Entry<String, Double> variant : expansion.entrySet()) {
                    double s = scoreTerm(variant.getKey(), section, index) * variant.getValue();
                    if (s > best) {
                        best = s;
                        bestTerm = variant.getKey();
                    }
                }
                if (bestTerm != null) {
                    matched.add(bestTerm);
                    matchedTerms++;
                    score += best;
                }
            }
            if (matchedTerms == 0 || matchedTerms * 2 < expansions.size()) {
                // an entry matching less than half of the query is about something else
                continue;
            }
            // favour entries covering more of the query
            score *= 0.5 + 0.5 * matchedTerms / expansions.size();
            if (queryTerms.size() > 1 && section.content().toLowerCase(Locale.ROOT).contains(phrase)) {
                score *= PHRASE_BOOST;
            }
            if (ranged && section.majorMigration()) {
                // migrating across a major version: favour the 2.x to 3.0 and 3.x to 4.0 migration guides
                score *= MAJOR_GUIDE_BOOST;
            }
            if (score >= MIN_SCORE) {
                hits.add(new SearchHit(section, score, List.copyOf(matched)));
            }
        }

        hits.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        return hits.size() > maxResults ? List.copyOf(hits.subList(0, maxResults)) : hits;
    }

    /**
     * Return the lines of the entry around its best match: the line containing the most (idf weighted) matched terms,
     * with some lines of context before it, and at most {@code maxLines} lines.
     */
    public String snippet(SearchHit hit, int maxLines) {
        String[] lines = hit.section().content().split("\n");
        if (lines.length <= maxLines) {
            return hit.section().content();
        }
        GuideIndex index = index();
        int bestLine = 0;
        double bestScore = 0;
        for (int i = 0; i < lines.length; i++) {
            Set<String> lineTerms = new HashSet<>(tokenize(lines[i]));
            double s = 0;
            for (String term : hit.matchedTerms()) {
                if (lineTerms.contains(term)) {
                    s += idf(term, index);
                }
            }
            if (s > bestScore) {
                bestScore = s;
                bestLine = i;
            }
        }
        int start = Math.max(0, bestLine - maxLines / 4);
        int end = Math.min(lines.length, start + maxLines);
        start = Math.max(0, end - maxLines);

        StringBuilder sb = new StringBuilder();
        if (start > 0) {
            sb.append("... (").append(start).append(" lines above)\n");
        }
        for (int i = start; i < end; i++) {
            sb.append(lines[i]).append("\n");
        }
        if (end < lines.length) {
            sb.append("... (truncated, ").append(lines.length - end).append(" more lines)");
        }
        return sb.toString();
    }

    private static double scoreTerm(String term, GuideSection section, GuideIndex index) {
        Integer tf = section.termFrequencies().get(term);
        if (tf == null) {
            return 0;
        }
        double idf = idf(term, index);
        double norm = BM25_K1 * (1 - BM25_B + BM25_B * section.length() / index.averageLength());
        double score = idf * tf * (BM25_K1 + 1) / (tf + norm);
        if (section.titleTerms().contains(term)) {
            score += idf * TITLE_BOOST;
        }
        return score;
    }

    private static double idf(String term, GuideIndex index) {
        int df = index.documentFrequencies().getOrDefault(term, 0);
        int n = index.sections().size();
        return Math.log(1 + (n - df + 0.5) / (df + 0.5));
    }

    /**
     * The vocabulary terms a query term matches, with their weight: the term itself and the terms containing it. When
     * the term itself does not occur in the guides, also the parts of a hyphenated term (http4 for camel-http4) or else
     * the terms within a small Levenshtein distance (typos, e.g. directvm for direct-vm).
     */
    private static Map<String, Double> expandTerm(String term, GuideIndex index) {
        Set<String> vocabulary = index.documentFrequencies().keySet();
        Map<String, Double> variants = new HashMap<>();
        if (vocabulary.contains(term)) {
            variants.put(term, 1.0);
        }
        if (variants.isEmpty() && term.indexOf('-') > 0) {
            for (String part : term.split("-+")) {
                if (part.length() > 1 && !STOP_WORDS.contains(part) && vocabulary.contains(part)) {
                    variants.putIfAbsent(part, PART_WEIGHT);
                }
            }
        }
        if (term.length() >= 4) {
            boolean known = !variants.isEmpty();
            for (String candidate : vocabulary) {
                if (candidate.equals(term)) {
                    continue;
                }
                int maxDist = Math.max(1, Math.min(term.length(), candidate.length()) / 4);
                if (!known && Math.abs(candidate.length() - term.length()) <= maxDist
                        && levenshteinDistance(term, candidate) <= maxDist) {
                    variants.putIfAbsent(candidate, FUZZY_WEIGHT);
                } else if (candidate.contains(term)) {
                    variants.putIfAbsent(candidate, SUBSTRING_WEIGHT);
                }
            }
        }
        return variants;
    }

    /**
     * The distinct search terms of a query: its words without stop words and bare numbers. A hyphenated word stays one
     * term, its parts are matched as variants of it.
     */
    static List<String> queryTerms(String query) {
        if (query == null) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String token : words(query)) {
            if (!STOP_WORDS.contains(token) && !token.chars().allMatch(Character::isDigit)) {
                terms.add(token);
            }
        }
        return new ArrayList<>(terms);
    }

    private static boolean inRange(GuideSection section, int[] from, int[] to) {
        int[] version;
        try {
            version = parseVersion(section.version(), "guide version");
        } catch (IllegalArgumentException e) {
            // skip a guide whose version cannot be read rather than failing the whole search
            return false;
        }
        if (version == null) {
            return false;
        }
        if (from != null) {
            int cmp = compareMajorMinor(version, from);
            // the guide of the starting release only matters for its patch releases
            if (cmp < 0 || cmp == 0 && from[2] == 0) {
                return false;
            }
        }
        return to == null || compareMajorMinor(version, to) <= 0;
    }

    private static int compareMajorMinor(int[] a, int[] b) {
        return a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]);
    }

    /**
     * Parse a version such as 2.25, 3.20.1, 4.22.1-SNAPSHOT or 4 into {major, minor, patch}, or null when blank.
     */
    static int[] parseVersion(String version, String name) {
        if (version == null || version.isBlank()) {
            return null;
        }
        Matcher m = VERSION_PATTERN.matcher(version.trim());
        if (!m.lookingAt()) {
            throw new IllegalArgumentException(
                    name + " must be a Camel version such as 2.25 or 4.22.1, got: " + version);
        }
        return new int[] {
                Integer.parseInt(m.group(1)),
                m.group(2) != null ? Integer.parseInt(m.group(2)) : 0,
                m.group(3) != null ? Integer.parseInt(m.group(3)) : 0 };
    }

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

    private static GuideIndex buildIndex(List<GuideSection> sections) {
        Map<String, Integer> df = new HashMap<>();
        long totalLength = 0;
        for (GuideSection section : sections) {
            for (String term : section.termFrequencies().keySet()) {
                df.merge(term, 1, Integer::sum);
            }
            totalLength += section.length();
        }
        double avg = sections.isEmpty() ? 1 : Math.max(1, (double) totalLength / sections.size());
        return new GuideIndex(List.copyOf(sections), Map.copyOf(df), avg);
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
                splitIntoSections(content, filename, sections);
            } catch (IOException e) {
                // skip unreadable files
            }
        }

        return sections;
    }

    /**
     * Split a guide into one entry per {@code ===} topic. The {@code ==} release heading is kept as context in the
     * entry title (e.g. "Upgrading Camel 4.20 to 4.21 / camel-cxf"); text between a release heading and its first topic
     * becomes an entry titled with the release heading alone. Deeper headings stay inside their topic, and headings
     * inside listing blocks are ignored.
     */
    static void splitIntoSections(String content, String filename, List<GuideSection> sections) {
        String guideName = guideNameFromFilename(filename);
        String version = versionFromFilename(filename);
        String url = urlFromFilename(filename);
        boolean major = isMajorMigrationGuide(filename);

        StringBuilder current = new StringBuilder();
        String release = null;
        String topic = null;
        String delimiter = null;

        for (String line : content.split("\n")) {
            if (delimiter == null && (line.equals("----") || line.equals("....") || line.equals("===="))) {
                delimiter = line;
            } else if (line.equals(delimiter)) {
                delimiter = null;
            } else if (delimiter == null && line.startsWith("== ")) {
                flush(guideName, version, url, major, title(guideName, release, topic), current, sections);
                release = line.substring(3).trim();
                topic = null;
                continue;
            } else if (delimiter == null && line.startsWith("=== ")) {
                flush(guideName, version, url, major, title(guideName, release, topic), current, sections);
                topic = line.substring(4).trim();
                continue;
            }
            current.append(line).append("\n");
        }
        flush(guideName, version, url, major, title(guideName, release, topic), current, sections);
    }

    private static String title(String guideName, String release, String topic) {
        if (release == null) {
            return topic != null ? topic : guideName;
        }
        return topic != null ? release + " / " + topic : release;
    }

    private static void flush(
            String guideName, String version, String url, boolean major, String title,
            StringBuilder current, List<GuideSection> sections) {
        String text = current.toString().trim();
        current.setLength(0);
        if (text.isEmpty() || text.startsWith(":") && text.lines().allMatch(l -> l.isBlank() || l.startsWith(":"))) {
            // nothing but blank lines or document attributes
            return;
        }
        List<String> tokens = tokenize(text);
        // only the topic part of the title gets the heading boost; the release heading is shared by many entries
        String topic = title.contains(" / ") ? title.substring(title.lastIndexOf(" / ") + 3) : title;
        Set<String> titleTerms = new HashSet<>(tokenize(topic));
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokens) {
            tf.merge(token, 1, Integer::sum);
        }
        for (String token : titleTerms) {
            tf.merge(token, 1, Integer::sum);
        }
        sections.add(new GuideSection(
                guideName, version, title, text, url, major, Map.copyOf(tf), Set.copyOf(titleTerms),
                tokens.size() + titleTerms.size()));
    }

    /**
     * Tokenize text into lowercase words, preserving hyphens within words (e.g., direct-vm). A hyphenated word also
     * yields its parts, so cxf matches camel-cxf.
     */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String word : words(text)) {
            tokens.add(word);
            if (word.indexOf('-') > 0) {
                for (String sub : word.split("-+")) {
                    if (sub.length() > 1) {
                        tokens.add(sub);
                    }
                }
            }
        }
        return tokens;
    }

    /**
     * Split text into lowercase words of at least two characters, keeping hyphenated words whole.
     */
    static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        for (String part : text.toLowerCase(Locale.ROOT).split("[^a-z0-9\\-]+")) {
            String word = trimHyphens(part);
            if (word.length() > 1) {
                words.add(word);
            }
        }
        return words;
    }

    private static String trimHyphens(String word) {
        int start = 0;
        int end = word.length();
        while (start < end && word.charAt(start) == '-') {
            start++;
        }
        while (end > start && word.charAt(end - 1) == '-') {
            end--;
        }
        return word.substring(start, end);
    }

    private static final LevenshteinDistance LEVENSHTEIN = LevenshteinDistance.getDefaultInstance();

    static int levenshteinDistance(String a, String b) {
        return LEVENSHTEIN.apply(a, b);
    }

    private static boolean isMajorMigrationGuide(String filename) {
        return filename.matches("camel-\\d+-migration-guide\\.adoc");
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

    /**
     * One searchable entry: a {@code ===} topic of a release section of a guide.
     */
    public record GuideSection(
            String guide,
            String version,
            String sectionTitle,
            String content,
            String url,
            boolean majorMigration,
            Map<String, Integer> termFrequencies,
            Set<String> titleTerms,
            int length) {
    }

    /**
     * A search result: the entry, its score and the vocabulary terms that matched the query.
     */
    public record SearchHit(GuideSection section, double score, List<String> matchedTerms) {
    }

    private record GuideIndex(List<GuideSection> sections, Map<String, Integer> documentFrequencies, double averageLength) {
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
