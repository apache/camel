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
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26874: the refusal of a second EIP in a step ends with the line to write, the EIP as its own - item; and
 * doCatch: at the column of doTry: is an option of doTry, with the lines to write two spaces further right.
 */
class IndentLinesTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    private static List<String> validate(String yaml) {
        return SourceValidator.validate("orders.camel.yaml", yaml, catalog, null);
    }

    @Test
    void aLogBelowTheEipGetsTheLineToWrite() {
        // r32n-7 route-enrich-and-dedupe step 2 (shortened): "Line 44: ... (log: is another EIP: ...)", sent 5 times
        String yaml = """
                - route:
                    id: incoming-orders
                    from:
                      uri: direct:orders
                      steps:
                        - setBody:
                            simple: "${body}"
                          log: "order ${body}"
                        - to: log:orders
                """;
        assertThat(validate(yaml)).singleElement().asString()
                .startsWith("Line 6: must have at most 1 properties")
                .endsWith("(log: is another EIP: start it as its own item, - log:) Write line 8 as:\n"
                          + "        - log: \"order ${body}\"");
    }

    @Test
    void aChoiceWithItsLinesGetsTheLineOfItsKey() {
        // the lines under choice: keep their columns: choice: is in the same column as its own - item
        String yaml = """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - setVariable:
                            name: order
                            simple: "${body}"
                          choice:
                            when:
                              - simple: "${variable.order} == null"
                                steps:
                                  - setHeader:
                                      name: CamelHttpResponseCode
                                      constant: "404"
                            otherwise:
                              steps:
                                - log: "order ${variable.order}"
                """;
        assertThat(validate(yaml)).singleElement().asString()
                .startsWith("Line 5: ")
                .endsWith("(choice: is another EIP: start it as its own item, - choice:) Write line 8 as:\n"
                          + "        - choice:");
    }

    @Test
    void aSetBodyBelowTheEipGetsTheLineToWrite() {
        // r32n-2 contracts-rest-code-first steps 1 and 2: "Line 52: ... (setBody: is another EIP: ...)", sent 14 and
        // 20 times
        String yaml = """
                - route:
                    from:
                      uri: direct:get-order-by-id
                      steps:
                        - setHeader:
                            name: CamelHttpResponseCode
                            constant: "404"
                          setBody:
                            constant: '{"error": "unknown order"}'
                """;
        assertThat(validate(yaml)).singleElement().asString()
                .startsWith("Line 5: ")
                .endsWith("(setBody: is another EIP: start it as its own item, - setBody:) Write line 8 as:\n"
                          + "        - setBody:");
    }

    @Test
    void twoOtherEipsInAStepGetNoLines() {
        // one line to write per refusal: the hint with a part for each key is left as it is
        String yaml = """
                - route:
                    from:
                      uri: direct:orders
                      steps:
                        - setBody:
                            simple: "${body}"
                          log: "order ${body}"
                          to: log:orders
                """;
        assertThat(validate(yaml)).singleElement().asString()
                .contains("log: is another EIP: start it as its own item, - log:; to: is another EIP")
                .doesNotContain("Write line");
    }

    @Test
    void aKeyThatIsNotInTheItemLeavesTheMessage() {
        String message
                = "Line 1: must have at most 1 properties (log: is another EIP: start it as its own item, - log:)";
        // log: further right is in the value of setBody:, not a key of the item
        String content = """
                - setBody:
                    simple: "${body}"
                    log: "order ${body}"
                - to: log:orders
                """;
        assertThat(IndentLines.withLines(List.of(message), content)).containsExactly(message);
        // the line of the message is not a - item
        assertThat(IndentLines.itemToWrite(content.split("\n"), 1, "log")).isNull();
    }

    /** r32n-4 route-batch-file step 2 (shortened): told that doCatch: is another EIP, sent 19 times as it was. */
    private static final String DO_CATCH_AT_DO_TRY = """
            - route:
                from:
                  uri: direct:batch
                  steps:
                    - doTry:
                        steps:
                          - to: file:out
                      doCatch:
                        - exception:
                            - java.io.IOException
                          steps:
                            - log: "failed ${exception.message}"
            """;

    @Test
    void doCatchAtTheColumnOfDoTryGetsTheLinesOfAnOption() {
        assertThat(validate(DO_CATCH_AT_DO_TRY)).singleElement().asString()
                .startsWith("Line 5: ")
                .contains("doCatch: lines up with doTry:, so it is read as a second key of the - item; it is an option"
                          + " of doTry:")
                .doesNotContain("another EIP")
                .endsWith(" Write lines 8-12 as:\n"
                          + "            doCatch:\n"
                          + "              - exception:\n"
                          + "                  - java.io.IOException\n"
                          + "                steps:\n"
                          + "                  - log: \"failed ${exception.message}\"");
    }

    @Test
    void theWriteIndentsDoCatchUnderDoTry(@TempDir Path dir) throws IOException {
        // an option at the column of its EIP has one reading, so the write repairs it (CAMEL-25532)
        JsonObject result = AuthoringTools.writeFile(new ToolContext(), dir, "batch.camel.yaml", DO_CATCH_AT_DO_TRY,
                true);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("created");
        assertThat((JsonArray) result.get("repaired"))
                .containsExactly("line 5: doCatch: indented two spaces, as an option of doTry:");
        assertThat(Files.readString(dir.resolve("batch.camel.yaml"))).isEqualTo("""
                - route:
                    from:
                      uri: direct:batch
                      steps:
                        - doTry:
                            steps:
                              - to: file:out
                            doCatch:
                              - exception:
                                  - java.io.IOException
                                steps:
                                  - log: "failed ${exception.message}"
                """);
    }
}
