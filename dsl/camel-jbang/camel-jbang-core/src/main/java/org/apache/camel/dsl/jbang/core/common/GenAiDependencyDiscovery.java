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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.CLASSPATH_FILES;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.GROOVY_FILES;

/**
 * Discovers GenAI-related dependencies for Camel JBang run/export from route URIs, LangChain4j provider classes and
 * observability settings.
 */
public final class GenAiDependencyDiscovery {

    public static final String AI_OBSERVABILITY_ENABLED = "camel.aiObservability.enabled";

    private static final String KNOWN_DEPENDENCIES = "camel-main-known-dependencies.properties";

    private static final Set<String> GEN_AI_SCHEMES = Set.of(
            "aws-bedrock", "aws-bedrock-agent", "aws-bedrock-agent-runtime",
            "aws2-textract", "docling",
            "langchain4j-chat", "langchain4j-embeddings", "langchain4j-embeddingstore",
            "langchain4j-tools", "langchain4j-agent", "langchain4j-web-search",
            "openai", "kserve", "tensorflow-serving", "djl",
            "huggingface", "ai-tool", "google-vertexai", "spring-ai-chat");

    private static final Pattern YAML_SCHEME_PATTERN = Pattern.compile(
            "(?:uri:\\s*[\"']?|from:[ \\t]+[\"']?|to:[ \\t]+[\"']?|toD:[ \\t]+[\"']?)"
                                                                       + "([a-zA-Z][a-zA-Z0-9+.-]*):(?://)?",
            Pattern.MULTILINE);

    private static final Pattern XML_SCHEME_PATTERN = Pattern.compile(
            "<(?:[\\w]+:)?(from|to|toD|enrich|wireTap)\\s+uri=[\"']([a-zA-Z][a-zA-Z0-9+.-]*):",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JAVA_URI_PATTERN = Pattern.compile(
            "(?:from|to|toD|wireTap|enrich|pollEnrich)\\s*\\(\\s*[\"']([a-zA-Z][a-zA-Z0-9+.-]*):(?://)?[^\"']*[\"']");

    private static volatile Map<String, String> langchain4jProviderDependencies;

    private GenAiDependencyDiscovery() {
    }

    public static Collection<String> discover(Collection<String> sourceFiles, Properties properties, boolean observe) {
        return discover(sourceFiles, properties, observe, new DefaultCamelCatalog());
    }

    static Collection<String> discover(
            Collection<String> sourceFiles, Properties properties, boolean observe,
            CamelCatalog catalog) {
        Set<String> deps = new LinkedHashSet<>();
        boolean hasGenAiRoutes = false;
        boolean hasProviderReferences = false;

        for (String file : sourceFiles) {
            String content = readContent(file);
            if (content == null) {
                continue;
            }
            String ext = extensionOf(file);
            for (String scheme : extractSchemes(content, ext)) {
                if (GEN_AI_SCHEMES.contains(scheme)) {
                    ComponentModel model = catalog.componentModel(scheme);
                    if (model != null) {
                        hasGenAiRoutes = true;
                        deps.add("camel:" + scheme);
                    }
                }
            }
            Collection<String> providers = discoverProviderDependencies(content);
            if (!providers.isEmpty()) {
                hasProviderReferences = true;
                deps.addAll(providers);
            }
        }

        if ((hasGenAiRoutes || hasProviderReferences) && includeAiObservability(properties, observe)
                && catalog.otherModel("ai-observability") != null) {
            deps.add("camel:ai-observability");
        }

        return deps;
    }

    public static Collection<String> discoverFromSettings(
            Path settings, Path profile, boolean observe,
            Collection<String> sourceFiles)
            throws IOException {
        List<String> files = new ArrayList<>(sourceFiles);
        if (settings != null && Files.exists(settings)) {
            for (String line : RuntimeUtil.loadPropertiesLines(settings)) {
                collectSourceFile(line, "camel.main.routesIncludePattern=", files);
                collectSourceFile(line, CLASSPATH_FILES + "=", files);
                collectSourceFile(line, GROOVY_FILES + "=", files);
            }
        }
        Properties properties = new Properties();
        if (profile != null && Files.exists(profile)) {
            RuntimeUtil.loadProperties(properties, profile);
        }
        return discover(files, properties, observe);
    }

    static boolean includeAiObservability(Properties properties, boolean observe) {
        String enabled = properties != null ? properties.getProperty(AI_OBSERVABILITY_ENABLED) : null;
        if ("false".equalsIgnoreCase(enabled)) {
            return false;
        }
        return observe || "true".equalsIgnoreCase(enabled);
    }

    static List<String> extractSchemes(String content, String ext) {
        if (content == null || ext == null) {
            return List.of();
        }
        List<String> schemes = new ArrayList<>();
        switch (ext) {
            case "yaml", "yml" -> addSchemeMatches(schemes, YAML_SCHEME_PATTERN, stripYamlComments(content));
            case "xml" -> addSchemeMatches(schemes, XML_SCHEME_PATTERN, content, 2);
            case "java", "groovy" -> addSchemeMatches(schemes, JAVA_URI_PATTERN, content, 1);
            default -> {
            }
        }
        schemes.removeIf(scheme -> "http".equals(scheme) || "https".equals(scheme));
        return schemes;
    }

    static Collection<String> discoverProviderDependencies(String content) {
        Set<String> deps = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : providerDependencies().entrySet()) {
            if (content.contains(entry.getKey())) {
                deps.add("mvn:" + entry.getValue());
            }
        }
        return deps;
    }

    private static Map<String, String> providerDependencies() {
        Map<String, String> answer = langchain4jProviderDependencies;
        if (answer != null) {
            return answer;
        }
        synchronized (GenAiDependencyDiscovery.class) {
            answer = langchain4jProviderDependencies;
            if (answer != null) {
                return answer;
            }
            answer = loadLangChain4jProviderDependencies();
            langchain4jProviderDependencies = answer;
            return answer;
        }
    }

    private static Map<String, String> loadLangChain4jProviderDependencies() {
        Map<String, String> answer = new LinkedHashMap<>();
        Properties properties = new Properties();
        try (InputStream is = GenAiDependencyDiscovery.class.getClassLoader().getResourceAsStream(KNOWN_DEPENDENCIES)) {
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException e) {
            return answer;
        }
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("dev.langchain4j.")) {
                answer.put(key, properties.getProperty(key));
            }
        }
        return answer;
    }

    private static void addSchemeMatches(List<String> schemes, Pattern pattern, String content) {
        addSchemeMatches(schemes, pattern, content, 1);
    }

    private static void addSchemeMatches(List<String> schemes, Pattern pattern, String content, int group) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String scheme = matcher.group(group);
            if (!schemes.contains(scheme)) {
                schemes.add(scheme);
            }
        }
    }

    private static String stripYamlComments(String content) {
        StringBuilder sb = new StringBuilder(content.length());
        for (String line : content.split("\n", -1)) {
            int idx = line.indexOf('#');
            if (idx >= 0) {
                line = line.substring(0, idx);
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static void collectSourceFile(String line, String prefix, List<String> files) {
        if (!line.startsWith(prefix)) {
            return;
        }
        String value = StringHelper.after(line, prefix);
        if (value == null || value.isBlank()) {
            return;
        }
        for (String file : value.split(",")) {
            file = file.trim();
            if (file.startsWith("file:")) {
                file = file.substring(5);
            }
            int query = file.indexOf('?');
            if (query > 0) {
                file = file.substring(0, query);
            }
            if (!file.isBlank() && !files.contains(file)) {
                files.add(file);
            }
        }
    }

    private static String extensionOf(String file) {
        if (file == null) {
            return null;
        }
        String path = file;
        if (path.startsWith("classpath:")) {
            path = path.substring("classpath:".length());
        } else if (path.startsWith("file:")) {
            path = path.substring(5);
        }
        int query = path.indexOf('?');
        if (query > 0) {
            path = path.substring(0, query);
        }
        return FileUtil.onlyExt(path, true);
    }

    private static String readContent(String file) {
        if (file == null || file.isBlank()) {
            return null;
        }
        String path = file;
        if (path.startsWith("classpath:")) {
            String resource = path.substring("classpath:".length());
            int query = resource.indexOf('?');
            if (query > 0) {
                resource = resource.substring(0, query);
            }
            try (var is = GenAiDependencyDiscovery.class.getClassLoader().getResourceAsStream(resource)) {
                if (is == null) {
                    return null;
                }
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        }
        if (path.startsWith("file:")) {
            path = path.substring(5);
        }
        int query = path.indexOf('?');
        if (query > 0) {
            path = path.substring(0, query);
        }
        Path source = Paths.get(path);
        if (!Files.exists(source) || Files.isDirectory(source)) {
            return null;
        }
        String ext = FileUtil.onlyExt(path, true);
        if (ext == null || !Set.of("java", "xml", "yaml", "yml", "properties", "groovy").contains(ext)) {
            return null;
        }
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
