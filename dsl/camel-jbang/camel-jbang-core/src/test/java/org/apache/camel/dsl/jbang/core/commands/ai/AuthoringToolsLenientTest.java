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
