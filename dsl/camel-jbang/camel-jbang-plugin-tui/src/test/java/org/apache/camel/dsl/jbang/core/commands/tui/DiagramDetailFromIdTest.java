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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26864: the app names the input of a route by the id of its from (from1), as the trace does; the diagram names
 * it by the route id. The detail panel finds it by either, and by the route id with an app that still says so.
 */
class DiagramDetailFromIdTest {

    @Test
    void theFromIsFoundByItsIdAndByTheRouteId() throws Exception {
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));
        DiagramDetailSupport detail = new DiagramDetailSupport(ctx, new DiagramSupport());
        detail.setRouteDetailForTesting((JsonObject) Jsoner.deserialize("""
                {"routes": [
                  {"routeId": "hello", "processors": [
                    {"id": "from1", "type": "from", "endpointUri": "timer://tick"},
                    {"id": "setBody1", "type": "setBody"}]},
                  {"routeId": "seven", "processors": [
                    {"id": "seven", "type": "from", "endpointUri": "direct://seven"}]}]}
                """));

        assertThat(detail.findProcessorEntry("from1").getString("endpointUri")).isEqualTo("timer://tick");
        assertThat(detail.findProcessorEntry("hello").getString("endpointUri")).as("the diagram's from node")
                .isEqualTo("timer://tick");
        assertThat(detail.findProcessorEntry("setBody1").getString("type")).isEqualTo("setBody");
        assertThat(detail.findProcessorEntry("seven").getString("endpointUri")).as("an app before CAMEL-26864")
                .isEqualTo("direct://seven");
        assertThat(detail.findProcessorEntry("nope")).isNull();
    }
}
