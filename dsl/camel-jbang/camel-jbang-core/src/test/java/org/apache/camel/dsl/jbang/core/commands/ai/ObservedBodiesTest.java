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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.yaml.validator.ProjectFacts;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bodies the endpoints of a running integration gave, from its route-structure console (CAMEL-25750).
 */
class ObservedBodiesTest {

    @Test
    void consumersAndProducers() {
        // the shape of the route-structure console of Camel 4.24: a choice whose branch calls seda, then log and toD
        JsonObject structure = Jsoner.deserialize(
                """
                        {"routes": [{"routeId": "orders", "code": [
                          {"type": "route", "id": "orders", "level": 0},
                          {"type": "from", "id": "orders", "level": 1, "uri": "direct:orders"},
                          {"type": "choice", "id": "choice1", "level": 2, "bodyTypes": ["java.lang.String"], "bodyKinds": ["payload"]},
                          {"type": "when", "id": "when1", "level": 3},
                          {"type": "to", "id": "to1", "level": 4, "uri": "seda:stock?size=5"},
                          {"type": "to", "id": "to2", "level": 2, "uri": "log:outbox", "bodyTypes": ["java.lang.String"], "bodyKinds": ["payload"]},
                          {"type": "toD", "id": "toD1", "level": 2, "bodyTypes": ["java.lang.String"], "bodyKinds": ["payload"]}
                        ]}]}
                        """,
                (JsonObject) null);

        Map<String, ProjectFacts.Observed> observed = ObservedBodies.fromRouteStructure(structure);

        // the first step was reached with what the consumer gave
        assertThat(observed.get(ProjectFacts.consumerKey("direct:orders")))
                .isEqualTo(new ProjectFacts.Observed(List.of("java.lang.String"), List.of("payload")));
        // and the step after log:outbox with what it returned
        assertThat(observed.get(ProjectFacts.producerKey("log:outbox")))
                .isEqualTo(new ProjectFacts.Observed(List.of("java.lang.String"), List.of("payload")));
        // the seda call ends its branch: the step after the choice is reached by both branches, not by it alone
        assertThat(observed).doesNotContainKey(ProjectFacts.producerKey("seda:stock"));
        assertThat(observed).hasSize(2);
    }

    @Test
    void theBodiesOfAnEndpointAcrossRoutes() {
        JsonObject structure = Jsoner.deserialize(
                """
                        {"routes": [
                          {"routeId": "report", "code": [
                            {"type": "route", "id": "report", "level": 0},
                            {"type": "from", "id": "report", "level": 1, "uri": "timer:report"},
                            {"type": "to", "id": "to1", "level": 2, "uri": "sql:select * from orders", "bodyTypes": ["null"], "bodyKinds": ["null"]},
                            {"type": "to", "id": "to2", "level": 2, "uri": "file:reports", "bodyTypes": ["java.util.ArrayList"], "bodyKinds": ["collection"]}
                          ]},
                          {"routeId": "audit", "code": [
                            {"type": "route", "id": "audit", "level": 0},
                            {"type": "from", "id": "audit", "level": 1, "uri": "direct:audit"},
                            {"type": "to", "id": "to3", "level": 2, "uri": "sql:select * from orders?outputType=SelectOne", "bodyTypes": ["java.lang.String"], "bodyKinds": ["payload"]},
                            {"type": "log", "id": "log1", "level": 2, "bodyTypes": ["java.util.LinkedHashMap"], "bodyKinds": ["data"]}
                          ]},
                          {"routeId": "idle", "code": [
                            {"type": "route", "id": "idle", "level": 0},
                            {"type": "from", "id": "idle", "level": 1, "uri": "direct:idle"},
                            {"type": "to", "id": "to4", "level": 2, "uri": "seda:archive"},
                            {"type": "log", "id": "log2", "level": 2}
                          ]}
                        ]}
                        """,
                (JsonObject) null);

        Map<String, ProjectFacts.Observed> observed = ObservedBodies.fromRouteStructure(structure);

        // the two calls of one endpoint (without the query) gave a List and a Map
        assertThat(observed.get(ProjectFacts.producerKey("sql:select * from orders")))
                .isEqualTo(new ProjectFacts.Observed(
                        List.of("java.util.ArrayList", "java.util.LinkedHashMap"), List.of("collection", "data")));
        assertThat(observed.get(ProjectFacts.consumerKey("timer:report")))
                .isEqualTo(new ProjectFacts.Observed(List.of("null"), List.of("null")));
        assertThat(observed.get(ProjectFacts.consumerKey("direct:audit")))
                .isEqualTo(new ProjectFacts.Observed(List.of("java.lang.String"), List.of("payload")));
        // a route no message has reached yet tells nothing
        assertThat(observed).doesNotContainKeys(
                ProjectFacts.consumerKey("direct:idle"), ProjectFacts.producerKey("seda:archive"));
    }

    @Test
    void theRoutesOfAFile() {
        Path file = Path.of("/work/orders/orders.camel.yaml");

        assertThat(ObservedBodies.sameFile("file:/work/orders/orders.camel.yaml:12", file)).isTrue();
        assertThat(ObservedBodies.sameFile("file:/other/orders.camel.yaml:12", file))
                .as("another project's file of the same name").isFalse();
        assertThat(ObservedBodies.sameFile("file:orders.camel.yaml:3", file)).isTrue();
        assertThat(ObservedBodies.sameFile("classpath:routes/orders.camel.yaml:3", file)).isTrue();
        assertThat(ObservedBodies.sameFile("file:invoices.camel.yaml:3", file)).isFalse();
        assertThat(ObservedBodies.sameFile(null, file)).isFalse();

        JsonObject status = Jsoner.deserialize("""
                {"routes": [{"routeId": "invoices", "source": "file:invoices.camel.yaml:3"},
                            {"routeId": "orders", "source": "file:orders.camel.yaml:5"}]}
                """, (JsonObject) null);
        assertThat(ObservedBodies.runsRoutesOf(status, file)).isTrue();
        assertThat(ObservedBodies.runsRoutesOf(status, Path.of("/work/orders/stock.camel.yaml"))).isFalse();
        assertThat(ObservedBodies.runsRoutesOf(null, file)).isFalse();
    }
}
