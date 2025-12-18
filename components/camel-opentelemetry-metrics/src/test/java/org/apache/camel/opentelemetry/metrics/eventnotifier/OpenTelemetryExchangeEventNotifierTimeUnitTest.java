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
package org.apache.camel.opentelemetry.metrics.eventnotifier;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.AbstractOpenTelemetryTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_LAST_PROCESSED_TIME_INSTRUMENT;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_SENT_TIMER;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenTelemetryExchangeEventNotifierTimeUnitTest extends AbstractOpenTelemetryTestSupport {

    private static final Long DELAY = 1100L;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        OpenTelemetryExchangeEventNotifier eventNotifier = new OpenTelemetryExchangeEventNotifier();
        eventNotifier.setMeter(otelExtension.getOpenTelemetry().getMeter("meterTest"));

        // override default time unit from milliseconds to seconds
        eventNotifier.setTimeUnit(TimeUnit.SECONDS);
        eventNotifier.setLastExchangeTimeUnit(TimeUnit.SECONDS);

        context.getManagementStrategy().addEventNotifier(eventNotifier);
        eventNotifier.init();
        return context;
    }

    @Test
    public void testElapsedTimerEvents() throws Exception {
        int count = 3;
        MockEndpoint mock = getMockEndpoint("mock://result");
        mock.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody("direct:bar", "Hello " + i);
        }

        mock.assertIsSatisfied();
        verifyElapsedTimerHistogramMetric("bar", Math.floorDiv(DELAY, 1000L), count);
    }

    private void verifyElapsedTimerHistogramMetric(String routeId, long delay, int msgCount) {
        PointData pd = getPointDataForRouteId(DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER, routeId);
        assertInstanceOf(HistogramPointData.class, pd);
        HistogramPointData hpd = (HistogramPointData) pd;
        // histogram values are in seconds
        assertTrue(hpd.getMax() == delay, "max value");
        assertTrue(hpd.getMin() == delay, "min value");
        assertTrue(hpd.getSum() >= msgCount * delay, "sum");
        assertEquals(msgCount, hpd.getCount(), "count");
    }

    @Test
    public void testMetricData() {
        template.sendBody("direct:bar", "Hello");

        List<MetricData> ls = getMetricData(DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER);
        assertEquals(1, ls.size());
        MetricData md = ls.get(0);
        assertEquals(MetricDataType.HISTOGRAM, md.getType());
        assertEquals("camel.exchange.elapsed", md.getName());
        assertEquals("Time taken to complete exchange", md.getDescription());
        // time unit should be in seconds as configured
        assertEquals("seconds", md.getUnit());

        ls = getMetricData(DEFAULT_CAMEL_EXCHANGE_SENT_TIMER);
        assertEquals(1, ls.size());
        md = ls.get(0);
        assertEquals(MetricDataType.HISTOGRAM, md.getType());
        assertEquals("camel.exchange.sent", md.getName());
        assertEquals("Time taken to send message to the endpoint", md.getDescription());
        // time unit should be in seconds as configured
        assertEquals("seconds", md.getUnit());

        ls = getMetricData(DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT);
        assertEquals(1, ls.size());
        md = ls.get(0);
        assertEquals(MetricDataType.LONG_GAUGE, md.getType());
        assertEquals("camel.exchanges.inflight", md.getName());
        assertEquals("Route in flight messages", md.getDescription());

        ls = getMetricData(DEFAULT_CAMEL_EXCHANGE_LAST_PROCESSED_TIME_INSTRUMENT);
        assertEquals(1, ls.size());
        md = ls.get(0);
        assertEquals(MetricDataType.LONG_GAUGE, md.getType());
        assertEquals("camel.exchanges.last.time", md.getName());
        assertEquals("Last exchange processed time since the Unix epoch", md.getDescription());
        // time unit should be in seconds as configured
        assertEquals("seconds", md.getUnit());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct://bar").routeId("bar").delay(DELAY).to("mock://result");
            }
        };
    }
}
