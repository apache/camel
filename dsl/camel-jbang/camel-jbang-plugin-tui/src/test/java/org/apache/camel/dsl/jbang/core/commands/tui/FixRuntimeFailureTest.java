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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fix with AI (Shift+F8) on a line that fails at runtime: in the Source view and editor, from the live run data and the
 * error registry (CAMEL-25358).
 */
class FixRuntimeFailureTest {

    private static final String ROUTE = """
            - route:
                id: orders
                from:
                  uri: timer:orders
                  steps:
                    - choice:
                        when:
                          - simple: "${header.amount} > 100"
                            steps:
                              - to:
                                  uri: direct:big-orders
                    - log:
                        message: "${body}"
            """;

    private static final int TO_LINE = 10; // 0-based line of uri: direct:big-orders

    private static final String EXCEPTION
            = "DirectConsumerNotAvailableException: No consumers available on endpoint: direct://big-orders";

    @TempDir
    Path tempDir;

    private final AtomicReference<String> problem = new AtomicReference<>();
    private final AtomicReference<String> failure = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    private SourceViewer viewer(Path file, long failed) {
        SourceViewer viewer = new SourceViewer();
        viewer.setAskAi(new MonitorContext.AskAi() {
            @Override
            public void fixProblem(Path f, int line, String p, String text) {
                problem.set(f.getFileName() + ":" + line + ": " + p);
            }

            @Override
            public void fixFailure(Path f, int line, String fail, String text) {
                failure.set(f.getFileName() + ":" + line + ": " + fail + " | " + text.strip());
            }
        });
        viewer.setLiveRunData(path -> Map.of(TO_LINE, new SourceViewer.LiveLine(5, failed, 2)));
        viewer.setLineFailures((path, line) -> line == TO_LINE ? EXCEPTION : null);
        viewer.loadFile(file);
        viewer.refreshLiveLinesForTesting();
        return viewer;
    }

    private Path routeFile() throws Exception {
        Path file = tempDir.resolve("orders.camel.yaml");
        Files.writeString(file, ROUTE, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void theQuestionToFixARuntimeFailure() {
        String q = AiFixPrompt.ofFailure(Path.of("/work/app"), Path.of("/work/app/orders.camel.yaml"), 11,
                "3 exchanges failed on this line, the last with " + EXCEPTION, "            uri: direct:big-orders");
        assertThat(q).startsWith("Exchanges fail at runtime on line 11 of orders.camel.yaml: 3 exchanges failed")
                .contains("The line is: uri: direct:big-orders")
                .contains("errors and log").contains("another route").contains("camel_edit_file");
    }

    @Test
    void shiftF8InTheViewAsksTheAiToFixTheFailingLine() throws Exception {
        SourceViewer viewer = viewer(routeFile(), 3);
        viewer.goToLine(TO_LINE);

        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));

        assertThat(failure.get()).isEqualTo("orders.camel.yaml:11: 3 exchanges failed on this line, the last with "
                                            + EXCEPTION + " | uri: direct:big-orders");
        assertThat(problem.get()).isNull();
    }

    @Test
    void shiftF8InTheEditorAsksTheAiToFixTheFailingLine() throws Exception {
        SourceViewer viewer = viewer(routeFile(), 1);
        viewer.enterEditMode();
        for (int i = 0; i < TO_LINE; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }

        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));

        assertThat(failure.get()).startsWith("orders.camel.yaml:11: 1 exchange failed on this line, the last with ");
        assertThat(viewer.isEditMode()).isFalse();
    }

    @Test
    void theHintShowsOnAFailingLineOfTheView() throws Exception {
        SourceViewer viewer = viewer(routeFile(), 3);
        viewer.goToLine(TO_LINE);
        assertThat(viewer.showsFixWithAiHint()).isTrue();

        viewer.goToLine(TO_LINE + 2);
        assertThat(viewer.showsFixWithAiHint()).isFalse();
    }

    @Test
    void aLineWithoutFailuresAsksNothing() throws Exception {
        SourceViewer viewer = viewer(routeFile(), 3);
        viewer.goToLine(TO_LINE + 2);

        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));

        assertThat(failure.get()).isNull();
        assertThat(problem.get()).isNull();
    }

    @Test
    void anEditedFileHasNoRuntimeFailures() throws Exception {
        SourceViewer viewer = viewer(routeFile(), 3);
        viewer.enterEditMode();
        viewer.handleKeyEvent(KeyEvent.ofChar('x', KeyModifiers.NONE));
        for (int i = 0; i < TO_LINE; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }

        // the live run data is of the saved file, so an unsaved edit does not use it
        assertThat(viewer.runtimeFailure(TO_LINE)).isNull();
    }

    @Test
    void theErrorOfALineIsFoundThroughItsProcessor() {
        RouteInfo route = new RouteInfo();
        route.routeId = "orders";
        route.source = "orders.camel.yaml:2";
        ProcessorInfo to = new ProcessorInfo();
        to.id = "to1";
        to.source = "file:/work/app/orders.camel.yaml:11";
        route.processors.add(to);
        ErrorInfo older = error("to1", 1000, "java.lang.IllegalStateException", "older");
        ErrorInfo last = error("to1", 2000, "org.apache.camel.component.direct.DirectConsumerNotAvailableException",
                "No consumers available on endpoint: direct://big-orders\nExchange[123]");
        ErrorInfo onRoute = error("unknown", 3000, "java.lang.RuntimeException", "on the route");

        assertThat(RuntimeFailures.sourceOf(List.of(route), last)).isEqualTo("file:/work/app/orders.camel.yaml:11");
        assertThat(RuntimeFailures.sourceOf(List.of(route), onRoute)).isEqualTo("orders.camel.yaml:2");
        assertThat(RuntimeFailures.lastFailure(List.of(route), List.of(older, last, onRoute),
                "/work/app/orders.camel.yaml", TO_LINE)).isEqualTo(EXCEPTION);
        assertThat(RuntimeFailures.lastFailure(List.of(route), List.of(older, last), "/work/app/other.camel.yaml",
                TO_LINE)).isNull();
        // the from line of the route counts the failure too: the last error in the file, with its line
        assertThat(RuntimeFailures.lastFailure(List.of(route), List.of(older, last), "/work/app/orders.camel.yaml", 3))
                .isEqualTo(EXCEPTION + " (at line 11)");

        last.repeatCount = 4;
        assertThat(RuntimeFailures.failureOf(last)).isEqualTo(EXCEPTION + " (4 times)");
    }

    @Test
    void theFileOfASourceLocationIsInTheSourceDirectory() throws Exception {
        Path routes = Files.createDirectories(tempDir.resolve("src/main/resources/camel"));
        Files.writeString(routes.resolve("orders.camel.yaml"), ROUTE, StandardCharsets.UTF_8);

        Path file = RuntimeFailures.fileOf(tempDir, "classpath:camel/orders.camel.yaml:11");

        assertThat(file).isEqualTo(routes.resolve("orders.camel.yaml"));
        assertThat(RuntimeFailures.lineText(file, TO_LINE)).contains("uri: direct:big-orders");
        assertThat(RuntimeFailures.fileOf(tempDir, "missing.camel.yaml:3")).isNull();
    }

    private static ErrorInfo error(String nodeId, long timestamp, String type, String message) {
        ErrorInfo e = new ErrorInfo();
        e.routeId = "orders";
        e.nodeId = nodeId;
        e.timestamp = timestamp;
        e.exceptionType = type;
        e.exceptionMessage = message;
        return e;
    }
}
