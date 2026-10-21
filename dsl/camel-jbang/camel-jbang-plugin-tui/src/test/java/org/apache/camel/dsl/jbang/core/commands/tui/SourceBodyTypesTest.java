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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.dsl.jbang.core.commands.tui.SourceLiveColumn.LiveLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The body types each step has been reached with, at the end of its line in the source view, with a ⚠ in the gutter of
 * a step reached with bodies of more than one kind (CAMEL-25897).
 */
class SourceBodyTypesTest {

    private static final String ROUTE = """
            - route:
                id: orders
                from:
                  uri: timer:orders
                  steps:
                    - unmarshal:
                        json: {}
                    - choice:
                        when:
                          - simple: "${body[priority]} == 1"
                            steps:
                              - marshal:
                                  json: {}
                    - to:
                        uri: log:outbox
            """;

    private static final int UNMARSHAL = 5;
    private static final int CHOICE = 7;
    private static final int TO = 13;

    @TempDir
    Path dir;

    private final Map<Integer, LiveLine> data = new HashMap<>();
    private SourceViewer viewer;
    private Path file;

    @BeforeEach
    void open() throws Exception {
        Theme.resetForTesting();
        file = Files.writeString(dir.resolve("orders.camel.yaml"), ROUTE);
        viewer = new SourceViewer();
        viewer.loadFile(file);
        viewer.liveColumn().setLiveRunData(path -> data);
        data.put(UNMARSHAL, line(List.of("org.apache.camel.converter.stream.ByteArrayInputStreamCache"), List.of("payload"),
                false));
        data.put(CHOICE, line(List.of("java.util.LinkedHashMap"), List.of("data"), false));
        data.put(TO, line(List.of("byte[]", "java.util.LinkedHashMap"), List.of("payload", "data"), true));
        viewer.liveColumn().refreshLiveLinesForTesting();
    }

    @Test
    void theBodyTypesOfAStepAtTheEndOfItsLineLinedUp() {
        String[] rows = render(120).split("\n");

        String unmarshal = row(rows, "- unmarshal:");
        String choice = row(rows, "- choice:");
        String to = row(rows, "- to:");
        assertThat(unmarshal).contains("ByteArrayInputStreamCache");
        assertThat(choice).contains("LinkedHashMap");
        assertThat(to).contains(BodyTypes.MARK + " byte[] | LinkedHashMap (payload and data)");
        // in a column of their own
        assertThat(unmarshal.indexOf("ByteArrayInputStreamCache"))
                .isEqualTo(choice.indexOf("LinkedHashMap"))
                .isEqualTo(to.indexOf(BodyTypes.MARK + " byte[]"));
        // the step reached with bodies of more than one kind has a ⚠ in the gutter, the others not
        assertThat(to.substring(0, 4)).contains(BodyTypes.MARK);
        assertThat(unmarshal.substring(0, 4)).doesNotContain(BodyTypes.MARK);
        // a line without a step has nothing
        assertThat(row(rows, "json: {}")).doesNotContain("ByteArrayInputStreamCache").doesNotContain("LinkedHashMap");
    }

    @Test
    void theTypesAreCutToTheRoomThereIs() {
        String to = row(render(56).split("\n"), "- to:");

        assertThat(to).contains(BodyTypes.MARK + " byte[]").doesNotContain("(payload and data)");
    }

    @Test
    void nothingInThePlainMode() {
        viewer.handleKeyEvent(KeyEvent.ofChar('p'));

        String rendered = render(120);

        assertThat(rendered).contains("- unmarshal:").doesNotContain("ByteArrayInputStreamCache");
    }

    @Test
    void nothingWhileTheIntegrationRunsAnotherVersionOfTheFile() {
        data.put(TO, new LiveLine(10, 2, 0, List.of("byte[]", "java.util.LinkedHashMap"), List.of("payload", "data"), true));
        viewer.liveColumn().refreshLiveLinesForTesting();
        assertThat(viewer.liveColumn().failsAtRuntime(TO)).isTrue();

        // saved after the integration loaded it: the lines it ran may be others
        viewer.liveColumn().setStale(() -> true);
        String[] rows = render(120).split("\n");

        assertThat(row(rows, "- unmarshal:")).doesNotContain("ByteArrayInputStreamCache");
        String to = row(rows, "- to:");
        assertThat(to).doesNotContain("byte[]");
        assertThat(to.substring(0, 4)).doesNotContain(BodyTypes.MARK);
        // the live run data stays, dimmed, and no line is offered to fix as failing at runtime
        assertThat(to).contains("10");
        assertThat(viewer.liveColumn().failsAtRuntime(TO)).isFalse();
        assertThat(viewer.liveColumn().bodyTypesDoc(TO)).isNull();
    }

    @Test
    void theQuickDocumentationHasThemAll() {
        SourceViewer.DocEntry to = viewer.liveColumn().bodyTypesDoc(TO);
        assertThat(to.title()).isEqualTo("Body types");
        assertThat(to.text()).isEqualTo("byte[] | LinkedHashMap   " + BodyTypes.MARK
                                        + " payload and data: the body here depends on the path a message takes");

        SourceViewer.DocEntry unmarshal = viewer.liveColumn().bodyTypesDoc(UNMARSHAL);
        assertThat(unmarshal.title()).isEqualTo("Body types (payload)");
        assertThat(unmarshal.text()).isEqualTo("ByteArrayInputStreamCache");

        assertThat(viewer.liveColumn().bodyTypesDoc(UNMARSHAL + 1)).isNull();
    }

    @Test
    void theStepsOfALineTogether() {
        RouteInfo route = new RouteInfo();
        route.source = "MyRoute.java:10";
        // two steps on one line, each with a kind of its own: the line is not mixed
        route.processors.add(processor("MyRoute.java:12", List.of("java.lang.String"), List.of("payload")));
        route.processors.add(processor("MyRoute.java:12", List.of("java.util.LinkedHashMap"), List.of("data")));
        route.processors.add(processor("MyRoute.java:14", List.of("byte[]", "java.util.LinkedHashMap"),
                List.of("payload", "data")));
        route.processors.add(processor("MyRoute.java:15", List.of(), List.of()));

        Map<Integer, LiveLine> lines = LiveRunLines.of(List.of(route), "/work/MyRoute.java");

        assertThat(lines.get(11).bodyTypes()).containsExactly("java.lang.String", "java.util.LinkedHashMap");
        assertThat(lines.get(11).mixed()).isFalse();
        assertThat(lines.get(13).mixed()).isTrue();
        assertThat(lines.get(14).bodyTypes()).isEmpty();
        assertThat(lines.get(14)).isEqualTo(new LiveLine(0, 0, 0));
    }

    @Test
    void aSaveInTheEditorLeavesThemOutUntilTheIntegrationIsSeenToLoadIt() throws Exception {
        long now = System.currentTimeMillis();
        Files.setLastModifiedTime(file, FileTime.fromMillis(now - 3_600_000));
        IntegrationInfo info = new IntegrationInfo();
        info.uptime = now - 60_000;
        RouteInfo route = new RouteInfo();
        route.routeId = "orders";
        route.source = "orders.camel.yaml:1";
        route.state = "Started";
        route.uptime = "59s";
        info.routes.add(route);
        SourceRunState.Tracker tracker = new SourceRunState.Tracker();
        tracker.shown(file.toString(), false);
        tracker.refresh(info);
        assertThat(tracker.stale()).isFalse();
        assertThat(tracker.current().kind()).isEqualTo(SourceRunState.Kind.RUNNING);

        // edited, then saved
        tracker.shown(file.toString(), true);
        tracker.shown(file.toString(), false);
        assertThat(tracker.stale()).isTrue();
        assertThat(tracker.current()).isNull();

        // the next refresh knows again: saved now, after the integration loaded the file
        Files.setLastModifiedTime(file, FileTime.fromMillis(now));
        tracker.refresh(info);
        assertThat(tracker.current().kind()).isEqualTo(SourceRunState.Kind.NOT_RUNNING);
        assertThat(tracker.stale()).isTrue();
    }

    private String render(int width) {
        Rect area = new Rect(0, 0, width, 24);
        Buffer buffer = Buffer.empty(area);
        viewer.render(Frame.forTesting(buffer), area);
        return TuiTestHelper.bufferToString(buffer);
    }

    private static String row(String[] rows, String code) {
        for (String row : rows) {
            if (row.contains(code)) {
                return row;
            }
        }
        throw new AssertionError("No row with " + code);
    }

    private static LiveLine line(List<String> types, List<String> kinds, boolean mixed) {
        return new LiveLine(10, 0, 0, types, kinds, mixed);
    }

    private static ProcessorInfo processor(String source, List<String> bodyTypes, List<String> bodyKinds) {
        ProcessorInfo proc = new ProcessorInfo();
        proc.source = source;
        proc.bodyTypes = bodyTypes;
        proc.bodyKinds = bodyKinds;
        return proc;
    }
}
