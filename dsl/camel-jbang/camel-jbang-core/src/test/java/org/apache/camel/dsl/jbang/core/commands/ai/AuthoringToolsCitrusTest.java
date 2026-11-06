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

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Citrus test, such as test/orders.citrus.it.yaml, is run with camel test run and is not a route: the write, edit and
 * validate tools check its YAML syntax and the test schema of Citrus, with none of the repairs, notes and reloads of a
 * route (test-citrus-orders of the local-model benchmark).
 */
class AuthoringToolsCitrusTest {

    private static final String FILE = "test/orders.citrus.it.yaml";

    /** The sample test camel test init writes. */
    private static final String SAMPLE = """
            name: orders.citrus.it
            author: Citrus
            status: FINAL
            description: Sample test in YAML
            variables:
              - name: message
                value: Citrus rocks!
            actions:
              - echo:
                  message: "${message}"
            """;

    /** The test step 1 of test-citrus-orders asks for. */
    private static final String ORDERS_TEST = """
            name: orders-test
            description: The order API accepts the sample order and logs it
            actions:
              - camel:
                  cli:
                    run:
                      integration:
                        name: "orders"
                        file: "../orders.camel.yaml"
                        systemProperties:
                          file: "../application.properties"
              - http:
                  client: "http://localhost:8080"
                  sendRequest:
                    POST:
                      path: "/orders"
                      contentType: "application/json"
                      body:
                        resource:
                          file: "../sample-order.json"
              - http:
                  client: "http://localhost:8080"
                  receiveResponse:
                    response:
                      status: "201"
              - camel:
                  cli:
                    verify:
                      integration: "orders"
                      logMessage: "Order ORD-1001 accepted for C-482 with 2 line(s)"
            """;

    private static final String LOG_MESSAGE = "          logMessage: \"Order ORD-1001 accepted for C-482 with 2 line(s)\"\n";

    @Test
    void theNamesCitrusRunsAsYamlTests() {
        assertThat(SourceValidator.isCitrusTest(FILE)).isTrue();
        assertThat(SourceValidator.isCitrusTest("orders.citrus.yaml")).isTrue();
        assertThat(SourceValidator.isCitrusTest("orders.citrus.test.yaml")).isTrue();
        assertThat(SourceValidator.isCitrusTest("orders.citrus-it.yaml")).isTrue();
        assertThat(SourceValidator.isCitrusTest("test/Orders.Citrus.IT.yml")).isTrue();
        assertThat(SourceValidator.isCitrusTest("orders.camel.yaml")).isFalse();
        assertThat(SourceValidator.isCitrusTest("citrus.yaml")).isFalse();
        assertThat(SourceValidator.isCitrusTest("orders.citrus.it.xml")).isFalse();
        assertThat(SourceValidator.isCitrusTest("test.citrus.d/orders.yaml")).isFalse();
        assertThat(SourceValidator.isCitrusTest("test/citrus-application.properties")).isFalse();
    }

    @Test
    void aCitrusTestIsWrittenWithoutTheChecksOfARoute(@TempDir Path dir) throws IOException {
        // step 1: written over the sample of camel test init, the test was said to have the problem "a Camel YAML file
        // is a list of entries", and the model made it a list, then a route
        Files.createDirectories(dir.resolve("test"));
        Files.writeString(dir.resolve(FILE), SAMPLE);

        JsonObject result = AuthoringTools.writeFile(new ToolContext(), dir, FILE, ORDERS_TEST, true);

        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("overwritten");
        assertThat(result.get("existingProblems")).isNull();
        assertThat(result.get("notes")).isNull();
        assertThat(result.getString("message")).isEqualTo(AuthoringTools.citrusTestNote(FILE))
                .contains("checked against the test schema of Citrus " + CitrusTestChecks.version())
                .contains("camel test run test/orders.citrus.it.yaml");
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(ORDERS_TEST);
        // the content a route check refuses
        assertThat(SourceValidator.validateCamelYaml(ORDERS_TEST, new DefaultCamelCatalog())).isNotEmpty();
    }

    @Test
    void theVariablesOfACitrusTestAreNotSimpleExpressions(@TempDir Path dir) {
        // the first write of the model was refused: Simple syntax error: Unknown function: orderId
        JsonObject result = AuthoringTools.writeFile(new ToolContext(), dir, FILE, """
                name: orders-test
                variables:
                  - name: orderId
                    value: ORD-1001
                actions:
                  - echo:
                      message: "Order ${orderId} accepted"
                """, true);

        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("created");
    }

    @Test
    void aCitrusTestThatDoesNotParseIsRefusedWithTheLineAndNotRepaired(@TempDir Path dir) throws IOException {
        // a list item two columns off: the write of a route repairs it, the write of a test says where it is
        Files.createDirectories(dir.resolve("test"));
        Files.writeString(dir.resolve(FILE), SAMPLE);

        JsonObject result = AuthoringTools.writeFile(new ToolContext(), dir, FILE, """
                name: orders-test
                actions:
                  - camel:
                      cli:
                        run:
                          integration:
                            file: "../orders.camel.yaml"
                    - http:
                        client: "http://localhost:8080"
                """, true);

        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
        assertThat(result.get("errors").toString()).contains("Line 8: the YAML does not parse at column 5")
                .contains("while parsing a block mapping that starts at line 3");
        assertThat(result.get("repaired")).isNull();
        assertThat(result.get("repairable")).isNull();
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(SAMPLE);
    }

    @Test
    void aCitrusTestOfActionsCitrusDoesNotHaveIsNotWritten(@TempDir Path dir) throws IOException {
        // a test a local model wrote for step 1 (r26 of the local-model benchmark): written, as its YAML parses, it
        // failed in camel test run with "Unable to find property 'http' on class: org.citrusframework.yaml.TestActions"
        String invented = """
                name: orders.citrus.it
                actions:
                  - http:
                      uri: http://localhost:8080/orders
                      method: POST
                      requestFile: sample-order.json
                      expectStatus: 201
                  - assert-log:
                      pattern: "Order ORD-1001 accepted"
                """;
        Files.createDirectories(dir.resolve("test"));
        Files.writeString(dir.resolve(FILE), SAMPLE);
        Files.writeString(dir.resolve("orders.camel.yaml"), "- from:\n    uri: timer:tick\n    steps:\n      - log: hi\n");
        Files.writeString(dir.resolve("application.properties"), "camel.server.port=8080\n");

        JsonObject result = AuthoringTools.writeFile(new ToolContext(), dir, FILE, invented, true);

        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
        assertThat(result.get("errors").toString())
                .contains("Line 4: uri, method, requestFile and expectStatus are not keys of the http action, which"
                          + " takes actor, client,")
                .contains("Line 8: assert-log is not a Citrus test action: echo: {message: ");
        // the test starts no integration: the action that starts the one of the project, not a tool the client may not
        // have (camel_catalog_examples is not a core tool)
        assertThat(result.getString("message")).startsWith("The file was not written")
                .doesNotContain("camel_catalog_examples")
                .endsWith(" The test calls the integration but does not start it, and camel test run runs only the"
                          + " test: start it with the first action of the test (or have it running when the test"
                          + " runs):\n" + """
                                    - camel:
                                        cli:
                                          run:
                                            integration:
                                              name: "orders"
                                              file: "../orders.camel.yaml"
                                              systemProperties:
                                                file: "../application.properties"
                                  """.stripTrailing());
        assertThat(result.get("repaired")).isNull();
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(SAMPLE);

        // the test the example has: written
        result = AuthoringTools.writeFile(new ToolContext(), dir, FILE, ORDERS_TEST, true);
        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("overwritten");
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(ORDERS_TEST);
    }

    @Test
    void aTestThatCallsTheIntegrationWithoutStartingItIsWrittenWithTheActionThatStartsIt(@TempDir Path dir)
            throws IOException {
        // camel test run runs only the test: with no action that starts the integration, the call fails (the
        // integration a test of test-citrus-orders calls is not running while the test runs). The name is the one the
        // test checks the log of
        Files.writeString(dir.resolve("orders.camel.yaml"), "- from:\n    uri: timer:tick\n    steps:\n      - log: hi\n");
        String test = ORDERS_TEST.substring(0, ORDERS_TEST.indexOf("  - camel:\n      cli:\n        run:"))
                      + ORDERS_TEST.substring(ORDERS_TEST.indexOf("  - http:")).replace("\"orders\"", "\"orders-app\"");

        JsonObject result = AuthoringTools.writeFile(new ToolContext(), dir, FILE, test, true);

        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("created");
        assertThat(result.getString("message")).isEqualTo(AuthoringTools.citrusTestNote(FILE)
                                                          + " The test calls the integration but does not start it, and"
                                                          + " camel test run runs only the test: start it with the first"
                                                          + " action of the test (or have it running when the test"
                                                          + " runs):\n" + """
                                                                    - camel:
                                                                        cli:
                                                                          run:
                                                                            integration:
                                                                              name: "orders-app"
                                                                              file: "../orders.camel.yaml"
                                                                  """.stripTrailing());
        // with the action that starts it, nothing is said
        assertThat(AuthoringTools.writeFile(new ToolContext(), dir, FILE, ORDERS_TEST, true).getString("message"))
                .isEqualTo(AuthoringTools.citrusTestNote(FILE));
    }

    @Test
    void anEditThatBreaksTheTestIsNotWritten(@TempDir Path dir) throws IOException {
        // step 2: the body of the request written as text, where Citrus takes body: {data: ...}
        Files.createDirectories(dir.resolve("test"));
        Files.writeString(dir.resolve(FILE), ORDERS_TEST);

        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE, """
                          body:
                            resource:
                              file: "../sample-order.json"
                """, """
                          body: '{"orderId": "ORD-1002", "country": "US"}'
                """);

        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("invalid");
        assertThat(result.get("errors").toString())
                .contains("Line 18: http.sendRequest.POST.body: string found, object expected; body takes data,"
                          + " resource, script");
        assertThat(Files.readString(dir.resolve(FILE))).isEqualTo(ORDERS_TEST);
    }

    @Test
    void withoutTheSchemaACitrusTestIsCheckedForItsYamlOnly(@TempDir Path dir) {
        // offline, with no catalog in the local Maven repository: the test is written, and the answer says how it
        // was checked
        CitrusTestChecks.setTestSchema(null);
        try {
            JsonObject result = AuthoringTools.writeFile(new ToolContext(), dir, FILE, """
                    name: orders-test
                    actions:
                      - log:
                          message: hello
                    """, true);

            assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("created");
            assertThat(result.getString("message")).contains("only its YAML syntax is checked")
                    .contains("camel test run " + FILE);
            assertThat(AuthoringTools.validate(new ToolContext(), FILE, ORDERS_TEST).getString("message"))
                    .startsWith("The YAML is valid.");
        } finally {
            CitrusTestChecks.reset();
        }
    }

    @Test
    void theRunningIntegrationIsNotSaidToReloadACitrusTest(@TempDir Path dir) {
        // the answer said "The running integration reloaded the file": dev mode with --source-dir reloads the routes
        // when the test changes, but loads no route from it
        ToolContext ctx = new ToolContext();
        ctx.selectProcess(ProcessHandle.current().pid());

        JsonObject result = AuthoringTools.writeFile(ctx, dir, FILE, ORDERS_TEST, true);

        assertThat(result.getString("status")).isEqualTo("created");
        assertThat(result.get("reload")).as(String.valueOf(result)).isNull();
        assertThat(result.getString("message")).doesNotContain("reload").contains("camel test run");
    }

    @Test
    void anEditOfACitrusTestIsCheckedAsATest(@TempDir Path dir) throws IOException {
        // step 2: a case added after the last action
        Files.createDirectories(dir.resolve("test"));
        Files.writeString(dir.resolve(FILE), ORDERS_TEST);

        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, FILE, LOG_MESSAGE, LOG_MESSAGE + """
                  - http:
                      client: "http://localhost:8080"
                      receiveResponse:
                        response:
                          status: "400"
                """);

        assertThat(result.getString("status")).as(String.valueOf(result)).isEqualTo("edited");
        assertThat(result.get("reload")).isNull();
        assertThat(result.getString("message")).contains("camel test run " + FILE);
        assertThat(Files.readString(dir.resolve(FILE))).endsWith("          status: \"400\"\n");

        JsonObject again = AuthoringTools.editFile(new ToolContext(), dir, FILE, LOG_MESSAGE, LOG_MESSAGE);
        assertThat(again.getString("status")).isEqualTo("unchanged");
        assertThat(again.getString("message")).contains("camel test run").doesNotContain("route does");
    }

    @Test
    void validateSourceChecksACitrusTest() {
        JsonObject valid = AuthoringTools.validate(new ToolContext(), FILE, ORDERS_TEST);
        assertThat(valid.getBoolean("valid")).as(String.valueOf(valid)).isTrue();
        assertThat(valid.getString("message")).startsWith("The test is valid.").contains("camel test run " + FILE);

        // an action Citrus does not have
        JsonObject log = AuthoringTools.validate(new ToolContext(), FILE, """
                name: orders-test
                actions:
                  - log:
                      message: hello
                """);
        assertThat(log.getBoolean("valid")).isFalse();
        assertThat(log.get("errors").toString()).contains("Line 3: log is not a Citrus test action");
        assertThat(log.getString("message")).isEqualTo("1 problem(s) found; fix them before writing the file");

        // a quote that is not closed: the parser reads to the end of the file, and names the line of the quote
        JsonObject invalid = AuthoringTools.validate(new ToolContext(), FILE, """
                name: orders-test
                actions:
                  - echo:
                      message: "Order accepted
                  - echo:
                      message: done
                """);
        assertThat(invalid.getBoolean("valid")).isFalse();
        assertThat(invalid.get("errors").toString()).contains("the YAML does not parse")
                .contains("while scanning a quoted scalar that starts at line 4");
    }
}
