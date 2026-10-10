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
}
