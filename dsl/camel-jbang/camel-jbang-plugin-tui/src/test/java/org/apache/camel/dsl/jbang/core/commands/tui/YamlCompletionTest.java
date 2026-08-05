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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;
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
    void keyCompletionIncludesPathOptions() {
        List<AutocompletePopup.CompletionItem> items = provideKeyCompletions("kafka", "producer");

        // "topic" is a path option in kafka, should appear in completion
        assertThat(items).anyMatch(i -> i.key().equals("topic"));
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

    // --- URI context detection (component name completion) ---

    @Test
    void findUriContextOnEmptyUriLine() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlUriContext ctx = viewer.findUriContext(1);
        assertThat(ctx).isNotNull();
        assertThat(ctx.consumer()).isTrue();
        assertThat(ctx.prefix()).isEmpty();
    }

    @Test
    void findUriContextOnToUri() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - to:",
                "          uri: ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlUriContext ctx = viewer.findUriContext(4);
        assertThat(ctx).isNotNull();
        assertThat(ctx.consumer()).isFalse();
        assertThat(ctx.prefix()).isEmpty();
    }

    @Test
    void findUriContextWithPartialPrefix() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: ka",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlUriContext ctx = viewer.findUriContext(1);
        assertThat(ctx).isNotNull();
        assertThat(ctx.consumer()).isTrue();
        assertThat(ctx.prefix()).isEqualTo("ka");
    }

    @Test
    void findUriContextReturnsNullWhenSchemeAlreadyComplete() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // scheme already has a colon — no component completion needed
        SourceViewer.YamlUriContext ctx = viewer.findUriContext(1);
        assertThat(ctx).isNull();
    }

    @Test
    void findUriContextOnInlineEip() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - to: ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlUriContext ctx = viewer.findUriContext(3);
        assertThat(ctx).isNotNull();
        assertThat(ctx.consumer()).isFalse();
    }

    @Test
    void findUriContextOnPollEnrich() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - pollEnrich:",
                "          uri: ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlUriContext ctx = viewer.findUriContext(4);
        assertThat(ctx).isNotNull();
        assertThat(ctx.consumer()).isTrue();
    }

    // --- Component name completion (from vs to filtering) ---

    @Test
    void componentCompletionForFromExcludesProducerOnly() {
        List<AutocompletePopup.CompletionItem> items = provideComponentCompletions("consumer");

        // timer is consumer-only — should be in the list
        assertThat(items).anyMatch(i -> i.key().equals("timer"));
        // log is producer-only — should NOT be in the list
        assertThat(items).noneMatch(i -> i.key().equals("log"));
    }

    @Test
    void componentCompletionForToExcludesConsumerOnly() {
        List<AutocompletePopup.CompletionItem> items = provideComponentCompletions("producer");

        // log is producer-only — should be in the list
        assertThat(items).anyMatch(i -> i.key().equals("log"));
        // timer is consumer-only — should NOT be in the list
        assertThat(items).noneMatch(i -> i.key().equals("timer"));
    }

    @Test
    void componentCompletionIncludesBothRoles() {
        List<AutocompletePopup.CompletionItem> consumerItems = provideComponentCompletions("consumer");
        List<AutocompletePopup.CompletionItem> producerItems = provideComponentCompletions("producer");

        // kafka supports both — should be in both lists
        assertThat(consumerItems).anyMatch(i -> i.key().equals("kafka"));
        assertThat(producerItems).anyMatch(i -> i.key().equals("kafka"));
    }

    @Test
    void componentCompletionHasDescriptions() {
        List<AutocompletePopup.CompletionItem> items = provideComponentCompletions("producer");

        var kafka = items.stream().filter(i -> i.key().equals("kafka")).findFirst();
        assertThat(kafka).isPresent();
        assertThat(kafka.get().description()).isNotNull().isNotEmpty();
        // type shows first label (e.g. "messaging") instead of generic "component"
        assertThat(kafka.get().type()).isEqualTo("messaging");
    }

    @Test
    void componentCompletionFilterMatchesLabels() {
        List<AutocompletePopup.CompletionItem> items = provideComponentCompletions("producer");

        // simulate typing "cloud" — should match components labeled "cloud"
        var popup = new AutocompletePopup(items, "", "");
        for (char c : "cloud".toCharArray()) {
            popup.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofChar(c, dev.tamboui.tui.event.KeyModifiers.NONE));
        }
        assertThat(popup.hasItems()).isTrue();
    }

    // --- Required options ---

    @Test
    void requiredOptionsAreSortedFirst() {
        List<AutocompletePopup.CompletionItem> items = provideKeyCompletions("jms", "producer");

        // find first required and first non-required
        int firstRequired = -1;
        int lastRequired = -1;
        int firstNonRequired = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).required()) {
                if (firstRequired < 0) {
                    firstRequired = i;
                }
                lastRequired = i;
            } else if (!items.get(i).deprecated()) {
                if (firstNonRequired < 0) {
                    firstNonRequired = i;
                }
            }
        }
        if (firstRequired >= 0 && firstNonRequired >= 0) {
            assertThat(lastRequired).isLessThan(firstNonRequired);
        }
    }

    @Test
    void jmsDestinationNameIncluded() {
        List<AutocompletePopup.CompletionItem> items = provideKeyCompletions("jms", "producer");

        // destinationName is a path option — should be included
        assertThat(items).anyMatch(i -> i.key().equals("destinationName"));
    }

    // --- Existing parameters filtering ---

    @Test
    void collectExistingParametersFindsKeys() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: kafka",
                "    parameters:",
                "      brokers: localhost",
                "      topic: orders",
                "      ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on blank line (line 5) inside parameters
        Set<String> existing = viewer.collectExistingParameters(5);
        assertThat(existing).containsExactlyInAnyOrder("brokers", "topic");
    }

    @Test
    void collectExistingParametersOnBlankLineAfterParametersHeader() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: kafka",
                "    parameters:",
                "      ",
                "      brokers: localhost",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on blank line right after parameters: (line 3)
        Set<String> existing = viewer.collectExistingParameters(3);
        assertThat(existing).contains("brokers");
    }

    @Test
    void existingParametersFilteredFromCompletions() {
        Set<String> existing = Set.of("brokers", "topic");
        List<AutocompletePopup.CompletionItem> items = provideKeyCompletions("kafka", "producer", existing);

        assertThat(items).noneMatch(i -> i.key().equals("brokers"));
        assertThat(items).noneMatch(i -> i.key().equals("topic"));
        // other options should still be present
        assertThat(items).isNotEmpty();
    }

    // --- EIP context detection ---

    @Test
    void findEnclosingEipDetectsSplit() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - split:",
                "          expression:",
                "            simple: \"${body}\"",
                "          ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlEipContext ctx = viewer.findEnclosingEip(6);
        assertThat(ctx).isNotNull();
        assertThat(ctx.eipName()).isEqualTo("split");
    }

    @Test
    void findEnclosingEipReturnsNullInsideParameters() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: kafka",
                "    parameters:",
                "      brokers: localhost",
                "      ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlEipContext ctx = viewer.findEnclosingEip(4);
        assertThat(ctx).isNull();
    }

    @Test
    void findEnclosingEipConvertsKebabCase() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - circuit-breaker:",
                "          ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlEipContext ctx = viewer.findEnclosingEip(4);
        assertThat(ctx).isNotNull();
        assertThat(ctx.eipName()).isEqualTo("circuitBreaker");
    }

    @Test
    void findEnclosingEipSkipsStructuralKeys() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - split:",
                "          expression:",
                "            simple: \"${body}\"",
                "          steps:",
                "            - log:",
                "                ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor inside log: block (nested in split's steps)
        SourceViewer.YamlEipContext ctx = viewer.findEnclosingEip(8);
        assertThat(ctx).isNotNull();
        assertThat(ctx.eipName()).isEqualTo("log");
    }

    @Test
    void findEnclosingEipOnEmptyLineBetweenEipAndOptions() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - log:",
                "",
                "            message: \"${body}\"",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // empty line between log: and message: should detect log as enclosing EIP
        SourceViewer.YamlEipContext ctx = viewer.findEnclosingEip(4);
        assertThat(ctx).isNotNull();
        assertThat(ctx.eipName()).isEqualTo("log");
    }

    // --- EIP option completion ---

    @Test
    void eipCompletionIncludesOnlyAttributes() {
        List<AutocompletePopup.CompletionItem> items = provideEipKeyCompletions("split");

        // streaming is an attribute — should be included
        assertThat(items).anyMatch(i -> i.key().equals("streaming"));
        assertThat(items).anyMatch(i -> i.key().equals("parallelProcessing"));
    }

    @Test
    void eipCompletionExcludesBoilerplate() {
        List<AutocompletePopup.CompletionItem> items = provideEipKeyCompletions("split");

        assertThat(items).noneMatch(i -> i.key().equals("id"));
        assertThat(items).noneMatch(i -> i.key().equals("note"));
        assertThat(items).noneMatch(i -> i.key().equals("description"));
        assertThat(items).noneMatch(i -> i.key().equals("disabled"));
    }

    @Test
    void eipCompletionIncludesExpressionAndElementKinds() {
        List<AutocompletePopup.CompletionItem> items = provideEipKeyCompletions("split");

        // canonical format: expression and element kinds are included as keys
        assertThat(items).anyMatch(i -> i.key().equals("expression"));
    }

    @Test
    void eipCompletionExcludesExistingOptions() {
        Set<String> existing = Set.of("streaming", "delimiter");
        List<AutocompletePopup.CompletionItem> items = provideEipKeyCompletions("split", existing);

        assertThat(items).noneMatch(i -> i.key().equals("streaming"));
        assertThat(items).noneMatch(i -> i.key().equals("delimiter"));
        assertThat(items).isNotEmpty();
    }

    @Test
    void eipCompletionForLogEip() {
        List<AutocompletePopup.CompletionItem> items = provideEipKeyCompletions("log");

        assertThat(items).anyMatch(i -> i.key().equals("message"));
        assertThat(items).anyMatch(i -> i.key().equals("loggingLevel"));
        assertThat(items).anyMatch(i -> i.key().equals("logName"));
    }

    @Test
    void eipValueCompletionForEnum() {
        List<AutocompletePopup.CompletionItem> items = provideEipValueCompletions("log", "loggingLevel");

        assertThat(items).anyMatch(i -> i.key().equals("INFO"));
        assertThat(items).anyMatch(i -> i.key().equals("ERROR"));
        assertThat(items).anyMatch(i -> i.key().equals("DEBUG"));
    }

    @Test
    void eipValueCompletionForBoolean() {
        List<AutocompletePopup.CompletionItem> items = provideEipValueCompletions("split", "streaming");

        assertThat(items).anyMatch(i -> i.key().equals("true"));
        assertThat(items).anyMatch(i -> i.key().equals("false"));
    }

    @Test
    void eipValueCompletionFiltersIncompatiblePlaceholders() {
        List<AutocompletePopup.CompletionItem> placeholders = List.of(
                new AutocompletePopup.CompletionItem(
                        "{{greeting.message}}", "Hello World", "placeholder",
                        null, false, null, "application.properties"),
                new AutocompletePopup.CompletionItem(
                        "{{log.level}}", "WARN", "placeholder",
                        null, false, null, "application.properties"));

        // enum option: only placeholders whose value matches a valid enum choice should be included
        List<AutocompletePopup.CompletionItem> items = provideEipValueCompletions("log", "loggingLevel", placeholders);

        assertThat(items).anyMatch(i -> i.key().equals("INFO"));
        assertThat(items).anyMatch(i -> i.key().equals("ERROR"));
        // {{log.level}} has value "WARN" which IS a valid enum value
        assertThat(items).anyMatch(i -> i.key().equals("{{log.level}}"));
        // {{greeting.message}} has value "Hello World" which is NOT a valid enum value
        assertThat(items).noneMatch(i -> i.key().equals("{{greeting.message}}"));
    }

    @Test
    void eipValueCompletionAllowsPlaceholdersForStringOptions() {
        List<AutocompletePopup.CompletionItem> placeholders = List.of(
                new AutocompletePopup.CompletionItem(
                        "{{greeting.message}}", "Hello World", "placeholder",
                        null, false, null, "application.properties"));

        // string option (logName): no type filter, all placeholders should be included
        List<AutocompletePopup.CompletionItem> items = provideEipValueCompletions("log", "logName", placeholders);

        assertThat(items).anyMatch(i -> i.key().equals("{{greeting.message}}"));
    }

    // --- collectExistingSiblingKeys ---

    @Test
    void collectExistingSiblingKeysFindsKeys() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - split:",
                "          streaming: true",
                "          delimiter: \",\"",
                "          ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        Set<String> keys = viewer.collectExistingSiblingKeys(6);
        assertThat(keys).containsExactlyInAnyOrder("streaming", "delimiter");
    }

    // --- dashToCamelCase ---

    @Test
    void dashToCamelCaseConverts() {
        assertThat(SourceViewer.dashToCamelCase("circuit-breaker")).isEqualTo("circuitBreaker");
        assertThat(SourceViewer.dashToCamelCase("wire-tap")).isEqualTo("wireTap");
        assertThat(SourceViewer.dashToCamelCase("split")).isEqualTo("split");
        assertThat(SourceViewer.dashToCamelCase(null)).isNull();
    }

    // --- findScopeLineRow ---

    @Test
    void findScopeLineRowDetectsUriLine() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - to:",
                "          uri: kafka",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on uri: line → scope is that row
        assertThat(viewer.findScopeLineRow(1)).isEqualTo(1);
        assertThat(viewer.findScopeLineRow(4)).isEqualTo(4);
    }

    @Test
    void findScopeLineRowInsideParameters() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: kafka",
                "    parameters:",
                "      brokers: localhost",
                "      groupId: test",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor inside parameters: block → scope is the uri: line
        assertThat(viewer.findScopeLineRow(3)).isEqualTo(1);
        assertThat(viewer.findScopeLineRow(4)).isEqualTo(1);
    }

    @Test
    void findScopeLineRowInsideEip() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - split:",
                "          expression:",
                "            simple: \"${body}\"",
                "          streaming: true",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on streaming: → scope is split: line
        assertThat(viewer.findScopeLineRow(6)).isEqualTo(3);
    }

    @Test
    void findScopeLineRowOnScopeLineItself() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - split:",
                "          streaming: true",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on the split: line itself → returns that row
        assertThat(viewer.findScopeLineRow(3)).isEqualTo(3);
    }

    @Test
    void findScopeLineRowNoScope() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on top-level from: → no parent scope
        assertThat(viewer.findScopeLineRow(0)).isEqualTo(-1);
    }

    @Test
    void findScopeLineRowInlineEip() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - to: kafka:topic",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // cursor on inline to: line → scope is that row
        assertThat(viewer.findScopeLineRow(3)).isEqualTo(3);
    }

    // --- Canonical expression completion (expression: → language → language options) ---

    @Test
    void eipCompletionIncludesExpressionKey() {
        List<AutocompletePopup.CompletionItem> items = provideEipKeyCompletions("split");

        // canonical format: expression is a key, not expanded into language names
        assertThat(items).anyMatch(i -> i.key().equals("expression"));
        // language names should NOT appear at EIP level
        assertThat(items).noneMatch(i -> i.key().equals("simple"));
        assertThat(items).noneMatch(i -> i.key().equals("jsonpath"));
    }

    @Test
    void eipCompletionForSetHeaderIncludesExpressionKey() {
        List<AutocompletePopup.CompletionItem> items = provideEipKeyCompletions("setHeader");

        assertThat(items).anyMatch(i -> i.key().equals("expression"));
        assertThat(items).anyMatch(i -> i.key().equals("name"));
        // no inline languages at EIP level
        assertThat(items).noneMatch(i -> i.key().equals("simple"));
    }

    @Test
    void eipCompletionExcludesExpressionWhenAlreadySpecified() {
        Set<String> existing = Set.of("expression");
        List<AutocompletePopup.CompletionItem> items = provideEipKeyCompletions("split", existing);

        assertThat(items).noneMatch(i -> i.key().equals("expression"));
        assertThat(items).anyMatch(i -> i.key().equals("streaming"));
    }

    @Test
    void expressionContextOffersLanguageNames() {
        List<AutocompletePopup.CompletionItem> items = provideExpressionLanguageCompletions("split");

        assertThat(items).anyMatch(i -> i.key().equals("simple"));
        assertThat(items).anyMatch(i -> i.key().equals("jsonpath"));
        assertThat(items).anyMatch(i -> i.key().equals("xpath"));
        assertThat(items).anyMatch(i -> i.key().equals("constant"));
        var simple = items.stream().filter(i -> i.key().equals("simple")).findFirst();
        assertThat(simple).isPresent();
        assertThat(simple.get().type()).isEqualTo("language");
    }

    @Test
    void expressionContextExcludesAlreadySpecified() {
        Set<String> existing = Set.of("simple");
        List<AutocompletePopup.CompletionItem> items = provideExpressionLanguageCompletions("split", existing);

        assertThat(items).isEmpty();
    }

    @Test
    void languageOptionCompletionForSimple() {
        List<AutocompletePopup.CompletionItem> items = provideLanguageOptionCompletions("simple");

        assertThat(items).anyMatch(i -> i.key().equals("expression"));
        assertThat(items).anyMatch(i -> i.key().equals("resultType"));
    }

    @Test
    void languageOptionCompletionForJsonpath() {
        List<AutocompletePopup.CompletionItem> items = provideLanguageOptionCompletions("jsonpath");

        assertThat(items).anyMatch(i -> i.key().equals("expression"));
        assertThat(items).anyMatch(i -> i.key().equals("resultType"));
    }

    @Test
    void languageOptionCompletionExcludesExisting() {
        Set<String> existing = Set.of("expression");
        List<AutocompletePopup.CompletionItem> items = provideLanguageOptionCompletions("simple", existing);

        assertThat(items).noneMatch(i -> i.key().equals("expression"));
        assertThat(items).anyMatch(i -> i.key().equals("resultType"));
    }

    @Test
    void languageOptionCompletionExcludesBoilerplate() {
        List<AutocompletePopup.CompletionItem> items = provideLanguageOptionCompletions("simple");

        assertThat(items).noneMatch(i -> i.key().equals("id"));
        assertThat(items).noneMatch(i -> i.key().equals("description"));
    }

    @Test
    void eipCompletionForLogDoesNotIncludeExpression() {
        List<AutocompletePopup.CompletionItem> items = provideEipKeyCompletions("log");

        // log has no expression option
        assertThat(items).noneMatch(i -> i.key().equals("expression"));
    }

    // --- Data format name completion ---

    @Test
    void dataFormatNameCompletionForMarshal() {
        List<AutocompletePopup.CompletionItem> items = provideDataFormatNameCompletions("marshal");

        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(i -> i.key().equals("json"));
        assertThat(items).anyMatch(i -> i.key().equals("csv"));
        assertThat(items).anyMatch(i -> i.key().equals("avro"));
        // type should be "dataformat"
        var json = items.stream().filter(i -> i.key().equals("json")).findFirst();
        assertThat(json).isPresent();
        assertThat(json.get().type()).isEqualTo("dataformat");
    }

    @Test
    void dataFormatNameCompletionForUnmarshal() {
        List<AutocompletePopup.CompletionItem> items = provideDataFormatNameCompletions("unmarshal");

        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(i -> i.key().equals("json"));
    }

    @Test
    void dataFormatNameCompletionExcludesAlreadySpecified() {
        Set<String> existing = Set.of("json");
        List<AutocompletePopup.CompletionItem> items = provideDataFormatNameCompletions("marshal", existing);

        // once a data format is specified, no more should appear
        assertThat(items).isEmpty();
    }

    // --- Data format option completion ---

    @Test
    void dataFormatOptionCompletionForJackson() {
        List<AutocompletePopup.CompletionItem> items = provideDataFormatOptionCompletions("jackson");

        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(i -> i.key().equals("prettyPrint"));
        assertThat(items).anyMatch(i -> i.key().equals("unmarshalType"));
    }

    @Test
    void dataFormatOptionCompletionExcludesBoilerplate() {
        List<AutocompletePopup.CompletionItem> items = provideDataFormatOptionCompletions("jackson");

        assertThat(items).noneMatch(i -> i.key().equals("id"));
        assertThat(items).noneMatch(i -> i.key().equals("description"));
    }

    @Test
    void dataFormatOptionCompletionExcludesExisting() {
        Set<String> existing = Set.of("prettyPrint");
        List<AutocompletePopup.CompletionItem> items = provideDataFormatOptionCompletions("jackson", existing);

        assertThat(items).noneMatch(i -> i.key().equals("prettyPrint"));
        assertThat(items).anyMatch(i -> i.key().equals("unmarshalType"));
    }

    @Test
    void dataFormatOptionCompletionForCsv() {
        List<AutocompletePopup.CompletionItem> items = provideDataFormatOptionCompletions("csv");

        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(i -> i.key().equals("delimiter"));
    }

    // --- Expression context detection ---

    @Test
    void findExpressionContextInsideExpressionBlock() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - split:",
                "          expression:",
                "            ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlExpressionContext ctx = viewer.findExpressionContext(5);
        assertThat(ctx).isNotNull();
        assertThat(ctx.eipName()).isEqualTo("split");
    }

    @Test
    void findExpressionContextReturnsNullOutsideExpression() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - split:",
                "          streaming: true",
                "          ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlExpressionContext ctx = viewer.findExpressionContext(5);
        assertThat(ctx).isNull();
    }

    // --- Language option context detection ---

    @Test
    void findLanguageOptionContextInsideSimple() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - split:",
                "          expression:",
                "            simple:",
                "              ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlLanguageOptionContext ctx = viewer.findLanguageOptionContext(6);
        assertThat(ctx).isNotNull();
        assertThat(ctx.languageName()).isEqualTo("simple");
    }

    @Test
    void findLanguageOptionContextReturnsNullWhenParentIsNotExpression() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - split:",
                "          streaming:",
                "            ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlLanguageOptionContext ctx = viewer.findLanguageOptionContext(5);
        assertThat(ctx).isNull();
    }

    // --- Marshal/unmarshal context detection ---

    @Test
    void findMarshalContextInsideMarshal() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - marshal:",
                "          ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlDataFormatContext ctx = viewer.findMarshalContext(4);
        assertThat(ctx).isNotNull();
        assertThat(ctx.eipName()).isEqualTo("marshal");
    }

    @Test
    void findMarshalContextInsideUnmarshal() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - unmarshal:",
                "          ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlDataFormatContext ctx = viewer.findMarshalContext(4);
        assertThat(ctx).isNotNull();
        assertThat(ctx.eipName()).isEqualTo("unmarshal");
    }

    @Test
    void findMarshalContextReturnsNullForSplit() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - split:",
                "          ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlDataFormatContext ctx = viewer.findMarshalContext(4);
        assertThat(ctx).isNull();
    }

    // --- Data format option context detection ---

    @Test
    void findDataFormatOptionContextInsideCsv() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - marshal:",
                "          csv:",
                "            ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlDataFormatOptionContext ctx = viewer.findDataFormatOptionContext(5);
        assertThat(ctx).isNotNull();
        assertThat(ctx.dataFormatName()).isEqualTo("csv");
    }

    @Test
    void findDataFormatOptionContextInsideUnmarshal() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - unmarshal:",
                "          csv:",
                "            delimiter: \";\"",
                "            ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlDataFormatOptionContext ctx = viewer.findDataFormatOptionContext(6);
        assertThat(ctx).isNotNull();
        assertThat(ctx.dataFormatName()).isEqualTo("csv");
    }

    @Test
    void findDataFormatOptionContextReturnsNullInsideSplit() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - split:",
                "          simple:",
                "            ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlDataFormatOptionContext ctx = viewer.findDataFormatOptionContext(5);
        assertThat(ctx).isNull();
    }

    // --- Route-level option completion ---

    @Test
    void findEnclosingEipDetectsRoute() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    ",
                "    from:",
                "      uri: timer:tick",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        SourceViewer.YamlEipContext ctx = viewer.findEnclosingEip(1);
        assertThat(ctx).isNotNull();
        assertThat(ctx.eipName()).isEqualTo("route");
    }

    @Test
    void routeEipCompletionIncludesRouteOptions() {
        List<AutocompletePopup.CompletionItem> items = provideEipKeyCompletions("route");

        assertThat(items).anyMatch(i -> i.key().equals("autoStartup"));
        assertThat(items).anyMatch(i -> i.key().equals("streamCache"));
        assertThat(items).anyMatch(i -> i.key().equals("logMask"));
        assertThat(items).anyMatch(i -> i.key().equals("messageHistory"));
    }

    @Test
    void routeEipCompletionExcludesStructural() {
        List<AutocompletePopup.CompletionItem> items = provideEipKeyCompletions("route");

        // from and steps are not attribute kind, should not appear
        assertThat(items).noneMatch(i -> i.key().equals("from"));
    }

    // --- Helpers that replicate SourceTab logic for testing ---

    private List<AutocompletePopup.CompletionItem> provideKeyCompletions(String componentName, String role) {
        return provideKeyCompletions(componentName, role, Set.of());
    }

    private List<AutocompletePopup.CompletionItem> provideKeyCompletions(
            String componentName, String role, Set<String> existingKeys) {
        ComponentModel model = catalog.componentModel(componentName);
        if (model == null) {
            return List.of();
        }
        boolean isConsumer = "consumer".equals(role);
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (ComponentModel.EndpointOptionModel opt : model.getEndpointOptions()) {
            if (includeEndpointOption(opt, isConsumer)) {
                if (!existingKeys.contains(opt.getName()) || opt.isMultiValue()) {
                    items.add(new AutocompletePopup.CompletionItem(
                            opt.getName(), opt.getDescription(), opt.getType(),
                            opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                            opt.getGroup(), opt.isRequired()));
                }
            }
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing(ci -> !ci.required())
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private List<AutocompletePopup.CompletionItem> provideComponentCompletions(String role) {
        boolean isConsumer = "consumer".equals(role);
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (String name : catalog.findComponentNames()) {
            ComponentModel model = catalog.componentModel(name);
            if (model == null) {
                continue;
            }
            if (isConsumer && model.isProducerOnly()) {
                continue;
            }
            if (!isConsumer && model.isConsumerOnly()) {
                continue;
            }
            String labels = model.getLabel();
            String firstLabel = labels != null && !labels.isEmpty()
                    ? labels.split(",")[0].trim()
                    : "component";
            items.add(new AutocompletePopup.CompletionItem(
                    model.getScheme(), model.getDescription(), firstLabel,
                    null, model.isDeprecated(), model.getDeprecationNote(),
                    labels));
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
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

    private static final Set<String> EIP_BOILERPLATE = Set.of("id", "note", "description", "disabled",
            "input", "outputs", "steps");

    private List<AutocompletePopup.CompletionItem> provideEipKeyCompletions(String eipName) {
        return provideEipKeyCompletions(eipName, Set.of());
    }

    private List<AutocompletePopup.CompletionItem> provideEipKeyCompletions(String eipName, Set<String> existingKeys) {
        EipModel model = catalog.eipModel(eipName);
        if (model == null) {
            return List.of();
        }
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (EipModel.EipOptionModel opt : model.getOptions()) {
            if ("expression".equals(opt.getKind()) || "element".equals(opt.getKind())) {
                if (EIP_BOILERPLATE.contains(opt.getName())) {
                    continue;
                }
                if (existingKeys.contains(opt.getName())) {
                    continue;
                }
                items.add(new AutocompletePopup.CompletionItem(
                        opt.getName(), opt.getDescription(), opt.getType(),
                        opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                        opt.getGroup(), opt.isRequired()));
                continue;
            }
            if (!"attribute".equals(opt.getKind())) {
                continue;
            }
            if (EIP_BOILERPLATE.contains(opt.getName())) {
                continue;
            }
            if (existingKeys.contains(opt.getName()) && !opt.isMultiValue()) {
                continue;
            }
            items.add(new AutocompletePopup.CompletionItem(
                    opt.getName(), opt.getDescription(), opt.getType(),
                    opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                    opt.getGroup(), opt.isRequired()));
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing((a, b) -> Boolean.compare(b.required(), a.required()))
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private List<AutocompletePopup.CompletionItem> provideExpressionLanguageCompletions(String eipName) {
        return provideExpressionLanguageCompletions(eipName, Set.of());
    }

    private List<AutocompletePopup.CompletionItem> provideExpressionLanguageCompletions(
            String eipName, Set<String> existingKeys) {
        EipModel model = catalog.eipModel(eipName);
        if (model == null) {
            return List.of();
        }
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (EipModel.EipOptionModel opt : model.getOptions()) {
            if (!"expression".equals(opt.getKind())) {
                continue;
            }
            List<String> oneOfs = opt.getOneOfs();
            if (oneOfs == null || oneOfs.isEmpty()) {
                continue;
            }
            if (oneOfs.stream().anyMatch(existingKeys::contains)) {
                continue;
            }
            for (String langName : oneOfs) {
                LanguageModel langModel = catalog.languageModel(langName);
                String desc = langModel != null
                        ? langModel.getTitle() + " - " + langModel.getDescription()
                        : langName;
                String label = langModel != null ? langModel.getLabel() : "language";
                boolean dep = langModel != null && langModel.isDeprecated();
                String depNote = langModel != null ? langModel.getDeprecationNote() : null;
                items.add(new AutocompletePopup.CompletionItem(
                        langName, desc, "language", null, dep, depNote, label));
            }
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private List<AutocompletePopup.CompletionItem> provideLanguageOptionCompletions(String langName) {
        return provideLanguageOptionCompletions(langName, Set.of());
    }

    private List<AutocompletePopup.CompletionItem> provideLanguageOptionCompletions(
            String langName, Set<String> existingKeys) {
        LanguageModel model = catalog.languageModel(langName);
        if (model == null) {
            return List.of();
        }
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (LanguageModel.LanguageOptionModel opt : model.getOptions()) {
            if (!"attribute".equals(opt.getKind()) && !"value".equals(opt.getKind())) {
                continue;
            }
            if (EIP_BOILERPLATE.contains(opt.getName())) {
                continue;
            }
            if (existingKeys.contains(opt.getName()) && !opt.isMultiValue()) {
                continue;
            }
            items.add(new AutocompletePopup.CompletionItem(
                    opt.getName(), opt.getDescription(), opt.getType(),
                    opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                    opt.getGroup(), opt.isRequired()));
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing((a, b) -> Boolean.compare(b.required(), a.required()))
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private List<AutocompletePopup.CompletionItem> provideEipValueCompletions(String eipName, String optionName) {
        return provideEipValueCompletions(eipName, optionName, List.of());
    }

    private List<AutocompletePopup.CompletionItem> provideEipValueCompletions(
            String eipName, String optionName, List<AutocompletePopup.CompletionItem> placeholders) {
        EipModel model = catalog.eipModel(eipName);
        if (model == null) {
            return List.of();
        }
        EipModel.EipOptionModel opt = null;
        for (EipModel.EipOptionModel o : model.getOptions()) {
            if (o.getName().equals(optionName)) {
                opt = o;
                break;
            }
        }
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        java.util.function.Predicate<String> valueFilter = null;
        if (opt != null) {
            List<String> enums = opt.getEnums();
            if (enums != null && !enums.isEmpty()) {
                Set<String> validValues = new HashSet<>();
                for (String value : enums) {
                    validValues.add(value.toLowerCase());
                    boolean isDefault = value.equals(String.valueOf(opt.getDefaultValue()));
                    items.add(new AutocompletePopup.CompletionItem(
                            value, opt.getDescription(), opt.getType(),
                            isDefault ? value : opt.getDefaultValue(),
                            false, null, opt.getGroup()));
                }
                valueFilter = v -> validValues.contains(v.toLowerCase());
            } else if ("boolean".equalsIgnoreCase(opt.getType())
                    || "java.lang.Boolean".equals(opt.getJavaType())) {
                valueFilter = v -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
                items.add(new AutocompletePopup.CompletionItem(
                        "true", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
                items.add(new AutocompletePopup.CompletionItem(
                        "false", opt.getDescription(), "boolean", opt.getDefaultValue(),
                        false, null, opt.getGroup()));
            }
        }
        for (AutocompletePopup.CompletionItem ph : placeholders) {
            if (valueFilter == null || (ph.description() != null && valueFilter.test(ph.description()))) {
                items.add(ph);
            }
        }
        return items;
    }

    private List<AutocompletePopup.CompletionItem> provideDataFormatNameCompletions(String eipName) {
        return provideDataFormatNameCompletions(eipName, Set.of());
    }

    private List<AutocompletePopup.CompletionItem> provideDataFormatNameCompletions(
            String eipName, Set<String> existingKeys) {
        EipModel model = catalog.eipModel(eipName);
        if (model == null) {
            return List.of();
        }
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (EipModel.EipOptionModel opt : model.getOptions()) {
            List<String> oneOfs = opt.getOneOfs();
            if (oneOfs == null || oneOfs.isEmpty()) {
                continue;
            }
            if (oneOfs.stream().anyMatch(existingKeys::contains)) {
                continue;
            }
            for (String dfName : oneOfs) {
                DataFormatModel dfModel = catalog.dataFormatModel(dfName);
                String desc = dfModel != null
                        ? dfModel.getTitle() + " - " + dfModel.getDescription()
                        : dfName;
                String label = dfModel != null ? dfModel.getLabel() : "dataformat";
                boolean dep = dfModel != null && dfModel.isDeprecated();
                String depNote = dfModel != null ? dfModel.getDeprecationNote() : null;
                items.add(new AutocompletePopup.CompletionItem(
                        dfName, desc, "dataformat", null, dep, depNote, label));
            }
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private List<AutocompletePopup.CompletionItem> provideDataFormatOptionCompletions(String dfName) {
        return provideDataFormatOptionCompletions(dfName, Set.of());
    }

    private List<AutocompletePopup.CompletionItem> provideDataFormatOptionCompletions(
            String dfName, Set<String> existingKeys) {
        DataFormatModel model = catalog.dataFormatModel(dfName);
        if (model == null) {
            return List.of();
        }
        List<AutocompletePopup.CompletionItem> items = new ArrayList<>();
        for (DataFormatModel.DataFormatOptionModel opt : model.getOptions()) {
            if (!"attribute".equals(opt.getKind())) {
                continue;
            }
            if (EIP_BOILERPLATE.contains(opt.getName())) {
                continue;
            }
            if (existingKeys.contains(opt.getName()) && !opt.isMultiValue()) {
                continue;
            }
            items.add(new AutocompletePopup.CompletionItem(
                    opt.getName(), opt.getDescription(), opt.getType(),
                    opt.getDefaultValue(), opt.isDeprecated(), opt.getDeprecationNote(),
                    opt.getGroup(), opt.isRequired()));
        }
        items.sort(Comparator.comparing(AutocompletePopup.CompletionItem::deprecated)
                .thenComparing(ci -> !ci.required())
                .thenComparing(AutocompletePopup.CompletionItem::key, String.CASE_INSENSITIVE_ORDER));
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
