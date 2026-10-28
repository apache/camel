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
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24955: a direct: or seda: endpoint a route sends to, and no route of the application consumes. With direct: the
 * exchange fails; with seda: nothing fails and the message is never read. The endpoints and EIPs that count are the
 * {@link EndpointRules} camel overview uses too (CAMEL-25180).
 */
public class EndpointConsumersTest {

    private static final String SENDS_TO = """
            - route:
                id: tick
                from:
                  uri: timer:tick
                  steps:
                    - to:
                        uri: %s
            """;

    @Test
    public void testNoRouteConsumesTheDirectEndpoint() {
        List<String> messages = EndpointConsumers.check(SENDS_TO.formatted("direct:lookup"), Set.of());
        assertThat(messages).containsExactly(
                "route tick: sends to direct:lookup, and no route consumes it - not in this file, nor in the other route"
                                             + " files of the directory: the exchange fails with No consumers available"
                                             + " on endpoint; add a route with from: direct:lookup, or correct the name");
    }

    @Test
    public void testAnotherFileConsumesIt() {
        // the routes of an application are spread over files: what the siblings consume is passed in
        assertThat(EndpointConsumers.check(SENDS_TO.formatted("direct:lookup?timeout=1000"), Set.of("direct:lookup")))
                .isEmpty();
    }

    @Test
    public void testWithoutADirectoryTheCheckSaysNothing() {
        // a file on its own cannot know what the rest of the application consumes
        assertThat(EndpointConsumers.check(SENDS_TO.formatted("direct:lookup"), null)).isEmpty();
    }

    @Test
    public void testTheSameFileConsumesIt() {
        // both spellings of the endpoint: direct://lookup, and uri: direct with parameters: {name: lookup}
        String yaml = SENDS_TO.formatted("direct://lookup") + """
                - route:
                    from:
                      uri: direct
                      parameters:
                        name: lookup
                      steps:
                        - log: found
                """;
        assertThat(EndpointConsumers.check(yaml, Set.of())).isEmpty();
    }

    @Test
    public void testSedaSaysTheMessageIsNeverRead() {
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - choice:
                            when:
                              - simple: "${header.urgent}"
                                steps:
                                  - wireTap:
                                      uri: seda:audit
                """;
        assertThat(EndpointConsumers.check(yaml, Set.of())).containsExactly(
                "sends to seda:audit, and no route consumes it - not in this file, nor in the other route files of the"
                                                                            + " directory: nothing fails, the messages are queued and never read;"
                                                                            + " add a route with from: seda:audit, or correct the name");
    }

    @Test
    public void testAnExternalEndpointIsNotItsBusiness() {
        // kafka:, vm: (another context) and an endpoint only known at runtime are not the routes of this application
        assertThat(EndpointConsumers.check(SENDS_TO.formatted("kafka:orders"), Set.of())).isEmpty();
        assertThat(EndpointConsumers.check(SENDS_TO.formatted("vm:orders"), Set.of())).isEmpty();
        assertThat(EndpointConsumers.check(SENDS_TO.formatted("direct:{{target}}"), Set.of())).isEmpty();
        assertThat(EndpointConsumers.check(SENDS_TO.formatted("\"direct:${header.target}\""), Set.of())).isEmpty();
    }

    @Test
    public void testConsumedListsTheDirectAndSedaRoutesOfAFile() {
        String yaml = """
                - from:
                    uri: seda:orders?concurrentConsumers=4
                    steps:
                      - log: order
                - route:
                    from:
                      uri: direct://lookup
                      steps:
                        - log: found
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - log: tick
                """;
        assertThat(EndpointConsumers.consumed(yaml)).containsExactlyInAnyOrder("seda:orders", "direct:lookup");
        assertThat(EndpointConsumers.consumed("not: [valid")).isEmpty();
    }

    @Test
    public void testAConsumerOnlyKnownAtRuntimeKeepsTheCheckQuiet() {
        // from: direct:{{name}} may well be direct:lookup: the check cannot be certain, so it says nothing
        String yaml = SENDS_TO.formatted("direct:lookup") + """
                - route:
                    from:
                      uri: "direct:{{name}}"
                      steps:
                        - log: found
                """;
        assertThat(EndpointConsumers.check(yaml, Set.of())).isEmpty();
        assertThat(EndpointConsumers.consumed(yaml)).isNull();
    }

    @Test
    public void testARouteTemplateKeepsTheCheckQuiet() {
        // the templated route lookup is made from the template, and consumes direct:lookup once the template is
        // instantiated: the routes a template creates are not read, so the check says nothing
        String yaml = SENDS_TO.formatted("direct:lookup") + """
                - routeTemplate:
                    id: lookup-template
                    parameters:
                      - name: name
                    from:
                      uri: "direct:{{name}}"
                      steps:
                        - log: found
                - templatedRoute:
                    routeTemplateRef: lookup-template
                    parameters:
                      - name: name
                        value: lookup
                """;
        assertThat(EndpointConsumers.check(yaml, Set.of())).isEmpty();
        assertThat(EndpointConsumers.consumed(yaml)).isNull();
    }

    @Test
    public void testAKameletConsumesWhatIsNotKnown() {
        String kamelet = """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: lookup-action
                spec:
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - to: direct:lookup
                """;
        assertThat(EndpointConsumers.consumed(kamelet)).isNull();
    }

    @Test
    public void testAPollingStepConsumesTheEndpoint() {
        // a poll or a pollEnrich of seda:orders reads the queue: the route consumes it, as camel overview counts it
        String yaml = SENDS_TO.formatted("seda:orders") + """
                - route:
                    from:
                      uri: timer:batch
                      steps:
                        - pollEnrich:
                            constant: seda:orders
                            timeout: 1000
                """;
        assertThat(EndpointConsumers.check(yaml, Set.of())).isEmpty();
        assertThat(EndpointConsumers.consumed(yaml)).containsExactly("seda:orders");
    }

    @Test
    public void testDisruptorIsCheckedAndNotMixedWithSeda() {
        // disruptor:q and seda:q are different endpoints at runtime
        assertThat(EndpointConsumers.check(SENDS_TO.formatted("disruptor:orders"), Set.of("seda:orders")))
                .singleElement().asString().contains("sends to disruptor:orders");
        assertThat(EndpointConsumers.check(SENDS_TO.formatted("disruptor-vm:orders"), Set.of())).isEmpty();
    }

    @Test
    public void testRestDeadLetterAndRouteConfigurationSend() {
        String yaml = """
                - rest:
                    get:
                      - path: /orders
                        to: direct:orders
                - routeConfiguration:
                    onException:
                      - onException:
                          exception:
                            - java.lang.Exception
                          steps:
                            - to: seda:errors
                - errorHandler:
                    deadLetterChannel:
                      deadLetterUri: direct:dlq
                """;
        assertThat(EndpointConsumers.check(yaml, Set.of()))
                .hasSize(3)
                .anySatisfy(m -> assertThat(m).contains("sends to direct:orders"))
                .anySatisfy(m -> assertThat(m).contains("sends to seda:errors"))
                .anySatisfy(m -> assertThat(m).contains("sends to direct:dlq"));
    }

    @Test
    public void testTheFlowsOfAnIntegrationConsume() {
        String integration = """
                apiVersion: camel.apache.org/v1
                kind: Integration
                metadata:
                  name: lookup
                spec:
                  flows:
                    - from:
                        uri: direct:lookup
                        steps:
                          - to: seda:audit
                """;
        assertThat(EndpointConsumers.consumed(integration)).containsExactly("direct:lookup");
        assertThat(EndpointConsumers.check(integration, Set.of()))
                .singleElement().asString().contains("sends to seda:audit");
    }

    @Test
    public void testAPipeConsumesWhatIsNotKnown() {
        String pipe = """
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: orders
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      name: timer-source
                  sink:
                    uri: direct:orders
                """;
        assertThat(EndpointConsumers.consumed(pipe)).isNull();
        // a YAML file that is not a route file consumes nothing, and does not keep the check quiet
        assertThat(EndpointConsumers.consumed("services:\n  db:\n    image: postgres\n")).isEmpty();
    }

    @Test
    public void testAnExternalConsumerOnlyKnownAtRuntimeDoesNotKeepTheCheckQuiet() {
        // kafka:{{topic}} is no direct: or seda: endpoint, whatever the topic; {{endpoint}} could be any of them
        String yaml = SENDS_TO.formatted("direct:lookup") + """
                - from:
                    uri: "kafka:{{topic}}"
                    steps:
                      - log: got
                """;
        assertThat(EndpointConsumers.check(yaml, Set.of())).hasSize(1);
        String anyEndpoint = SENDS_TO.formatted("direct:lookup") + """
                - from:
                    uri: "{{endpoint}}"
                    steps:
                      - log: got
                """;
        assertThat(EndpointConsumers.consumed(anyEndpoint)).isNull();
    }

}
