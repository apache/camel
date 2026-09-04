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
import java.util.Set;

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

    @Test
    void findEnclosingComponentReturnsNullAfterDedentPastParameters() throws IOException {
        // reproduces the cursor being Shift+Tab-dedented from inside an endpoint's parameters:
        // block back down to the steps list-item level — findEnclosingComponent must stop
        // offering that endpoint's options once the cursor is no longer really inside them,
        // even though the blank line's own leftover whitespace still looks "deep"
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - to:",
                "          uri: file:xxx",
                "          parameters:",
                "            autoCreate: true",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();
        viewer.editState().moveCursorToStart();
        for (int i = 0; i < 6; i++) {
            viewer.editState().moveCursorDown();
        }
        viewer.editState().moveCursorToLineEnd();

        // sanity check: still inside the file endpoint's parameters here
        SourceViewer.YamlEndpointContext before = viewer.findEnclosingComponent(viewer.editState().cursorRow());
        assertThat(before).isNotNull();
        assertThat(before.component()).isEqualTo("file");

        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.ENTER, dev.tamboui.tui.event.KeyModifiers.NONE));
        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.TAB, dev.tamboui.tui.event.KeyModifiers.SHIFT));
        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.TAB, dev.tamboui.tui.event.KeyModifiers.SHIFT));

        SourceViewer.YamlEndpointContext after = viewer.findEnclosingComponent(viewer.editState().cursorRow());
        assertThat(after).isNull();
    }

    @Test
    void findEnclosingComponentDoesNotOfferToRecreateExistingParameters() throws IOException {
        // reproduces the cursor dedenting exactly one level — from inside parameters: down to
        // being a sibling of both uri: and parameters: — which must not be treated as "needs a
        // parameters: block" (parameters already exists there), or Tab ends up inserting a
        // second, duplicate "parameters:" key
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - to:",
                "          uri: file:xxx",
                "          parameters:",
                "            autoCreate: true",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();
        viewer.editState().moveCursorToStart();
        for (int i = 0; i < 6; i++) {
            viewer.editState().moveCursorDown();
        }
        viewer.editState().moveCursorToLineEnd();

        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.ENTER, dev.tamboui.tui.event.KeyModifiers.NONE));
        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.TAB, dev.tamboui.tui.event.KeyModifiers.SHIFT));

        // now a sibling of uri:/parameters: (indent 10), not inside parameters: children (12)
        assertThat(viewer.editState().cursorCol()).isEqualTo(10);

        SourceViewer.YamlEndpointContext ctx = viewer.findEnclosingComponent(viewer.editState().cursorRow());
        assertThat(ctx).isNull();
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

    // --- isEmptyValueLine (drives suppressing inline validation errors while a value is
    // still being typed, without hiding a genuinely wrong, non-empty value) ---

    @Test
    void isEmptyValueLineDetectsMissingValue() {
        assertThat(SourceViewer.isEmptyValueLine("            checksumFileAlgorithm:")).isTrue();
        assertThat(SourceViewer.isEmptyValueLine("            checksumFileAlgorithm: ")).isTrue();
        assertThat(SourceViewer.isEmptyValueLine("        - to:")).isTrue();
        assertThat(SourceViewer.isEmptyValueLine("")).isTrue();
    }

    @Test
    void isEmptyValueLineKeepsGenuinelyWrongValues() {
        // a typo in a Simple expression (or any other non-empty, wrong value) must not be
        // treated as "still being typed" — it should keep flagging immediately
        assertThat(SourceViewer.isEmptyValueLine("              expression: ${bdoy}")).isFalse();
        assertThat(SourceViewer.isEmptyValueLine("            checksumFileAlgorithm: NOT_REAL")).isFalse();
        assertThat(SourceViewer.isEmptyValueLine("        - to: file:xxx")).isFalse();
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

    // --- findParentYamlKey context detection (tree-driven) ---

    @Test
    void findParentYamlKeyInsideSplit() throws IOException {
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

        assertThat(viewer.findParentYamlKey(4)).isEqualTo("split");
    }

    @Test
    void findParentYamlKeyInsideExpression() throws IOException {
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

        assertThat(viewer.findParentYamlKey(5)).isEqualTo("expression");
    }

    @Test
    void findParentYamlKeyInsideSimpleUnderExpression() throws IOException {
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

        assertThat(viewer.findParentYamlKey(6)).isEqualTo("simple");
    }

    @Test
    void findParentYamlKeyOnEmptyLineUnderExpression() throws IOException {
        // reproduces a truly empty cursor line (no pre-typed indentation), which relies on the
        // preceding "expression:" line to derive the intended nesting level
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - setVariable:",
                "          name: cheese",
                "          expression:",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        assertThat(viewer.findParentYamlKey(6)).isEqualTo("expression");
    }

    @Test
    void findParentYamlKeyInsideFrom() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    from:",
                "      uri: timer",
                "      ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        assertThat(viewer.findParentYamlKey(3)).isEqualTo("from");
    }

    @Test
    void findParentYamlKeyInsideSteps() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        assertThat(viewer.findParentYamlKey(3)).isEqualTo("steps");
    }

    @Test
    void findParentYamlKeyOnDashLineInsideSteps() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        // "      - " is a list item starter inside steps
        assertThat(viewer.findParentYamlKey(3)).isEqualTo("steps");
        assertThat(viewer.findEnclosingComponent(3)).isNull();
    }

    @Test
    void findParentYamlKeyInsideRoute() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        assertThat(viewer.findParentYamlKey(1)).isEqualTo("route");
    }

    @Test
    void findParentYamlKeyInsideMarshal() throws IOException {
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

        assertThat(viewer.findParentYamlKey(4)).isEqualTo("marshal");
    }

    @Test
    void findParentYamlKeyInsideCsvUnderMarshal() throws IOException {
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

        assertThat(viewer.findParentYamlKey(5)).isEqualTo("csv");
    }

    @Test
    void findParentYamlKeyAtRootLevel() throws IOException {
        String yaml = String.join("\n",
                "",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();

        assertThat(viewer.findParentYamlKey(0)).isEqualTo("root");
    }

    @Test
    void findParentYamlKeyConvertsKebabCase() throws IOException {
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

        assertThat(viewer.findParentYamlKey(4)).isEqualTo("circuitBreaker");
    }

    // --- Insertion behavior ---

    @Test
    void insertStructuralKeyAddsNewlineAndIndent() throws IOException {
        // line "          " has 10 spaces — structural key should insert key:\n + 12 spaces
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
        // move cursor to the blank line (line 4)
        viewer.editState().moveCursorToStart();
        for (int i = 0; i < 4; i++) {
            viewer.editState().moveCursorDown();
        }

        AutocompletePopup.CompletionItem item = new AutocompletePopup.CompletionItem(
                "expression", "The expression", "object", null, false, null, "common", true);
        viewer.insertYamlCompletion(item, false, "          ");

        String result = viewer.editState().text();
        assertThat(result).contains("expression:\n            ");
    }

    @Test
    void insertScalarKeyAddsSpaceAfterColon() throws IOException {
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
        viewer.editState().moveCursorToStart();
        for (int i = 0; i < 4; i++) {
            viewer.editState().moveCursorDown();
        }

        AutocompletePopup.CompletionItem item = new AutocompletePopup.CompletionItem(
                "streaming", "Enable streaming", "boolean", "false", false, null, "common");
        viewer.insertYamlCompletion(item, false, "          ");

        String result = viewer.editState().text();
        assertThat(result).contains("streaming: ");
        assertThat(result).doesNotContain("streaming:\n");
    }

    @Test
    void insertStepsUnderCircuitBreakerNotUnderConfiguration() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    from:",
                "      uri: timer:tick",
                "      steps:",
                "        - circuitBreaker:",
                "            resilience4jConfiguration:",
                "              failureRateThreshold: 123",
                "            ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();
        // move cursor to the blank line (line 7) after failureRateThreshold
        viewer.editState().moveCursorToStart();
        for (int i = 0; i < 7; i++) {
            viewer.editState().moveCursorDown();
        }

        AutocompletePopup.CompletionItem item = new AutocompletePopup.CompletionItem(
                "steps", "Steps", "array", null, false, null, "common");
        viewer.insertYamlCompletion(item, false, "            ");

        String result = viewer.editState().text();
        // steps: should be at same indent as resilience4jConfiguration (child of circuitBreaker)
        assertThat(result).contains("            steps:");
        // NOT at deeper indent under resilience4jConfiguration
        assertThat(result).doesNotContain("              steps:");
    }

    @Test
    void insertStepsAfterEnterAddsListItemPrefix() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    from:",
                "      uri: timer:tick",
                "      steps:",
                "        - circuitBreaker:",
                "            steps:",
                "              ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();
        viewer.setListItemNodeChecker(key -> "steps".equals(key) || "root".equals(key));
        // move cursor to line 5 (steps:) and press Enter
        viewer.editState().moveCursorToStart();
        for (int i = 0; i < 5; i++) {
            viewer.editState().moveCursorDown();
        }
        viewer.editState().moveCursorToLineEnd();
        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.ENTER, dev.tamboui.tui.event.KeyModifiers.NONE));

        String result = viewer.editState().text();
        // after steps:, the new line should have "- " list item prefix
        assertThat(result).contains("            steps:\n              - ");
    }

    @Test
    void insertLanguageKeyUnderExpressionKeepsNestedIndent() throws IOException {
        // reproduces choosing "expression:" (object) then a language (e.g. "constant") for its
        // auto-inserted child line — the language must nest under expression, not become its sibling
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - setVariable:",
                "          name: cheese",
                "          ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();
        viewer.editState().moveCursorToStart();
        for (int i = 0; i < 5; i++) {
            viewer.editState().moveCursorDown();
        }

        AutocompletePopup.CompletionItem expressionItem = new AutocompletePopup.CompletionItem(
                "expression", "The expression", "object", null, false, null, "common", true);
        viewer.insertYamlCompletion(expressionItem, false, "          ");

        // cursor now sits on the auto-inserted (whitespace-only, but real) child line
        String childLine = viewer.editState().getLine(viewer.editState().cursorRow());
        AutocompletePopup.CompletionItem constantItem = new AutocompletePopup.CompletionItem(
                "constant", "A fixed value", "object", null, false, null, "language,core");
        viewer.insertYamlCompletion(constantItem, false, childLine);

        String result = viewer.editState().text();
        assertThat(result).contains("          expression:\n            constant:");
    }

    @Test
    void shiftTabMovesCursorToPreviousIndentStop() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - setVariable:",
                "          name: cheese",
                "          expression:",
                "            ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();
        viewer.editState().moveCursorToStart();
        for (int i = 0; i < 6; i++) {
            viewer.editState().moveCursorDown();
        }
        viewer.editState().moveCursorToLineEnd();
        assertThat(viewer.editState().cursorCol()).isEqualTo(12);
        String before = viewer.editState().text();

        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.TAB, dev.tamboui.tui.event.KeyModifiers.SHIFT));
        assertThat(viewer.editState().cursorRow()).isEqualTo(6);
        assertThat(viewer.editState().cursorCol()).isEqualTo(10);

        // second Shift+Tab dedents past the "name"/"expression" siblings (indent 10) to the
        // enclosing "- setVariable:" line's own indent
        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.TAB, dev.tamboui.tui.event.KeyModifiers.SHIFT));
        assertThat(viewer.editState().cursorCol()).isEqualTo(6);

        // buffer content must be untouched — this is cursor movement only
        assertThat(viewer.editState().text()).isEqualTo(before);
    }

    @Test
    void insertListItemUsesDedentedCursorColumnNotStaleLineLength() throws IOException {
        // reproduces inserting a "steps" EIP (e.g. "bean") as a list item after Shift+Tab
        // dedented the cursor within a longer, pre-existing whitespace-only line: the insert
        // must land at the cursor's column, not at the stale, deeper length of that line
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - setVariable:",
                "          name: cheese",
                "          expression:",
                "            ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();
        viewer.setListItemNodeChecker(key -> "steps".equals(key) || "root".equals(key));
        viewer.editState().moveCursorToStart();
        for (int i = 0; i < 6; i++) {
            viewer.editState().moveCursorDown();
        }
        viewer.editState().moveCursorToLineEnd();

        // dedent to the "- setVariable:" list-item indent (6), without touching the buffer
        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.TAB, dev.tamboui.tui.event.KeyModifiers.SHIFT));
        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.TAB, dev.tamboui.tui.event.KeyModifiers.SHIFT));
        assertThat(viewer.editState().cursorCol()).isEqualTo(6);
        String currentLine = viewer.editState().getLine(viewer.editState().cursorRow());
        assertThat(currentLine.length()).isEqualTo(12);

        AutocompletePopup.CompletionItem beanItem = new AutocompletePopup.CompletionItem(
                "bean", "Invokes a method on a bean", "object", null, false, null, "eip,endpoint");
        viewer.insertYamlCompletion(beanItem, false, currentLine, true, viewer.editState().cursorCol());

        String result = viewer.editState().text();
        assertThat(result).contains("      - bean:");
        assertThat(result).doesNotContain("            - bean:");
    }

    @Test
    void autoInsertedParametersBlockAlignsWithUriIndent() throws IOException {
        // reproduces the auto-inserted "parameters:" block landing one level too deep relative
        // to "uri:" (it must be a sibling), which then broke findEnclosingComponent's
        // uri-sibling lookup for every parameter added afterward
        String yaml = String.join("\n",
                "- route:",
                "    from:",
                "      uri: timer:tick",
                "      steps:",
                "        - to:",
                "            uri: file:xxx",
                "            ",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();
        viewer.setAutocompleteProvider(context -> List.of());
        viewer.editState().moveCursorToStart();
        for (int i = 0; i < 6; i++) {
            viewer.editState().moveCursorDown();
        }
        viewer.editState().moveCursorToLineEnd();

        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.TAB, dev.tamboui.tui.event.KeyModifiers.NONE));

        String result = viewer.editState().text();
        assertThat(result).contains("            uri: file:xxx\n            parameters:");
        assertThat(result).doesNotContain("              parameters:");
    }

    @Test
    void shiftTabIsNoOpWhenLineHasTypedContent() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:tick",
                "    steps:",
                "      - setVariable:",
                "          name: cheese",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        SourceViewer viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.enterEditMode();
        viewer.editState().moveCursorToStart();
        for (int i = 0; i < 4; i++) {
            viewer.editState().moveCursorDown();
        }
        viewer.editState().moveCursorToLineEnd();
        int colBefore = viewer.editState().cursorCol();
        String before = viewer.editState().text();

        viewer.handleKeyEvent(dev.tamboui.tui.event.KeyEvent.ofKey(
                dev.tamboui.tui.event.KeyCode.TAB, dev.tamboui.tui.event.KeyModifiers.SHIFT));

        assertThat(viewer.editState().cursorCol()).isEqualTo(colBefore);
        assertThat(viewer.editState().text()).isEqualTo(before);
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
