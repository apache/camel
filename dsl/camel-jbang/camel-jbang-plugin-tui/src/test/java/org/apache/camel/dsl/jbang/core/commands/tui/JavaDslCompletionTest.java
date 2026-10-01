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
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SplitDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tab completion of the Java DSL route chain in the Source editor (CAMEL-25241).
 */
class JavaDslCompletionTest {

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

    /** The context at the end of the last line. */
    private static JavaChainContext at(String... lines) {
        int row = lines.length - 1;
        return JavaChainContext.at(List.of(lines), row, lines[row].length());
    }

    private static List<String> keys(String... lines) {
        return JavaDslCompletions.provide(catalog, at(lines)).stream().map(AutocompletePopup.CompletionItem::key)
                .toList();
    }

    private static JavaDslCompletions.State state(String... lines) {
        return JavaDslCompletions.resolve(at(lines).calls());
    }

    @Test
    void theChainAtTheCursor() {
        JavaChainContext c = at(
                "    public void configure() {",
                "        from(\"timer:tick?period=1000\") // a comment with (",
                "            .split(body().tokenize(\",\")) /* and ) */",
                "            .str");
        assertThat(c.calls()).extracting(JavaChainContext.Call::name).containsExactly("from", "split");
        assertThat(c.calls().get(1).arguments()).isEqualTo(1);
        assertThat(c.prefix()).isEqualTo("str");

        // the commas of an anonymous class body and of type arguments are no argument separators
        c = at("this.from(\"a\").process(new Processor() { Map<String, String> m; void x() { f(1, 2); } }).");
        assertThat(c.calls()).extracting(JavaChainContext.Call::name).containsExactly("from", "process");
        assertThat(c.calls().get(1).arguments()).isEqualTo(1);

        // in an argument, in a string, in a comment, not after a dot
        assertThat(at("from(\"a\").split(body().")).isNull();
        assertThat(at("from(\"a.")).isNull();
        assertThat(at("from(\"a\") // .")).isNull();
        assertThat(at("from(\"a\") ")).isNull();
    }

    @Test
    void theOptionsOfTheEipComeFirst() {
        List<String> keys = keys("from(\"a\").split(body()).");
        assertThat(keys).contains("parallelProcessing", "streaming", "to", "log", "end");
        assertThat(keys.indexOf("streaming")).isLessThan(keys.indexOf("to"));
        // getters, internals and deprecated methods are no DSL to offer
        assertThat(keys).doesNotContain("getOutputs", "addOutput", "copyDefinition", "parallelAggregate");
    }

    @Test
    void theBlocksOfTheChain() {
        // to() returns the split itself: its options still apply; end() goes back to the route
        assertThat(state("from(\"a\").split(body()).to(\"b\").").current()).isEqualTo(SplitDefinition.class);
        assertThat(state("from(\"a\").split(body()).to(\"b\").end().").current()).isEqualTo(RouteDefinition.class);

        // when and otherwise stay on the choice; endChoice goes back to it from inside
        assertThat(keys("from(\"a\").choice().when(header(\"x\")).to(\"b\").")).contains("when", "otherwise",
                "endChoice");
        assertThat(state(
                "from(\"a\").choice().when(header(\"x\")).split(body()).endChoice().").current())
                .isEqualTo(ChoiceDefinition.class);
        // a choice in a choice is a new block: end() ends the inner one only
        assertThat(state(
                "from(\"a\").choice().when(header(\"x\")).choice().when(header(\"y\")).end().").blocks())
                .containsExactly(RouteDefinition.class, ChoiceDefinition.class);
        assertThat(keys("from(\"a\").to(\"b\").")).doesNotContain("endChoice", "endDoTry");
    }

    @Test
    void anEipThatIsNoBlockHasItsOptionsUntilTheNextEip() {
        assertThat(state("from(\"a\").recipientList(header(\"x\")).").current())
                .isEqualTo(RecipientListDefinition.class);
        assertThat(keys("from(\"a\").recipientList(header(\"x\")).")).contains("parallelProcessing", "to");
        // the next EIP goes into the route, and end() ends the recipient list
        assertThat(state("from(\"a\").recipientList(header(\"x\")).to(\"b\").").current())
                .isEqualTo(RouteDefinition.class);
        assertThat(state(
                "from(\"a\").split(body()).recipientList(header(\"x\")).end().").current())
                .isEqualTo(SplitDefinition.class);
    }

    @Test
    void clausesReturnToTheirEip() {
        // the expression clause of split: its languages, then back on the split
        assertThat(keys("from(\"a\").split().")).contains("simple", "header", "jsonpath").doesNotContain("to");
        assertThat(state("from(\"a\").split().simple(\"${body}\").").current()).isEqualTo(SplitDefinition.class);
        // a data format clause, its options stay on it
        assertThat(keys("from(\"a\").marshal().")).contains("json", "csv");
        assertThat(state("from(\"a\").marshal().variableSend(\"v\").json().").current())
                .isEqualTo(RouteDefinition.class);
        // an overload the text cannot tell apart is decided by what follows it
        assertThat(state("from(\"a\").recipientList(\",\").header(\"x\").").current())
                .isEqualTo(RecipientListDefinition.class);
        // a configuration ended by end()
        assertThat(keys("from(\"a\").circuitBreaker().resilience4jConfiguration().")).contains("end",
                "timeoutEnabled");
    }

    @Test
    void anEipIsInsertedWithTheCursorInItsArguments() throws Exception {
        SourceViewer viewer = viewer("""
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .spl
                    }
                }
                """);
        cursorAt(viewer, 3);
        tab(viewer);
        enter(viewer);
        assertThat(line(viewer, 3)).isEqualTo("            .split()");
        type(viewer, "body()");
        assertThat(line(viewer, 3)).isEqualTo("            .split(body())");
    }

    @Test
    void anOptionOfTheEip() throws Exception {
        SourceViewer viewer = viewer("""
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .split(body()).strea
                    }
                }
                """);
        cursorAt(viewer, 3);
        tab(viewer);
        enter(viewer);
        assertThat(line(viewer, 3)).isEqualTo("            .split(body()).streaming()");
    }

    @Test
    void theUriStringStaysWithTheUriCompletion() throws Exception {
        SourceViewer viewer = viewer("""
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick")
                            .to("sed");
                    }
                }
                """);
        for (int i = 0; i < 3; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE));
        for (int i = 0; i < 3; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.LEFT, KeyModifiers.NONE));
        }
        tab(viewer);
        type(viewer, "a");
        enter(viewer);
        assertThat(line(viewer, 3)).isEqualTo("            .to(\"seda:\");");
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
        viewer.setUriCompletion("java");
        viewer.setSimpleCompletion(assist::provideSimpleCompletions);
        viewer.setJavaCompletion(c -> JavaDslCompletions.provide(catalog, c));
        viewer.loadFile(file);
        viewer.enterEditMode();
        return viewer;
    }

    /** Puts the cursor at the end of the line. */
    private static void cursorAt(SourceViewer viewer, int row) {
        for (int i = 0; i < row; i++) {
            viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.DOWN, KeyModifiers.NONE));
        }
        viewer.handleKeyEvent(KeyEvent.ofKey(KeyCode.END, KeyModifiers.NONE));
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
