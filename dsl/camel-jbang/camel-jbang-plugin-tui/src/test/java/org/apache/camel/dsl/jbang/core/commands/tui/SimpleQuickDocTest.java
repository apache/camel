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

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The quick doc of the simple function the cursor is on (CAMEL-25219).
 */
class SimpleQuickDocTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    void theFunctionTheCursorIsOn() {
        String line = "        .setBody(simple(\"Total ${abs(${header.price})} at ${date:now}\"))";
        int abs = line.indexOf("abs");
        int price = line.indexOf("price");
        int date = line.indexOf("date");

        assertThat(SimpleCompletionContext.functionAt(line, abs).text()).isEqualTo("abs(${header.price})");
        // the innermost of nested ones
        assertThat(SimpleCompletionContext.functionAt(line, price).text()).isEqualTo("header.price");
        assertThat(SimpleCompletionContext.functionAt(line, date).text()).isEqualTo("date:now");
        // on the ${ and on the }
        assertThat(SimpleCompletionContext.functionAt(line, date - 2).text()).isEqualTo("date:now");
        assertThat(SimpleCompletionContext.functionAt(line, line.indexOf("}\"")).text()).isEqualTo("date:now");
        // between them, and one being typed
        assertThat(SimpleCompletionContext.functionAt(line, line.indexOf(" at "))).isNull();
        assertThat(SimpleCompletionContext.functionAt("\"${bod", 6).text()).isEqualTo("bod");
    }

    @Test
    void writtenFunctionsAreFoundInTheCatalog() {
        assertThat(name("body")).isEqualTo("body");
        assertThat(name("body.length")).isEqualTo("body");
        assertThat(name("header.priority")).isEqualTo("header.name");
        assertThat(name("headerAs(foo,Integer)")).isEqualTo("headerAs(key,type)");
        assertThat(name("date:now-24h")).isEqualTo("date(command)");
        assertThat(name("bean:myBean.hello")).isEqualTo("bean(name.method)");
        assertThat(name("uuid(short)")).isEqualTo("uuid(type)");
        assertThat(name("uuid()")).isEqualTo("uuid(type)");
        // overloads by their number of arguments
        assertThat(name("a2a:emit('Searching')")).isEqualTo("a2a:emit(message)");
        assertThat(name("a2a:emit(WORKING,'Searching, still')")).isEqualTo("a2a:emit(state,message)");
        assertThat(SimpleQuickDoc.resolve(catalog, "noSuchThing")).isNull();
    }

    private static String name(String written) {
        return SimpleQuickDoc.resolve(catalog, written).getName();
    }

    @Test
    void theDocOfAFunction() {
        List<String> lines = List.of("    .setBody(simple(\"Created ${date:now-24h}\"))");
        List<String> doc = texts(SimpleQuickDoc.at(catalog, lines, 0, lines.get(0).indexOf("now")));
        assertThat(doc.get(0)).startsWith("date:command — Date — ");
        assertThat(doc).anyMatch(t -> t.startsWith("Example: ${date:"));
        assertThat(doc).anyMatch(t -> t.startsWith("command (required)"));
    }

    @Test
    void theDocOfAHeaderTheFileSets() {
        List<String> lines = List.of(
                "from(\"kafka:orders\")",
                "    .setHeader(\"priority\", constant(1))",
                "    .filter(simple(\"${header.priority} == 1 && ${header.CamelKafkaKey} != null\"))");
        String line = lines.get(2);
        assertThat(texts(SimpleQuickDoc.at(catalog, lines, 2, line.indexOf("priority"))))
                .contains("priority — Set in this file");
        assertThat(texts(SimpleQuickDoc.at(catalog, lines, 2, line.indexOf("CamelKafkaKey"))))
                .anyMatch(t -> t.startsWith("CamelKafkaKey — kafka — "));
    }

    @Test
    void theDocOfAnOperator() {
        List<String> lines = List.of("<simple>${body} contains 'Camel'</simple>");
        List<String> doc = texts(SimpleQuickDoc.at(catalog, lines, 0, lines.get(0).indexOf("tains")));
        assertThat(doc.get(0)).startsWith("contains — Contains — ");
        assertThat(doc).contains("Syntax: LHS contains RHS");

        // a word of a log message is no operator
        lines = List.of("    .log(\"Got contains\")");
        assertThat(SimpleQuickDoc.at(catalog, lines, 0, lines.get(0).indexOf("contains"))).isEmpty();
    }

    @Test
    void theViewListsTheFunctionsOfTheLine() {
        List<String> lines = List.of("      simple: \"${header.foo} at ${date:now:yyyyMMdd} by ${body}\"");
        assertThat(texts(SimpleQuickDoc.at(catalog, lines, 0, -1)))
                .containsExactly("Simple functions: header.name, date:command, body");
        assertThat(SimpleQuickDoc.at(catalog, List.of("    .to(\"log:x\")"), 0, -1)).isEmpty();
    }

    private static List<String> texts(List<SourceViewer.DocEntry> entries) {
        return entries.stream().map(SourceViewer.DocEntry::text).toList();
    }
}
