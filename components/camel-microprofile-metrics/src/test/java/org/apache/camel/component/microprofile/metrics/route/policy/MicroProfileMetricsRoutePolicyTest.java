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
package org.apache.camel.component.microprofile.metrics.route.policy;

import java.util.Arrays;

import io.smallrye.metrics.TagsUtils;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsTestSupport;
import org.apache.camel.component.microprofile.metrics.gauge.AtomicIntegerGauge;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.DEFAULT_CAMEL_ROUTE_POLICY_PROCESSING_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_COMPLETED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_EXTERNAL_REDELIVERIES_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_FAILURES_HANDLED_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_INFLIGHT_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EXCHANGES_TOTAL_METRIC_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.ROUTE_ID_TAG;

public class MicroProfileMetricsRoutePolicyTest extends MicroProfileMetricsTestSupport {

    private static final long DELAY_FOO = 20;
    private static final long DELAY_BAR = 50;

    @Test
    public void testMetricsRoutePolicy() throws Exception {
        int count = 10;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(7);

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("direct:foo", i);
            } else {
                template.sendBody("direct:bar", i);
            }
        }

        assertMockEndpointsSatisfied();

        Timer fooTimer = getTimer(DEFAULT_CAMEL_ROUTE_POLICY_PROCESSING_METRIC_NAME);
        assertEquals(count / 2, fooTimer.getCount());

        Snapshot fooSnapshot = fooTimer.getSnapshot();
        assertTrue(fooSnapshot.getMean() > DELAY_FOO);
        assertTrue(fooSnapshot.getMax() > DELAY_FOO);

        String contextTag = "camelContext=" + context.getName();
        String[] tagStrings = new String[] {contextTag, "routeId=foo"};
        Tag[] tags = TagsUtils.parseTagsAsArray(tagStrings);

        Timer barTimer = MicroProfileMetricsHelper.findMetric(metricRegistry, DEFAULT_CAMEL_ROUTE_POLICY_PROCESSING_METRIC_NAME, Timer.class, Arrays.asList(tags));
        assertEquals(count / 2, barTimer.getCount());

        Snapshot barSnapshot = fooTimer.getSnapshot();
        assertTrue(barSnapshot.getMean() > DELAY_FOO);
        assertTrue(barSnapshot.getMax() > DELAY_FOO);

        assertRouteExchangeMetrics("foo", 2);
        assertRouteExchangeMetrics("bar", 1);
    }

    @Test
    public void removeRouteTest() throws Exception {
        assertEquals(6, countRouteMetrics("foo"));
        assertEquals(6, countRouteMetrics("bar"));

        context.getRouteController().stopRoute("foo");
        context.removeRoute("foo");

        assertEquals(0, countRouteMetrics("foo"));
        assertEquals(6, countRouteMetrics("bar"));
    }

    private long countRouteMetrics(String routeId) {
        return metricRegistry.getMetricIDs()
            .stream()
            .filter(metricID -> metricID.getTags().containsValue(routeId))
            .count();
    }

    private void assertRouteExchangeMetrics(String routeId, int expectedFailuresHandled) {
        Tag[] tags = new Tag[] {
            new Tag(CAMEL_CONTEXT_TAG, context.getName()),
            new Tag(ROUTE_ID_TAG, routeId)
        };

        Counter exchangesCompleted = getCounter(DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME + EXCHANGES_COMPLETED_METRIC_NAME, tags);
        assertEquals(5, exchangesCompleted.getCount());

        Counter exchangesFailed = getCounter(DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME + EXCHANGES_FAILED_METRIC_NAME, tags);
        assertEquals(0, exchangesFailed.getCount());

        Counter exchangesTotal = getCounter(DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME + EXCHANGES_TOTAL_METRIC_NAME, tags);
        assertEquals(5, exchangesTotal.getCount());

        AtomicIntegerGauge exchangesInflight = getAtomicIntegerGauge(DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME + EXCHANGES_INFLIGHT_METRIC_NAME, tags);
        assertEquals(0, exchangesInflight.getValue().intValue());

        Counter externalRedeliveries = getCounter(DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME + EXCHANGES_EXTERNAL_REDELIVERIES_METRIC_NAME, tags);
        assertEquals(0, externalRedeliveries.getCount());

        Counter failuresHandled = getCounter(DEFAULT_CAMEL_ROUTE_POLICY_METRIC_NAME + EXCHANGES_FAILURES_HANDLED_METRIC_NAME, tags);
        assertEquals(expectedFailuresHandled, failuresHandled.getCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class)
                    .handled(true);

                from("direct:foo").routeId("foo")
                    .process(exchange -> {
                        Integer count = exchange.getIn().getBody(Integer.class);
                        if (count % 3 == 0) {
                            throw new IllegalStateException("Invalid count");
                        }
                    })
                    .delay(DELAY_FOO).to("mock:result");

                from("direct:bar").routeId("bar")
                    .process(exchange -> {
                        Integer count = exchange.getIn().getBody(Integer.class);
                        if (count % 5 == 0) {
                            throw new IllegalStateException("Invalid count");
                        }
                    })
                    .delay(DELAY_BAR).to("mock:result");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MicroProfileMetricsRoutePolicyFactory factory = new MicroProfileMetricsRoutePolicyFactory();
        factory.setMetricRegistry(metricRegistry);

        CamelContext camelContext = super.createCamelContext();
        camelContext.addRoutePolicyFactory(factory);
        return camelContext;
    }
}
