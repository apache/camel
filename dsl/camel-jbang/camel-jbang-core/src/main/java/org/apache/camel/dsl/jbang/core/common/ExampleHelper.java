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
import java.util.List;
import java.util.Locale;

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

    public static String getCategory(JsonObject entry) {
        String name = entry.getString("name");
        int slash = name != null ? name.indexOf('/') : -1;
        return slash > 0 ? name.substring(0, slash) : "";
    }

    public static String formatCategory(String category) {
        return switch (category) {
            case "ai" -> "AI";
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

    public static Path extractBundledExample(JsonObject entry) throws Exception {
        String name = entry.getString("name");
        List<String> fileNames = getFiles(entry);
        Path tempDir = Files.createTempDirectory("camel-example-");

        for (String fileName : fileNames) {
            String resourcePath = "examples/" + name + "/" + fileName;
            try (InputStream is = ExampleHelper.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    String content = IOHelper.loadText(is);
                    Path targetFile = tempDir.resolve(fileName);
                    // create parent dirs for nested files like input/account.xml
                    Files.createDirectories(targetFile.getParent());
                    Files.writeString(targetFile, content);
                    targetFile.toFile().deleteOnExit();
                    targetFile.getParent().toFile().deleteOnExit();
                }
            }
        }

        tempDir.toFile().deleteOnExit();
        return tempDir;
    }

    public static Path downloadGithubExample(JsonObject entry) throws Exception {
        String name = entry.getString("name");
        List<String> fileNames = getFiles(entry);
        Path tempDir = Files.createTempDirectory("camel-example-");

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
                targetFile.toFile().deleteOnExit();
                targetFile.getParent().toFile().deleteOnExit();
            }
        }

        tempDir.toFile().deleteOnExit();
        return tempDir;
    }

    public static String getGithubUrl(JsonObject entry) {
        return GITHUB_EXAMPLES_URL + entry.getString("name");
    }

}
