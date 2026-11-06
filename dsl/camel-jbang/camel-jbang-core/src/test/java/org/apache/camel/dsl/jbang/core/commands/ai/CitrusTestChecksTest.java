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

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Citrus test is checked against the test schema of the Citrus catalog, which the tests have on the classpath (it is
 * never downloaded here). The invalid tests in src/test/resources/citrus-orders are tests a local model wrote for
 * test-citrus-orders of the local-model benchmark; camel test run failed on each of them.
 */
class CitrusTestChecksTest {

    private static final Path ORDERS = Path.of("src/test/resources/citrus-orders");

    /** What is said of an action named log: what to write to log a message, and to check the log of the integration. */
    private static final String LOG_HINT = "echo: {message: \"...\"} writes a message to the test log, and camel: {cli:"
                                           + " {verify: {integration: \"<name>\", logMessage: \"...\"}}} checks that the"
                                           + " integration logged a line";

    /** What is said of the request and its response written as one http action, before the lines to write. */
    private static final String TWO_ACTIONS = ". An http action of a client either sends the request (sendRequest) or"
                                              + " receives the response (receiveResponse), so the request and the"
                                              + " response it expects are two http actions.";

    @Test
    void theSchemaIsOnTheClasspath() {
        assertThat(CitrusTestChecks.hasSchema()).as(CitrusTestChecks.schemaProblem()).isTrue();
        assertThat(CitrusTestChecks.version()).isNotBlank().doesNotContain("${");
        assertThat(CitrusTestChecks.schema().actions()).contains("echo", "http", "camel", "sequential", "waitFor")
                .doesNotContain("log");
        // the containers, whose actions the schema takes as any object, read from the schema
        assertThat(CitrusTestChecks.schema().nested().get("sequential")).containsExactly(List.of("actions", "[]"));
        assertThat(CitrusTestChecks.schema().nested().get("waitFor")).containsExactly(List.of("action"));
        assertThat(CitrusTestChecks.schema().lists()).containsExactly(List.of("actions", "[]"), List.of("finally", "[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "step1.citrus.it.yaml", "step2.citrus.it.yaml", "step3.citrus.it.yaml" })
    void theTestsOfTheBenchmarkExampleAreValid(String file) throws Exception {
        assertThat(validate(ORDERS.resolve(file))).isEmpty();
    }

    static Stream<Arguments> bundledTests() throws Exception {
        // the Citrus tests of the bundled examples, from the classpath as ExampleRoutesLoadTest reads them, and the
        // golden test
        URL examples = CitrusTestChecksTest.class.getClassLoader().getResource("examples");
        Path dir = Path.of(examples.toURI());
        try (Stream<Path> files = Files.walk(dir)) {
            List<Path> tests = files.filter(p -> SourceValidator.isCitrusTest(p.getFileName().toString())).sorted()
                    .toList();
            assertThat(tests).hasSizeGreaterThan(20);
            Path golden = Path.of("src/test/resources/golden/citrus/test/orders.citrus.it.yaml");
            return Stream.concat(tests.stream().map(p -> Arguments.of(Named.of(dir.relativize(p).toString(), p))),
                    Stream.of(Arguments.of(Named.of("golden/" + golden.getFileName(), golden))));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bundledTests")
    void theTestsOfTheExamplesAreValid(Path file) throws Exception {
        assertThat(validate(file)).isEmpty();
        // each starts the integration it calls
        Path project = file.getParent().getParent();
        assertThat(CitrusTestChecks.startNote(Files.readString(file), project, project.relativize(file).toString()))
                .isEmpty();
    }

    @Test
    void anHttpActionWithKeysOfItsOwnAndLogActions() throws Exception {
        List<String> msgs = validate(ORDERS.resolve("http-of-its-own.citrus.it.yaml"));

        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0)).isEqualTo("Line 32: uri, method, requestFile, contentType and expectStatus are not keys of"
                                          + " the http action, which takes actor, client, description, receiveRequest,"
                                          + " receiveResponse, sendRequest, sendResponse, server" + TWO_ACTIONS
                                          + " Write lines 31-36 as:\n" + """
                                                    - http:
                                                        client: "http://localhost:8080"
                                                        sendRequest:
                                                          POST:
                                                            path: "/orders"
                                                            contentType: "application/json"
                                                            body:
                                                              resource:
                                                                file: "sample-order.json"
                                                    - http:
                                                        client: "http://localhost:8080"
                                                        receiveResponse:
                                                          response:
                                                            status: "201"
                                                  """.stripTrailing());
        assertThat(msgs.get(1)).isEqualTo("Line 39: log is not a Citrus test action: " + LOG_HINT);
        assertThat(msgs.get(2)).isEqualTo("Line 44: assert-log is not a Citrus test action: " + LOG_HINT);
    }

    @Test
    void anHttpActionWithARequestAndAResponse() throws Exception {
        List<String> msgs = validate(ORDERS.resolve("request-and-response.citrus.it.yaml"));

        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).startsWith("Line 30: request and response are not keys of the http action, which takes")
                .contains("sendRequest", "receiveResponse")
                .contains(TWO_ACTIONS + " Write lines 29-50 as:\n  - http:\n")
                // the text of the request and of the response as written, under data:
                .contains("""
                                  body:
                                    data: |
                                      {
                                        "orderId": "ORD-1001",
                        """)
                .endsWith("""
                                response:
                                  status: "201"
                                  body:
                                    data: |
                                      {
                                        "orderId": "ORD-1001",
                                        "status": "accepted"
                                      }
                        """.stripTrailing());
        assertThat(msgs.get(1)).startsWith("Line 53: log is not a Citrus test action");
    }

    @Test
    void aBodyIsAnObjectWithItsData() throws Exception {
        // step 2: the body of a request and of a response given as text, which is body: {data: ...}
        List<String> msgs = validate(ORDERS.resolve("body-as-text.citrus.it.yaml"));

        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).isEqualTo("""
                Line 60: http.sendRequest.POST.body: string found, object expected; body takes data, resource, script; the\
                 text goes under data:, which is the body to send. Write line 60 as:
                          body:
                            data: '{"orderId": "ORD-1002", "country": "US", "lines": [{"sku": "CAMEL-HAT", "qty": 1,\
                 "price": 15.0}]}'""");
        assertThat(msgs.get(1)).isEqualTo("""
                Line 66: http.receiveResponse.response.body: string found, object expected; body takes data, resource,\
                 script; the text goes under data:, which is the body the test expects (Citrus compares a JSON body as\
                 JSON). To check fields instead of the whole body, validate them next to response: validate: [{jsonPath:\
                 [{expression: "$.error", value: "an order needs a customer"}]}]. Write line 66 as:
                          body:
                            data: '{"error": "an order needs a customer"}'""");
    }

    /**
     * r28 of the local-model benchmark, test-citrus-orders step 1: all eight writes put the request and its status
     * straight under http:, and were refused with the keys of http only. The lines to write are the two actions with
     * the values as written, the file the request sends named from the test (Citrus reads it from there); written, the
     * http problem is gone.
     */
    @Test
    void theRequestAndItsStatusUnderHttpAreTwoActions(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("sample-order.json"), "{\"orderId\": \"ORD-1001\"}");
        Files.createDirectories(dir.resolve("test"));
        String test = """
                name: orders.citrus.it
                actions:
                  # POST the sample order to /orders endpoint
                  - http:
                      uri: http://localhost:8080/orders
                      method: POST
                      requestFile: sample-order.json
                      contentType: application/json
                      expectStatus: 201

                  - camel:
                      cli:
                        verify:
                          integration: orders
                          logMessage: "Order ORD-1001 accepted for C-482 with 2 line(s)"
                """;
        List<String> msgs = CitrusTestChecks.validate(test, dir, "test/orders.citrus.it.yaml");

        assertThat(msgs).singleElement().asString()
                .startsWith("Line 5: uri, method, requestFile, contentType and expectStatus are not keys of the http"
                            + " action, which takes actor, client,")
                .endsWith(TWO_ACTIONS + " Write lines 4-9 as:\n" + """
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
                        """.stripTrailing());
        assertThat(CitrusTestChecks.validate(written(test, msgs.get(0)), dir, "test/orders.citrus.it.yaml")).isEmpty();
    }

    @Test
    void theTextOfTheRequestUnderHttpGoesUnderData() {
        // r28n-3 step 1: url, the order as text, and statusCode
        String test = """
                name: orders.citrus.it
                actions:
                  - http:
                      url: http://localhost:8080/orders
                      method: POST
                      body: |
                        {
                          "orderId": "ORD-1001",
                          "customer": "C-482"
                        }
                      statusCode: 201
                """;
        List<String> msgs = CitrusTestChecks.validate(test);

        assertThat(msgs).singleElement().asString()
                .startsWith("Line 4: url, method, body and statusCode are not keys of the http action")
                .endsWith(TWO_ACTIONS + " Write lines 3-11 as:\n" + """
                          - http:
                              client: "http://localhost:8080"
                              sendRequest:
                                POST:
                                  path: "/orders"
                                  body:
                                    data: |
                                      {
                                        "orderId": "ORD-1001",
                                        "customer": "C-482"
                                      }
                          - http:
                              client: "http://localhost:8080"
                              receiveResponse:
                                response:
                                  status: "201"
                        """.stripTrailing());
        assertThat(CitrusTestChecks.validate(written(test, msgs.get(0)))).isEmpty();
    }

    @Test
    void aRequestWithoutAStatusIsOneActionAndTheKeysWithNoPlaceAreSaid() {
        // r26n-7 step 1 wrote the request without its status; r26n-2 had type: Async, which Citrus has no place for
        String test = """
                name: orders.citrus.it
                actions:
                  - http:
                      uri: http://localhost:8080/orders
                      type: Async
                      method: POST
                      contentType: application/json
                      requestFile: sample-order.json
                """;
        List<String> msgs = CitrusTestChecks.validate(test);

        assertThat(msgs).singleElement().asString()
                .contains(", so the request is an action of its own, and a second http action checks the response with"
                          + " receiveResponse: {response: {status: \"200\"}} (type has no place in them and is left out)."
                          + " Write lines 3-8 as:\n  - http:\n")
                .doesNotContain("Async", "receiveResponse:\n");
        assertThat(CitrusTestChecks.validate(written(test, msgs.get(0)))).isEmpty();
    }

    @Test
    void aRequestUnderHttpWithoutItsUrlIsSaidByItsShape() {
        String test = """
                name: t
                actions:
                  - http:
                      method: GET
                      expectStatus: 200
                """;
        assertThat(CitrusTestChecks.validate(test)).singleElement().asString()
                .startsWith("Line 4: method and expectStatus are not keys of the http action")
                .endsWith(", so the request and the response it expects are two http actions, such as - http: {client:"
                          + " \"http://localhost:8080\", sendRequest: {POST: {path: \"/...\", body: {data: \"...\"}}}}"
                          + " and - http: {client: \"http://localhost:8080\", receiveResponse: {response: {status:"
                          + " \"200\"}}}");
        // a key close to one of http: what is said of it does not change
        assertThat(CitrusTestChecks.validate("""
                name: t
                actions:
                  - http:
                      clientt: c
                """)).containsExactly("Line 4: clientt (did you mean client?) is not a key of the http action, which"
                                      + " takes actor, client, description, receiveRequest, receiveResponse, sendRequest,"
                                      + " sendResponse, server");
    }

    /**
     * r28 test-citrus-orders step 2: five of seven runs had the whole case right except the check of the body, written
     * as text, equal or equalTo; the body the test expects is data, and its fields can be checked with jsonPath.
     */
    @ParameterizedTest
    @ValueSource(strings = { "text", "equal", "equalTo" })
    void theBodyTheTestExpectsIsData(String key) {
        String test = CASE_400.formatted(key + ": '{\"error\": \"an order needs a customer\"}'");
        List<String> msgs = CitrusTestChecks.validate(test);

        assertThat(msgs).singleElement().asString().isEqualTo("""
                Line 16: %s is not a key of http.receiveResponse.response.body, which takes data, resource, script; the\
                 text goes under data:, which is the body the test expects (Citrus compares a JSON body as JSON). To\
                 check fields instead of the whole body, validate them next to response: validate: [{jsonPath:\
                 [{expression: "$.error", value: "an order needs a customer"}]}]. Write lines 15-16 as:
                          body:
                            data: '{"error": "an order needs a customer"}'""".formatted(key));
        assertThat(CitrusTestChecks.validate(written(test, msgs.get(0)))).isEmpty();
        // the check the message offers instead
        assertThat(CitrusTestChecks.validate(CASE_400.formatted("data: x").replace("""
                          body:
                            data: x
                """, """
                        validate: [{jsonPath: [{expression: "$.error", value: "an order needs a customer"}]}]
                """))).isEmpty();
    }

    @Test
    void aPartOfTheBodyTheTestExpectsIsCheckedWithJsonPath() {
        // r28n-4 and r28n-7 step 2
        assertThat(CitrusTestChecks.validate(CASE_400.formatted("contains: \"an order needs a customer\"")))
                .containsExactly("Line 16: contains is not a key of http.receiveResponse.response.body, which takes"
                                 + " data, resource, script; data is the whole body the test expects, compared as a"
                                 + " whole. To check that one field holds this text, leave out body: and validate the"
                                 + " field next to response: validate: [{jsonPath: [{expression: \"$.<field>\", value:"
                                 + " \"an order needs a customer\"}]}]");
    }

    @Test
    void theTextOfARequestGoesUnderData() {
        // r28n-2 step 2: the body of the request as text
        String test = """
                name: t
                actions:
                  - http:
                      client: "http://localhost:8080"
                      sendRequest:
                        POST:
                          path: "/orders"
                          body: '{"orderId": "ORD-1002", "country": "US"}'
                """;
        List<String> msgs = CitrusTestChecks.validate(test);

        assertThat(msgs).singleElement().asString().isEqualTo("""
                Line 8: http.sendRequest.POST.body: string found, object expected; body takes data, resource, script; the\
                 text goes under data:, which is the body to send. Write line 8 as:
                          body:
                            data: '{"orderId": "ORD-1002", "country": "US"}'""");
        assertThat(CitrusTestChecks.validate(written(test, msgs.get(0)))).isEmpty();
    }

    /** The case of test-citrus-orders step 2, with the check of the body of the response to fill in. */
    private static final String CASE_400 = """
            name: t
            actions:
              - http:
                  client: "http://localhost:8080"
                  sendRequest:
                    POST:
                      path: "/orders"
                      body:
                        data: '{"orderId": "ORD-1002"}'
              - http:
                  client: "http://localhost:8080"
                  receiveResponse:
                    response:
                      status: "400"
                      body:
                        %s
            """;

    /** The test with the lines a message says to write in place of the lines it names. */
    private static String written(String test, String message) {
        Matcher m = Pattern.compile(" Write lines? (\\d+)(?:-(\\d+))? as:\n").matcher(message);
        assertThat(m.find()).as(message).isTrue();
        int first = Integer.parseInt(m.group(1));
        int last = m.group(2) != null ? Integer.parseInt(m.group(2)) : first;
        List<String> lines = new ArrayList<>(List.of(test.split("\n", -1)));
        lines.subList(first - 1, last).clear();
        lines.addAll(first - 1, List.of(message.substring(m.end()).split("\n")));
        return String.join("\n", lines);
    }

    @Test
    void aTestCutOffInTheMiddleOfAnAction() throws Exception {
        List<String> msgs = validate(ORDERS.resolve("cut-off.citrus.it.yaml"));

        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).isEqualTo("Line 72: http: string found, object expected; http takes actor, client,"
                                          + " description, receiveRequest, receiveResponse, sendRequest, sendResponse,"
                                          + " server");
    }

    @Test
    void aKeyCloseToAnActionIsSaid() {
        List<String> msgs = CitrusTestChecks.validate("""
                name: t
                actions:
                  - Echo:
                      message: a
                  - create-variables:
                      variables: []
                  - htpp:
                      client: c
                  - wait:
                      seconds: 1
                """);

        assertThat(msgs).hasSize(4);
        assertThat(msgs.get(0)).isEqualTo("Line 3: Echo is not a Citrus test action (did you mean echo?)");
        assertThat(msgs.get(1)).isEqualTo("Line 5: create-variables is not a Citrus test action (did you mean"
                                          + " createVariables?)");
        assertThat(msgs.get(2)).isEqualTo("Line 7: htpp is not a Citrus test action (did you mean http?)");
        // nothing close: the actions of the schema
        assertThat(msgs.get(3)).startsWith("Line 9: wait is not a Citrus test action; the actions are action, agent,")
                .contains(" echo, ", " http, ", " waitFor");
    }

    @Test
    void theActionsOfAContainerAreCheckedToo() {
        // the schema takes the actions of sequential, parallel, catch and waitFor as any object
        List<String> msgs = CitrusTestChecks.validate("""
                name: t
                actions:
                  - sequential:
                      actions:
                        - assertLog:
                            message: x
                        - echo:
                            msg: y
                        - parallel:
                            actions:
                              - sleep:
                                  millis: 5
                  - waitFor:
                      action:
                        htpp:
                          client: c
                finally:
                  - print:
                      text: bye
                """);

        assertThat(msgs).hasSize(5);
        assertThat(msgs.get(0)).startsWith("Line 5: assertLog is not a Citrus test action: echo:");
        assertThat(msgs.get(1)).isEqualTo("Line 8: msg is not a key of the echo action, which takes description, message");
        assertThat(msgs.get(2)).isEqualTo("Line 12: millis is not a key of the sleep action, which takes description,"
                                          + " milliseconds, seconds");
        assertThat(msgs.get(3)).isEqualTo("Line 15: htpp is not a Citrus test action (did you mean http?)");
        assertThat(msgs.get(4)).isEqualTo("Line 19: text is not a key of the print action, which takes description,"
                                          + " message");
    }

    @Test
    void twoActionsInOneListItem() {
        // a - left out: Citrus runs one of the two
        List<String> msgs = CitrusTestChecks.validate("""
                name: t
                actions:
                  - echo:
                      message: a
                    sleep:
                      milliseconds: 5
                  - echo:
                      message: b
                    echo:
                      message: c
                """);

        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).isEqualTo("Line 5: echo and sleep are two actions in one list item, and Citrus runs only"
                                          + " one of them: start each action with - on a line of its own");
        assertThat(msgs.get(1)).isEqualTo("Line 9: echo is given twice in one list item, and Citrus runs only one of them:"
                                          + " start each action with - on a line of its own");
    }

    @Test
    void theTestItself() {
        // a route, the way a model wrote one in place of the test
        assertThat(CitrusTestChecks.validate("""
                - route:
                    from:
                      uri: timer:tick
                """)).containsExactly("Line 1: array found, object expected: a Citrus test is a map with the keys"
                                      + " actions, author, description, endpoints, finally, name, status, variables");
        List<String> msgs = CitrusTestChecks.validate("""
                name: t
                status: final
                steps:
                  - echo:
                      message: a
                """);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).isEqualTo("Line 2: status: final is not one of DRAFT, READY_FOR_REVIEW, FINAL, DISABLED"
                                          + " (did you mean FINAL?)");
        assertThat(msgs.get(1)).isEqualTo("Line 3: steps is not a key of a Citrus test, which takes actions, author,"
                                          + " description, endpoints, finally, name, status, variables");
    }

    @Test
    void aBodyTakesOneOfItsKeys() {
        assertThat(CitrusTestChecks.validate("""
                name: t
                actions:
                  - http:
                      client: c
                      sendRequest:
                        POST:
                          path: /orders
                          body:
                            data: x
                            resource:
                              file: y
                """)).containsExactly("Line 9: http.sendRequest.POST.body: data and resource are given together, and"
                                      + " body takes only one of data, resource, script");
    }

    @Test
    void aScalarCitrusConvertsIsValid() {
        // SnakeYAML, which Citrus reads the test with, sets 201 as the text "201" and parses "3" as a number
        assertThat(CitrusTestChecks.validate("""
                name: t
                actions:
                  - sleep:
                      milliseconds: 500
                  - iterate:
                      times: "3"
                      actions:
                        - echo:
                            message: hi
                  - http:
                      client: c
                      receiveResponse:
                        response:
                          status: 201
                """)).isEmpty();
        assertThat(CitrusTestChecks.validate("""
                name: t
                actions:
                  - iterate:
                      times: three
                      actions:
                        - echo:
                            message: hi
                """)).containsExactly("Line 4: iterate.times: string found, integer expected");
    }

    @Test
    void anActionIsAMap() {
        List<String> msgs = CitrusTestChecks.validate("""
                name: t
                actions:
                  - echo "hi"
                  - {}
                  - echo:
                """);
        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0)).isEqualTo("Line 3: string found, object expected: an action is a map with the action as"
                                          + " its key, such as - echo: {message: \"...\"}");
        assertThat(msgs.get(1)).isEqualTo("Line 4: the list item has no action: write the action as its key, such as"
                                          + " - echo: {message: \"...\"}");
        assertThat(msgs.get(2)).isEqualTo("Line 5: echo: null found, object expected; echo takes description, message");
    }

    @Test
    void theYamlCheckSaysWhereATestDoesNotParse() {
        // the schema is for a test that parses: the YAML check comes first
        String content = """
                name: t
                actions:
                  - echo:
                      message: "a
                """;
        assertThat(CitrusTestChecks.validate(content)).isEmpty();
        assertThat(SourceValidator.validate("test/t.citrus.it.yaml", content, new DefaultCamelCatalog(), null))
                .singleElement().asString().contains("the YAML does not parse");
    }

    @Test
    void withoutTheSchemaATestIsCheckedForItsYamlOnly() throws Exception {
        // offline, with no catalog in the local Maven repository: no test is refused for the schema
        CitrusTestChecks.setTestSchema(null);
        try {
            assertThat(CitrusTestChecks.hasSchema()).isFalse();
            assertThat(validate(ORDERS.resolve("http-of-its-own.citrus.it.yaml"))).isEmpty();
            assertThat(AuthoringTools.citrusTestNote("test/orders.citrus.it.yaml"))
                    .contains("only its YAML syntax is checked, as the test schema of Citrus")
                    .contains("could not be loaded (no schema (test))");
        } finally {
            CitrusTestChecks.reset();
        }
        assertThat(CitrusTestChecks.hasSchema()).isTrue();
    }

    private static List<String> validate(Path file) throws Exception {
        return SourceValidator.validate(file.getFileName().toString(), Files.readString(file), new DefaultCamelCatalog(),
                null);
    }
}
