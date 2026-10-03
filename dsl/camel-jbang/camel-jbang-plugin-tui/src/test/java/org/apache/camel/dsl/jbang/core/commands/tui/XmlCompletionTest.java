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
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tab completion of the XML DSL in the Source editor (CAMEL-25240).
 */
class XmlCompletionTest {

    private static CamelCatalog catalog;
    private static XmlSchemaModel model;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void loadSchema() {
        catalog = new DefaultCamelCatalog();
        model = XmlCompletions.model(catalog);
    }

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    private static List<String> names(List<XmlSchemaModel.Child> children) {
        return children.stream().map(XmlSchemaModel.Child::name).toList();
    }

    @Test
    void theSchemaHasTheStructure() {
        assertThat(model).isNotNull();
        assertThat(names(model.children(model.elementType("route")))).contains("from", "to", "setBody", "choice");
        assertThat(names(model.children(model.elementType("choice")))).contains("when", "otherwise");
        // an expression goes inside setBody
        assertThat(names(model.children(model.elementType("setBody")))).contains("simple", "constant");
        assertThat(model.hasText(model.elementType("simple"))).isTrue();
        assertThat(model.children(model.elementType("to"))).isEmpty();

        // uri of to is required and documented in the catalog model, not in the schema
        List<XmlSchemaModel.Attribute> to = XmlCompletions.attributes(catalog, model, "to", model.elementType("to"));
        assertThat(to).anyMatch(a -> a.name().equals("uri") && a.required() && a.doc() != null);
        // inherited from the base types too (uri from sendDefinition, id and description from the processors)
        assertThat(to.stream().map(XmlSchemaModel.Attribute::name).toList()).contains("variableSend", "uri", "id",
                "description");
    }

    @Test
    void anElementWithItsTypeInline() {
        // allowableValues of a rest param has no named type in the schema
        String type = model.typeOf(List.of("rests", "rest", "get", "param", "allowableValues"));
        assertThat(type).isNotNull();
        assertThat(names(model.children(type))).containsExactly("value");
    }

    @Test
    void theElementsOpenAroundTheCursor() {
        List<String> lines = List.of(
                "<?xml version=\"1.0\"?>",
                "<!-- <route> in a comment is not open -->",
                "<camel:routes xmlns:camel=\"http://camel.apache.org/schema/xml-io\">",
                "  <route id=\"a\">",
                "    <from uri=\"timer:x?a=1&amp;b=2\"/>",
                "    <choice>",
                "      <when><simple><![CDATA[${body} > 1]]></simple>",
                "        <log message=\"a > b\"/>",
                "      </when>",
                "      ");
        XmlCompletionContext c = XmlCompletionContext.at(lines, 9, 6);
        assertThat(c.kind()).isEqualTo(XmlCompletionContext.Kind.ELEMENT);
        assertThat(c.path()).containsExactly("routes", "route", "choice");
        assertThat(c.open()).isFalse();
        assertThat(model.typeOf(c.path())).isEqualTo(model.elementType("choice"));
        // the when above is closed: a sibling, not open
        assertThat(c.siblings()).containsExactly("when");
    }

    @Test
    void theLanguagesGoOnceTheElementHasItsExpression() {
        List<String> before = keys(at("<route>", "  <choice>", "    <when>", "      "));
        assertThat(before).contains("simple", "to", "log");
        List<String> after = keys(at("<route>", "  <choice>", "    <when>", "      <simple>${body}</simple>", "      "));
        assertThat(after).contains("to", "log").doesNotContain("simple", "tokenize", "language");
    }

    @Test
    void anElementAttributeOrValueAtTheCursor() {
        XmlCompletionContext c = at("<routes>", "  <route>", "    <camel:setB");
        assertThat(c.kind()).isEqualTo(XmlCompletionContext.Kind.ELEMENT);
        assertThat(c.prefix()).isEqualTo("setB");
        assertThat(c.ns()).isEqualTo("camel:");
        assertThat(c.open()).isTrue();

        // a start tag over two lines
        c = at("<route>", "  <log message=\"hi\"", "       logg");
        assertThat(c.kind()).isEqualTo(XmlCompletionContext.Kind.ATTRIBUTE);
        assertThat(c.element()).isEqualTo("log");
        assertThat(c.prefix()).isEqualTo("logg");
        assertThat(c.given()).containsExactly("message");

        c = at("<route>", "  <log loggingLevel=\"WA");
        assertThat(c.kind()).isEqualTo(XmlCompletionContext.Kind.VALUE);
        assertThat(c.attribute()).isEqualTo("loggingLevel");
        assertThat(c.prefix()).isEqualTo("WA");

        // nothing in text, a comment, an end tag, or right after a value
        assertThat(at("<route>", "  <log message=\"x\"/> hello")).isNull();
        assertThat(at("<route>", "  <!-- <to ")).isNull();
        assertThat(at("<route>", "  </rou")).isNull();
        assertThat(at("<route>", "  <log message=\"x\"")).isNull();
    }

    private static XmlCompletionContext at(String... lines) {
        int row = lines.length - 1;
        return XmlCompletionContext.at(List.of(lines), row, lines[row].length());
    }

    @Test
    void snippets() {
        assertThat(snippet("to", false)).isEqualTo("<to uri=\"|\"/>");
        assertThat(snippet("split", true)).isEqualTo("split>|</split>");
        assertThat(snippet("simple", false)).isEqualTo("<simple>|</simple>");
        assertThat(snippet("stop", false)).isEqualTo("<stop/>|");
        assertThat(XmlCompletions.elementSnippet(catalog, model, child("to"), true, "camel:")).contains("/>");
        assertThat(XmlCompletions.elementSnippet(catalog, model, child("split"), false, "camel:"))
                .isEqualTo("<camel:split>" + XmlCompletions.CARET + "</camel:split>");
    }

    private static String snippet(String name, boolean open) {
        return XmlCompletions.elementSnippet(catalog, model, child(name), open, "").replace(XmlCompletions.CARET, '|');
    }

    private static XmlSchemaModel.Child child(String name) {
        return new XmlSchemaModel.Child(name, model.elementType(name), model.elementDoc(name));
    }

    @Test
    void valuesOfAnAttribute() {
        List<String> levels = keys(at("<route>", "  <log loggingLevel=\""));
        assertThat(levels).contains("INFO", "WARN", "ERROR");
        assertThat(keys(at("<route>", "  <split parallelProcessing=\""))).containsExactly("true", "false");
        // an option of a language
        assertThat(keys(at("<route>", "  <setBody>", "    <simple trim=\""))).containsExactly("true", "false");
    }

    private static List<String> keys(XmlCompletionContext c) {
        return XmlCompletions.provide(catalog, c, List::of).stream().map(AutocompletePopup.CompletionItem::key)
                .toList();
    }

    @Test
    void anElementThenALanguageInIt() throws Exception {
        SourceViewer viewer = viewer("""
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick"/>
                       \s
                    </route>
                </routes>
                """);
        cursorAt(viewer, 3, "");
        tab(viewer);
        type(viewer, "setBody");
        enter(viewer);
        assertThat(line(viewer, 3)).isEqualTo("        <setBody></setBody>");

        // the cursor is inside the new element: the languages go there
        tab(viewer);
        type(viewer, "simple");
        enter(viewer);
        assertThat(line(viewer, 3)).isEqualTo("        <setBody><simple></simple></setBody>");
        type(viewer, "Hello");
        assertThat(line(viewer, 3)).isEqualTo("        <setBody><simple>Hello</simple></setBody>");
    }

    @Test
    void anAttributeThenItsValue() throws Exception {
        SourceViewer viewer = viewer("""
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick"/>
                        <log message="hi" logg/>
                    </route>
                </routes>
                """);
        cursorAt(viewer, 3, "/>");
        tab(viewer);
        enter(viewer);
        assertThat(line(viewer, 3)).isEqualTo("        <log message=\"hi\" loggingLevel=\"\"/>");

        // the values open right away in the quotes
        type(viewer, "WA");
        enter(viewer);
        assertThat(line(viewer, 3)).isEqualTo("        <log message=\"hi\" loggingLevel=\"WARN\"/>");
    }

    @Test
    void theUriAttributeStaysWithTheUriCompletion() throws Exception {
        SourceViewer viewer = viewer("""
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick"/>
                        <to uri="sed"/>
                    </route>
                </routes>
                """);
        cursorAt(viewer, 3, "\"/>");
        tab(viewer);
        type(viewer, "a");
        enter(viewer);
        assertThat(line(viewer, 3)).isEqualTo("        <to uri=\"seda:\"/>");
    }

    private SourceViewer viewer(String src) throws Exception {
        Path file = tempDir.resolve("routes.xml");
        Files.writeString(file, src, StandardCharsets.UTF_8);
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        SourceEditAssist assist = new SourceEditAssist(new MonitorContext(data, infraData));
        SourceViewer viewer = new SourceViewer();
        viewer.setAutocompleteProvider(assist::provideYamlKeyCompletions);
        viewer.setAutocompleteValueProvider(assist::provideYamlValueCompletions);
        viewer.setUriCompletion("xml");
        viewer.setSimpleCompletion(assist::provideSimpleCompletions);
        viewer.setXmlCompletion((c, lines) -> XmlCompletions.provide(catalog, c, List::of));
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

    private static void tab(SourceViewer viewer) {
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.NONE));
    }

    private static void enter(SourceViewer viewer) {
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
    }

    private static void type(SourceViewer viewer, String text) {
        for (char ch : text.toCharArray()) {
            viewer.handleKeyEvent(KeyEvent.ofChar(ch, KeyModifiers.NONE));
        }
    }

    private static String line(SourceViewer viewer, int row) {
        return viewer.editText().split("\n")[row];
    }
}
