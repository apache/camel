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

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25532: the shapes a model wrote in the benchmark that have one reading are repaired by the write, which then
 * goes ahead and says what it repaired; a snippet to find that the model wrote from memory is matched to the closest
 * lines of the file when that is certain.
 */
class AuthoringToolsLenientTest {

    private static final String FILE = "demo.camel.yaml";

    private static JsonObject write(Path dir, String content) {
        return AuthoringTools.writeFile(new ToolContext(), dir, FILE, content, true);
    }

    private static void assertRepairedAndValid(Path dir, JsonObject result, String... repairs) throws IOException {
        assertThat(result.getString("status")).as(String.valueOf(result)).isIn("created", "overwritten");
        JsonArray repaired = (JsonArray) result.get("repaired");
        assertThat(repaired).as(String.valueOf(result)).isNotNull();
        for (String r : repairs) {
            assertThat(repaired).anySatisfy(x -> assertThat(String.valueOf(x)).contains(r));
        }
        assertThat(result.getString("message")).startsWith("Written with these repairs of the YAML");
        String written = Files.readString(dir.resolve(FILE));
        List<String> errors = SourceValidator.validate(FILE, written, new DefaultCamelCatalog(), null, dir, null, false);
        assertThat(errors).as(written).isEmpty();
    }

    @Test
    void anOptionAtTheColumnOfItsEip(@TempDir Path dir) throws IOException {
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: timer:x
                      steps:
                        - split:
                            expression:
                              simple: "${body}"
                          steps:
                            - log:
                                message: "a"
                        - to:
                            uri: "sql:INSERT INTO t (id) VALUES (:#id)"
                          parameters:
                            allowNamedParameters: true
                """);
        assertRepairedAndValid(dir, result, "steps: indented two spaces, as an option of split:",
                "parameters: indented two spaces, as an option of to:");
        assertThat(Files.readString(dir.resolve(FILE)))
                .contains("            steps:\n              - log:")
                .contains("            parameters:\n              allowNamedParameters: true");
    }

    @Test
    void plainValuesListsAndCase(@TempDir Path dir) throws IOException {
        JsonObject result = write(dir, """
                - onException:
                    exception:
                      - java.lang.IllegalStateException
                    handled: true
                    steps:
                      - log:
                          message: "x"
                - route:
                    from:
                      uri: timer:x
                      steps:
                        - setBody:
                            constant: "{}"
                        - unmarshal:
                            json:
                              library: jackson
                        - setBody:
                            expression: "${body}"
                        - filter:
                            expression: constant true
                            steps:
                              - log:
                                  message: "f"
                        - choice:
                            when:
                              - expression:
                                  simple: "${body} != null"
                                steps:
                                  - log:
                                      message: "a"
                            otherwise:
                              - log:
                                  message: "b"
                """);
        assertRepairedAndValid(dir, result, "handled: written as constant:", "library: the value in the case",
                "expression: written as simple:", "expression: written as constant:", "otherwise: its steps put under steps:");
        assertThat(Files.readString(dir.resolve(FILE)))
                .contains("    handled:\n      constant:\n        expression: \"true\"")
                .contains("library: Jackson")
                .contains("            expression:\n              constant:\n                expression: \"true\"")
                .contains("otherwise:\n              steps:\n                - log:");
    }

    @Test
    void theNamesModelsUseForAnOption(@TempDir Path dir) throws IOException {
        JsonObject result = write(dir, """
                - onException:
                    exceptionType: java.lang.IllegalStateException
                    steps:
                      - log:
                          message: "x"
                - route:
                    errorHandler:
                      noErrorHandler: true
                    from:
                      uri: timer:x
                      steps:
                        - setHeader:
                            headerName: a
                            constant: "b"
                        - setProperty:
                            propertyName: c
                            expression:
                              constant:
                                expression: "d"
                        - log:
                            level: WARN
                            message: "x"
                        - setBody:
                            constant: "{}"
                        - unmarshal:
                            jackson: {}
                """);
        assertRepairedAndValid(dir, result, "exceptionType: written as exception:", "noErrorHandler: {}",
                "headerName: written as name:", "propertyName: written as name:", "level: written as loggingLevel:",
                "jackson: written as json: with library: Jackson");
        assertThat(Files.readString(dir.resolve(FILE)))
                .contains("    exception:\n      - java.lang.IllegalStateException")
                .contains("json:\n              library: Jackson");
    }

    @Test
    void entriesQuotesAndColumns(@TempDir Path dir) throws IOException {
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: timer:x
                      parameters:
                        period: {{welcome.period}}
                      steps:
                        - log:
                            message: "x"
                         - log:
                            message: "y"
                - onException:
                    exception:
                      - java.lang.IllegalStateException
                    steps:
                      - log:
                          message: "z"
                - rest:
                    path: /stock
                    post:
                      path: {sku}
                      to:
                        uri: direct:x
                - route:
                    from:
                      uri: direct:x
                      steps:
                        - log:
                            message: "x"
                             logName: foo
                """);
        assertRepairedAndValid(dir, result, "the placeholder quoted", "the list item moved to the column of the list",
                "moved above the first - route:", "post: written as a list item", "{sku} quoted",
                "logName moved to the column of the keys of its mapping");
        String written = Files.readString(dir.resolve(FILE));
        assertThat(written).startsWith("- onException:").contains("period: \"{{welcome.period}}\"")
                .contains("post:\n      - path: \"{sku}\"\n        to:");
    }

    @Test
    void aBareSimpleNameIsNotTakenForAConstant(@TempDir Path dir) {
        // r9L-2: split: {expression: body} written as constant "body" split the text "body"
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: timer:x
                      steps:
                        - split:
                            expression: body
                            steps:
                              - log:
                                  message: "x"
                """);
        assertThat(result.getString("status")).isEqualTo("invalid");
        assertThat(result.get("repaired")).isNull();
    }

    @Test
    void aConstantThatIsNotANameIsStillRepaired(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("stock.json"), "[]");
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: timer:x
                      steps:
                        - setBody:
                            expression: resource:file:stock.json
                        - setHeader:
                            name: CamelHttpResponseCode
                            expression: "404"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isIn("created", "overwritten");
        assertThat(Files.readString(dir.resolve(FILE)))
                .contains("constant:\n                expression: \"resource:file:stock.json\"")
                .contains("constant:\n                expression: \"404\"");
    }

    @Test
    void aListItemFarOffIsNotMoved(@TempDir Path dir) {
        // six columns off is another structure, not a slip
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: timer:x
                      steps:
                        - log:
                            message: "a"
                              - log:
                            message: "b"
                """);
        assertThat(result.getString("status")).isEqualTo("invalid");
        assertThat(result.get("repaired")).isNull();
        assertThat(result.get("repairable")).isNull();
    }

    @Test
    void aListItemTwoColumnsOffDoesNotHideTheProblemBehindIt(@TempDir Path dir) throws IOException {
        // r23l-3, r24l-2 and r24l-6 route-aggregator step 1: the log two columns off stopped the parser, so the steps:
        // at the route level was never reported, and the column alone was refused up to 19 times in a row
        String content = """
                - route:
                    id: shipments
                    from:
                      uri: direct:shipment
                    steps:
                      - aggregate:
                          aggregationStrategy: "#class:org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy"
                          correlationExpression:
                            simple:
                              expression: "${header.orderId}"
                          completionSize: 2
                        - log:
                            message: "Shipment complete"
                """;
        JsonObject result = write(dir, content);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
        assertThat(result.get("errors").toString()).contains("steps: goes under from:")
                .doesNotContain("every item of a list");
        assertThat(result.get("repairable").toString()).contains("the list item moved to the column of the list");
        assertThat(result.getString("message")).contains("the write repairs it by itself");
        assertThat(result.get("repaired")).isNull();

        // the steps: under from:, the log still two columns off: the write goes ahead with the repair
        JsonObject again = write(dir, """
                - route:
                    id: shipments
                    from:
                      uri: direct:shipment
                      steps:
                        - aggregate:
                            aggregationStrategy: "#class:org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy"
                            correlationExpression:
                              simple:
                                expression: "${header.orderId}"
                            completionSize: 2
                          - log:
                              message: "Shipment complete"
                """);
        assertRepairedAndValid(dir, again, "the list item moved to the column of the list");
    }

    @Test
    void aListItemTwoColumnsOffDoesNotHideABodyTypeProblem(@TempDir Path dir) {
        // r24n-2 route-enrich-and-dedupe step 2: the log after a pollEnrich two columns off, refused 16 times in a row,
        // hid that the log reads a field of the file that the pollEnrich put in a variable
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: file:orders
                      steps:
                        - unmarshal:
                            json: {}
                        - pollEnrich:
                            expression:
                              simple:
                                expression: "file:customers?fileName=${body[customer]}.json&noop=true"
                            timeout: 5000
                            variableReceive: customerData
                          - log:
                              message: "Order for ${variable.customerData[name]}"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
        assertThat(result.get("errors").toString()).contains("holds the file as it was read")
                .doesNotContain("every item of a list");
        assertThat(result.get("repairable").toString()).contains("the list item moved to the column of the list");
    }

    @Test
    void anEditKeepsWhatTheWriteRepairsByItself(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), """
                - route:
                    from:
                      uri: file:orders
                      steps:
                        - unmarshal:
                            json: {}
                        - pollEnrich:
                            expression:
                              simple:
                                expression: "file:customers?fileName=${body[customer]}.json&noop=true"
                            timeout: 5000
                            variableReceive: customerData
                """);
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE, """
                            variableReceive: customerData
                """, """
                            variableReceive: customerData
                          - log:
                              message: "Order for ${variable.customerData[name]}"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
        assertThat(result.get("errors").toString()).contains("holds the file as it was read");
        assertThat(result.getString("message")).startsWith("The file was not changed")
                .contains("the write repairs it by itself");
    }

    /**
     * r28l-4 fail-well-error-handling step 2: the steps: of the when of a choice nested in otherwise:, written after
     * that otherwise:, in the column of the - of the choice. Told two columns, both wrong Camel, the model sent the
     * same lines 16 times. The schema takes the steps: in one place, the when item further up, so the write puts them
     * there.
     */
    @Test
    void stepsInTheColumnOfTheDashesOfAListAreWrittenWhereTheSchemaTakesThem(@TempDir Path dir) throws IOException {
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: direct:charge
                      steps:
                        - choice:
                            when:
                              - expression:
                                  simple:
                                    expression: "${body[orderId]} == 'ORD-1003'"
                                steps:
                                  - throwException:
                                      exceptionType: java.lang.IllegalStateException
                                      message: "card declined"
                            otherwise:
                              steps:
                                - choice:
                                    when:
                                      - expression:
                                          simple:
                                            expression: "${body[orderId]} == 'ORD-1002'"
                                    otherwise:
                                      steps:
                                        - log:
                                            message: "Order ${body[orderId]} charged successfully"
                                steps:
                                  - throwException:
                                      exceptionType: java.net.ConnectException
                                      message: "payment provider did not answer"
                """);
        assertRepairedAndValid(dir, result, "line 25: steps: moved to column 25 after line 20 (a key of the item at line"
                                            + " 18, as expression is)");
        assertThat(Files.readString(dir.resolve(FILE))).contains("""
                                      - expression:
                                          simple:
                                            expression: "${body[orderId]} == 'ORD-1002'"
                                        steps:
                                          - throwException:
                                              exceptionType: java.net.ConnectException
                                              message: "payment provider did not answer"
                                    otherwise:
                """);
    }

    /** r28n-1 contracts-rest-code-first step 1: otherwise: in the column of the dashes of the when items. */
    @Test
    void anOtherwiseInTheColumnOfTheDashesOfTheWhenItemsIsWrittenAsAKeyOfTheChoice(@TempDir Path dir)
            throws IOException {
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - choice:
                            when:
                              - simple: "${body} != null"
                                steps:
                                  - log: "found"
                              otherwise:
                                steps:
                                  - log: "not found"
                """);
        assertRepairedAndValid(dir, result, "line 10: otherwise: moved to column 13 (a key next to when (line 6))");
    }

    /**
     * Under otherwise: with a list the schema checks nothing, so no place is said, and the write repairs nothing: the
     * columns are said as before.
     */
    @Test
    void aKeyInTheColumnOfTheDashesWhosePlaceTheSchemaCannotJudgeIsNotRepaired(@TempDir Path dir) {
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - choice:
                            when:
                              - simple: "${body} == 'a'"
                                steps:
                                  - log: "a"
                            otherwise:
                              - choice:
                                  when:
                                    - simple: "${body} == 'b'"
                                    steps:
                                      - log: "b"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
        assertThat(result.get("repaired")).isNull();
        assertThat(result.get("errors").toString()).contains("Write it in column 23 to make it a key of the item, as"
                                                             + " simple is, or in column 19 to make it a key next to when");
    }

    /**
     * r28n-6 connect-sftp-transfer step 2: - route: in column 3 after a top-level onException was told to line up with
     * the steps of the onException, 12 times. Named as an entry of the file, it is two columns off the entries, which
     * the write repairs.
     */
    @Test
    void aRouteTwoColumnsOffTheEntriesOfTheFileIsRepaired(@TempDir Path dir) throws IOException {
        JsonObject result = write(dir, """
                - onException:
                    exception:
                      - java.lang.Exception
                    steps:
                      - log:
                          message: "Batch failed: ${exception.message}"
                  - route:
                      id: invoice-batches
                      from:
                        uri: timer:tick
                        steps:
                          - log: "tick"
                """);
        assertRepairedAndValid(dir, result, "line 7: the list item moved to the column of the list");
        assertThat(Files.readString(dir.resolve(FILE))).contains("\n- route:\n    id: invoice-batches\n");
    }

    /**
     * r27l-4 connect-http-client step 3: a second condition and an - otherwise: item written under otherwise:. The
     * write put them under steps:, where they were still not steps, and said the shape needed no change; the model sent
     * the same lines 16 times. They are conditions: no repair, and the answer says where they go.
     */
    @Test
    void anOtherwiseHoldingASecondConditionIsNotRepaired(@TempDir Path dir) {
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: direct:check
                      steps:
                        - choice:
                            when:
                              - simple:
                                  expression: "${header.CamelHttpResponseCode} != 200"
                                steps:
                                  - log: "not found"
                            otherwise:
                              - simple:
                                  expression: "${body[qty]} >= ${variable.needed}"
                                steps:
                                  - log: "in stock"
                              - otherwise:
                                steps:
                                  - log: "back-order"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
        assertThat(result.get("repaired")).isNull();
        assertThat(result.get("repairable")).isNull();
        assertThat(result.getString("message")).doesNotContain("needs no change from you");
        assertThat(result.get("errors").toString()).contains("Line 12: otherwise: array found, object expected (otherwise"
                                                             + " holds only steps:, the steps for a message that no when"
                                                             + " matches. A second condition is another item of when:");
    }

    /**
     * r27n-2 contracts-rest-code-first step 1: - otherwise: written at the column of when:, which the write moves into
     * the when: list. There it is still not an item of when, so the note must not say that the shape needs no change.
     */
    @Test
    void aRepairWhoseLinesStillHaveAProblemOfTheirShapeIsSaidSo(@TempDir Path dir) {
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - choice:
                            when:
                              - simple: "${body} != null"
                                steps:
                                  - log: "found"
                            - otherwise:
                                steps:
                                  - log: "not found"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
        assertThat(result.get("errors").toString()).contains("otherwise is not an item of when: it is a key of choice");
        assertThat(result.get("repairable").toString())
                .contains("line 10: the list item moved to the column of the list (these lines still have errors)");
        assertThat(result.getString("message")).doesNotContain("needs no change from you")
                .endsWith("The write repairs part of the YAML shape by itself (line 10: the list item moved to the column"
                          + " of the list (these lines still have errors)), and the lines marked still have errors of"
                          + " their shape: change them as the errors say. The errors are what the content has with that"
                          + " repaired.");
    }

    @Test
    void aProblemTheWriteCannotRepairKeepsTheAnswerAsItWas(@TempDir Path dir) {
        // the case of library: is repaired, the misspelt key is not: both are said as written, nothing is hidden
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: timer:x
                      steps:
                        - unmarshal:
                            json:
                              library: jackson
                        - log:
                            mesage: "x"
                """);
        assertThat(result.getString("status")).isEqualTo("invalid");
        assertThat(result.get("repairable")).isNull();
        assertThat(result.get("errors").toString()).contains("mesage");
    }

    @Test
    void whatHasNoSingleReadingIsStillRefused(@TempDir Path dir) {
        // a name the alias table does not have: the write is refused as before, nothing guessed
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: timer:x
                      steps:
                        - log:
                            mesage: "x"
                """);
        assertThat(result.getString("status")).isEqualTo("invalid");
        assertThat(result.get("repaired")).isNull();
        assertThat(result.get("errors").toString()).contains("mesage");
    }

    /**
     * r29 of the local-model benchmark, contracts-rest-code-first step 1: a line break escaped twice in the tool call,
     * so the line holds the two characters \n before the next key. Told what they are and given the two lines to write,
     * the model sent the same lines 15, 10 and 16 times in three runs. They cannot be text there: the write makes them
     * a line break, and says so.
     */
    @Test
    void theTwoCharactersOfALineBreakBeforeTheNextKeyAreWrittenAsALineBreak(@TempDir Path dir) throws IOException {
        JsonObject result = write(dir, """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.orderId == '${header.id}')]"\\n                resultType: java.util.List
                        - log: "${body}"
                """);
        assertRepairedAndValid(dir, result,
                "lines 8-9: the two characters \\n after the value of expression written as a line break");
        assertThat(result.getString("message")).startsWith("Written with these repairs of the YAML (the file has them"
                                                           + " now): lines 8-9: the two characters \\n after the value of"
                                                           + " expression written as a line break. ");
        assertThat(Files.readString(dir.resolve(FILE))).contains("""
                              jsonpath:
                                expression: "$[?(@.orderId == '${header.id}')]"
                                resultType: java.util.List
                        - log: "${body}"
                """);
    }

    /** Several in one write: two on one line, one on another line, after a plain value, and before a list item. */
    @Test
    void eachLineBreakEscapedTwiceIsWrittenAsALineBreak(@TempDir Path dir) throws IOException {
        JsonObject result = write(dir,
                """
                        - route:
                            from:
                              uri: direct:get-order
                              steps:
                                - setBody:
                                    expression:
                                      jsonpath:
                                        expression: '$[0]'\\n                resultType: java.util.Map\\n                suppressExceptions: true
                                - setHeader:
                                    name: orderId
                                    expression:
                                      simple:
                                        expression: ${body[orderId]}\\n                trim: true
                                - to: direct:audit\\n        - log: "${header.orderId}"
                        - route:
                            from:
                              uri: direct:audit
                              steps:
                                - log: "audit"
                        """);
        assertRepairedAndValid(dir, result,
                "lines 8-10: the two characters \\n after the values of expression and resultType written as line breaks",
                "lines 15-16: the two characters \\n after the value of expression written as a line break",
                "lines 17-18: the two characters \\n after the value of - to written as a line break");
        assertThat(Files.readString(dir.resolve(FILE))).contains("""
                              jsonpath:
                                expression: '$[0]'
                                resultType: java.util.Map
                                suppressExceptions: true
                        - setHeader:
                            name: orderId
                            expression:
                              simple:
                                expression: ${body[orderId]}
                                trim: true
                        - to: direct:audit
                        - log: "${header.orderId}"
                """);
    }

    /** The edit tool writes through the write, so a replacement with the two characters \n is repaired the same way. */
    @Test
    void anEditWithALineBreakEscapedTwiceIsRepairedAsAWriteIs(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[0]"
                        - log: "${body}"
                """);
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE,
                "                expression: \"$[0]\"",
                "                expression: \"$[?(@.orderId == '${header.id}')]\"\\n                resultType: java.util.List");
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("edited");
        assertThat(result.get("repaired").toString())
                .contains("lines 8-9: the two characters \\n after the value of expression written as a line break");
        assertThat(Files.readString(dir.resolve(FILE))).contains("""
                                expression: "$[?(@.orderId == '${header.id}')]"
                                resultType: java.util.List
                        - log: "${body}"
                """);
    }

    /**
     * A \n inside quotes is an escape (double quotes) or text (single quotes), and a block scalar or Groovy code holds
     * it as written: none of them is a line break escaped twice, so the file is written as it is.
     */
    @Test
    void theTwoCharactersAsTextAreNotRepaired(@TempDir Path dir) throws IOException {
        String content = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            expression:
                              groovy:
                                expression: "body.split('\\n').collect { it + ': x' }.join('\\n')"
                        - setBody:
                            expression:
                              simple:
                                expression: "line one\\nresultType: ${body}"
                        - setBody:
                            expression:
                              groovy:
                                expression: |
                                  def lines = "a\\nb: c".split('\\n')
                                  return lines.size()
                        - log:
                            message: 'first\\n  second: ${body}'
                        - log:
                            message: Hello\\nWorld
                """;
        JsonObject result = write(dir, content);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("created");
        assertThat(result.get("repaired")).isNull();
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(content);
    }

    /**
     * After a \n, text that starts in no column of a key of the lines above is not a key the line break was meant for:
     * not repaired, and the answer is the one the validator gives. A key in the column of a mapping above is repaired,
     * and when that is not its place, the repair says its lines still have errors.
     */
    @Test
    void aLineBreakEscapedTwiceBeforeAKeyInNoColumnOfTheLinesAboveIsNotRepaired(@TempDir Path dir) {
        String route = """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[0]"\\n%sresultType: java.util.List
                """;
        for (String indent : List.of("", "                  ")) {
            JsonObject result = write(dir, route.formatted(indent));
            assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
            assertThat(result.get("repaired")).isNull();
            assertThat(result.get("repairable")).isNull();
            assertThat(result.get("errors").toString()).contains("the value of expression is followed by \\n");
        }
        JsonObject result = write(dir, route.formatted("              "));
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
        assertThat(result.get("errors").toString()).contains("property 'resultType' is not defined");
        assertThat(result.get("repairable").toString()).contains("lines 8-9: the two characters \\n after the value of"
                                                                 + " expression written as a line break (these lines still"
                                                                 + " have errors)");
        assertThat(result.getString("message")).doesNotContain("needs no change from you");
    }

    // -------------------------------------------------------------------------------------------------------------
    // the snippet to find, written from memory
    // -------------------------------------------------------------------------------------------------------------

    private static final String ROUTE = """
            - route:
                id: invoices
                from:
                  uri: file:inbox
                  steps:
                    - unmarshal:
                        json:
                          library: Jackson
                    - log:
                        message: "Invoice ${body[invoiceId]} for ${body[orderId]}"
                    - to:
                        uri: file:archive
            """;

    @Test
    void aSnippetFromMemoryIsMatchedToTheClosestLines(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), ROUTE);
        // the model remembers the log line, and the unmarshal above it as it was before (jackson, not json)
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE, """
                    - unmarshal:
                        jackson: {}
                    - log:
                        message: "Invoice ${body[invoiceId]} for ${body[orderId]}"
                """, """
                    - unmarshal:
                        json:
                          library: Jackson
                    - log:
                        message: "Invoice ${body[invoiceId]} for ${body[orderId]}: ${body[amount]} EUR"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("edited");
        assertThat(result.getString("message")).startsWith("The text to find was not in the file as given");
        String after = Files.readString(dir.resolve(FILE));
        assertThat(after).contains("message: \"Invoice ${body[invoiceId]} for ${body[orderId]}: ${body[amount]} EUR\"")
                .contains("uri: file:archive").contains("uri: file:inbox");
    }

    @Test
    void aSnippetWithoutADistinctiveLineIsNotGuessed(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), ROUTE);
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE, """
                - log:
                    message: "something else"
                """, "- log: \"x\"");
        assertThat(result.getString("status")).isEqualTo("not-found");
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(ROUTE);
    }

    @Test
    void twoPlacesThatMatchAsWellAreNotGuessed() {
        String content = """
                - route:
                    from:
                      uri: timer:a
                      steps:
                        - log:
                            message: "same line in both"
                        - to:
                            uri: direct:a
                - route:
                    from:
                      uri: timer:b
                      steps:
                        - log:
                            message: "same line in both"
                        - to:
                            uri: direct:b
                """;
        // the distinctive line is in the file twice: no window is certain
        assertThat(AuthoringTools.fuzzyWindow(content, """
                - log:
                    message: "same line in both"
                - to:
                    uri: direct:c
                """)).isNull();
    }

    private static final String ORDER_LINES = """
            - route:
                id: order-lines
                from:
                  uri: file
                  parameters:
                    directoryName: orders
                    noop: true
                    sortBy: "file:name"
                  steps:
                    - unmarshal:
                        json:
                          library: Jackson
                    - split:
                        expression:
                          simple: "${body[lines]}"
                        steps:
                          - log:
                              message: "  pick ${body[qty]} x ${body[sku]}"
            """;

    @Test
    void theLinesTheModelChangedAreChangedOnceInTheFile(@TempDir Path dir) throws IOException {
        // r11L-1 route-order-lines step 2: the model remembered the steps of the split two columns to the left, and
        // the message without its leading spaces. The closest window ended before the message line, the window was
        // replaced, and the old message stayed below the new one: the key twice
        Files.writeString(dir.resolve(FILE), ORDER_LINES);
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE, """
                        - unmarshal:
                            json:
                              library: Jackson
                        - split:
                            expression:
                              simple: "${body[lines]}"
                          steps:
                            - log:
                                message: "pick ${body[qty]} x ${body[sku]}"
                """, """
                        - unmarshal:
                            json:
                              library: Jackson
                        - setHeader:
                            name: orderId
                            simple: "${body[orderId]}"
                        - split:
                            expression:
                              simple: "${body[lines]}"
                          steps:
                            - log:
                                message: "  pick ${body[qty]} x ${body[sku]} for ${header.orderId}"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("edited");
        assertThat(result.getString("message")).contains("the lines you changed were changed there");
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo("""
                - route:
                    id: order-lines
                    from:
                      uri: file
                      parameters:
                        directoryName: orders
                        noop: true
                        sortBy: "file:name"
                      steps:
                        - unmarshal:
                            json:
                              library: Jackson
                        - setHeader:
                            name: orderId
                            simple: "${body[orderId]}"
                        - split:
                            expression:
                              simple: "${body[lines]}"
                            steps:
                              - log:
                                  message: "  pick ${body[qty]} x ${body[sku]} for ${header.orderId}"
                """);
    }

    @Test
    void aLineRememberedWronglyThatTheModelChangesIsReplacedNotAdded(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), ROUTE);
        // the model remembers the log message wrongly, and changes it: the file's message is the one it changes
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE, """
                    - unmarshal:
                        json:
                          library: Jackson
                    - log:
                        message: "Invoice ${body[invoiceId]}"
                """, """
                    - unmarshal:
                        json:
                          library: Jackson
                    - log:
                        message: "Invoice ${body[invoiceId]} for ${body[orderId]}: ${body[amount]} EUR"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("edited");
        String after = Files.readString(dir.resolve(FILE));
        assertThat(after).contains("message: \"Invoice ${body[invoiceId]} for ${body[orderId]}: ${body[amount]} EUR\"")
                .doesNotContain("message: \"Invoice ${body[invoiceId]} for ${body[orderId]}\"\n")
                .contains("uri: file:archive");
        assertThat(after.split("message:", -1)).hasSize(2);
    }

    @Test
    void aLineRememberedWronglyThatTheModelKeepsStaysAsTheFileHasIt(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), ROUTE);
        // the model remembers the log message wrongly and does not change it: the file keeps its own message, and the
        // step the model adds after it goes in
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE, """
                    - unmarshal:
                        json:
                          library: Jackson
                    - log:
                        message: "Invoice ${body[invoiceId]}"
                    - to:
                        uri: file:archive
                """, """
                    - unmarshal:
                        json:
                          library: Jackson
                    - log:
                        message: "Invoice ${body[invoiceId]}"
                    - to:
                        uri: file:archive
                    - log:
                        message: "archived"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("edited");
        String after = Files.readString(dir.resolve(FILE));
        assertThat(after).contains("message: \"Invoice ${body[invoiceId]} for ${body[orderId]}\"")
                .doesNotContain("message: \"Invoice ${body[invoiceId]}\"\n")
                .contains("message: \"archived\"");
    }

    /**
     * A line of the file that the snippet from memory does not have stays: the model remembered the cron endpoint
     * without its name parameter, and the merge dropped name: report (r21 of the local-model benchmark).
     */
    @Test
    void aLineOfTheFileTheSnippetDoesNotHaveStays(@TempDir Path dir) throws IOException {
        String route = """
                - route:
                    id: nightly-report
                    from:
                      uri: cron
                      parameters:
                        name: report
                        schedule: "0/10 * * * * ?"
                      steps:
                        - log:
                            message: "Inventory report: 120 T-shirts and 45 mugs in stock"
                """;
        Files.writeString(dir.resolve(FILE), route);
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE, """
                      uri: cron:report
                      parameters:
                        schedule: "0/10 * * * * ?"
                """, """
                      uri: cron:report
                      parameters:
                        schedule: "{{report.schedule}}"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("edited");
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(route.replace("\"0/10 * * * * ?\"",
                "\"{{report.schedule}}\""));
    }

    /** A line the model adds between two lines it remembers goes in, and the file's line between them stays. */
    @Test
    void aLineAddedBetweenTwoRememberedLinesKeepsTheFilesLineThere(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), ROUTE);
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE, """
                    - unmarshal:
                        json:
                    - log:
                        message: "Invoice ${body[invoiceId]} for ${body[orderId]}"
                """, """
                    - unmarshal:
                        json:
                          allowUnmarshallType: true
                    - log:
                        message: "Invoice ${body[invoiceId]} for ${body[orderId]}"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("edited");
        String after = Files.readString(dir.resolve(FILE));
        assertThat(after).contains("library: Jackson").contains("allowUnmarshallType: true");
    }

    @Test
    void aClosestMatchWithAnInvalidResultIsNotFound(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), ROUTE);
        // after a refused edit, the next text to find is the model's own replacement, which the file never got: the
        // answer is the one of an exact match, not found with the lines the file has there, and the file is unchanged
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE, """
                    - unmarshal:
                        json:
                          library: Jackson
                    - lgo:
                        message: "Invoice ${body[invoiceId]} for ${body[orderId]}"
                """, """
                    - unmarshal:
                        json:
                          library: Jackson
                    - lgo:
                        message: "Invoice ${body[invoiceId]} for ${body[orderId]}: ${body[amount]} EUR"
                """);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("not-found");
        assertThat(result.getString("fileWindow")).contains("- log:");
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(ROUTE);
    }
}
