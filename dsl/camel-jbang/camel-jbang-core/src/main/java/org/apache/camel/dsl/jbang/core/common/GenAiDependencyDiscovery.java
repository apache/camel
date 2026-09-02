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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
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

/**
 * Discovers GenAI-related dependencies for Camel JBang run/export from route URIs, LangChain4j provider classes and
 * observability settings.
 */
public final class GenAiDependencyDiscovery {

    public static final String AI_OBSERVABILITY_ENABLED = "camel.aiObservability.enabled";

    private static final Set<String> GEN_AI_SCHEMES = Set.of(
            "aws-bedrock", "aws-bedrock-agent", "aws-bedrock-agent-runtime",
            "aws2-textract", "docling",
            "langchain4j-chat", "langchain4j-embeddings", "langchain4j-embeddingstore",
            "langchain4j-tools", "langchain4j-agent", "langchain4j-web-search",
            "openai", "kserve", "tensorflow-serving", "djl",
            "huggingface", "ai-tool", "google-vertexai");

    private static final Map<String, String> LANGCHAIN4J_PROVIDER_DEPENDENCIES = Map.ofEntries(
            Map.entry("dev.langchain4j.model.ollama.OllamaChatModel",
                    "mvn:dev.langchain4j:langchain4j-ollama:${langchain4j-version}"),
            Map.entry("dev.langchain4j.model.ollama.OllamaEmbeddingModel",
                    "mvn:dev.langchain4j:langchain4j-ollama:${langchain4j-version}"),
            Map.entry("dev.langchain4j.model.ollama.OllamaLanguageModel",
                    "mvn:dev.langchain4j:langchain4j-ollama:${langchain4j-version}"),
            Map.entry("dev.langchain4j.model.openai.OpenAiChatModel",
                    "mvn:dev.langchain4j:langchain4j-open-ai:${langchain4j-version}"),
            Map.entry("dev.langchain4j.model.openai.OpenAiEmbeddingModel",
                    "mvn:dev.langchain4j:langchain4j-open-ai:${langchain4j-version}"),
            Map.entry("dev.langchain4j.model.openai.OpenAiLanguageModel",
                    "mvn:dev.langchain4j:langchain4j-open-ai:${langchain4j-version}"),
            Map.entry("dev.langchain4j.model.huggingface.HuggingFaceChatModel",
                    "mvn:dev.langchain4j:langchain4j-hugging-face:${langchain4j-beta-version}"),
            Map.entry("dev.langchain4j.model.anthropic.AnthropicChatModel",
                    "mvn:dev.langchain4j:langchain4j-anthropic:${langchain4j-version}"),
            Map.entry("dev.langchain4j.model.azure.AzureOpenAiChatModel",
                    "mvn:dev.langchain4j:langchain4j-azure-open-ai:${langchain4j-version}"),
            Map.entry("dev.langchain4j.model.mistralai.MistralAiChatModel",
                    "mvn:dev.langchain4j:langchain4j-mistral-ai:${langchain4j-version}"),
            Map.entry("dev.langchain4j.model.vertexai.VertexAiChatModel",
                    "mvn:dev.langchain4j:langchain4j-vertex-ai:${langchain4j-version}"),
            Map.entry("dev.langchain4j.model.googleai.GoogleAiGeminiChatModel",
                    "mvn:dev.langchain4j:langchain4j-google-ai-gemini:${langchain4j-version}"));

    private static final Pattern YAML_SCHEME_PATTERN = Pattern.compile(
            "(?:uri:\\s*[\"']?|from:[ \\t]+[\"']?|to:[ \\t]+[\"']?|toD:[ \\t]+[\"']?)"
                                                                       + "([a-zA-Z][a-zA-Z0-9+.-]*):(?://)?",
            Pattern.MULTILINE);

    private static final Pattern XML_SCHEME_PATTERN = Pattern.compile(
            "(?:<from|<to|<toD)\\s+uri=[\"']([a-zA-Z][a-zA-Z0-9+.-]*):",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JAVA_URI_PATTERN = Pattern.compile(
            "[\"']([a-zA-Z][a-zA-Z0-9+.-]*):(?://)?[^\"']*[\"']");

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

        for (String file : sourceFiles) {
            String content = readContent(file);
            if (content == null) {
                continue;
            }
            for (String scheme : extractSchemes(content)) {
                if (GEN_AI_SCHEMES.contains(scheme)) {
                    ComponentModel model = catalog.componentModel(scheme);
                    if (model != null) {
                        hasGenAiRoutes = true;
                        deps.add("camel:" + scheme);
                    }
                }
            }
            deps.addAll(discoverProviderDependencies(content));
        }

        if (hasGenAiRoutes && includeAiObservability(properties, observe)
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
                collectSourceFile(line, "java=", files);
                collectSourceFile(line, "xml=", files);
                collectSourceFile(line, "yaml=", files);
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
        if ("true".equalsIgnoreCase(enabled)) {
            return true;
        }
        // include by default for GenAI routes; --observe explicitly enables observability stack
        return observe || enabled == null;
    }

    static List<String> extractSchemes(String content) {
        List<String> schemes = new ArrayList<>();
        addSchemeMatches(schemes, YAML_SCHEME_PATTERN, content);
        addSchemeMatches(schemes, XML_SCHEME_PATTERN, content);
        addSchemeMatches(schemes, JAVA_URI_PATTERN, content);
        schemes.removeIf(scheme -> "http".equals(scheme) || "https".equals(scheme));
        return schemes;
    }

    static Collection<String> discoverProviderDependencies(String content) {
        Set<String> deps = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : LANGCHAIN4J_PROVIDER_DEPENDENCIES.entrySet()) {
            if (content.contains(entry.getKey())) {
                deps.add(entry.getValue());
            }
        }
        return deps;
    }

    private static void addSchemeMatches(List<String> schemes, Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String scheme = matcher.group(1);
            if (!schemes.contains(scheme)) {
                schemes.add(scheme);
            }
        }
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
            if (!file.isBlank() && !files.contains(file)) {
                files.add(file);
            }
        }
    }

    private static String readContent(String file) {
        if (file == null || file.isBlank()) {
            return null;
        }
        String path = file;
        if (path.startsWith("classpath:")) {
            String resource = path.substring("classpath:".length());
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
        Path source = Paths.get(path);
        if (!Files.exists(source) || Files.isDirectory(source)) {
            return null;
        }
        String ext = FileUtil.onlyExt(path, true);
        if (ext == null || !Set.of("java", "xml", "yaml", "yml", "properties").contains(ext)) {
            return null;
        }
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
