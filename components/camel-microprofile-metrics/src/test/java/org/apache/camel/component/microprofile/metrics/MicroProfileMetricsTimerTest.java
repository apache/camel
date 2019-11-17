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
package org.apache.camel.component.microprofile.metrics;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.HEADER_TIMER_ACTION;

public class MicroProfileMetricsTimerTest extends MicroProfileMetricsTestSupport {

    private static final long DELAY = 100L;

    @Test
    public void testTimerMetric() {
        template.sendBody("direct:timer", null);
        Timer timer = getTimer("test-timer");
        assertEquals(1, timer.getCount());
        assertTrue(timer.getSnapshot().getMax() > DELAY);
    }

    @Test
    public void testTimerMetricActionFromHeader() {
        template.sendBody("direct:timer", null);
        Timer timer = getTimer("test-timer");
        assertEquals(1, timer.getCount());
        assertTrue(timer.getSnapshot().getMax() > DELAY);
    }

    @Test
    public void testTimerMetricActionFromHeaderOverride() {
        template.sendBodyAndHeader("direct:timer", null, HEADER_TIMER_ACTION, "stop");
        Timer timer = getTimer("test-timer");
        assertNull(timer);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:timer")
                    .to("microprofile-metrics:timer:test-timer?action=start")
                    .delayer(DELAY)
                    .to("microprofile-metrics:timer:test-timer?action=stop");

                from("direct:timerFromHeader")
                    .setHeader(HEADER_TIMER_ACTION, constant(TimerAction.START))
                    .to("microprofile-metrics:timer:test-timer-header")
                    .delayer(DELAY)
                    .setHeader(HEADER_TIMER_ACTION, constant(TimerAction.STOP))
                    .to("microprofile-metrics:timer:test-timer-header");
            }
        };
    }
}
