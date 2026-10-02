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
 * Tab completion in the endpoint uris of Java and XML route files (CAMEL-25208).
 */
class EndpointUriCompletionTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @Test
    void theStringTheCursorIsIn() {
        EndpointUriContext c = EndpointUriContext.inJava("        from(\"timer:tick?per\")", 28);
        assertThat(c.call()).isEqualTo("from");
        assertThat(c.before()).isEqualTo("timer:tick?per");
        assertThat(c.isEndpoint()).isTrue();
        assertThat(c.role()).isEqualTo("consumer");

        c = EndpointUriContext.inJava("            .to( \"kaf", 21);
        assertThat(c.call()).isEqualTo("to");
        assertThat(c.before()).isEqualTo("kaf");
        assertThat(c.role()).isEqualTo("producer");

        // after the string, in another call, in a comment, not in a string
        assertThat(EndpointUriContext.inJava("        from(\"timer:tick\").to(", 30)).isNull();
        // the second string of a concatenation is not the argument's start
        assertThat(EndpointUriContext.inJava("        log.info(\"x\" + \"y", 25)).isNull();
        assertThat(EndpointUriContext.inJava("        log.info(\"x", 19).call()).isEqualTo("info");
        assertThat(EndpointUriContext.inJava("        // to(\"kaf", 18)).isNull();
        assertThat(EndpointUriContext.inJava("        x = \"kaf", 17)).isNull();
        // an escaped quote stays in the string
        assertThat(EndpointUriContext.inJava("        to(\"a\\\"b", 16).before()).isEqualTo("a\\\"b");
    }

    @Test
    void theUriAttributeTheCursorIsIn() {
        String line = "        <from uri=\"timer:tick?per\"/>";
        EndpointUriContext c = EndpointUriContext.inXml(line, line.length() - 3);
        assertThat(c.call()).isEqualTo("from");
        assertThat(c.before()).isEqualTo("timer:tick?per");
        assertThat(c.role()).isEqualTo("consumer");

        line = "<camel:to id=\"x\" uri='kafka:t?a=1&amp;b";
        c = EndpointUriContext.inXml(line, line.length());
        assertThat(c.call()).isEqualTo("to");
        assertThat(c.before()).isEqualTo("kafka:t?a=1&b");

        // the attribute closed before the cursor, another attribute, no element
        line = "        <to uri=\"seda:a\" id=\"x";
        assertThat(EndpointUriContext.inXml(line, line.length())).isNull();
        line = "        <to id=\"seda";
        assertThat(EndpointUriContext.inXml(line, line.length())).isNull();
        assertThat(EndpointUriContext.inXml("uri=\"seda", 9)).isNull();
    }

    @Test
    void anXmlUri() throws Exception {
        SourceViewer viewer = viewer("routes.xml", """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick?period=1000&amp;fixedR"/>
                        <to uri="sed"/>
                    </route>
                </routes>
                """);
        viewer.setUriCompletion("xml");
        cursorAt(viewer, 3, "\"/>");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
        type(viewer, "a");
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertThat(line(viewer, 3)).isEqualTo("        <to uri=\"seda:\"/>");

        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.UP, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE));
        for (int i = 0; i < 3; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.LEFT, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertThat(line(viewer, 2)).isEqualTo("        <from uri=\"timer:tick?period=1000&amp;fixedRate=\"/>");
    }

    private SourceViewer viewer(String src) throws Exception {
        return viewer("MyRoute.java", src);
    }

    private SourceViewer viewer(String fileName, String src) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, src, StandardCharsets.UTF_8);
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        SourceEditAssist assist = new SourceEditAssist(new MonitorContext(data, infraData));
        SourceViewer viewer = new SourceViewer();
        viewer.setAutocompleteProvider(assist::provideYamlKeyCompletions);
        viewer.setAutocompleteValueProvider(assist::provideYamlValueCompletions);
        viewer.setUriCompletion("java");
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
