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
package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The trace of an exchange goes forward in time: a route called later by the same exchange (direct) is entered and left
 * at the time it happens, not at the time the exchange was created, which put its steps before the call that reached
 * them.
 */
class BacklogTracerCalledRouteTimestampTest extends ContextTestSupport {

    @Test
    void theCalledRouteIsEnteredWhenItIsCalled() {
        BacklogTracer tracer = context.getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        tracer.setEnabled(true);

        template.sendBody("direct:start", "Hello");

        List<BacklogTracerEventMessage> events = tracer.dumpAllTracedMessages();
        BacklogTracerEventMessage created = events.stream()
                .filter(e -> e.isFirst() && "start".equals(e.getRouteId())).findFirst().orElseThrow();
        BacklogTracerEventMessage entered = events.stream()
                .filter(e -> e.isFirst() && "called".equals(e.getRouteId())).findFirst().orElseThrow();
        BacklogTracerEventMessage left = events.stream()
                .filter(e -> e.isLast() && "called".equals(e.getRouteId())).findFirst().orElseThrow();

        // the delay of 200 ms comes before the call
        assertThat(entered.getTimestamp() - created.getTimestamp()).isGreaterThanOrEqualTo(150);
        // the last event of a route carries the time it was entered, and its elapsed time the time it was left
        assertThat(left.getTimestamp()).isEqualTo(entered.getTimestamp());
        // in order, as a reader sees them: no event before the one before it (the elapsed time is measured from a
        // moment after the stamp, so a few ms of slack; the history went back by the whole delay before)
        for (int i = 1; i < events.size(); i++) {
            assertThat(seen(events.get(i))).isGreaterThanOrEqualTo(seen(events.get(i - 1)) - 20);
        }
    }

    private static long seen(BacklogTracerEventMessage event) {
        return event.isLast() ? event.getTimestamp() + event.getElapsed() : event.getTimestamp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setBacklogTracing(true);

                from("direct:start").routeId("start")
                        .delay(200)
                        .to("direct:called");

                from("direct:called").routeId("called")
                        .log("called");
            }
        };
    }
}
