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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.Capabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.FlowAnimation.Reading;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.FlowFeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The messages moving between the groups of the architecture: along a link, the messages into the routes of the group
 * it goes to, from the routes that send them; into the error handling, the failures of a group.
 */
class ArchitectureFlowTest {

    private static final String ROUTES = """
            - route:
                id: order
                group: shop
                from:
                  uri: platform-http:/orders
                  steps:
                    - doTry:
                        steps:
                          - to: direct:invoice
                        doCatch:
                          - exception:
                              - java.lang.Exception
                            steps:
                              - to: direct:dlq
            - route:
                id: invoice
                group: billing
                from:
                  uri: direct:invoice
                  steps:
                    - to: kafka:invoices
            - route:
                id: dlq
                from:
                  uri: direct:dlq
                  steps:
                    - to: kafka:dlq
            """;

    @TempDir
    Path project;

    private ProjectOverview.Overview overview;
    private Capabilities caps;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(project.resolve("shop.camel.yaml"), ROUTES, StandardCharsets.UTF_8);
        overview = ProjectOverview.analyze(project, new DefaultCamelCatalog());
        caps = ProjectCapabilities.withErrorHandling(overview, ProjectCapabilities.build(overview, null));
    }

    @Test
    void aLinkMovesAtTheMessagesIntoTheGroupItGoesTo() {
        FlowFeed feed = ArchitectureFlow.feed(overview, caps,
                List.of(route("order", 100, 0, 0), route("invoice", 90, 4, 0), route("dlq", 10, 0, 0)),
                List.of(), UnaryOperator.identity());

        String link = FlowFeed.edgeId(caps.groupOf().get("order"), caps.groupOf().get("invoice"));
        assertThat(feed.edges()).containsKey(link);
        FlowFeed.Edge edge = feed.edges().get(link);
        assertThat(edge.senders()).as("the routes that send along it").containsExactly("route/order");
        Reading reading = feed.readings().get(edge.key());
        assertThat(reading.total()).as("the messages into invoice").isEqualTo(90);
        assertThat(reading.failed()).isEqualTo(4);
    }

    @Test
    void theFailuresOfAGroupGoToTheErrorHandlingInRed() {
        FlowFeed feed = ArchitectureFlow.feed(overview, caps,
                List.of(route("order", 100, 3, 7), route("invoice", 90, 0, 0), route("dlq", 10, 0, 0)),
                List.of(), UnaryOperator.identity());

        String shop = caps.groupOf().get("order");
        FlowFeed.Edge edge = feed.edges().get(FlowFeed.edgeId(shop, ProjectCapabilities.ERRORS));
        assertThat(edge).as("the shop group sends its failures to the error handling").isNotNull();
        Reading reading = feed.readings().get(edge.key());
        assertThat(reading.total()).as("failed and handled").isEqualTo(10);
        assertThat(reading.failed()).as("all of them failures: red").isEqualTo(reading.total());
    }

    @Test
    void aSourceRouteIsKnownByTheIdItRunsWith() {
        // a route without an id in the source runs with the id Camel made up
        FlowFeed feed = ArchitectureFlow.feed(overview, caps,
                List.of(route("order", 100, 0, 0), route("route2", 90, 0, 0)),
                List.of(), key -> "invoice".equals(key) ? "route2" : key);

        assertThat(feed.readings()).containsKey("route/invoice");
        assertThat(feed.readings().get("route/invoice").total()).isEqualTo(90);
    }

    @Test
    void noRunningRoutesNoFlow() {
        assertThat(ArchitectureFlow.feed(overview, caps, List.of(), List.of(), UnaryOperator.identity())).isNull();
        assertThat(ArchitectureFlow.feed(overview, caps, List.of(route("other", 5, 0, 0)), List.of(),
                UnaryOperator.identity()))
                .as("another project's routes").isNull();
    }

    @Test
    void aSystemMovesAtTheMessagesOfTheRouteItFeedsOrOfTheEndpointSentTo() {
        // as the running integration writes its endpoints
        FlowFeed feed = ArchitectureFlow.feed(overview, caps,
                List.of(route("order", 100, 0, 0), route("invoice", 90, 0, 0), route("dlq", 10, 0, 0)),
                // a polling consumer counts no hits: the route has the messages it got
                List.of(endpoint("platform-http:///orders", "in", "order", 0),
                        endpoint("kafka://invoices?brokers=x", "out", "invoice", 42)),
                UnaryOperator.identity());

        String in = FlowFeed.edgeId(ArchitectureView.systemId("Platform HTTP", true), caps.groupOf().get("order"));
        String out = FlowFeed.edgeId(caps.groupOf().get("invoice"), ArchitectureView.systemId("Kafka", false));
        assertThat(feed.edges()).containsKeys(in, out);
        assertThat(feed.readings().get(feed.edges().get(in).key()).total()).as("the messages of order").isEqualTo(100);
        assertThat(feed.readings().get(feed.edges().get(out).key()).total()).as("the hits of kafka:invoices")
                .isEqualTo(42);
    }

    @Test
    void withoutTheCountsOfItsEndpointASystemMovesAtItsRoute() {
        FlowFeed feed = ArchitectureFlow.feed(overview, caps,
                List.of(route("order", 100, 0, 0), route("invoice", 90, 0, 0), route("dlq", 10, 0, 0)),
                List.of(), UnaryOperator.identity());

        String out = FlowFeed.edgeId(caps.groupOf().get("invoice"), ArchitectureView.systemId("Kafka", false));
        assertThat(feed.readings().get(feed.edges().get(out).key()).total()).isEqualTo(90);
    }

    @Test
    void anEndpointUriIsKnownWithOrWithoutItsSlashesAndOptions() {
        assertThat(ArchitectureFlow.endpointKey("file:inbox?delete=true&delay=200"))
                .isEqualTo(ArchitectureFlow.endpointKey("file://inbox?delay=200&delete=true"));
        assertThat(ArchitectureFlow.endpointKey("Kafka:orders")).isEqualTo("kafka:orders");
    }

    private static EndpointInfo endpoint(String uri, String direction, String routeId, long hits) {
        EndpointInfo e = new EndpointInfo();
        e.uri = uri;
        e.direction = direction;
        e.routeId = routeId;
        e.hits = hits;
        return e;
    }

    private static RouteInfo route(String id, long total, long failed, long handled) {
        RouteInfo r = new RouteInfo();
        r.routeId = id;
        r.total = total;
        r.failed = failed;
        r.failuresHandled = handled;
        return r;
    }
}
