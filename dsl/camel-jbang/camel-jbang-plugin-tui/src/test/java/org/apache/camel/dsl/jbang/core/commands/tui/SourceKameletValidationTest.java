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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Kamelet checks in the Source tab (CAMEL-25411), as camel_validate_source runs them: the shape of a Kamelet file,
 * the kamelet: endpoints of a route against the project's own Kamelets, and the dependency notes that do not block a
 * save. The Kamelet is the one a local model wrote in the Kamelet side check of the benchmark.
 */
class SourceKameletValidationTest {

    private static final String KAMELET = """
            apiVersion: camel.apache.org/v1
            kind: Kamelet
            metadata:
              name: tag-order-action
              labels:
                camel.apache.org/kamelet.type: action
            spec:
              definition:
                title: Tag Order Action
                required:
                  - tag
                properties:
                  tag:
                    title: Tag
                    type: string
              dependencies:
                - "camel:kamelet"
              template:
                from:
                  uri: kamelet:source
                  steps:
                    - setBody:
                        expression:
                          simple:
                            expression: "${body} [{{tag}}]"
            """;

    private static final String ROUTE = """
            - route:
                from:
                  uri: timer:tick
                  steps:
                    - setBody:
                        constant: "Order ORD-5"
                    - to:
                        uri: kamelet:tag-order-action
                        parameters:
                          tagg: priority
            """;

    @TempDir
    Path tempDir;

    private final AtomicReference<String> lastNotification = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @Test
    void theShapeOfAKameletFileIsChecked() {
        Path file = tempDir.resolve("tag-order-action.kamelet.yaml");
        assertThat(SourceEditAssist.validateKamelets(file, KAMELET)).isEmpty();
        assertThat(SourceEditAssist.validateKamelets(file,
                KAMELET.replace("uri: kamelet:source", "uri: kamelet:tag-order-action")))
                .singleElement().asString().contains("entered from kamelet:source");
        String propertiesUnderSpec = KAMELET.replace("""
                    required:
                      - tag
                    properties:
                """, """
                  properties:
                """);
        assertThat(SourceEditAssist.validateKamelets(file, propertiesUnderSpec))
                .anySatisfy(m -> assertThat(m).contains("spec.properties is not a key of a Kamelet"));
    }

    @Test
    void theKameletOfARouteIsCheckedAgainstTheProjectFile() throws Exception {
        Files.writeString(tempDir.resolve("tag-order-action.kamelet.yaml"), KAMELET, StandardCharsets.UTF_8);
        List<String> errors = SourceEditAssist.validateKamelets(tempDir.resolve("orders.camel.yaml"), ROUTE);
        assertThat(errors).anySatisfy(m -> assertThat(m).contains("unknown property 'tagg'"));
        assertThat(errors).anySatisfy(m -> assertThat(m).contains("the required property tag is missing"));
        assertThat(SourceEditAssist.validateKamelets(tempDir.resolve("orders.camel.yaml"),
                ROUTE.replace("tagg:", "tag:"))).isEmpty();
    }

    @Test
    void theParametersOfAKameletEndpointCompleteItsProperties() throws Exception {
        Files.writeString(tempDir.resolve("tag-order-action.kamelet.yaml"), KAMELET, StandardCharsets.UTF_8);
        SourceEditAssist assist = new SourceEditAssist(
                new MonitorContext(
                        new AtomicReference<>(List.of()), new AtomicReference<>(List.of())));
        List<AutocompletePopup.CompletionItem> items
                = assist.provideYamlKeyCompletions("yaml:kamelet:producer|kamelet:tag-order-action", tempDir);
        // the property of the Kamelet first, then the options of the kamelet component
        assertThat(items.get(0).key()).isEqualTo("tag");
        assertThat(items.get(0).required()).isTrue();
        assertThat(items.get(0).group()).isEqualTo("kamelet tag-order-action");
        assertThat(items).extracting(AutocompletePopup.CompletionItem::key).contains("routeId", "timeout");
        // a property already given is not offered again
        assertThat(assist.provideYamlKeyCompletions("yaml:kamelet:producer:tag|kamelet:tag-order-action", tempDir))
                .extracting(AutocompletePopup.CompletionItem::key).doesNotContain("tag");
        // a Kamelet of the catalog, in the query of the uri
        assertThat(assist.provideYamlKeyCompletions("yaml:kamelet:consumer|kamelet:timer-source?period=1000", tempDir))
                .extracting(AutocompletePopup.CompletionItem::key).startsWith("message");
    }

    @Test
    void theOptionsPopupIsNamedAfterTheKamelet() {
        assertThat(SourceViewer.optionsTitle("kamelet", "kamelet:tag-order-action")).isEqualTo("tag-order-action options");
        assertThat(SourceViewer.optionsTitle("kamelet", "kamelet:timer-source?period=1000"))
                .isEqualTo("timer-source options");
        assertThat(SourceViewer.optionsTitle("kamelet", null)).isEqualTo("kamelet options");
        assertThat(SourceViewer.optionsTitle("timer", "timer:tick")).isEqualTo("timer options");
    }

    @Test
    void aDividerSetsTheKameletComponentOptionsApart() throws Exception {
        Files.writeString(tempDir.resolve("tag-order-action.kamelet.yaml"), KAMELET, StandardCharsets.UTF_8);
        SourceEditAssist assist = new SourceEditAssist(
                new MonitorContext(
                        new AtomicReference<>(List.of()), new AtomicReference<>(List.of())));
        List<AutocompletePopup.CompletionItem> items
                = assist.provideYamlKeyCompletions("yaml:kamelet:producer|kamelet:tag-order-action", tempDir);
        // tag above the divider, the options of the kamelet component below it
        assertThat(new AutocompletePopup(items, "", "").dividerAt()).isEqualTo(1);
        // filtering keeps the property of the Kamelet above: "t" matches tag and timeout
        AutocompletePopup filtered = new AutocompletePopup(items, "t", "t");
        assertThat(filtered.dividerAt()).isEqualTo(1);
        // the title counts the properties of the Kamelet, not the options of the kamelet component
        AutocompletePopup all = new AutocompletePopup(items, "", "");
        all.setTitlePrefix("tag-order-action options");
        assertThat(all.title()).isEqualTo(" tag-order-action options (1) ");
        // "rout" matches routeId only: none of the Kamelet's properties shown
        AutocompletePopup component = new AutocompletePopup(items, "rout", "rout");
        component.setTitlePrefix("tag-order-action options");
        assertThat(component.title()).isEqualTo(" tag-order-action options (0/1) ");
        // only the Kamelet's properties left, or none: no divider
        assertThat(new AutocompletePopup(items, "tag", "tag").dividerAt()).isEqualTo(-1);
        assertThat(new AutocompletePopup(items, "routeId", "routeId").dividerAt()).isEqualTo(-1);
        // a Kamelet without properties: only the options of the component, no divider
        String noProperties = KAMELET.replace("""
                    required:
                      - tag
                    properties:
                      tag:
                        title: Tag
                        type: string
                """, "").replace("{{tag}}", "tagged");
        Files.writeString(tempDir.resolve("tag-order-action.kamelet.yaml"), noProperties, StandardCharsets.UTF_8);
        List<AutocompletePopup.CompletionItem> none
                = assist.provideYamlKeyCompletions("yaml:kamelet:producer|kamelet:tag-order-action", tempDir);
        assertThat(none).isNotEmpty();
        assertThat(new AutocompletePopup(none, "", "").dividerAt()).isEqualTo(-1);
    }

    @Test
    void aKameletFileWithAProblemIsMarkedOnLoadAndNotSaved() throws Exception {
        Path file = tempDir.resolve("tag-order-action.kamelet.yaml");
        String self = KAMELET.replace("uri: kamelet:source", "uri: kamelet:tag-order-action");
        Files.writeString(file, self, StandardCharsets.UTF_8);
        SourceViewer viewer = viewer(file);
        viewer.loadFile(file);
        int fromLine = lineOf(self, "uri: kamelet:tag-order-action");
        assertThat(viewer.viewErrors()).containsOnlyKeys(fromLine);

        viewer.enterEditMode();
        appendSpaceToLine(viewer, 1);
        viewer.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.CTRL));
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(self);
        assertThat(viewer.inlineErrors()).containsOnlyKeys(fromLine);
    }

    @Test
    void anUnusedDependencyIsMarkedButTheFileIsSaved() throws Exception {
        Path file = tempDir.resolve("tag-order-action.kamelet.yaml");
        String timer = KAMELET.replace("    - \"camel:kamelet\"\n", "    - \"camel:kamelet\"\n    - \"camel:timer\"\n");
        Files.writeString(file, timer, StandardCharsets.UTF_8);
        SourceViewer viewer = viewer(file);
        viewer.loadFile(file);
        int timerLine = lineOf(timer, "\"camel:timer\"");
        assertThat(viewer.viewErrors()).containsOnlyKeys(timerLine);
        // a note, marked as a warning and counted apart from the errors
        assertThat(viewer.noteLines()).containsOnly(timerLine);

        viewer.enterEditMode();
        appendSpaceToLine(viewer, 1);
        viewer.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.CTRL));
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("kind: Kamelet ");
        assertThat(lastNotification.get()).startsWith("Saved: tag-order-action.kamelet.yaml with 1 Camel problem: ")
                .contains("camel:timer is not used by the template");
        assertThat(viewer.inlineErrors()).containsOnlyKeys(timerLine);
        assertThat(viewer.noteLines()).containsOnly(timerLine);
    }

    @Test
    void shiftF9RemovesAnUnusedDependency() throws Exception {
        Path file = tempDir.resolve("tag-order-action.kamelet.yaml");
        // camel:timer the only dependency, as the model wrote it: the dependencies: key goes with it
        String timer = KAMELET.replace("    - \"camel:kamelet\"\n", "    - \"camel:timer\"\n");
        Files.writeString(file, timer, StandardCharsets.UTF_8);
        SourceViewer viewer = viewer(file);
        viewer.loadFile(file);
        viewer.enterEditMode();
        for (int i = 0; i < lineOf(timer, "\"camel:timer\""); i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F9, KeyModifiers.SHIFT));
        assertThat(viewer.editText()).doesNotContain("camel:timer").doesNotContain("dependencies:")
                .contains("  template:");
        assertThat(lastNotification.get()).isEqualTo("Fixed: remove camel:timer");
    }

    @Test
    void anErrorIsNotANote() throws Exception {
        Path file = tempDir.resolve("tag-order-action.kamelet.yaml");
        String self = KAMELET.replace("uri: kamelet:source", "uri: kamelet:tag-order-action");
        Files.writeString(file, self, StandardCharsets.UTF_8);
        SourceViewer viewer = viewer(file);
        viewer.loadFile(file);
        assertThat(viewer.viewErrors()).isNotEmpty();
        assertThat(viewer.noteLines()).isEmpty();
    }

    @Test
    void theTemplateOfAKameletIsNamedAfterIt() {
        assertThat(SourceTab.kameletName(List.of(KAMELET.split("\n")))).isEqualTo("tag-order-action");
        assertThat(SourceTab.kameletName(List.of(ROUTE.split("\n")))).isNull();
    }

    private SourceViewer viewer(Path file) {
        SourceViewer viewer = new SourceViewer();
        viewer.setNotificationCallback((msg, error) -> lastNotification.set(msg));
        viewer.setKameletValidator(content -> SourceEditAssist.validateKamelets(file, content));
        viewer.setKameletNotes(content -> SourceEditAssist.kameletNotes(file, content));
        return viewer;
    }

    /** The line, from 0, that has the text. */
    private static int lineOf(String content, String text) {
        List<String> lines = List.of(content.split("\n"));
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    /** Moves to the line (from 0) and types a space at its end, a change that keeps the YAML as it was. */
    private static void appendSpaceToLine(SourceViewer viewer, int line) {
        for (int i = 0; i < line; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofChar(' ', KeyModifiers.NONE));
    }
}
