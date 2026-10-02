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

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The refactorings of the source editor (Ctrl+R) for Java and XML routes (CAMEL-25256).
 */
class RouteRefactoringsTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @Test
    void theEndpointUriOfALine() {
        String java = "            .to(\"kafka:orders?brokers=a:9092\")";
        RouteRefactorings.Value uri = RouteRefactorings.uri("java", java);
        assertThat(uri.text()).isEqualTo("kafka:orders?brokers=a:9092");
        assertThat(RouteRefactorings.replace("java", java, uri, "seda:orders"))
                .isEqualTo("            .to(\"seda:orders\")");

        String xml = "        <to id=\"x\" uri=\"kafka:orders?a=1&amp;b=2\"/>";
        uri = RouteRefactorings.uri("xml", xml);
        assertThat(uri.text()).isEqualTo("kafka:orders?a=1&b=2");
        assertThat(RouteRefactorings.replace("xml", xml, uri, "seda:x?a=1&b=2"))
                .isEqualTo("        <to id=\"x\" uri=\"seda:x?a=1&amp;b=2\"/>");

        assertThat(RouteRefactorings.uri("java", "            .log(\"Hello\")")).isNull();
        assertThat(RouteRefactorings.uri("xml", "        <log message=\"uri\"/>")).isNull();
    }

    @Test
    void theValueAtTheCursor() {
        String java = "            .setHeader(\"region\", constant(\"EU \\\"west\\\"\"))";
        RouteRefactorings.Value v = RouteRefactorings.valueAt("java", java, java.indexOf("EU"));
        assertThat(v.text()).isEqualTo("EU \"west\"");
        assertThat(RouteRefactorings.valueAt("java", java, java.indexOf("region")).text()).isEqualTo("region");
        // not in a string, or in a comment
        assertThat(RouteRefactorings.valueAt("java", java, java.indexOf("constant"))).isNull();
        assertThat(RouteRefactorings.valueAt("java", "  // .log(\"x\")", 12)).isNull();

        String xml = "        <log message=\"Got ${body}\" loggingLevel=\"WARN\"/>";
        assertThat(RouteRefactorings.valueAt("xml", xml, xml.indexOf("Got")).text()).isEqualTo("Got ${body}");
        // on the attribute name too
        assertThat(RouteRefactorings.valueAt("xml", xml, xml.indexOf("loggingLevel")).text()).isEqualTo("WARN");

        assertThat(RouteRefactorings.isExtractable(new RouteRefactorings.Value(0, 0, "{{x}}"))).isFalse();
        assertThat(RouteRefactorings.isExtractable(new RouteRefactorings.Value(0, 0, " "))).isFalse();
    }

    @Test
    void javaEscapesAreUndoneAndThePropertiesValueReadsBackTheSame() throws Exception {
        String java = "            .log(\"Hello\\n\\tCamel \\u00e9 \\101 C:\\\\temp \\\"x\\\"\")";
        RouteRefactorings.Value v = RouteRefactorings.valueAt("java", java, java.indexOf("Hello"));
        assertThat(v.text()).isEqualTo("Hello\n\tCamel \u00e9 A C:\\temp \"x\"");

        // written to application.properties and read back by java.util.Properties: the same value
        for (String value : List.of(v.text(), "C:\\temp", " leading space", "a=b: c")) {
            Properties props = new Properties();
            props.load(new StringReader("key=" + RouteRefactorings.propertiesValue(value) + "\n"));
            assertThat(props.getProperty("key")).isEqualTo(value);
        }
        assertThat(RouteRefactorings.javaEscape("a\"b\\c\nd")).isEqualTo("a\\\"b\\\\c\\nd");
    }

    @Test
    void aCommentOverLinesDoesNotEndTheBlock() {
        List<String> xml = List.of(
                "<routes>",
                "    <route>",
                "        <from uri=\"timer:tick\"/>",
                "        <split>",
                "            <simple>${body}</simple>",
                "            <!-- </split> written in a comment",
                "                 <to uri=\"direct:x\"/> </split> -->",
                "            <to uri=\"direct:item\"/>",
                "        </split>",
                "    </route>",
                "</routes>");
        assertThat(RouteRefactorings.xmlStep(xml, 3)).isEqualTo(new RouteRefactorings.Block(3, 8, "split", 8));
    }

    @Test
    void thePropertiesFileOfARouteFile() {
        assertThat(RouteRefactorings.propertiesFile(Path.of("/p/src/main/java/com/acme/MyRoute.java")))
                .isEqualTo(Path.of("/p/src/main/resources/application.properties"));
        assertThat(RouteRefactorings.propertiesFile(Path.of("/p/routes/orders.camel.xml")))
                .isEqualTo(Path.of("/p/routes/application.properties"));
    }

    private static final List<String> XML = List.of(
            "<routes xmlns=\"http://camel.apache.org/schema/xml-io\">",
            "    <route id=\"orders\">",
            "        <from uri=\"kafka:orders\"/>",
            "        <split>",
            "            <simple>${body}</simple>",
            "            <to uri=\"direct:item\"/>",
            "        </split>",
            "        <log message=\"done\"/>",
            "    </route>",
            "</routes>");

    @Test
    void anXmlStepBlock() {
        RouteRefactorings.Block split = RouteRefactorings.xmlStep(XML, 3);
        assertThat(split).isEqualTo(new RouteRefactorings.Block(3, 6, "split", 8));
        assertThat(RouteRefactorings.xmlStep(XML, 7)).isEqualTo(new RouteRefactorings.Block(7, 7, "log", 8));
        // the route, its from, an expression, and outside a route are no steps to extract
        assertThat(RouteRefactorings.xmlStep(XML, 1)).isNull();
        assertThat(RouteRefactorings.xmlStep(XML, 2)).isNull();
        assertThat(RouteRefactorings.xmlStep(XML, 4)).isNull();
        assertThat(RouteRefactorings.xmlStep(XML, 0)).isNull();

        List<String> replaced = RouteRefactorings.replaceWithTo(XML, split, "items");
        assertThat(replaced.get(3)).isEqualTo("        <to uri=\"direct:items\"/>");
        assertThat(replaced.get(4)).isEqualTo("        <log message=\"done\"/>");

        String file = RouteRefactorings.xmlRouteFile("items", XML.subList(3, 7), 8);
        assertThat(file).contains("<route id=\"items\">", "        <from uri=\"direct:items\"/>",
                "        <split>", "            <simple>${body}</simple>", "        </split>");
        assertThat(RouteRefactorings.addXmlRoute(file, "    <route id=\"more\"/>\n")).endsWith(
                "    <route id=\"more\"/>\n</routes>\n");
    }

    @Test
    void extractAJavaValueToAProperty() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("src/main/java/com/acme"));
        SourceViewer viewer = viewer(dir.resolve("MyRoute.java"), """
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .log("Hello Camel");
                    }
                }
                """, "java");
        cursorAt(viewer, 3, "Camel\");");
        refactor(viewer, "app.greeting");
        assertThat(line(viewer, 3)).isEqualTo("            .log(\"{{app.greeting}}\");");
        assertThat(Files.readString(tempDir.resolve("src/main/resources/application.properties")))
                .isEqualTo("app.greeting=Hello Camel\n");
    }

    @Test
    void extractAnXmlStepToANewFile() throws Exception {
        Path file = tempDir.resolve("orders.camel.xml");
        SourceViewer viewer = viewer(file, String.join("\n", XML) + "\n", "xml");
        cursorAt(viewer, 3, "<split>");
        // the menu: extract to new file first
        refactor(viewer, "items");
        assertThat(line(viewer, 3)).isEqualTo("        <to uri=\"direct:items\"/>");
        assertThat(Files.readString(file)).contains("<to uri=\"direct:items\"/>").doesNotContain("<split>");
        assertThat(Files.readString(tempDir.resolve("items.camel.xml"))).contains("<from uri=\"direct:items\"/>",
                "<split>", "<to uri=\"direct:item\"/>");
    }

    private SourceViewer viewer(Path file, String src, String dsl) throws Exception {
        Files.writeString(file, src, StandardCharsets.UTF_8);
        SourceViewer viewer = new SourceViewer();
        viewer.setUriCompletion(dsl);
        viewer.loadFile(file);
        viewer.enterEditMode();
        return viewer;
    }

    /** Ctrl+R, Enter on the first refactoring, the value typed, Enter. */
    private static void refactor(SourceViewer viewer, String value) {
        viewer.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.CTRL));
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        for (char ch : value.toCharArray()) {
            viewer.handleKeyEvent(KeyEvent.ofChar(ch, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
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

    private static String line(SourceViewer viewer, int row) {
        return viewer.editText().split("\n")[row];
    }
}
