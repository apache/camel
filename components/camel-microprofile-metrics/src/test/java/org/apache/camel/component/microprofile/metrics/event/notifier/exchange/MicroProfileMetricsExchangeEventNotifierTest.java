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
package org.apache.camel.component.microprofile.metrics.event.notifier.exchange;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsTestSupport;
import org.apache.camel.component.microprofile.metrics.gauge.AtomicIntegerGauge;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ExpressionAdapter;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_COMPLETED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_EXTERNAL_REDELIVERIES_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILURES_HANDLED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_INFLIGHT_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_TOTAL_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.PROCESSING_METRICS_SUFFIX;

public class MicroProfileMetricsExchangeEventNotifierTest extends MicroProfileMetricsTestSupport {

    private MicroProfileMetricsExchangeEventNotifier eventNotifier;

    @Test
    public void testMicroProfileMetricsExchangeEventNotifier() {
        int count = 10;
        Long delay = 50L;

        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.returnReplyBody(new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                try {
                    Thread.sleep(50L);
                    return exchange.getIn().getBody();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CamelExecutionException(e.getMessage(), exchange, e);
                }
            }
        });
        mockEndpoint.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            try {
                template.sendBody("direct:start", i);
            } catch (Exception e) {
                // Expected
            }
        }

        Timer timer = getTimer("mock://result" + PROCESSING_METRICS_SUFFIX);
        assertEquals(5, timer.getCount());
        assertTrue(timer.getSnapshot().getMean() > delay.doubleValue());

        Tag[] tags = new Tag[] {new Tag(CAMEL_CONTEXT_TAG, context.getName())};

        Counter exchangesCompleted = getCounter(CAMEL_CONTEXT_METRIC_NAME + EXCHANGES_COMPLETED_METRIC_NAME, tags);
        assertEquals(count, exchangesCompleted.getCount());

        Counter exchangesFailed = getCounter(CAMEL_CONTEXT_METRIC_NAME + EXCHANGES_FAILED_METRIC_NAME, tags);
        assertEquals(0, exchangesFailed.getCount());

        Counter exchangesTotal = getCounter(CAMEL_CONTEXT_METRIC_NAME + EXCHANGES_TOTAL_METRIC_NAME, tags);
        assertEquals(count, exchangesTotal.getCount());

        AtomicIntegerGauge exchangesInflight = getAtomicIntegerGauge(CAMEL_CONTEXT_METRIC_NAME + EXCHANGES_INFLIGHT_METRIC_NAME, tags);
        assertEquals(0, exchangesInflight.getValue().intValue());

        Counter externalRedeliveries = getCounter(CAMEL_CONTEXT_METRIC_NAME + EXCHANGES_EXTERNAL_REDELIVERIES_METRIC_NAME, tags);
        assertEquals(0, externalRedeliveries.getCount());

        Counter failuresHandled = getCounter(CAMEL_CONTEXT_METRIC_NAME + EXCHANGES_FAILURES_HANDLED_METRIC_NAME, tags);
        assertEquals(5, failuresHandled.getCount());
    }

    @Test
    public void testMicroProfileMetricsExchangeEventNotifierStop() {
        template.sendBody("direct:start", 1);
        assertEquals(9, metricRegistry.getMetrics().size());
        eventNotifier.stop();
        assertEquals(0, metricRegistry.getMetrics().size());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        eventNotifier = new MicroProfileMetricsExchangeEventNotifier();
        eventNotifier.setNamingStrategy((exchange, endpoint) -> endpoint.getEndpointUri());
        eventNotifier.setMetricRegistry(metricRegistry);

        CamelContext camelContext = super.createCamelContext();
        camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalStateException.class)
                    .handled(true);

                from("direct:start").routeId("test")
                    .process(exchange -> {
                        Integer count = exchange.getIn().getBody(Integer.class);

                        IllegalStateException foo = new IllegalStateException("Invalid count");
                        if (count % 2 == 0) {
                            throw foo;
                        }
                    })
                    .to("mock:result");
            }
        };
    }
}
