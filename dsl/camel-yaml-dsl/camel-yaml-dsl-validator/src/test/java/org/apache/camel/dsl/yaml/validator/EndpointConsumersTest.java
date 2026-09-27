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
 * route fails to start; with seda: nothing fails and the message is never read.
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
                                             + " files of the directory: the route fails to start with No consumers available"
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
}
