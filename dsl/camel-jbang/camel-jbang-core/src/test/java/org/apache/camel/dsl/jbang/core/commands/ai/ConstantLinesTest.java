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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26825: the refusal of a constant with ${...} ends with the lines to write, simple: in place of constant:.
 */
class ConstantLinesTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    private static List<String> validate(String yaml) {
        return SourceValidator.validate("orders.camel.yaml", yaml, catalog, null);
    }

    /** The line of the YAML, 1-based, as written. */
    private static String line(String yaml, int number) {
        return yaml.split("\n")[number - 1];
    }

    @Test
    void aConstantOnItsLineGetsTheLineToWrite() {
        String yaml = """
                - route:
                    from:
                      uri: direct:getStock
                      steps:
                        - setBody:
                            expression:
                              constant: '{"error": "unknown sku ${header.sku}"}'
                """;
        assertThat(validate(yaml)).singleElement().asString()
                .startsWith("Line 7: ")
                .contains("constant: ${header.sku} is not evaluated, a constant is used as written")
                .endsWith(". Write line 7 as:\n" + line(yaml, 7).replace("constant:", "simple:"));
    }

    @Test
    void aConstantWithItsValueBelowGetsAllItsLines() {
        // r31n-7 contracts-rest-code-first step 1 (shortened): sent 13 times as it is
        String yaml = """
                - route:
                    from:
                      uri: direct:get-order-by-id
                      steps:
                        - setBody:
                            expression:
                              constant:
                                expression: '{"error": "unknown order ${header.id}"}'
                        - setHeader:
                            name: CamelHttpResponseCode
                            constant: "404"
                """;
        assertThat(validate(yaml)).singleElement().asString()
                .contains("constant: ${header.id} is not evaluated")
                .endsWith(". Write lines 7-8 as:\n" + line(yaml, 7).replace("constant:", "simple:") + "\n" + line(yaml, 8));
    }

    @Test
    void theShortFormOfAHeaderGetsTheLineToWrite() {
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - setHeader:
                            name: greeting
                            constant: "Hello ${body}"
                """;
        assertThat(validate(yaml)).singleElement().asString()
                .endsWith(". Write line 7 as:\n" + line(yaml, 7).replace("constant:", "simple:"));
    }

    @Test
    void aListItemKeepsItsDash() {
        String[] lines = """
                when:
                  - constant: "${header.ok}"
                    steps:
                      - to: log:ok
                """.split("\n");
        assertThat(ConstantLines.linesToWrite(lines, 1)).isEqualTo(" Write line 2 as:\n  - simple: \"${header.ok}\"");
    }

    @Test
    void aLongValueGetsTheLineOfTheKeyOnly() {
        StringBuilder sb = new StringBuilder("constant: |\n");
        for (int i = 1; i <= 12; i++) {
            sb.append("  line ").append(i).append(" of order ${header.id}\n");
        }
        String[] lines = sb.toString().split("\n");
        assertThat(ConstantLines.linesToWrite(lines, 0)).isEqualTo(" Write line 1 as:\nsimple: |");
    }

    @Test
    void otherLinesAndMessagesAreLeftAsTheyAre() {
        String[] lines = """
                - setBody:
                    expression:
                      simple:
                        expression: "${header.id}"
                """.split("\n");
        assertThat(ConstantLines.linesToWrite(lines, 3)).isNull();
        assertThat(ConstantLines.withLines(List.of("Line 2: something else"), String.join("\n", lines)))
                .containsExactly("Line 2: something else");
    }
}
