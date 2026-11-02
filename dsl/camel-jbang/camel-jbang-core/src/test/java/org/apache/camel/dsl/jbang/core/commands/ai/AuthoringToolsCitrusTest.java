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
 * validate tools check its YAML syntax only, with none of the repairs, notes and reloads of a route (test-citrus-orders
 * of the local-model benchmark).
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
                  jbang:
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
                  jbang:
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
                      jbang:
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
    void anEditOfACitrusTestIsCheckedForItsYamlOnly(@TempDir Path dir) throws IOException {
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
    void validateSourceChecksTheYamlOfACitrusTest() {
        JsonObject valid = AuthoringTools.validate(new ToolContext(), FILE, ORDERS_TEST);
        assertThat(valid.getBoolean("valid")).as(String.valueOf(valid)).isTrue();
        assertThat(valid.getString("message")).startsWith("The YAML is valid.").contains("camel test run " + FILE);

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
