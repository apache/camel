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
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
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
    }

    @Test
    void anHttpActionWithKeysOfItsOwnAndLogActions() throws Exception {
        List<String> msgs = validate(ORDERS.resolve("http-of-its-own.citrus.it.yaml"));

        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0)).isEqualTo("Line 32: uri, method, requestFile, contentType and expectStatus are not keys of"
                                          + " the http action, which takes actor, client, description, receiveRequest,"
                                          + " receiveResponse, sendRequest, sendResponse, server");
        assertThat(msgs.get(1)).isEqualTo("Line 39: log is not a Citrus test action: " + LOG_HINT);
        assertThat(msgs.get(2)).isEqualTo("Line 44: assert-log is not a Citrus test action: " + LOG_HINT);
    }

    @Test
    void anHttpActionWithARequestAndAResponse() throws Exception {
        List<String> msgs = validate(ORDERS.resolve("request-and-response.citrus.it.yaml"));

        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).startsWith("Line 30: request and response are not keys of the http action, which takes")
                .contains("sendRequest", "receiveResponse");
        assertThat(msgs.get(1)).startsWith("Line 53: log is not a Citrus test action");
    }

    @Test
    void aBodyIsAnObjectWithItsData() throws Exception {
        // step 2: the body of a request and of a response given as text, which is body: {data: ...}
        List<String> msgs = validate(ORDERS.resolve("body-as-text.citrus.it.yaml"));

        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).isEqualTo("Line 60: http.sendRequest.POST.body: string found, object expected; body takes"
                                          + " data, resource, script");
        assertThat(msgs.get(1)).isEqualTo("Line 66: http.receiveResponse.response.body: string found, object expected;"
                                          + " body takes data, resource, script");
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
