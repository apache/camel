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
package org.apache.camel.dsl.yaml.validator;

import java.util.List;

import com.networknt.schema.Error;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24844: a route that reads the body although nothing in the file ever sets one. The answer follows the direct:
 * edges between the routes, the way the route topology does at runtime.
 */
public class BodyTypeFlowTest {

    private static YamlValidator validator;

    @BeforeAll
    public static void setup() throws Exception {
        validator = new YamlValidator();
        validator.init();
    }

    @Test
    public void testTheBodyIsNeverSetInTheChain() {
        // what the benchmark wrote, with the verb spelled out: a GET carries no body, getStock passes it on, and
        // lookup reads it with jsonpath
        String yaml = """
                - rest:
                    get:
                      - path: /stock/{sku}
                        to: direct:getStock
                - route:
                    id: getStock
                    from:
                      uri: direct:getStock
                      steps:
                        - to:
                            uri: direct:lookup
                - route:
                    id: lookup
                    from:
                      uri: direct:lookup
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.sku == '${header.sku}')]"
                """;
        assertThat(messages(yaml))
                .anyMatch(m -> m.contains("jsonpath reads the message body")
                        && m.contains("has none")
                        && m.contains("resource:file:"));
    }

    @Test
    public void testATimerAtTheRootOfTheChain() {
        String yaml = """
                - route:
                    id: tick
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: direct:lookup
                - route:
                    id: lookup
                    from:
                      uri: direct:lookup
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.sku"
                """;
        assertThat(messages(yaml)).anyMatch(m -> m.contains("reads the message body"));
    }

    @Test
    public void testAPostCarriesABodySoNothingIsSaid() {
        String yaml = """
                - rest:
                    post:
                      - path: /orders
                        to: direct:orders
                - route:
                    id: orders
                    from:
                      uri: direct:orders
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.sku"
                """;
        assertThat(messages(yaml)).noneMatch(m -> m.contains("reads the message body"));
    }

    @Test
    public void testAnOpenApiBindingHidesTheVerbSoNothingIsSaid() {
        // the verb lives in the specification, not in the route: this is the gap, and it must stay quiet
        String yaml = """
                - rest:
                    openApi:
                      specification: stock-api.json
                - route:
                    id: getStock
                    from:
                      uri: direct:getStock
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.sku"
                """;
        assertThat(messages(yaml)).noneMatch(m -> m.contains("reads the message body"));
    }

    @Test
    public void testTheReadIsThere() {
        String yaml = """
                - route:
                    id: lookup
                    from:
                      uri: direct:lookup
                      steps:
                        - setBody:
                            expression:
                              constant:
                                expression: resource:file:stock.json
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.sku == '${header.sku}')]"
                """;
        assertThat(messages(yaml)).noneMatch(m -> m.contains("reads the message body"));
    }

    @Test
    public void testTheCallerSetsTheBody() {
        String yaml = """
                - route:
                    id: caller
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            expression:
                              constant:
                                expression: resource:file:stock.json
                        - to:
                            uri: direct:lookup
                - route:
                    id: lookup
                    from:
                      uri: direct:lookup
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.sku"
                """;
        assertThat(messages(yaml)).noneMatch(m -> m.contains("reads the message body"));
    }

    @Test
    public void testAConsumerThatBringsABody() {
        String yaml = """
                - route:
                    id: fromFile
                    from:
                      uri: file:inbox
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.sku"
                """;
        assertThat(messages(yaml)).noneMatch(m -> m.contains("reads the message body"));
    }

    @Test
    public void testARouteNobodyInTheFileCalls() {
        // the caller may be in another file: say nothing
        String yaml = """
                - route:
                    id: lookup
                    from:
                      uri: direct:lookup
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.sku"
                """;
        assertThat(messages(yaml)).noneMatch(m -> m.contains("reads the message body"));
    }

    private List<String> messages(String yaml) {
        try {
            return validator.validate(yaml).stream().map(Error::getMessage).toList();
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }
}
