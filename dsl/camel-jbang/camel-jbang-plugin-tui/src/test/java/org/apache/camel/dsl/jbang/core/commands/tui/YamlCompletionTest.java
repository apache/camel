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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class YamlCompletionTest {

    private static CamelCatalog catalog;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    // --- Context detection via SourceViewer ---

    @Test
    void findComponentInsideParametersBlock() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    from:",
                "      uri: \"timer:tick\"",
                "      parameters:",
                "        period: 1000",
                "      steps:",
                "        - to:",
                "            uri: \"kafka:myTopic\"",
                "            parameters:",
                "              brokers: localhost",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on "brokers: localhost" (line 9, 0-based)
        SourceViewer.YamlEndpointContext ctx = viewer.findEnclosingComponent(9);
        assertThat(ctx).isNotNull();
        assertThat(ctx.component()).isEqualTo("kafka");
        assertThat(ctx.consumer()).isFalse();
    }

    @Test
    void findConsumerComponentInsideFromParameters() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    from:",
                "      uri: \"timer:tick\"",
                "      parameters:",
                "        period: 1000",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on "period: 1000" (line 4, 0-based)
        SourceViewer.YamlEndpointContext ctx = viewer.findEnclosingComponent(4);
        assertThat(ctx).isNotNull();
        assertThat(ctx.component()).isEqualTo("timer");
        assertThat(ctx.consumer()).isTrue();
    }

    @Test
    void findComponentWithUriWithoutPath() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    from:",
                "      uri: timer",
                "      parameters:",
                "        timerName: tick",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on "timerName: tick" (line 4, 0-based)
        SourceViewer.YamlEndpointContext ctx = viewer.findEnclosingComponent(4);
        assertThat(ctx).isNotNull();
        assertThat(ctx.component()).isEqualTo("timer");
        assertThat(ctx.consumer()).isTrue();
    }

    @Test
    void findComponentOnBlankLineInsideParameters() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    from:",
                "      uri: timer",
                "      parameters:",
                "        timerName: tick",
                "",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on the blank line (line 5, 0-based) — still inside parameters block
        SourceViewer.YamlEndpointContext ctx = viewer.findEnclosingComponent(5);
        assertThat(ctx).isNotNull();
        assertThat(ctx.component()).isEqualTo("timer");
        assertThat(ctx.consumer()).isTrue();
    }

    @Test
    void findComponentOnBlankLineAfterParameters() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: kafka:orders",
                "    parameters:",
                "",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on blank line right after parameters: (line 3)
        SourceViewer.YamlEndpointContext ctx = viewer.findEnclosingComponent(3);
        assertThat(ctx).isNotNull();
        assertThat(ctx.component()).isEqualTo("kafka");
        assertThat(ctx.consumer()).isTrue();
    }

    @Test
    void returnsNullOutsideParametersBlock() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    from:",
                "      uri: \"timer:tick\"",
                "      steps:",
                "        - log: \"hello\"",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on "- log:" (line 4, 0-based) — inside steps, not parameters
        SourceViewer.YamlEndpointContext ctx = viewer.findEnclosingComponent(4);
        assertThat(ctx).isNull();
    }

    @Test
    void returnsNullOnRouteLevel() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    from:",
                "      uri: \"timer:tick\"",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on "from:" (line 1, 0-based)
        SourceViewer.YamlEndpointContext ctx = viewer.findEnclosingComponent(1);
        assertThat(ctx).isNull();
    }

    @Test
    void findComponentWithInlineUri() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: \"timer:tick\"",
                "    steps:",
                "      - to: \"kafka:orders\"",
                "        parameters:",
                "          brokers: localhost",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on "brokers: localhost" (line 5, 0-based)
        SourceViewer.YamlEndpointContext ctx = viewer.findEnclosingComponent(5);
        assertThat(ctx).isNotNull();
        assertThat(ctx.component()).isEqualTo("kafka");
        assertThat(ctx.consumer()).isFalse();
    }

    // --- Key completion from catalog ---

    @Test
    void keyCompletionReturnsKafkaEndpointOptions() {
        List<AutocompletePopup.CompletionItem> items = provideKeyCompletions("kafka", "producer");

        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(i -> i.key().equals("brokers"));
        // should have descriptions and types
        var brokers = items.stream().filter(i -> i.key().equals("brokers")).findFirst();
        assertThat(brokers).isPresent();
        assertThat(brokers.get().description()).isNotNull().isNotEmpty();
        assertThat(brokers.get().type()).isNotNull();
    }

    @Test
    void keyCompletionExcludesPathOptions() {
        List<AutocompletePopup.CompletionItem> items = provideKeyCompletions("kafka", "producer");

        // "topic" is a path option in kafka, should NOT appear in parameters completion
        assertThat(items).noneMatch(i -> i.key().equals("topic"));
    }

    @Test
    void keyCompletionFiltersConsumerOnlyOptions() {
        List<AutocompletePopup.CompletionItem> items = provideKeyCompletions("kafka", "producer");

        // consumer-only options should not appear for a producer
        ComponentModel model = catalog.componentModel("kafka");
        List<String> consumerOnlyOptions = new ArrayList<>();
        for (ComponentModel.EndpointOptionModel opt : model.getEndpointParameterOptions()) {
            String label = opt.getLabel();
            if (label != null && label.contains("consumer") && !label.contains("producer")) {
                consumerOnlyOptions.add(opt.getName());
            }
        }
        if (!consumerOnlyOptions.isEmpty()) {
            for (String opt : consumerOnlyOptions) {
                assertThat(items).noneMatch(i -> i.key().equals(opt));
            }
        }
    }

    @Test
    void keyCompletionShowsConsumerOptionsForConsumer() {
        List<AutocompletePopup.CompletionItem> items = provideKeyCompletions("kafka", "consumer");

        // should include consumer options
        ComponentModel model = catalog.componentModel("kafka");
        List<String> consumerOptions = new ArrayList<>();
        for (ComponentModel.EndpointOptionModel opt : model.getEndpointParameterOptions()) {
            String label = opt.getLabel();
            if (label != null && label.contains("consumer") && !label.contains("producer")) {
                consumerOptions.add(opt.getName());
            }
        }
        if (!consumerOptions.isEmpty()) {
            for (String opt : consumerOptions) {
                assertThat(items).anyMatch(i -> i.key().equals(opt));
            }
        }
    }

    @Test
    void keyCompletionSortedNonDeprecatedFirst() {
        List<AutocompletePopup.CompletionItem> items = provideKeyCompletions("kafka", "producer");

        int lastNonDeprecatedIdx = -1;
        int firstDeprecatedIdx = items.size();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).deprecated()) {
                lastNonDeprecatedIdx = i;
            } else if (i < firstDeprecatedIdx) {
                firstDeprecatedIdx = i;
            }
        }
        if (firstDeprecatedIdx < items.size()) {
            assertThat(lastNonDeprecatedIdx).isLessThan(firstDeprecatedIdx);
        }
    }

    // --- Value completion ---

    @Test
    void valueCompletionReturnsBooleanValues() {
        List<AutocompletePopup.CompletionItem> items = provideValueCompletions("kafka", "autoCommitEnable");

        assertThat(items).anyMatch(i -> i.key().equals("true"));
        assertThat(items).anyMatch(i -> i.key().equals("false"));
    }

    @Test
    void valueCompletionReturnsEnumValues() {
        List<AutocompletePopup.CompletionItem> items = provideValueCompletions("kafka", "autoOffsetReset");

        // kafka autoOffsetReset has enum values: latest, earliest, none
        assertThat(items).anyMatch(i -> i.key().equals("latest"));
        assertThat(items).anyMatch(i -> i.key().equals("earliest"));
    }

    // --- Property placeholder loading ---

    @Test
    void loadPlaceholdersFromPropertiesFile() throws IOException {
        Path propsFile = tempDir.resolve("application.properties");
        Files.writeString(propsFile, String.join("\n",
                "# Kafka settings",
                "kafka.brokers=localhost:9092",
                "kafka.topic=my-orders",
                "",
                "# App settings",
                "myapp.timeout=30000",
                ""));

        List<AutocompletePopup.CompletionItem> items = loadPlaceholders(tempDir);

        assertThat(items).hasSize(3);
        assertThat(items).anyMatch(i -> i.key().equals("{{kafka.brokers}}"));
        assertThat(items).anyMatch(i -> i.key().equals("{{kafka.topic}}"));
        assertThat(items).anyMatch(i -> i.key().equals("{{myapp.timeout}}"));

        // descriptions should show the property values
        var brokers = items.stream().filter(i -> i.key().equals("{{kafka.brokers}}")).findFirst();
        assertThat(brokers).isPresent();
        assertThat(brokers.get().description()).isEqualTo("localhost:9092");
    }

    @Test
    void loadPlaceholdersSkipsCommentsAndBlanks() throws IOException {
        Path propsFile = tempDir.resolve("application.properties");
        Files.writeString(propsFile, String.join("\n",
                "# comment",
                "! another comment",
                "",
                "valid.key=value",
                ""));

        List<AutocompletePopup.CompletionItem> items = loadPlaceholders(tempDir);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).key()).isEqualTo("{{valid.key}}");
    }

    @Test
    void loadPlaceholdersReturnsEmptyForNoPropertiesFiles() {
        List<AutocompletePopup.CompletionItem> items = loadPlaceholders(tempDir);
        assertThat(items).isEmpty();
    }

    // --- Helpers that replicate SourceTab logic for testing ---

    private List<AutocompletePopup.CompletionItem> provideKeyCompletions(String componentName, String role) {
        ComponentModel model = catalog.componentModel(componentName);
        if (model == null) {
            return List.of();
        }
        boolean isConsumer = "consumer".equals(role);
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (ComponentModel.EndpointOptionModel opt : model.getEndpointParameterOptions()) {
            if (includeEndpointOption(opt, isConsumer)) {
                items.add(new AutocompletePopup.CompletionItem(
                        opt.getName(), opt.getDescription(), opt.getType(),
                        opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                        opt.getGroup()));
            }
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private static boolean includeEndpointOption(ComponentModel.EndpointOptionModel opt, boolean isConsumer) {
        String label = opt.getLabel();
        if (label == null || label.isEmpty()) {
            return true;
        }
        if (label.contains("consumer") && label.contains("producer")) {
            return true;
        }
        if (isConsumer) {
            return !label.contains("producer");
        } else {
            return !label.contains("consumer");
        }
    }

    private List<AutocompletePopup.CompletionItem> provideValueCompletions(String componentName, String optionName) {
        ComponentModel model = catalog.componentModel(componentName);
        if (model == null) {
            return List.of();
        }

        ComponentModel.EndpointOptionModel opt = null;
        for (ComponentModel.EndpointOptionModel o : model.getEndpointOptions()) {
            if (o.getName().equals(optionName)) {
                opt = o;
                break;
            }
        }

        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        if (opt != null) {
            List<String> enums = opt.getEnums();
            if (enums != null && !enums.isEmpty()) {
                for (String value : enums) {
                    boolean isDefault = value.equals(String.valueOf(opt.getDefaultValue()));
                    items.add(new AutocompletePopup.CompletionItem(
                            value, opt.getDescription(), opt.getType(),
                            isDefault ? value : opt.getDefaultValue(),
                            false, null, opt.getGroup()));
                }
            } else if ("boolean".equalsIgnoreCase(opt.getType())
                    || "java.lang.Boolean".equals(opt.getJavaType())) {
                items.add(new AutocompletePopup.CompletionItem(
                        "true", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
                items.add(new AutocompletePopup.CompletionItem(
                        "false", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
            }
        }
        return items;
    }

    private List<AutocompletePopup.CompletionItem> loadPlaceholders(Path dir) {
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".properties"))
                    .forEach(p -> {
                        try {
                            for (String line : Files.readAllLines(p)) {
                                String trimmed = line.trim();
                                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                                    continue;
                                }
                                int eq = trimmed.indexOf('=');
                                if (eq > 0) {
                                    String key = trimmed.substring(0, eq).trim();
                                    String value = trimmed.substring(eq + 1).trim();
                                    items.add(new AutocompletePopup.CompletionItem(
                                            "{{" + key + "}}", value, "placeholder",
                                            null, false, null, p.getFileName().toString()));
                                }
                            }
                        } catch (IOException e) {
                            // skip
                        }
                    });
        } catch (IOException e) {
            return List.of();
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        return items;
    }
}
