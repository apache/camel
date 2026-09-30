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
package org.apache.camel.dsl.yaml;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateTest extends YamlTestSupport {

    @Override
    public void doSetup() throws Exception {
        context.start();
    }

    @Test
    void aggregate() throws Exception {
        loadRoutes("""
                - beans:
                  - name: myAggregatorStrategy
                    type: org.apache.camel.processor.aggregate.UseLatestAggregationStrategy
                - from:
                    uri: "direct:route"
                    steps:
                      - aggregate:
                          aggregationStrategy: "myAggregatorStrategy"
                          completionSize: 2
                          correlationExpression:
                            simple: "${header.StockSymbol}"
                          steps:
                            - to: "mock:route"
                """);

        withMock("mock:route", mock -> mock.expectedBodiesReceived("2", "4"));

        withTemplate(t -> {
            t.to("direct:route").withBody("1").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("2").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("3").withHeader("StockSymbol", 2).send();
            t.to("direct:route").withBody("4").withHeader("StockSymbol", 2).send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void aggregateFlow() throws Exception {
        loadRoutes("""
                - beans:
                  - name: myAggregatorStrategy
                    type: org.apache.camel.processor.aggregate.UseLatestAggregationStrategy
                - from:
                    uri: "direct:route"
                    steps:
                      - aggregate:
                          aggregationStrategy: "myAggregatorStrategy"
                          completionSize: 2
                          correlationExpression:
                            simple: "${header.StockSymbol}"
                      - to: "mock:route"
                """);

        withMock("mock:route", mock -> mock.expectedBodiesReceived("2", "4"));

        withTemplate(t -> {
            t.to("direct:route").withBody("1").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("2").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("3").withHeader("StockSymbol", 2).send();
            t.to("direct:route").withBody("4").withHeader("StockSymbol", 2).send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void aggregateStrategyRefClass() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:route"
                    steps:
                      - aggregate:
                          aggregationStrategy: "#class:org.apache.camel.processor.aggregate.UseLatestAggregationStrategy"
                          completionSize: 2
                          correlationExpression:
                            simple: "${header.StockSymbol}"
                          steps:
                            - to: "mock:route"
                """);

        withMock("mock:route", mock -> mock.expectedBodiesReceived("2", "4"));

        withTemplate(t -> {
            t.to("direct:route").withBody("1").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("2").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("3").withHeader("StockSymbol", 2).send();
            t.to("direct:route").withBody("4").withHeader("StockSymbol", 2).send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void aggregateStrategyRefClassNotFound() {
        FailedToCreateRouteException ex = assertThrows(FailedToCreateRouteException.class, () -> loadRoutes("""
                - from:
                    uri: "direct:route"
                    steps:
                      - aggregate:
                          aggregationStrategy: "#class:com.foo.UseLatestAggregationStrategy"
                          completionSize: 2
                          correlationExpression:
                            simple: "${header.StockSymbol}"
                          steps:
                            - to: "mock:route"
                """));
        String msg = ex.getMessage() + "\n" + (ex.getCause() != null ? ex.getCause().getMessage() : "");
        assertTrue(msg.contains(
                "No bean could be found in the registry for: #class:com.foo.UseLatestAggregationStrategy of type: org.apache.camel.AggregationStrategy"),
                "Expected message about missing bean, got: " + msg);
        assertTrue(msg.contains("did you mean org.apache.camel.processor.aggregate.UseLatestAggregationStrategy"),
                "Expected 'did you mean' hint, got: " + msg);
    }

    @Test
    void aggregateStrategyRefUnknownClassListsBuiltInStrategies() {
        FailedToCreateRouteException ex = assertThrows(FailedToCreateRouteException.class, () -> loadRoutes("""
                - from:
                    uri: "direct:route"
                    steps:
                      - aggregate:
                          aggregationStrategy: "#class:com.foo.MyStrategy"
                          completionSize: 2
                          correlationExpression:
                            simple: "${header.StockSymbol}"
                          steps:
                            - to: "mock:route"
                """));
        String msg = ex.getMessage() + "\n" + (ex.getCause() != null ? ex.getCause().getMessage() : "");
        assertTrue(msg.contains("the built-in AggregationStrategy beans are"),
                "Expected built-in strategies listing, got: " + msg);
        assertTrue(
                msg.contains(
                        "UseLatestAggregationStrategy (org.apache.camel.processor.aggregate.UseLatestAggregationStrategy)"),
                "Expected UseLatestAggregationStrategy in listing, got: " + msg);
    }
}
