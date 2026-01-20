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
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.AbstractOpenTelemetryTestSupport;
import org.apache.camel.support.ExpressionAdapter;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.Test;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_LAST_PROCESSED_TIME_INSTRUMENT;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_EXCHANGE_SENT_TIMER;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ENDPOINT_NAME_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.EVENT_TYPE_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.FAILED_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.KIND_ATTRIBUTE;
import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.ROUTE_ID_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenTelemetryExchangeEventNotifierTest extends AbstractOpenTelemetryTestSupport {

    private static final Long DELAY = 250L;
    private static final Long TOLERANCE = 100L;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        OpenTelemetryExchangeEventNotifier eventNotifier = new OpenTelemetryExchangeEventNotifier();
        eventNotifier.setMeter(otelExtension.getOpenTelemetry().getMeter("meterTest"));
        context.getManagementStrategy().addEventNotifier(eventNotifier);
        eventNotifier.init();
        return context;
    }

    // verify that the 'inflight' gauge is registered and working
    @Test
    public void testCamelInflightInstrument() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock://result");
        mock.expectedMessageCount(1);

        // verify gauges registered for both routes
        assertEquals(0, inFlightExchangesForRoute("foo"));
        assertEquals(0, inFlightExchangesForRoute("bar"));

        // verify we have an 'in flight' instrument for each route
        assertEquals(2, getAllPointData(DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT).size());

        verifyInflightExchange(mock, () -> {
            assertEquals(1L, inFlightExchangesForRoute("foo"));
        });

        template.sendBody("direct:foo", "Hello");

        assertEquals(0, inFlightExchangesForRoute("foo"));
        assertEquals(0, inFlightExchangesForRoute("bar"));

        mock.assertIsSatisfied();
    }

    @Test
    public void testElapsedTimerEvents() throws Exception {
        int count = 10;
        MockEndpoint mock = getMockEndpoint("mock://result");
        mock.expectedMessageCount(count);
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("direct:foo", "Hello " + i);
            } else {
                template.sendBody("direct:bar", "Hello " + i);
            }
        }

        mock.assertIsSatisfied();

        verifyElapsedTimerHistogramMetric("bar", DELAY, count / 2);
        verifyElapsedTimerHistogramMetric("foo", 0, count / 2);
    }

    @Test
    public void testSentTimerEvents() throws Exception {
        int count = 10;
        MockEndpoint mock = getMockEndpoint("mock://result");
        mock.expectedMessageCount(count);
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("direct:foo", "Hello " + i);
            } else {
                template.sendBody("direct:bar", "Hello " + i);
            }
        }

        mock.assertIsSatisfied();

        verifySentTimerHistogramMetric("bar", DELAY, count / 2);
        verifySentTimerHistogramMetric("foo", 0, count / 2);
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
        assertEquals("milliseconds", md.getUnit());

        ls = getMetricData(DEFAULT_CAMEL_EXCHANGE_SENT_TIMER);
        assertEquals(1, ls.size());
        md = ls.get(0);
        assertEquals(MetricDataType.HISTOGRAM, md.getType());
        assertEquals("camel.exchange.sent", md.getName());
        assertEquals("Time taken to send message to the endpoint", md.getDescription());
        assertEquals("milliseconds", md.getUnit());

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
        assertEquals("milliseconds", md.getUnit());
    }

    @Test
    public void testMetricMultipleRouteData() {
        template.sendBody("direct:foo", "Hello");
        template.sendBody("direct:bar", "Hello");

        List<MetricData> ls = getMetricData(DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER);
        assertEquals(1, ls.size());
        MetricData md = ls.get(0);
        assertEquals(MetricDataType.HISTOGRAM, md.getType());
        assertEquals("camel.exchange.elapsed", md.getName());
        assertEquals("Time taken to complete exchange", md.getDescription());
        assertEquals("milliseconds", md.getUnit());

        ls = getMetricData(DEFAULT_CAMEL_EXCHANGE_SENT_TIMER);
        assertEquals(1, ls.size());
        md = ls.get(0);
        assertEquals(MetricDataType.HISTOGRAM, md.getType());
        assertEquals("camel.exchange.sent", md.getName());
        assertEquals("Time taken to send message to the endpoint", md.getDescription());
        assertEquals("milliseconds", md.getUnit());

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
        assertEquals("milliseconds", md.getUnit());
    }

    @Test
    public void testLastTimeInstrument() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock://result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:foo", "Hello");
        Thread.sleep(DELAY);

        long diff = System.currentTimeMillis() - getLastTimeInstrument();
        assertTrue(diff >= DELAY && diff < DELAY + TOLERANCE, "last time instrument");
    }

    private void verifyElapsedTimerHistogramMetric(String routeId, long delay, int msgCount) {
        PointData pd = getPointDataForRouteId(DEFAULT_CAMEL_EXCHANGE_ELAPSED_TIMER, routeId);
        assertEquals(routeId, pd.getAttributes().get(stringKey(ROUTE_ID_ATTRIBUTE)));
        assertEquals("direct://" + routeId, pd.getAttributes().get(stringKey(ENDPOINT_NAME_ATTRIBUTE)));
        assertEquals("CamelExchangeEvent", pd.getAttributes().get(stringKey(KIND_ATTRIBUTE)));
        assertEquals("ExchangeCompletedEvent", pd.getAttributes().get(stringKey(EVENT_TYPE_ATTRIBUTE)));
        assertEquals("false", pd.getAttributes().get(stringKey(FAILED_ATTRIBUTE)));

        // verify instrument recorded values
        assertInstanceOf(HistogramPointData.class, pd);
        HistogramPointData hpd = (HistogramPointData) pd;
        assertTrue(hpd.getMax() < delay + TOLERANCE, "max value");
        assertTrue(hpd.getMin() >= delay, "min value");
        assertEquals(msgCount, hpd.getCount(), "count");
        assertTrue(hpd.getSum() >= msgCount * delay, "sum");
    }

    private void verifySentTimerHistogramMetric(String routeId, long delay, int msgCount) {
        List<PointData> ls = getAllPointDataForRouteId(DEFAULT_CAMEL_EXCHANGE_SENT_TIMER, routeId);
        assertEquals(2, ls.size());
        for (PointData pd : ls) {
            String endPoint = pd.getAttributes().get(stringKey(ENDPOINT_NAME_ATTRIBUTE));
            assertNotNull(endPoint);
            if (("direct://" + routeId).equals(endPoint)) {
                verifySentTimerHistogramMetric(pd, delay + TOLERANCE, delay, msgCount);
            } else if ("mock://result".equals(endPoint)) {
                verifySentTimerHistogramMetric(pd, msgCount, 0, msgCount);
            } else {
                throw new IllegalStateException("Unexpected endpoint name: " + endPoint);
            }
            assertEquals(routeId, pd.getAttributes().get(stringKey(ROUTE_ID_ATTRIBUTE)));
            assertEquals("CamelExchangeEvent", pd.getAttributes().get(stringKey(KIND_ATTRIBUTE)));
            assertEquals("ExchangeSentEvent", pd.getAttributes().get(stringKey(EVENT_TYPE_ATTRIBUTE)));
            assertEquals("false", pd.getAttributes().get(stringKey(FAILED_ATTRIBUTE)));
        }
    }

    private long getLastTimeInstrument() {
        List<PointData> ls = getAllPointData(DEFAULT_CAMEL_EXCHANGE_LAST_PROCESSED_TIME_INSTRUMENT);
        assertEquals(1, ls.size());
        PointData pd = ls.get(0);
        assertInstanceOf(LongPointData.class, pd);
        return ((LongPointData) pd).getValue();
    }

    private void verifyInflightExchange(MockEndpoint mock, ThrowingRunnable tr) {
        mock.returnReplyBody(new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                try {
                    Awaitility.await().pollDelay(DELAY, TimeUnit.MILLISECONDS)
                            .catchUncaughtExceptions().untilAsserted(tr);
                    return exchange.getIn().getBody();
                } catch (Exception e) {
                    if (e.getCause() instanceof InterruptedException) {
                        throw new CamelExecutionException(e.getMessage(), exchange, e);
                    } else {
                        throw new RuntimeException("Unexpected Exception");
                    }
                }
            }
        });
    }

    private long inFlightExchangesForRoute(String routeId) {
        PointData pd = getPointDataForRouteId(DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT, routeId);
        assertInstanceOf(LongPointData.class, pd, "Expected LongPointData");
        assertEquals(routeId, pd.getAttributes().get(stringKey(ROUTE_ID_ATTRIBUTE)));
        assertEquals("CamelExchangeEvent", pd.getAttributes().get(stringKey(KIND_ATTRIBUTE)));
        return ((LongPointData) pd).getValue();
    }

    private void verifySentTimerHistogramMetric(PointData pd, long max, long min, int msgCount) {
        assertInstanceOf(HistogramPointData.class, pd);
        HistogramPointData hpd = (HistogramPointData) pd;
        assertTrue(hpd.getMax() < max, "max value");
        assertTrue(hpd.getMin() >= min, "min value");
        assertEquals(msgCount, hpd.getCount(), "count");
        assertTrue(hpd.getSum() >= msgCount * min, "sum");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct://foo").routeId("foo").to("mock://result");
                from("direct://bar").routeId("bar").delay(DELAY).to("mock://result");
            }
        };
    }
}
