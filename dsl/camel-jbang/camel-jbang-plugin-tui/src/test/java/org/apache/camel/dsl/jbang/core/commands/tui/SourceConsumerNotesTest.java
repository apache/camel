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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A line of a YAML, Java or XML route that sends to a direct: or seda: endpoint no route of the project consumes is
 * marked as a note in the Source tab (CAMEL-24955): a warning that does not block the save, as the consuming route may
 * not be written yet. The other route files are read on a background thread, run here by hand.
 */
class SourceConsumerNotesTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    private static final String ROUTE = """
            - route:
                id: tick
                from:
                  uri: timer:tick
                  steps:
                    - to: direct:cheese
                    - to: direct:other
            """;

    private static final String OTHER = """
            - route:
                from:
                  uri: direct:other
                  steps:
                    - log: other
            """;

    @TempDir
    Path tempDir;

    private final Deque<Runnable> background = new ArrayDeque<>();
    private final ConsumedEndpoints consumed = new ConsumedEndpoints(background::add, Runnable::run);
    private final AtomicReference<String> lastNotification = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @Test
    void aDirectEndpointNoRouteConsumesIsANoteOnItsLine() throws Exception {
        Files.writeString(tempDir.resolve("other.camel.yaml"), OTHER, StandardCharsets.UTF_8);
        Path file = tempDir.resolve("r.camel.yaml");
        Files.writeString(file, ROUTE, StandardCharsets.UTF_8);
        SourceViewer viewer = viewer(file);
        viewer.loadFile(file);
        // the other route files are being read: nothing is said yet
        assertThat(viewer.viewErrors()).isEmpty();
        runBackground();
        int cheese = lineOf(ROUTE, "direct:cheese");
        assertThat(viewer.viewErrors()).containsOnlyKeys(cheese);
        assertThat(viewer.viewErrors().get(cheese))
                .startsWith("route tick: sends to direct:cheese, and no route consumes it");
        assertThat(viewer.noteLines()).containsOnly(cheese);

        // a note: the file is saved
        viewer.enterEditMode();
        assertThat(viewer.inlineErrors()).containsOnlyKeys(cheese);
        appendSpaceToLine(viewer, 1);
        viewer.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.CTRL));
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("id: tick ");
        assertThat(lastNotification.get()).startsWith("Saved: r.camel.yaml with 1 Camel problem: ")
                .contains("sends to direct:cheese");
    }

    @Test
    void theNoteGoesInTheEditorWhenAnotherFileConsumesTheEndpoint() throws Exception {
        Files.writeString(tempDir.resolve("other.camel.yaml"), OTHER, StandardCharsets.UTF_8);
        Path file = tempDir.resolve("r.camel.yaml");
        Files.writeString(file, ROUTE, StandardCharsets.UTF_8);
        SourceViewer viewer = viewer(file);
        viewer.loadFile(file);
        runBackground();
        viewer.enterEditMode();
        assertThat(viewer.noteLines()).containsOnly(lineOf(ROUTE, "direct:cheese"));
        // another file consumes it now, and a file was opened: the next check reads the files again
        Files.writeString(tempDir.resolve("cheese.camel.yaml"), OTHER.replace("direct:other", "direct:cheese"),
                StandardCharsets.UTF_8);
        consumed.expire();
        viewer.recheck(file);
        // until the read is done, what was read before
        assertThat(viewer.noteLines()).isNotEmpty();
        runBackground();
        // the editor checks its text again, without a change of it
        assertThat(viewer.inlineErrors()).isEmpty();
        assertThat(viewer.noteLines()).isEmpty();
        assertThat(viewer.isDirty()).isFalse();
    }

    @Test
    void theNoteGoesWhenARouteConsumesTheEndpoint() throws Exception {
        Files.writeString(tempDir.resolve("other.camel.yaml"), OTHER, StandardCharsets.UTF_8);
        Path file = tempDir.resolve("r.camel.yaml");
        String withCheese = ROUTE + """
                - route:
                    from:
                      uri: direct:cheese
                      steps:
                        - log: cheese
                """;
        Files.writeString(file, withCheese, StandardCharsets.UTF_8);
        SourceViewer viewer = viewer(file);
        viewer.loadFile(file);
        runBackground();
        assertThat(viewer.viewErrors()).isEmpty();
        assertThat(viewer.noteLines()).isEmpty();
    }

    @Test
    void aJavaOrXmlRouteGetsTheNoteToo() throws Exception {
        Files.writeString(tempDir.resolve("other.camel.yaml"), OTHER, StandardCharsets.UTF_8);
        String java = """
                import org.apache.camel.builder.RouteBuilder;

                public class Tick extends RouteBuilder {
                    @Override
                    public void configure() {
                        from("timer:tick")
                            .to("direct:cheese")
                            .to("direct:other");
                    }
                }
                """;
        Path file = tempDir.resolve("Tick.java");
        Files.writeString(file, java, StandardCharsets.UTF_8);
        SourceViewer viewer = viewer(file);
        viewer.loadFile(file);
        runBackground();
        int cheese = lineOf(java, "direct:cheese");
        assertThat(viewer.viewErrors()).containsOnlyKeys(cheese);
        assertThat(viewer.viewErrors().get(cheese)).startsWith("sends to direct:cheese, and no route consumes it");
        assertThat(viewer.noteLines()).containsOnly(cheese);

        String xml = """
                <routes>
                    <route>
                        <from uri="timer:tick"/>
                        <to uri="direct:cheese"/>
                    </route>
                </routes>
                """;
        Path xmlFile = tempDir.resolve("tick.camel.xml");
        Files.writeString(xmlFile, xml, StandardCharsets.UTF_8);
        SourceViewer xmlViewer = viewer(xmlFile);
        xmlViewer.loadFile(xmlFile);
        runBackground();
        assertThat(xmlViewer.noteLines()).containsOnly(lineOf(xml, "direct:cheese"));
        // a note does not keep an XML file from being saved, as an error would
        xmlViewer.enterEditMode();
        appendSpaceToLine(xmlViewer, 1);
        xmlViewer.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.CTRL));
        assertThat(Files.readString(xmlFile, StandardCharsets.UTF_8)).contains("<route> ");
        assertThat(lastNotification.get()).startsWith("Saved: tick.camel.xml with 1 Camel problem: ");
    }

    @Test
    void theOtherFilesAreReadOnceAndAgainForAnotherFile() throws Exception {
        Files.writeString(tempDir.resolve("other.camel.yaml"), OTHER, StandardCharsets.UTF_8);
        ConsumedEndpoints consumed = new ConsumedEndpoints(background::add, Runnable::run);
        AtomicInteger changes = new AtomicInteger();
        Path file = tempDir.resolve("r.camel.yaml");
        assertThat(consumed.notes(file, tempDir, ROUTE, CATALOG, Map.of(), changes::incrementAndGet)).isEmpty();
        assertThat(background).hasSize(1);
        // asked again while reading: no second read
        consumed.notes(file, tempDir, ROUTE, CATALOG, Map.of(), changes::incrementAndGet);
        assertThat(background).hasSize(1);
        runBackground();
        assertThat(changes).hasValue(1);
        assertThat(consumed.notes(file, tempDir, ROUTE, CATALOG, Map.of(), changes::incrementAndGet))
                .singleElement().asString().startsWith("Line 6: route tick: sends to direct:cheese");
        // read a moment ago: kept
        assertThat(background).isEmpty();

        // the other file opened before the read of the first one is done: that read is followed by one for it
        Path other = tempDir.resolve("other.camel.yaml");
        consumed.notes(file, tempDir, ROUTE, CATALOG, Map.of(), changes::incrementAndGet);
        Files.writeString(tempDir.resolve("cheese.camel.yaml"), OTHER.replace("direct:other", "direct:cheese"),
                StandardCharsets.UTF_8);
        consumed.get(tempDir, "nowhere.camel.yaml", CATALOG, changes::incrementAndGet);
        assertThat(consumed.notes(other, tempDir, OTHER, CATALOG, Map.of(), changes::incrementAndGet)).isEmpty();
        runBackground();
        assertThat(consumed.get(tempDir, "other.camel.yaml", CATALOG, changes::incrementAndGet))
                .containsOnly("direct:cheese");
    }

    @Test
    void aFileOutsideTheProjectIsCheckedAgainstItsOwnDirectory() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path elsewhere = Files.createDirectory(tempDir.resolve("elsewhere"));
        Files.writeString(project.resolve("cheese.camel.yaml"), OTHER.replace("direct:other", "direct:cheese"),
                StandardCharsets.UTF_8);
        Files.writeString(elsewhere.resolve("other.camel.yaml"), OTHER, StandardCharsets.UTF_8);
        ConsumedEndpoints consumed = new ConsumedEndpoints(background::add, Runnable::run);
        Path file = elsewhere.resolve("r.camel.yaml");
        consumed.notes(file, project, ROUTE, CATALOG, Map.of(), () -> {
        });
        runBackground();
        assertThat(consumed.notes(file, project, ROUTE, CATALOG, Map.of(), () -> {
        })).singleElement().asString().contains("sends to direct:cheese");
    }

    private SourceViewer viewer(Path file) {
        SourceViewer viewer = new SourceViewer();
        viewer.setNotificationCallback((msg, error) -> lastNotification.set(msg));
        viewer.setNotes(content -> consumed.notes(file, tempDir, content, CATALOG, Map.of(),
                () -> viewer.recheck(file)));
        return viewer;
    }

    private void runBackground() {
        while (!background.isEmpty()) {
            background.poll().run();
        }
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
