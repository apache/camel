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
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.LanguageModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tab completion in the simple expressions of YAML, Java and XML route files (CAMEL-25219).
 */
class SimpleCompletionTest {

    private static CamelCatalog catalog;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    private static SimpleCompletionContext at(String line) {
        return SimpleCompletionContext.at(List.of(line), 0, line.length());
    }

    @Test
    void theFunctionTheCursorIsIn() {
        SimpleCompletionContext c = at("        .setBody(simple(\"Hello ${hea");
        assertThat(c.kind()).isEqualTo(SimpleCompletionContext.Kind.FUNCTION);
        assertThat(c.prefix()).isEqualTo("hea");

        assertThat(at("      simple: \"${").prefix()).isEmpty();
        // nested in the argument of another function
        assertThat(at("${abs(${bo").prefix()).isEqualTo("bo");
        // in the arguments of a function, after a closed one, with no ${ at all
        assertThat(at("${substring(1, ")).isNull();
        assertThat(at("${body}")).isNull();
        assertThat(at("        .log(\"Hello")).isNull();
    }

    @Test
    void theNameTheCursorIsIn() {
        SimpleCompletionContext c = at("${header.Camel");
        assertThat(c.kind()).isEqualTo(SimpleCompletionContext.Kind.HEADER);
        assertThat(c.prefix()).isEqualTo("Camel");
        assertThat(c.closing()).isEqualTo("}");

        c = at("${headers['ord");
        assertThat(c.kind()).isEqualTo(SimpleCompletionContext.Kind.HEADER);
        assertThat(c.prefix()).isEqualTo("ord");
        assertThat(c.closing()).isEqualTo("']}");

        assertThat(at("${exchangeProperty.").kind()).isEqualTo(SimpleCompletionContext.Kind.PROPERTY);
        assertThat(at("${variable.x").kind()).isEqualTo(SimpleCompletionContext.Kind.VARIABLE);
    }

    @Test
    void theArgumentOfAFunction() {
        SimpleCompletionContext c = at("${date:no");
        assertThat(c.kind()).isEqualTo(SimpleCompletionContext.Kind.DATE_COMMAND);
        assertThat(c.prefix()).isEqualTo("no");
        c = at("${date:now:yyyy");
        assertThat(c.kind()).isEqualTo(SimpleCompletionContext.Kind.DATE_PATTERN);
        assertThat(c.prefix()).isEqualTo("yyyy");
        assertThat(c.closing()).isEqualTo("}");

        assertThat(at("${date-with-timezone(no").kind()).isEqualTo(SimpleCompletionContext.Kind.DATE_COMMAND);
        c = at("${date-with-timezone(now:Eur");
        assertThat(c.kind()).isEqualTo(SimpleCompletionContext.Kind.TIME_ZONE);
        assertThat(c.closing()).isEqualTo(":");
        c = at("${date-with-timezone(now:UTC:HH");
        assertThat(c.kind()).isEqualTo(SimpleCompletionContext.Kind.DATE_PATTERN);
        assertThat(c.closing()).isEqualTo(")}");

        c = at("${bean:my");
        assertThat(c.kind()).isEqualTo(SimpleCompletionContext.Kind.BEAN);
        assertThat(c.closing()).isEmpty();
        c = at("${properties:app.");
        assertThat(c.kind()).isEqualTo(SimpleCompletionContext.Kind.PROPERTY_KEY);
        assertThat(c.prefix()).isEqualTo("app.");
        assertThat(at("${propertiesExist:x").kind()).isEqualTo(SimpleCompletionContext.Kind.PROPERTY_KEY);
    }

    @Test
    void theValuesOfTheArguments() {
        assertThat(keys(at("${date:"), SimpleCompletions.Project.NONE)).contains("now", "exchangeCreated", "header.");
        assertThat(keys(at("${date:now:"), SimpleCompletions.Project.NONE)).contains("yyyy-MM-dd", "HH:mm:ss");
        List<String> zones = keys(at("${date-with-timezone(now:"), SimpleCompletions.Project.NONE);
        assertThat(zones.get(0)).isEqualTo("UTC");
        assertThat(zones).contains("Europe/Paris");

        SimpleCompletions.Project project = new SimpleCompletions.Project(
                () -> List.of(new AutocompletePopup.CompletionItem("orderService", null, null, null, false, null, null)),
                () -> List.of(new AutocompletePopup.CompletionItem("app.name", "Camel", null, null, false, null, null)));
        assertThat(keys(at("${bean:"), project)).containsExactly("orderService");
        assertThat(keys(at("${properties:"), project)).containsExactly("app.name");
    }

    private static List<String> keys(SimpleCompletionContext c, SimpleCompletions.Project project) {
        return SimpleCompletions.provide(catalog, c, List.of(), project).stream()
                .map(AutocompletePopup.CompletionItem::key).toList();
    }

    @Test
    void anotherBeanKeepsTheMethod() throws Exception {
        SourceViewer viewer = viewer("route.camel.yaml", """
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "${bean:orderService.total}"
                """);
        viewer.setSimpleCompletion((c, lines) -> SimpleCompletions.provide(catalog, c, lines,
                new SimpleCompletions.Project(
                        () -> List.of(
                                new AutocompletePopup.CompletionItem("orderService", null, null, null, false, null, null),
                                new AutocompletePopup.CompletionItem("priceCalculator", null, null, null, false, null, null)),
                        List::of)));
        cursorAt(viewer, 4, "orderService.total}\"");
        tab(viewer);
        type(viewer, "pri");
        enter(viewer);
        assertThat(line(viewer, 4)).isEqualTo("          simple: \"${bean:priceCalculator.total}\"");
    }

    @Test
    void aDateFromItsFunctionToItsPattern() throws Exception {
        SourceViewer viewer = viewer("route.camel.yaml", """
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "Created ${dat"
                """);
        cursorAt(viewer, 4, "\"");
        tab(viewer);
        type(viewer, "e:");
        enter(viewer);
        // date: opens the commands right away
        type(viewer, "now");
        enter(viewer);
        assertThat(line(viewer, 4)).isEqualTo("          simple: \"Created ${date:now\"");
        type(viewer, ":");
        tab(viewer);
        type(viewer, "yyyy-MM-dd");
        enter(viewer);
        assertThat(line(viewer, 4)).isEqualTo("          simple: \"Created ${date:now:yyyy-MM-dd}\"");
    }

    @Test
    void theOperatorAfterAFunction() {
        SimpleCompletionContext c = at("        .filter(simple(\"${header.foo} con");
        assertThat(c.kind()).isEqualTo(SimpleCompletionContext.Kind.OPERATOR);
        assertThat(c.prefix()).isEqualTo("con");
        assertThat(c.owners()).startsWith("filter");

        // right after the } or past the string
        assertThat(at("${header.foo}")).isNull();
        assertThat(at("        .log(\"${body}\" + ")).isNull();
    }

    @Test
    void aPredicateIsToldByTheEipBeforeIt() {
        assertThat(predicate(List.of("        .filter(simple(\"${body} "))).isTrue();
        assertThat(predicate(List.of("        .choice().when().simple(\"${body} "))).isTrue();
        assertThat(predicate(List.of("        .setBody(simple(\"${body} "))).isFalse();
        assertThat(predicate(List.of("        .log(\"${body} "))).isFalse();
        // onException options
        assertThat(predicate(List.of("        onException(Exception.class).handled(simple(\"${body} "))).isTrue();
        // YAML and XML: the EIP is on a line above
        assertThat(predicate(List.of("- filter:", "    simple: \"${body} "))).isTrue();
        assertThat(predicate(List.of("- filter:", "    expression:", "      simple:", "        expression: \"${body} ")))
                .isTrue();
        assertThat(predicate(List.of("- log:", "    message: \"${body} "))).isFalse();
        assertThat(predicate(List.of("<when>", "  <simple>${body} "))).isTrue();
        assertThat(predicate(List.of("<setBody>", "  <simple>${body} "))).isFalse();
    }

    private static boolean predicate(List<String> lines) {
        int row = lines.size() - 1;
        SimpleCompletionContext c = SimpleCompletionContext.at(lines, row, lines.get(row).length());
        List<String> ops = SimpleCompletions.provide(catalog, c, lines).stream()
                .map(AutocompletePopup.CompletionItem::key).toList();
        boolean predicate = ops.contains("==");
        // the operators of an expression are never mixed with the ones of a predicate
        assertThat(ops.contains("~>")).isNotEqualTo(predicate);
        return predicate;
    }

    @Test
    void functionsAreInsertedInTheirSyntax() {
        LanguageModel simple = catalog.languageModel("simple");
        Map<String, AutocompletePopup.CompletionItem> items = SimpleCompletions.functions(simple).stream()
                .collect(Collectors.toMap(AutocompletePopup.CompletionItem::key, Function.identity()));

        assertThat(items.get("body").insertText()).isEqualTo("body}");
        assertThat(items.get("header.name").insertText()).isEqualTo("header.");
        assertThat(items.get("random(min,max)").insertText()).isEqualTo("random(");
        assertThat(items.get("uuid(type)").insertText()).isEqualTo("uuid()}");
        assertThat(items.get("properties:key:default").insertText()).isEqualTo("properties:");
        // the catalog names date(command) and bean(name.method), written ${date:now} and ${bean:foo}
        assertThat(items.get("date:command").insertText()).isEqualTo("date:");
        assertThat(items.get("bean:name.method").insertText()).isEqualTo("bean:");
        assertThat(items).doesNotContainKey("date(command)");
        // the details show the parameters and examples
        assertThat(items.get("random(min,max)").description()).contains("Parameters:", "min (required)", "Examples:");
    }

    @Test
    void functionsAreListedWithTheirWholeName() {
        AutocompletePopup popup = new AutocompletePopup(
                SimpleCompletions.functions(catalog.languageModel("simple")), "header.", "header.", false);
        popup.setFullKeys(true);
        Rect area = new Rect(0, 0, 120, 30);
        Buffer buffer = Buffer.empty(area);
        popup.render(Frame.forTesting(buffer), area, 2, 10);

        // not name, as a property key in its group would be shown
        assertThat(HealthTabRenderTest.bufferToString(buffer)).contains("   header.name ");
    }

    @Test
    void theHeaderNamesOfTheFile() {
        List<String> lines = List.of(
                "from(\"kafka:orders\")",
                "    .setHeader(\"orderId\", simple(\"${body}\"))",
                "    .filter(simple(\"${header.priority} == 'high'\"))",
                "- setHeader:",
                "    name: region",
                "<setHeader id=\"x\" name=\"customer\">");
        List<String> names = SimpleCompletions.names(catalog, lines, "Header", "header", true).stream()
                .map(AutocompletePopup.CompletionItem::key).toList();
        // the ones the file sets or reads first, then the ones of the kafka component
        assertThat(names).startsWith("orderId", "region", "customer", "priority");
        assertThat(names).contains("CamelKafkaKey");
    }

    @Test
    void aJavaFunction() throws Exception {
        SourceViewer viewer = viewer("MyRoute.java", """
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .setHeader("orderId", constant(1))
                            .filter(simple("${headerA"));
                    }
                }
                """);
        cursorAt(viewer, 4, "\"));");
        tab(viewer);
        enter(viewer);
        assertThat(line(viewer, 4)).isEqualTo("            .filter(simple(\"${headerAs(\"));");
    }

    @Test
    void aJavaHeaderThenAnOperator() throws Exception {
        // header. opens the header names right away
        SourceViewer v2 = viewer("MyRoute.java", """
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .setHeader("orderId", constant(1))
                            .filter(simple("${heade"));
                    }
                }
                """);
        cursorAt(v2, 4, "\"));");
        tab(v2);
        type(v2, "r.");
        enter(v2);
        assertThat(line(v2, 4)).isEqualTo("            .filter(simple(\"${header.\"));");
        type(v2, "ord");
        enter(v2);
        assertThat(line(v2, 4)).isEqualTo("            .filter(simple(\"${header.orderId}\"));");

        // then the operators of a predicate
        type(v2, " ");
        tab(v2);
        type(v2, "contains");
        enter(v2);
        assertThat(line(v2, 4)).isEqualTo("            .filter(simple(\"${header.orderId} contains \"));");
    }

    @Test
    void aYamlFunction() throws Exception {
        SourceViewer viewer = viewer("route.camel.yaml", """
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "Hello ${bod"
                """);
        cursorAt(viewer, 4, "\"");
        tab(viewer);
        enter(viewer);
        assertThat(line(viewer, 4)).isEqualTo("          simple: \"Hello ${body}\"");
    }

    @Test
    void anXmlFunctionKeepsTheClosingBrace() throws Exception {
        SourceViewer viewer = viewer("routes.xml", """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick"/>
                        <log message="Got ${exchangeI}"/>
                    </route>
                </routes>
                """);
        cursorAt(viewer, 3, "}\"/>");
        tab(viewer);
        enter(viewer);
        assertThat(line(viewer, 3)).isEqualTo("        <log message=\"Got ${exchangeId}\"/>");
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
        viewer.setSimpleCompletion(assist::provideSimpleCompletions);
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
