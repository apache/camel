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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A producer has the numbers of the steps of its route that send to its endpoint.
 */
class ProducersTabStatsTest {

    private IntegrationInfo info;
    private MonitorContext ctx;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "shop";
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";

        RouteInfo route = new RouteInfo();
        route.routeId = "stock-check";
        // two sends to the supplier, written without the // after the scheme
        route.processors.add(step("to1", "direct:supplier", 100, 3, 1, 4, 9));
        route.processors.add(step("to2", "direct:supplier", 20, 0, 2, 5, 7));
        route.processors.add(step("log1", null, 120, 0, 0, 0, 0));
        info.routes.add(route);
    }

    @Test
    void theStepsThatSendToTheEndpointAddUp() {
        ProducersTab.Stats st = ProducersTab.stats(info, producer("stock-check", "direct://supplier"));

        assertThat(st.total()).isEqualTo(120);
        assertThat(st.failed()).isEqualTo(3);
        assertThat(st.busiest().id).isEqualTo("to1");
    }

    @Test
    void aProducerNoStepSendsToHasNoNumbers() {
        // a toD: the endpoint is computed per message
        assertThat(ProducersTab.stats(info, producer("stock-check", "http://warehouse/stock/42"))).isNull();
        // another route
        assertThat(ProducersTab.stats(info, producer("orders", "direct://supplier"))).isNull();
    }

    @Test
    void theTableShowsTheNumbers() {
        info.producers.add(producer("stock-check", "direct://supplier"));

        String rendered = TuiTestHelper.renderToString(new ProducersTab(ctx), 160, 10);

        assertThat(rendered).contains("TOTAL").contains("FAIL").contains("P50/P95/P99");
        assertThat(rendered).containsPattern("120 +3 +1ms/4ms/9ms +direct://supplier");
    }

    private static ProcessorInfo step(String id, String uri, long total, long failed, long p50, long p95, long p99) {
        ProcessorInfo p = new ProcessorInfo();
        p.id = id;
        p.uri = uri;
        p.total = total;
        p.failed = failed;
        p.p50Time = p50;
        p.p95Time = p95;
        p.p99Time = p99;
        return p;
    }

    private static ProducerInfo producer(String routeId, String uri) {
        ProducerInfo p = new ProducerInfo();
        p.routeId = routeId;
        p.uri = uri;
        p.state = "Started";
        p.className = "org.apache.camel.component.direct.DirectProducer";
        return p;
    }
}
