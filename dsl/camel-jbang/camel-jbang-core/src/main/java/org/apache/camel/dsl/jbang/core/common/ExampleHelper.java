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
package org.apache.camel.dsl.jbang.core.common;

import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

public final class ExampleHelper {

    private static final String CATALOG_RESOURCE = "examples/camel-jbang-example-catalog.json";
    private static final String GITHUB_EXAMPLES_URL
            = "https://github.com/apache/camel-jbang-examples/tree/main/";
    private static final String GITHUB_RAW_URL
            = "https://raw.githubusercontent.com/apache/camel-jbang-examples/main/%s/%s";

    private ExampleHelper() {
    }

    public static List<JsonObject> loadCatalog() {
        List<JsonObject> catalog = new ArrayList<>();
        try (InputStream is = ExampleHelper.class.getClassLoader().getResourceAsStream(CATALOG_RESOURCE)) {
            if (is == null) {
                return catalog;
            }
            String json = IOHelper.loadText(is);
            JsonArray array = (JsonArray) Jsoner.deserialize(json);
            for (Object item : array) {
                catalog.add((JsonObject) item);
            }
        } catch (Exception e) {
            // ignore
        }
        return catalog;
    }

    public static JsonObject findExample(List<JsonObject> catalog, String name) {
        // first try exact match (e.g. "language/groovy")
        for (JsonObject entry : catalog) {
            if (name.equals(entry.getString("name"))) {
                return entry;
            }
        }
        // then try matching by short name without category prefix (e.g. "groovy" matches "language/groovy")
        for (JsonObject entry : catalog) {
            if (name.equals(getShortName(entry))) {
                return entry;
            }
        }
        return null;
    }

    /**
     * All examples with the given short name (the part after the group), so a caller can tell an ambiguous short name
     * from an unknown one.
     */
    public static List<JsonObject> findExamplesByShortName(List<JsonObject> catalog, String name) {
        List<JsonObject> matches = new ArrayList<>();
        for (JsonObject entry : catalog) {
            if (name.equals(getShortName(entry))) {
                matches.add(entry);
            }
        }
        return matches;
    }

    public static List<String> getExampleNames(List<JsonObject> catalog) {
        List<String> names = new ArrayList<>();
        for (JsonObject entry : catalog) {
            String name = entry.getString("name");
            names.add(name);
            String shortName = getShortName(entry);
            if (!shortName.equals(name)) {
                names.add(shortName);
            }
        }
        return names;
    }

    public static List<JsonObject> filterExamples(List<JsonObject> catalog, String filter) {
        if (filter == null || filter.isEmpty()) {
            return catalog;
        }
        String lowerFilter = filter.toLowerCase();
        List<JsonObject> result = new ArrayList<>();
        for (JsonObject entry : catalog) {
            if (matches(entry, lowerFilter)) {
                result.add(entry);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static boolean matches(JsonObject entry, String filter) {
        String name = entry.getString("name");
        if (name != null && name.toLowerCase().contains(filter)) {
            return true;
        }
        String title = entry.getString("title");
        if (title != null && title.toLowerCase().contains(filter)) {
            return true;
        }
        String desc = entry.getString("description");
        if (desc != null && desc.toLowerCase().contains(filter)) {
            return true;
        }
        String level = entry.getString("level");
        if (level != null && level.toLowerCase().contains(filter)) {
            return true;
        }
        Collection<String> tags = (Collection<String>) entry.get("tags");
        if (tags != null) {
            for (String tag : tags) {
                if (tag.toLowerCase().contains(filter)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The groups of the example ladder in reading order: level, title, and the one-line introduction the README of
     * camel-jbang-examples uses. Quick start comes first, showcase last; a level not listed here sorts after them.
     */
    private static final String[][] GROUPS = {
            {
                    "quick-start", "Quick start",
                    "The first ten minutes: generic examples with no story and no service, each running in seconds." },
            { "run", "Run", "Running Camel: timers and cron schedules, a bean in a route, properties and profiles." },
            { "transform", "Transform and map", "JSON, XML and CSV in and out, field-by-field mapping, Groovy and XSLT." },
            {
                    "route", "Route",
                    "The routing patterns: content-based router, splitter, aggregator, filter and multicast." },
            {
                    "fail-well", "Fail well",
                    "Retries, a dead letter channel, and a circuit breaker in front of a flaky service." },
            {
                    "connect", "Connect without a service",
                    "Files, an HTTP client and a REST server; everything runs inside the example." },
            {
                    "connect-service", "Connect to one service",
                    "SQL, JMS, MQTT, Kafka and FTP against a service the Camel CLI starts with camel infra run." },
            {
                    "contracts", "Contracts and security",
                    "An OpenAPI contract served and called, and an API protected by Keycloak." },
            { "ai", "AI", "A local model writing text, routes exposed as MCP tools, RAG over documents, PII redaction." },
            {
                    "cloud", "Cloud",
                    "A cloud service, run locally through LocalStack and switched to the real thing by properties." },
            {
                    "showcase", "Showcase",
                    "Tooling demos outside the ladder: the TUI, a memory leak, message sizes, log analysis." },
    };

    /**
     * The levels of the ladder in reading order.
     */
    public static List<String> getGroupOrder() {
        List<String> order = new ArrayList<>();
        for (String[] g : GROUPS) {
            order.add(g[0]);
        }
        return order;
    }

    /**
     * The title of a group (level), for example "Quick start" for quick-start; an unknown level is capitalized.
     */
    public static String getGroupTitle(String level) {
        for (String[] g : GROUPS) {
            if (g[0].equals(level)) {
                return g[1];
            }
        }
        return formatCategory(level);
    }

    /**
     * The one-line introduction of a group (level), or an empty string for an unknown level.
     */
    public static String getGroupIntro(String level) {
        for (String[] g : GROUPS) {
            if (g[0].equals(level)) {
                return g[2];
            }
        }
        return "";
    }

    /**
     * Groups the examples by level in ladder order, each group sorted by name; empty groups are left out and levels not
     * on the ladder come last in the order they appear.
     */
    public static Map<String, List<JsonObject>> groupByLevel(List<JsonObject> catalog) {
        Map<String, List<JsonObject>> groups = new LinkedHashMap<>();
        for (String level : getGroupOrder()) {
            groups.put(level, new ArrayList<>());
        }
        for (JsonObject entry : catalog) {
            String level = entry.getStringOrDefault("level", "other");
            groups.computeIfAbsent(level, k -> new ArrayList<>()).add(entry);
        }
        groups.values().removeIf(List::isEmpty);
        for (List<JsonObject> entries : groups.values()) {
            entries.sort(Comparator.comparingInt(ExampleHelper::getOrder)
                    .thenComparing(e -> e.getStringOrDefault("name", "")));
        }
        return groups;
    }

    /**
     * The reading order of the example within its group from the metadata, or a large number when it has none, so
     * examples with an order come first and the rest sort by name.
     */
    public static int getOrder(JsonObject entry) {
        Object order = entry.get("order");
        if (order instanceof Number n) {
            return n.intValue();
        }
        return Integer.MAX_VALUE;
    }

    /**
     * What the example teaches from its metadata: the components, EIPs, languages and data formats, each as a list of
     * names in that order; keys without names are left out, so an example without metadata gives an empty map.
     */
    public static Map<String, List<String>> getTeaches(JsonObject entry) {
        Map<String, List<String>> answer = new LinkedHashMap<>();
        JsonObject teaches = entry.getMap("teaches");
        if (teaches == null || teaches.isEmpty()) {
            return answer;
        }
        for (String key : new String[] { "components", "eips", "languages", "dataformats" }) {
            // the catalog holds string arrays here; anything else in a hand-edited metadata file is skipped
            if (!(teaches.get(key) instanceof Collection<?> values) || values.isEmpty()) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof String s) {
                    names.add(s);
                }
            }
            if (!names.isEmpty()) {
                answer.put(key, names);
            }
        }
        return answer;
    }

    /**
     * What the example teaches, as one line: the components and the EIPs from its metadata, or an empty string.
     */
    public static String getTeachesSummary(JsonObject entry) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : getTeaches(entry).entrySet()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(e.getKey()).append(": ").append(String.join(", ", e.getValue()));
        }
        return sb.toString();
    }

    /**
     * Whether the example's test is skipped in CI because it needs a language model or another resource a build agent
     * does not have.
     */
    public static boolean isCiSkip(JsonObject entry) {
        Boolean skip = entry.getBoolean("ciSkip");
        return skip != null && skip;
    }

    /**
     * Whether the name is one of the groups (levels) of the ladder.
     */
    public static boolean isGroup(String name) {
        if (name == null) {
            return false;
        }
        for (String[] g : GROUPS) {
            if (g[0].equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Wraps the text at word boundaries so no line is longer than the width; a single word longer than the width stays
     * on its own line.
     */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        if (width < 20) {
            width = 20;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    public static String getCategory(JsonObject entry) {
        String name = entry.getString("name");
        int slash = name != null ? name.indexOf('/') : -1;
        return slash > 0 ? name.substring(0, slash) : "";
    }

    public static String formatCategory(String category) {
        if (category == null || category.isEmpty()) {
            return "";
        }
        for (String[] g : GROUPS) {
            if (g[0].equals(category)) {
                return g[1];
            }
        }
        return switch (category) {
            case "eip" -> "EIP";
            case "rest" -> "REST";
            default -> category.substring(0, 1).toUpperCase(Locale.ROOT) + category.substring(1);
        };
    }

    public static String getShortName(JsonObject entry) {
        String name = entry.getString("name");
        int slash = name != null ? name.indexOf('/') : -1;
        return slash > 0 ? name.substring(slash + 1) : name != null ? name : "";
    }

    /**
     * The arguments of a camel run of an example, for a process started in the folder of the example: --example=name
     * (or --example name) replaced by the files of the example, and its name unless one is given (CAMEL-25236).
     */
    public static List<String> runArgs(List<String> args, JsonObject example) {
        List<String> answer = new ArrayList<>();
        boolean named = args.stream().anyMatch(a -> a.equals("--name") || a.startsWith("--name="));
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("--example") || a.startsWith("--example=")) {
                if (a.equals("--example") && i + 1 < args.size()) {
                    i++; // its value
                }
                answer.addAll(getFiles(example));
                if (!named) {
                    answer.add("--name=" + getShortName(example));
                }
            } else {
                answer.add(a);
            }
        }
        return answer;
    }

    public static boolean isBundled(JsonObject entry) {
        Boolean bundled = entry.getBoolean("bundled");
        return bundled != null && bundled;
    }

    public static boolean requiresDocker(JsonObject entry) {
        Boolean docker = entry.getBoolean("requiresDocker");
        if (docker != null && docker) {
            return true;
        }
        return !getInfraServices(entry).isEmpty();
    }

    public static boolean hasCitrusTests(JsonObject entry) {
        Boolean citrus = entry.getBoolean("hasCitrusTests");
        return citrus != null && citrus;
    }

    /**
     * What an example needs beyond its infra services, for the user to do before the run (a model to pull, an API key
     * to set, another example to run first), as plain text; null when it needs nothing else.
     */
    public static String getNeeds(JsonObject entry) {
        String needs = entry.getString("needs");
        if (needs == null || needs.isBlank()) {
            return null;
        }
        // the catalog writes it as Markdown for the README tables: `ollama pull granite4:3b`
        return needs.replace("`", "").trim();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getInfraServices(JsonObject entry) {
        Collection<String> services = (Collection<String>) entry.get("infraServices");
        if (services == null) {
            return List.of();
        }
        return new ArrayList<>(services);
    }

    @SuppressWarnings("unchecked")
    public static List<String> getFiles(JsonObject entry) {
        Collection<String> files = (Collection<String>) entry.get("files");
        if (files == null) {
            return List.of();
        }
        return new ArrayList<>(files);
    }

    /** The name prefix of the temporary folder an example runs in. */
    public static final String EXAMPLE_DIR_PREFIX = "camel-example-";

    public static Path extractBundledExample(JsonObject entry) throws Exception {
        return extractBundledExample(entry, true);
    }

    /**
     * Extracts a bundled example to a temporary folder of its own.
     *
     * @param deleteOnExit whether the folder is deleted when this JVM exits; false when the example may outlive it, as
     *                     the examples the monitor starts do (they keep running when it quits)
     */
    public static Path extractBundledExample(JsonObject entry, boolean deleteOnExit) throws Exception {
        String name = entry.getString("name");
        List<String> fileNames = getFiles(entry);
        Path tempDir = Files.createTempDirectory(EXAMPLE_DIR_PREFIX);

        for (String fileName : fileNames) {
            String resourcePath = "examples/" + name + "/" + fileName;
            try (InputStream is = ExampleHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    String content = IOHelper.loadText(is);
                    Path targetFile = tempDir.resolve(fileName);
                    // create parent dirs for nested files like input/account.xml
                    Files.createDirectories(targetFile.getParent());
                    Files.writeString(targetFile, content);
                }
            }
        }

        if (deleteOnExit) {
            deleteOnExit(tempDir);
        }
        return tempDir;
    }

    public static Path downloadGithubExample(JsonObject entry) throws Exception {
        return downloadGithubExample(entry, true);
    }

    /**
     * Downloads an example from GitHub to a temporary folder of its own.
     *
     * @param deleteOnExit whether the folder is deleted when this JVM exits; false when the example may outlive it
     */
    public static Path downloadGithubExample(JsonObject entry, boolean deleteOnExit) throws Exception {
        String name = entry.getString("name");
        List<String> fileNames = getFiles(entry);
        Path tempDir = Files.createTempDirectory(EXAMPLE_DIR_PREFIX);

        HttpClient hc = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build();
        for (String fileName : fileNames) {
            String rawUrl = String.format(GITHUB_RAW_URL, name, fileName);
            HttpResponse<String> res = hc.send(
                    HttpRequest.newBuilder(new URI(rawUrl)).timeout(Duration.ofSeconds(20)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                Path targetFile = tempDir.resolve(fileName);
                Files.createDirectories(targetFile.getParent());
                Files.writeString(targetFile, res.body());
            }
        }

        if (deleteOnExit) {
            deleteOnExit(tempDir);
        }
        return tempDir;
    }

    /**
     * Deletes the folder of an example, with what the example wrote in it (out/, a done folder), when this JVM exits.
     * File.deleteOnExit leaves the folder behind once anything else is in it.
     */
    private static void deleteOnExit(Path dir) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtil.removeDir(dir.toFile()), "CamelExampleCleanup"));
    }

    /**
     * The folders of examples in the temporary directory that are older than the given age and that none of the given
     * folders (those of running integrations) is in: left behind by runs that ended, or by a process that was killed.
     */
    public static List<Path> staleExampleDirs(Collection<Path> inUse, Duration minAge) {
        return staleExampleDirs(Path.of(System.getProperty("java.io.tmpdir")), inUse, minAge);
    }

    static List<Path> staleExampleDirs(Path tmp, Collection<Path> inUse, Duration minAge) {
        List<Path> answer = new ArrayList<>();
        List<Path> used = inUse.stream().map(ExampleHelper::realPath).toList();
        long cutoff = System.currentTimeMillis() - minAge.toMillis();
        try (var dirs = Files.newDirectoryStream(tmp, EXAMPLE_DIR_PREFIX + "*")) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir) || Files.getLastModifiedTime(dir).toMillis() > cutoff) {
                    continue;
                }
                Path real = realPath(dir);
                if (used.stream().noneMatch(u -> u.startsWith(real))) {
                    answer.add(dir);
                }
            }
        } catch (Exception e) {
            // the temporary directory cannot be listed: nothing to clean
        }
        return answer;
    }

    // on macOS the temporary directory is reached through a link (/var is /private/var)
    private static Path realPath(Path p) {
        try {
            return p.toRealPath();
        } catch (Exception e) {
            return p.toAbsolutePath().normalize();
        }
    }

    public static String getGithubUrl(JsonObject entry) {
        return GITHUB_EXAMPLES_URL + entry.getString("name");
    }

}
