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
 * Tab completion in the endpoint uris of a Java route file (CAMEL-25208).
 */
class JavaStringCompletionTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @Test
    void theStringTheCursorIsIn() {
        JavaStringContext c = JavaStringContext.at("        from(\"timer:tick?per\")", 28);
        assertThat(c.call()).isEqualTo("from");
        assertThat(c.before()).isEqualTo("timer:tick?per");
        assertThat(c.isEndpoint()).isTrue();
        assertThat(c.role()).isEqualTo("consumer");

        c = JavaStringContext.at("            .to( \"kaf", 21);
        assertThat(c.call()).isEqualTo("to");
        assertThat(c.before()).isEqualTo("kaf");
        assertThat(c.role()).isEqualTo("producer");

        // after the string, in another call, in a comment, not in a string
        assertThat(JavaStringContext.at("        from(\"timer:tick\").to(", 30)).isNull();
        // the second string of a concatenation is not the argument's start
        assertThat(JavaStringContext.at("        log.info(\"x\" + \"y", 25)).isNull();
        assertThat(JavaStringContext.at("        log.info(\"x", 19).call()).isEqualTo("info");
        assertThat(JavaStringContext.at("        // to(\"kaf", 18)).isNull();
        assertThat(JavaStringContext.at("        x = \"kaf", 17)).isNull();
        // an escaped quote stays in the string
        assertThat(JavaStringContext.at("        to(\"a\\\"b", 16).before()).isEqualTo("a\\\"b");
    }

    private SourceViewer viewer(String src) throws Exception {
        Path file = tempDir.resolve("MyRoute.java");
        Files.writeString(file, src, StandardCharsets.UTF_8);
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        SourceEditAssist assist = new SourceEditAssist(new MonitorContext(data, infraData));
        SourceViewer viewer = new SourceViewer();
        viewer.setAutocompleteProvider(assist::provideYamlKeyCompletions);
        viewer.setAutocompleteValueProvider(assist::provideYamlValueCompletions);
        viewer.setJavaStringCompletion(true);
        viewer.loadFile(file);
        viewer.enterEditMode();
        return viewer;
    }

    /** Puts the cursor on the line, before the given text at its end. */
    private static void cursorAt(SourceViewer viewer, int row, String tail) {
        for (int i = 0; i < row; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE));
        for (int i = 0; i < tail.length(); i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.LEFT, KeyModifiers.NONE));
        }
    }

    private static void type(SourceViewer viewer, String text) {
        for (char ch : text.toCharArray()) {
            viewer.handleKeyEvent(KeyEvent.ofChar(ch, KeyModifiers.NONE));
        }
    }

    private static String line(SourceViewer viewer, int row) {
        return viewer.editText().split("\n")[row];
    }

    @Test
    void theComponentOfAnEndpoint() throws Exception {
        SourceViewer viewer = viewer("""
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .to("sed");
                    }
                }
                """);
        cursorAt(viewer, 3, "\");");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
        // the popup filters as the prefix is typed further, then Enter takes the selected component
        type(viewer, "a");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertThat(line(viewer, 3)).isEqualTo("            .to(\"seda:\");");
    }

    @Test
    void anOptionAndItsValue() throws Exception {
        SourceViewer viewer = viewer("""
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .to("file:out?fileExi");
                    }
                }
                """);
        cursorAt(viewer, 3, "\");");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertThat(line(viewer, 3)).isEqualTo("            .to(\"file:out?fileExist=\");");

        // the cursor is after the = now: the values of the option
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
        type(viewer, "Appe");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertThat(line(viewer, 3)).isEqualTo("            .to(\"file:out?fileExist=Append\");");
    }

    @Test
    void aNameInTheMiddleOfTheUriIsReplacedWhole() throws Exception {
        // found in the live TUI: Tab after siz in ?siz=100 wrote size==100
        SourceViewer viewer = viewer("""
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .to("seda:orders?siz=100&blockWhenFull=true");
                    }
                }
                """);
        cursorAt(viewer, 3, "=100&blockWhenFull=true\");");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertThat(line(viewer, 3)).isEqualTo("            .to(\"seda:orders?size=100&blockWhenFull=true\");");

        // Tab with the cursor inside a name: the rest of it is replaced as well
        viewer = viewer("""
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .to("sedx:orders");
                    }
                }
                """);
        cursorAt(viewer, 3, "x:orders\");");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
        type(viewer, "da");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertThat(line(viewer, 3)).isEqualTo("            .to(\"seda:orders\");");
    }

    @Test
    void noCompletionOutsideAnEndpoint() throws Exception {
        SourceViewer viewer = viewer("""
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .log("sed");
                    }
                }
                """);
        String before = viewer.editText();
        cursorAt(viewer, 3, "\");");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        // no popup: Enter splits the line as in any editor
        assertThat(viewer.editText()).isNotEqualTo(before);
        assertThat(viewer.editText()).doesNotContain("seda:");
    }
}
