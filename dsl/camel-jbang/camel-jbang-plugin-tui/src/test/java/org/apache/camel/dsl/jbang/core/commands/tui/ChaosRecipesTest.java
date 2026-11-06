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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The known rules of the chaos model ({@link ChaosRecipes}): parsed from the catalog, filled in, and the values to pick
 * from in an integration.
 */
class ChaosRecipesTest {

    // the shape of the recipes of the chaos model of the catalog
    static final String MODEL = """
            {"chaos": {"recipes": [
              {"id": "http-error", "group": "Downstream", "title": "Returns an error (HTTP status)",
               "description": "An HTTP call fails with a status",
               "rule": "endpoint=${endpoint} error status=${status} rate=${rate}",
               "placeholders": [
                 {"name": "endpoint", "type": "endpoint", "description": "The HTTP endpoint the route calls",
                  "example": "https://inventory/stock", "pick": {"from": "producers", "schemes": ["http", "https"]}},
                 {"name": "status", "type": "integer", "description": "The HTTP status", "default": "503", "example": "503"},
                 {"name": "rate", "type": "rate", "description": "How often", "default": "0.25", "example": "0.25"}]},
              {"id": "stall", "group": "Consumers and routes", "title": "The route stalls",
               "description": "The route is suspended for a while",
               "rule": "route=${route} stall duration=${duration}",
               "placeholders": [
                 {"name": "route", "type": "route", "description": "The route", "example": "orders",
                  "pick": {"from": "routes"}},
                 {"name": "duration", "type": "duration", "description": "How long", "default": "20s", "example": "20s"}]},
              {"id": "corrupt-entry", "group": "Messages", "title": "Arrives corrupt",
               "rule": "route=${route} corrupt mode=${mode}",
               "placeholders": [
                 {"name": "route", "type": "route", "example": "orders", "pick": {"from": "routes"}},
                 {"name": "mode", "type": "enum", "default": "headers", "example": "headers",
                  "pick": {"from": "values", "values": ["headers", "charset", "empty"]}}]},
              {"id": "missing-header", "group": "Messages", "title": "Is sent without a header",
               "rule": "endpoint=${endpoint} corrupt mode=remove-header header=${header}",
               "placeholders": [
                 {"name": "endpoint", "type": "endpoint", "example": "kafka:orders", "pick": {"from": "producers"}},
                 {"name": "header", "type": "string", "example": "X-Trace-Id", "pick": {"from": "headers"}}]},
              {"id": "step-error", "group": "Error handling", "title": "A step fails",
               "rule": "step=${step} error",
               "placeholders": [{"name": "step", "type": "step", "example": "enrich", "pick": {"from": "steps"}}]},
              {"id": "poll-error", "group": "Consumers and routes", "title": "Polls fail",
               "rule": "endpoint=${endpoint} error at=poll",
               "placeholders": [{"name": "endpoint", "type": "endpoint", "example": "file:inbox",
                                 "pick": {"from": "pollingConsumers"}}]}
            ]}}
            """;

    static IntegrationInfo chaosdemo() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "chaosdemo";
        for (String uri : List.of("http://localhost:8765/stock?bridgeEndpoint=true", "seda://shipping", "direct://inventory")) {
            ProducerInfo producer = new ProducerInfo();
            producer.uri = uri;
            info.producers.add(producer);
        }
        // both are scheduled, as the consumers console says; only the file consumer polls (it has a scheduler)
        ConsumerInfo timer = new ConsumerInfo();
        timer.uri = "timer://orders?period=500";
        timer.scheduled = true;
        ConsumerInfo files = new ConsumerInfo();
        files.uri = "file://inbox?delay=1000";
        files.scheduled = true;
        files.polls = true;
        info.consumers.add(timer);
        info.consumers.add(files);
        for (String id : List.of("orders", "inventory")) {
            RouteInfo route = new RouteInfo();
            route.routeId = id;
            info.routes.add(route);
        }
        for (String step : List.of("to1", "callStock", "log2", "enrich")) {
            ProcessorInfo p = new ProcessorInfo();
            p.id = step;
            info.routes.get(0).processors.add(p);
        }
        return info;
    }

    @Test
    void theRecipesOfTheModel() {
        ChaosRecipes recipes = ChaosRecipes.parse(MODEL);
        assertThat(recipes.recipes()).hasSize(6);
        ChaosRecipes.Recipe http = recipes.recipes().get(0);
        assertThat(http.title()).isEqualTo("Returns an error (HTTP status)");
        assertThat(http.placeholders()).extracting(ChaosRecipes.Placeholder::name).containsExactly("endpoint", "status",
                "rate");
        assertThat(http.placeholders().get(0).schemes()).containsExactly("http", "https");
        assertThat(http.fill(Map.of("endpoint", "http://localhost:8765/stock", "status", "503", "rate", "0.25")))
                .isEqualTo("endpoint=http://localhost:8765/stock error status=503 rate=0.25");
        // a placeholder without a value stays, so the form can say what is missing
        assertThat(http.fill(Map.of("status", "503"))).startsWith("endpoint=${endpoint} error status=503");

        assertThat(ChaosRecipes.parse("{\"other\": 1}")).isNull();
        assertThat(ChaosRecipes.parse("not json")).isNull();
    }

    @Test
    void theValuesToPickFromInTheIntegration() {
        ChaosRecipes recipes = ChaosRecipes.parse(MODEL);
        IntegrationInfo info = chaosdemo();
        List<ChaosRecipes.Recipe> r = recipes.recipes();
        // the producers of the schemes of the placeholder, without their query
        assertThat(ChaosRecipes.candidates(r.get(0).placeholders().get(0), info))
                .containsExactly("http://localhost:8765/stock");
        assertThat(ChaosRecipes.candidates(r.get(3).placeholders().get(0), info))
                .containsExactly("http://localhost:8765/stock", "seda://shipping", "direct://inventory");
        assertThat(ChaosRecipes.candidates(r.get(1).placeholders().get(0), info)).containsExactly("orders", "inventory");
        assertThat(ChaosRecipes.candidates(r.get(2).placeholders().get(1), info)).containsExactly("headers", "charset",
                "empty");
        // the steps with an id of their own first
        assertThat(ChaosRecipes.candidates(r.get(4).placeholders().get(0), info))
                .containsExactly("callStock", "enrich", "to1", "log2");
        // the consumers that poll on a schedule
        assertThat(ChaosRecipes.candidates(r.get(5).placeholders().get(0), info)).containsExactly("file://inbox");
        // a header name is typed
        assertThat(ChaosRecipes.candidates(r.get(3).placeholders().get(1), info)).isEmpty();
    }

    @Test
    void theCallsToOtherSystemsFirstAndTheDeadLetterChannelLast() {
        // CAMEL-26699: as the status of the demo shop has them
        IntegrationInfo info = new IntegrationInfo();
        for (String[] p : new String[][] {
                { "direct://payment", "false" }, { "seda://dlq", "false" }, { "seda://shipping", "false" },
                { "direct://inventory", "false" }, { "http://localhost:8765/stock", "true" },
                { "seda://dlq", "false" }, { "http://localhost:8765/pay", "true" } }) {
            ProducerInfo producer = new ProducerInfo();
            producer.uri = p[0];
            producer.remote = Boolean.parseBoolean(p[1]);
            info.producers.add(producer);
        }
        for (String[] e : new String[][] {
                { "direct://inventory", "in", "false" }, { "direct://inventory", "out", "false" },
                { "platform-http:///stock", "in", "true" }, { "platform-http:///stock", "out", "true" },
                { "http://localhost:8765/stock", "out", "true" }, { "seda://dlq", "in", "false" },
                { "direct://payment", "out", "false" }, { "seda://shipping", "in", "false" },
                { "seda://shipping", "out", "false" }, { "http://localhost:8765/pay", "out", "true" } }) {
            EndpointInfo endpoint = new EndpointInfo();
            endpoint.uri = e[0];
            endpoint.direction = e[1];
            endpoint.remote = Boolean.parseBoolean(e[2]);
            info.endpoints.add(endpoint);
        }
        ChaosRecipes.Placeholder any = ChaosRecipes.parse(MODEL).recipes().get(3).placeholders().get(0);

        // the reply of platform-http is not a send, and the dead letter channel only an error handler sends to
        assertThat(ChaosRecipes.candidates(any, info)).containsExactly(
                "http://localhost:8765/stock", "http://localhost:8765/pay", "direct://payment", "seda://shipping",
                "direct://inventory", "seda://dlq");
    }

    @Test
    void aTimerIsNotOfferedForThePollFaults() {
        IntegrationInfo info = chaosdemo();
        ChaosRecipes.Placeholder polled = ChaosRecipes.parse(MODEL).recipes().get(5).placeholders().get(0);
        assertThat(ChaosRecipes.candidates(polled, info)).containsExactly("file://inbox");
        ConsumerInfo timer = info.consumers.get(0);
        timer.id = "orders";
        assertThat(ChaosHere.forConsumer(timer).polled()).isNull();
    }

    @Test
    void theCatalogOfTheCliHasTheRecipes() {
        ChaosRecipes recipes = ChaosRecipes.load();
        assertThat(recipes).isNotNull();
        assertThat(recipes.recipes()).extracting(ChaosRecipes.Recipe::id).contains("http-error", "failed-after-success",
                "flap");
    }
}
