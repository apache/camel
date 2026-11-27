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
package org.apache.camel.management;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.debugger.BacklogTracer;
import org.apache.camel.impl.debugger.DefaultBacklogTracerEventMessage;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic test of the message history of the {@link BacklogTracer} where the downstream routes of a multicast
 * (via SEDA) finish in a different order than they started, as it happens when the threads are scheduled differently.
 * The events are fed directly to the tracer, so there are no threads and no timing involved.
 */
public class BacklogTracerMessageHistoryDownstreamOrderTest {

    private static final String BREADCRUMB = "bid-1";

    private CamelContext context;
    private BacklogTracer tracer;
    private long uid;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        context.setMessageHistory(true);
        tracer = BacklogTracer.createTracer(context);
        tracer.setEnabled(true);
        tracer.setRemoveOnDump(false);
    }

    @AfterEach
    public void tearDown() throws Exception {
        context.close();
    }

    @Test
    public void testDownstreamRouteStartsAfterOriginatingRouteAndAnotherDownstreamRouteFinished() {
        // the starter route multicasts to the exchanges 1 (fast) and 2 (slow)
        trace("starter", "exchange-0", null, true, false);
        trace("starter", "exchange-0", null, false, false);
        trace("fast-route", "exchange-1", "exchange-0", true, false);
        trace("fast-route", "exchange-1", "exchange-0", false, false);
        // the originating route finishes and completes the history
        trace("starter", "exchange-0", null, false, true);
        // the fast route finishes after that, which is the only event in the provisional queue
        trace("fast-route", "exchange-1", "exchange-0", false, true);
        // and the slow route does everything after the fast route finished
        trace("slow-route", "exchange-2", "exchange-0", true, false);
        trace("slow-route", "exchange-2", "exchange-0", false, false);
        trace("slow-route", "exchange-2", "exchange-0", false, true);

        List<BacklogTracerEventMessage> events = tracer.dumpLatestMessageHistory();

        Set<String> routeIds = events.stream().map(BacklogTracerEventMessage::getRouteId).collect(Collectors.toSet());
        assertEquals(Set.of("starter", "fast-route", "slow-route"), routeIds);
        assertEquals(1, count(events, "slow-route", true));
        assertEquals(1, count(events, "fast-route", true));
        assertEquals(9, events.size());
        assertTrue(events.stream().anyMatch(e -> e.isLast() && "slow-route".equals(e.getRouteId())),
                "Should contain the last event of slow-route");
    }

    private static long count(List<BacklogTracerEventMessage> events, String routeId, boolean first) {
        return events.stream().filter(e -> routeId.equals(e.getRouteId()) && e.isFirst() == first).count();
    }

    private void trace(String routeId, String exchangeId, String correlationId, boolean first, boolean last) {
        tracer.traceEvent(new DefaultBacklogTracerEventMessage(
                context, first, last, ++uid, System.currentTimeMillis(), null, null, routeId, "node" + uid, null, null, null,
                "to", "to", 1, exchangeId, correlationId, BREADCRUMB, false, false, null));
    }

}
