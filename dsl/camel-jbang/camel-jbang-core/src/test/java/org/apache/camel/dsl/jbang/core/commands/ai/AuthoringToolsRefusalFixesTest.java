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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26758: the same refused content a third time is written with the fixes the refusals gave as exact text, when
 * every problem has one and the result is valid.
 */
class AuthoringToolsRefusalFixesTest {

    private static final String FILE = "orders.camel.yaml";

    /** r30n-5 contracts-rest-code-first step 1 (shortened): sent 15 times with the [] the refusal said to leave out. */
    private static final String BRACKETS = """
            - route:
                id: get-order-by-id
                from:
                  uri: direct:get-order-by-id
                  steps:
                    - setBody:
                        expression:
                          jsonpath:
                            expression: "$[?(@.orderId == '${header.id}')][]"
                            resultType: java.util.List
                    - log: "${body}"
            """;

    private static final String FIXED = BRACKETS.replace("')][]\"", "')]\"");

    private static JsonObject write(Path dir, String content) {
        return AuthoringTools.writeFile(new ToolContext(), dir, FILE, content, true);
    }

    @Test
    void theThirdSameWriteGetsTheFixTheRefusalsGave(@TempDir Path dir) throws IOException {
        JsonObject first = write(dir, BRACKETS);
        assertThat(first.getString("status")).as(String.valueOf(first)).isEqualTo("invalid");
        assertThat((JsonArray) first.get("errors")).singleElement().asString()
                .endsWith("write the line as expression: \"$[?(@.orderId == '${header.id}')]\"");
        // the second time the model still chooses its own fix
        JsonObject second = write(dir, BRACKETS);
        assertThat(second.getString("status")).isEqualTo("invalid");
        assertThat(second.getInteger("repeated")).isEqualTo(2);
        assertThat(dir.resolve(FILE)).doesNotExist();

        JsonObject third = write(dir, BRACKETS);
        assertThat(third.getString("status")).as(String.valueOf(third)).isEqualTo("created");
        assertThat((JsonArray) third.get("repaired")).containsExactly(
                "line 9: written as the refusals said: expression: \"$[?(@.orderId == '${header.id}')]\"");
        assertThat(third.getString("message")).startsWith("Written with these repairs of the YAML")
                .contains("The same content came a third time, so the write made the fixes the refusals gave");
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(FIXED);

        // the count starts again: the same content once more is a first refusal
        JsonObject again = write(dir, BRACKETS);
        assertThat(again.getString("status")).isEqualTo("invalid");
        assertThat(again.get("repeated")).isNull();
    }

    @Test
    void nothingIsWrittenWhenAProblemHasNoTextToWrite(@TempDir Path dir) {
        // an option the log EIP does not have: the refusal says so, but gives no line to write
        String content = BRACKETS.replace("    - log: \"${body}\"", "    - log:\n            message: \"${body}\"\n"
                                                                    + "            colour: red");
        JsonObject result = null;
        for (int i = 1; i <= 4; i++) {
            result = write(dir, content);
            assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
        }
        assertThat(result.getInteger("repeated")).isEqualTo(4);
        assertThat(String.valueOf(result.get("errors"))).contains("colour");
        assertThat(dir.resolve(FILE)).doesNotExist();
    }

    @Test
    void theThirdSameEditGetsTheFixTheRefusalsGave(@TempDir Path dir) throws IOException {
        assertThat(write(dir, FIXED).getString("status")).isEqualTo("created");
        String find = "')]\"\n                resultType: java.util.List";
        String replace = "')][]\"\n                resultType: java.util.Map";
        for (int i = 1; i <= 2; i++) {
            JsonObject refused = AuthoringTools.editFile(new ToolContext(), dir, FILE, find, replace);
            assertThat(refused.getString("status")).as(String.valueOf(refused)).isEqualTo("invalid");
        }
        JsonObject third = AuthoringTools.editFile(new ToolContext(), dir, FILE, find, replace);
        assertThat(third.getString("status")).as(String.valueOf(third)).isEqualTo("edited");
        assertThat((JsonArray) third.get("repaired")).singleElement().asString()
                .startsWith("line 9: written as the refusals said");
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(FIXED.replace("java.util.List", "java.util.Map"));
    }

    @Test
    void anUnclosedQuoteIsWrittenAsTheRefusalsSaid(@TempDir Path dir) throws IOException {
        // r28l-4, r29l-3 and r30l-2/5/6 route-filter-and-multicast step 1 sent this line 3 times each
        String content = """
                - route:
                    from:
                      uri: direct:orders
                      steps:
                        - filter:
                            simple: "${body[status]} == 'paid'
                            steps:
                              - to: log:paid
                """;
        for (int i = 1; i <= 2; i++) {
            JsonObject refused = write(dir, content);
            assertThat(refused.getString("status")).as(String.valueOf(refused)).isEqualTo("invalid");
            assertThat((JsonArray) refused.get("errors")).anySatisfy(e -> assertThat(String.valueOf(e))
                    .endsWith("write the line as simple: \"${body[status]} == 'paid'\""));
        }
        JsonObject third = write(dir, content);
        assertThat(third.getString("status")).as(String.valueOf(third)).isEqualTo("created");
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(content.replace("== 'paid'\n", "== 'paid'\"\n"));
    }

    @Test
    void aConstantWithSimpleIsWrittenAsTheRefusalsSaid(@TempDir Path dir) throws IOException {
        // CAMEL-26825, r31n-7 contracts-rest-code-first step 1 (shortened): sent 13 times with two problems, the []
        // whose refusal gave the line to write, and a constant with ${header.id} whose refusal gave none
        String content = BRACKETS.replace("        - log: \"${body}\"\n", """
                        - choice:
                            when:
                              - simple: "${body.size()} == 0"
                                steps:
                                  - setBody:
                                      expression:
                                        constant:
                                          expression: '{"error": "unknown order ${header.id}"}'
                        - log: "${body}"
                """);
        String[] lines = content.split("\n");
        for (int i = 1; i <= 2; i++) {
            JsonObject refused = write(dir, content);
            assertThat(refused.getString("status")).as(String.valueOf(refused)).isEqualTo("invalid");
            assertThat((JsonArray) refused.get("errors")).as(String.valueOf(refused)).hasSize(2)
                    .anySatisfy(e -> assertThat(String.valueOf(e))
                            .contains("constant: ${header.id} is not evaluated")
                            .endsWith(". Write lines 17-18 as:\n" + lines[16].replace("constant:", "simple:") + "\n"
                                      + lines[17]));
        }
        JsonObject third = write(dir, content);
        assertThat(third.getString("status")).as(String.valueOf(third)).isEqualTo("created");
        assertThat((JsonArray) third.get("repaired")).containsExactly(
                "line 9: written as the refusals said: expression: \"$[?(@.orderId == '${header.id}')]\"",
                "lines 17-18: written as the refusals said");
        assertThat(Files.readString(dir.resolve(FILE)))
                .isEqualTo(content.replace("')][]\"", "')]\"").replace("constant:\n", "simple:\n"));
    }

    @Test
    void aSecondEipInAStepIsWrittenAsTheRefusalsSaid(@TempDir Path dir) throws IOException {
        // CAMEL-26874, r32n-2 contracts-rest-code-first step 2 (shortened): setBody: in the column of the keys of
        // - setHeader:, sent 20 times as it was; the refusal said to start it as its own item, with no line to write
        String content = """
                - route:
                    id: get-order-by-id
                    from:
                      uri: direct:get-order-by-id
                      steps:
                        - choice:
                            when:
                              - simple: "${body} == null"
                                steps:
                                  - setHeader:
                                      name: CamelHttpResponseCode
                                      constant: "404"
                                    setBody:
                                      constant: '{"error": "unknown order"}'
                            otherwise:
                              steps:
                                - log: "order ${body}"
                """;
        for (int i = 1; i <= 2; i++) {
            JsonObject refused = write(dir, content);
            assertThat(refused.getString("status")).as(String.valueOf(refused)).isEqualTo("invalid");
            assertThat((JsonArray) refused.get("errors")).singleElement().asString()
                    .startsWith("Line 10: ")
                    .endsWith("(setBody: is another EIP: start it as its own item, - setBody:) Write line 13 as:\n"
                              + "                  - setBody:");
        }
        JsonObject third = write(dir, content);
        assertThat(third.getString("status")).as(String.valueOf(third)).isEqualTo("created");
        assertThat((JsonArray) third.get("repaired")).containsExactly("line 13: written as the refusals said");
        assertThat(third.getString("message"))
                .contains("The same content came a third time, so the write made the fixes the refusals gave");
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(content.replace("    setBody:", "  - setBody:"));
    }
}
