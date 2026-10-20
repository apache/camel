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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.diagram.RouteDiagramHelper;
import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.TopologyHelper;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

/**
 * The diagrams ask for the structure of the routes only when the routes change, and take the numbers from the status
 * file (CAMEL-25630).
 */
class DiagramTopologyTest {

    /** As the route-topology console answers without numbers: route a reads kafka:in, sends to kafka:out and b. */
    private static final String TOPOLOGY
            = """
                    {
                      "nodes": [
                        { "routeId": "a", "from": "kafka:in", "fromScheme": "kafka", "nodeType": "route" },
                        { "routeId": "b", "from": "direct:b", "fromScheme": "direct", "nodeType": "route" }
                      ],
                      "edges": [
                        { "fromRouteId": "a", "toRouteId": "b", "endpoint": "direct:b", "connectionType": "direct" }
                      ],
                      "externalEndpoints": [
                        { "id": "ext-in", "uri": "kafka:in", "scheme": "kafka", "direction": "in", "routeId": "a" },
                        { "id": "ext-out", "uri": "kafka:out", "scheme": "kafka", "direction": "out", "routeId": "a" }
                      ],
                      "routes": [
                        { "routeId": "a", "from": "kafka:in", "code": [
                          { "line": 1, "type": "route", "id": "a", "level": 0, "code": "route" },
                          { "line": 2, "type": "from", "id": "a", "level": 0, "code": "from(\\"kafka:in\\")", "uri": "kafka:in" },
                          { "line": 3, "type": "to", "id": "to1", "level": 1, "code": "to(\\"kafka:out\\")", "uri": "kafka:out" },
                          { "line": 4, "type": "wireTap", "id": "tap1", "level": 1, "code": "wireTap(\\"kafka:out\\")", "uri": "kafka:out" },
                          { "line": 5, "type": "to", "id": "to2", "level": 1, "code": "to(\\"direct:b\\")", "uri": "direct:b" }
                        ] }
                      ]
                    }""";

    /** The status file of the integration, as the route console writes it with the processors. */
    private static final String STATUS = """
            {
              "context": { "name": "orders", "statistics": { "reload": { "reloaded": 0 } } },
              "routes": [
                { "routeId": "a", "state": "Started",
                  "statistics": { "exchangesTotal": 10, "exchangesFailed": 1, "coverage": "4/4",
                                  "load01": "0.10", "load05": "0.05", "load15": "0.01", "meanProcessingTime": 4 },
                  "processors": [
                    { "id": "to1", "processor": "to", "uri": "kafka://out?brokers=localhost:9092",
                      "statistics": { "exchangesTotal": 9, "exchangesFailed": 1, "meanProcessingTime": 3 } },
                    { "id": "tap1", "processor": "wireTap", "uri": "kafka:out",
                      "statistics": { "exchangesTotal": 2, "exchangesFailed": 0 } },
                    { "id": "to2", "processor": "to", "uri": "direct://b",
                      "statistics": { "exchangesTotal": 8, "exchangesFailed": 0 } }
                  ] },
                { "routeId": "b", "state": "Started", "statistics": { "exchangesTotal": 8, "exchangesFailed": 0 } }
              ]
            }""";

    private static JsonObject json(String text) {
        JsonObject answer = Jsoner.deserialize(text, new JsonObject());
        assertThat(answer).as("the fixture is JSON").isNotEmpty();
        return answer;
    }

    private static IntegrationInfo status() {
        return StatusParser.parseIntegration(ProcessHandle.current(), json(STATUS));
    }

    @Test
    void theRoutesHaveTheirTotalsFromTheStatusFile() {
        JsonObject topology = DiagramTopology.withNumbers(json(TOPOLOGY), status());

        assertThat(TopologyHelper.parseNodes(topology))
                .extracting(n -> n.routeId, n -> n.exchangesTotal, n -> n.exchangesFailed)
                .containsExactly(tuple("a", 10L, 1L), tuple("b", 8L, 0L));
    }

    @Test
    void aSystemReadFromHasTheRoutesMessagesAndOneSentToThoseOfTheStepsSendingToIt() {
        JsonObject topology = DiagramTopology.withNumbers(json(TOPOLOGY), status());

        List<TopologyNodeInfo> nodes = new ArrayList<>();
        List<TopologyEdgeInfo> edges = new ArrayList<>();
        TopologyHelper.addExternalEndpoints(nodes, edges, topology);

        // the to and the wireTap both send to kafka:out, one with options and // in its uri
        assertThat(nodes)
                .extracting(n -> n.routeId, n -> n.exchangesTotal, n -> n.exchangesFailed)
                .containsExactly(tuple("ext-in", 10L, 1L), tuple("ext-out", 11L, 1L));
    }

    @Test
    void theStepsHaveTheirStatisticsByIdAndTheFromLineTheRoutesWithoutItsLoad() {
        JsonObject topology = DiagramTopology.withNumbers(json(TOPOLOGY), status());

        List<RouteDiagramLayoutEngine.NodeInfo> lines = RouteDiagramHelper.parseRoutes(topology).get(0).nodes;

        assertThat(lines).extracting(n -> n.id, n -> n.stat.exchangesTotal, n -> n.stat.exchangesFailed)
                .containsExactly(tuple("a", 10L, 1L), tuple("a", 10L, 1L), tuple("to1", 9L, 1L), tuple("tap1", 2L, 0L),
                        tuple("to2", 8L, 0L));
        assertThat(lines.get(0).stat).isInstanceOf(RouteDiagramLayoutEngine.RouteStatInfo.class);
        assertThat(((RouteDiagramLayoutEngine.RouteStatInfo) lines.get(0).stat).coverage).isEqualTo("4/4");
        assertThat(lines.get(2).stat.meanProcessingTime).isEqualTo(3);
        JsonObject from = topology.getJsonArray("routes").getJsonObject(0).getJsonArray("code").getJsonObject(1);
        assertThat(from.getJsonObject("statistics")).containsKey("meanProcessingTime")
                .doesNotContainKeys("coverage", "load01", "load05", "load15");
    }

    @Test
    void theStepsHaveTheBodyTypesOfTheStatusFileAsMoreMessagesCome() {
        IntegrationInfo info = status();
        ProcessorInfo to1 = info.routes.get(0).processors.get(0);
        to1.bodyTypes = List.of("byte[]", "java.util.LinkedHashMap");
        to1.bodyKinds = List.of("payload", "data");

        JsonObject topology = DiagramTopology.withNumbers(json(TOPOLOGY), info);

        List<RouteDiagramLayoutEngine.NodeInfo> lines = RouteDiagramHelper.parseRoutes(topology).get(0).nodes;
        assertThat(lines.get(2).id).isEqualTo("to1");
        assertThat(lines.get(2).bodyTypes).containsExactly("byte[]", "java.util.LinkedHashMap");
        assertThat(lines.get(2).bodyKinds).containsExactly("payload", "data");
        // a step not reached yet has none, nor have the route and its from
        assertThat(lines.get(3).bodyTypes).isEmpty();
        assertThat(lines.get(0).bodyTypes).isEmpty();
        assertThat(lines.get(1).bodyTypes).isEmpty();
    }

    @Test
    void theStructureKeptForTheNextTimeIsLeftWithoutNumbers() {
        JsonObject structure = json(TOPOLOGY);

        DiagramTopology.withNumbers(structure, status());

        assertThat(structure).isEqualTo(json(TOPOLOGY));
    }

    @Test
    void theStructureIsAskedForOnceAndAgainWhenTheRoutesChange() {
        IntegrationInfo info = status();
        AnsweringContext ctx = new AnsweringContext(info);
        DiagramTopology topology = new DiagramTopology();

        assertThat(topology.load(ctx, info.pid)).isNotNull();
        assertThat(topology.load(ctx, info.pid)).isNotNull();
        // the numbers are not asked for: they are in the status file
        assertThat(ctx.metrics).containsExactly("false");

        // the same routes, listed in another order
        Collections.reverse(info.routes);
        topology.load(ctx, info.pid);
        assertThat(ctx.metrics).hasSize(1);

        info.reloaded = 1;
        topology.load(ctx, info.pid);
        assertThat(ctx.metrics).hasSize(2);

        RouteInfo added = new RouteInfo();
        added.routeId = "c";
        info.routes.add(added);
        topology.load(ctx, info.pid);
        assertThat(ctx.metrics).hasSize(3);

        topology.load(ctx, info.pid);
        assertThat(ctx.metrics).hasSize(3);
    }

    @Test
    void withoutAStatusTheNumbersComeWithTheStructure() {
        AnsweringContext ctx = new AnsweringContext(status());
        ctx.data.set(List.of());
        DiagramTopology topology = new DiagramTopology();

        topology.load(ctx, "123");
        topology.load(ctx, "123");

        assertThat(ctx.metrics).containsExactly("true", "true");
    }

    @Test
    void aStructureNotAnsweredIsAskedForAgain() {
        IntegrationInfo info = status();
        AnsweringContext ctx = new AnsweringContext(info);
        ctx.unanswered = 1;
        DiagramTopology topology = new DiagramTopology();

        assertThat(topology.load(ctx, info.pid)).isNull();
        assertThat(topology.load(ctx, info.pid)).isNotNull();

        assertThat(ctx.metrics).containsExactly("false", "false");
    }

    @Test
    void tabsLoadingTogetherAskOnce() throws InterruptedException {
        IntegrationInfo info = status();
        AnsweringContext ctx = new AnsweringContext(info);
        ctx.answer = new CountDownLatch(1);
        DiagramTopology topology = new DiagramTopology();
        List<JsonObject> loaded = new CopyOnWriteArrayList<>();

        Thread routesTab = new Thread(() -> loaded.add(topology.load(ctx, info.pid)));
        routesTab.start();
        await().atMost(5, TimeUnit.SECONDS).until(() -> ctx.metrics.size() == 1);
        Thread diagramTab = new Thread(() -> loaded.add(topology.load(ctx, info.pid)));
        diagramTab.start();
        // the second waits for the answer to the first, or asks as well
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> diagramTab.getState() == Thread.State.BLOCKED || ctx.metrics.size() > 1);
        ctx.answer.countDown();
        routesTab.join(5000);
        diagramTab.join(5000);

        assertThat(ctx.metrics).containsExactly("false");
        assertThat(loaded).hasSize(2).doesNotContainNull();
    }

    @Test
    void endpointsMatchWithoutTheirOptionsAndTheSlashesAfterTheScheme() {
        assertThat(DiagramTopology.endpointKey("kafka://out?brokers=localhost:9092")).isEqualTo("kafka:out");
        assertThat(DiagramTopology.endpointKey("kafka:out")).isEqualTo("kafka:out");
        assertThat(DiagramTopology.endpointKey("http://host:8080/api?q=1")).isEqualTo("http:host:8080/api");
    }

    /** An integration whose status is the one given, and that answers the topology request. */
    private static final class AnsweringContext extends MonitorContext {

        final List<String> metrics = new CopyOnWriteArrayList<>();
        int unanswered;
        /** When set, the answer waits for it. */
        CountDownLatch answer;

        AnsweringContext(IntegrationInfo info) {
            super(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        }

        @Override
        JsonObject executeAction(String pid, JsonObject request, long timeoutMs) {
            assertThat(request.getString("action")).isEqualTo("route-topology");
            assertThat(request.getString("routes")).isEqualTo("true");
            metrics.add(request.getString("metric"));
            if (answer != null) {
                try {
                    answer.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (unanswered > 0) {
                unanswered--;
                return null;
            }
            return json(TOPOLOGY);
        }
    }
}
