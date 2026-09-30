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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Camel checks of Java and XML DSL route files in the Source tab (CAMEL-25208): marked on their lines, said on
 * save, never blocking it.
 */
class SourceRouteValidationTest {

    private static final String JAVA = """
            import org.apache.camel.builder.RouteBuilder;

            public class MyRoute extends RouteBuilder {
                @Override
                public void configure() throws Exception {
                    from("timer:tick?peroid=1000")
                        .to("seda:out");
                }
            }
            """;

    @TempDir
    Path tempDir;

    private final AtomicReference<String> lastNotification = new AtomicReference<>();
    private final AtomicReference<Boolean> lastNotificationError = new AtomicReference<>();

    private static SourceEditAssist assist() {
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        return new SourceEditAssist(new MonitorContext(data, infraData));
    }

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @Test
    void theEndpointsOfAJavaRouteAreChecked() {
        List<String> errors = assist().validateRoutes(Path.of("MyRoute.java"), JAVA);
        assertThat(errors).containsExactly("Line 6: timer: Unknown option 'peroid'. Did you mean: [period]");
        assertThat(assist().validateRoutes(Path.of("MyRoute.java"), JAVA.replace("peroid", "period"))).isEmpty();
    }

    @Test
    void theRoutesOfAnXmlFileAreChecked() {
        List<String> errors = assist().validateRoutes(Path.of("routes.xml"), """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick"/>
                        <filter>
                            <simple>${header.foo} ==</simple>
                            <to uri="seda:out"/>
                        </filter>
                    </route>
                </routes>
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Line 5: Simple syntax error");
    }

    @Test
    void theQuickDocOfAJavaLine() {
        String src = """
                import org.apache.camel.builder.RouteBuilder;

                public class MyRoute extends RouteBuilder {
                    @Override
                    public void configure() throws Exception {
                        from("timer:tick?period=1000")
                            .filter(simple("${header.foo} == 'bar'"))
                                .to("seda:out");
                    }
                }
                """;
        List<String> lines = List.of(src.split("\n"));
        SourceEditAssist assist = assist();
        List<SourceViewer.DocEntry> from = assist.provideRouteEditQuickDoc(Path.of("MyRoute.java"), lines, 5);
        assertThat(from).extracting(SourceViewer.DocEntry::text)
                .satisfies(t -> assertThat(t.get(0)).startsWith("Timer — "))
                .anySatisfy(t -> assertThat(t).startsWith("period=1000 — "));
        List<SourceViewer.DocEntry> filter = assist.provideRouteEditQuickDoc(Path.of("MyRoute.java"), lines, 6);
        assertThat(filter).extracting(SourceViewer.DocEntry::text)
                .containsExactly(filter.get(0).text(), "Simple predicate: ${header.foo} == 'bar'");
        assertThat(filter.get(0).text()).startsWith("Filter — ");
        // a line of plain Java has none
        assertThat(assist.provideRouteEditQuickDoc(Path.of("MyRoute.java"), lines, 2)).isEmpty();

        List<JsonObject> codeData = new ArrayList<>();
        for (String l : lines) {
            JsonObject jo = new JsonObject();
            jo.put("code", l);
            codeData.add(jo);
        }
        Map<Integer, List<SourceViewer.DocEntry>> all = assist.provideRouteQuickDocs(Path.of("MyRoute.java"), codeData);
        assertThat(all).containsKeys(5, 6, 7);
        assertThat(all.get(7).get(0).text()).startsWith("SEDA — ");
    }

    @Test
    void theQuickDocOfAnXmlLine() {
        List<String> lines = List.of("""
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick?period=1000"/>
                        <to uri="seda:out"/>
                    </route>
                </routes>
                """.split("\n"));
        List<SourceViewer.DocEntry> from = assist().provideRouteEditQuickDoc(Path.of("routes.xml"), lines, 2);
        assertThat(from.get(0).text()).startsWith("Timer — ");
    }

    @Test
    void aProblemIsMarkedAndSaidButTheFileIsSaved() throws Exception {
        Path file = tempDir.resolve("MyRoute.java");
        Files.writeString(file, JAVA.replace("peroid", "period"), StandardCharsets.UTF_8);
        SourceViewer viewer = new SourceViewer();
        viewer.setNotificationCallback((msg, error) -> {
            lastNotification.set(msg);
            lastNotificationError.set(error);
        });
        SourceEditAssist assist = assist();
        viewer.setRouteValidator(content -> assist.validateRoutes(file, content));
        viewer.loadFile(file);
        viewer.enterEditMode();

        // line 6: timer:tick?period=1000 becomes timer:tick?peroid=1000
        for (int i = 0; i < 5; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.HOME, KeyModifiers.NONE));
        String line = viewer.editText().split("\n")[5];
        int col = line.indexOf("period") + 3;
        for (int i = 0; i < col; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.RIGHT, KeyModifiers.NONE));
        }
        // "per|iod": delete the i, type it after the o
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DELETE, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.RIGHT, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofChar('i', KeyModifiers.NONE));
        assertThat(viewer.editText()).contains("timer:tick?peroid=1000");

        // save and keep editing: saved, the problem said and marked on its line
        viewer.handleKeyEvent(KeyEvent.ofChar('s', KeyModifiers.CTRL));
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("peroid=1000");
        assertThat(lastNotification.get()).startsWith("Saved: MyRoute.java with 1 Camel problem: Line 6: ");
        assertThat(lastNotificationError.get()).isTrue();
        assertThat(viewer.isEditMode()).isTrue();
        assertThat(viewer.inlineErrors()).containsOnlyKeys(5);

        // a clean save says nothing more than that: "peroi|d" back to "period"
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.BACKSPACE, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.LEFT, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofChar('i', KeyModifiers.NONE));
        assertThat(viewer.editText()).contains("timer:tick?period=1000");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.F5, KeyModifiers.NONE));
        assertThat(lastNotification.get()).isEqualTo("Saved: MyRoute.java");
        assertThat(lastNotificationError.get()).isFalse();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("period=1000");
    }
}
