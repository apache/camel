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
package org.apache.camel.component.microprofile.metrics.message.history;

import java.util.Arrays;
import java.util.SortedMap;

import io.smallrye.metrics.TagsUtils;
import io.smallrye.metrics.exporters.JsonExporter;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsHelper;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsTestSupport;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.Test;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.DEFAULT_CAMEL_MESSAGE_HISTORY_METRIC_NAME;

public class MicroProfileMetricsMessageHistoryTest extends MicroProfileMetricsTestSupport {

    private MicroProfileMetricsMessageHistoryFactory factory;

    @Test
    public void testMessageHistory() throws Exception {
        int count = 10;

        getMockEndpoint("mock:foo").expectedMessageCount(count / 2);
        getMockEndpoint("mock:bar").expectedMessageCount(count / 2);
        getMockEndpoint("mock:baz").expectedMessageCount(count / 2);

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("direct:foo", "Hello " + i);
            } else {
                template.sendBody("direct:bar", "Hello " + i);
            }
        }

        assertMockEndpointsSatisfied();

        SortedMap<MetricID, Timer> timers = metricRegistry.getTimers();
        assertEquals(3, timers.size());

        String contextTag = "camelContext=" + context.getName();
        Tag[] fooTags = getTags(new String[] {contextTag, "nodeId=foo", "routeId=routeA"});
        Timer fooTimer = MicroProfileMetricsHelper.findMetric(metricRegistry, DEFAULT_CAMEL_MESSAGE_HISTORY_METRIC_NAME, Timer.class, Arrays.asList(fooTags));
        assertEquals(count / 2, fooTimer.getCount());

        Tag[] barTags = getTags(new String[] {contextTag, "nodeId=bar", "routeId=routeB"});
        Timer barTimer = MicroProfileMetricsHelper.findMetric(metricRegistry, DEFAULT_CAMEL_MESSAGE_HISTORY_METRIC_NAME, Timer.class, Arrays.asList(barTags));
        assertEquals(count / 2, barTimer.getCount());

        Tag[] bazTags = getTags(new String[] {contextTag, "nodeId=baz", "routeId=routeB"});
        Timer bazTimer = MicroProfileMetricsHelper.findMetric(metricRegistry, DEFAULT_CAMEL_MESSAGE_HISTORY_METRIC_NAME, Timer.class, Arrays.asList(bazTags));
        assertEquals(count / 2, bazTimer.getCount());

        MicroProfileMetricsMessageHistoryService service = context.hasService(MicroProfileMetricsMessageHistoryService.class);
        assertNotNull(service);

        JsonExporter exporter = new JsonExporter();
        String json = exporter.exportOneScope(Type.APPLICATION).toString();
        assertNotNull(json);
        assertTrue(json.contains("nodeId=foo"));
        assertTrue(json.contains("nodeId=bar"));
        assertTrue(json.contains("nodeId=baz"));
    }

    @Test
    public void testMicroProfileMetricsMessageHistoryFactoryStop() {
        template.sendBody("direct:foo", null);
        assertEquals(1, metricRegistry.getMetrics().size());
        factory.stop();
        assertEquals(0, metricRegistry.getMetrics().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("routeA")
                    .to("mock:foo").id("foo");

                from("direct:bar").routeId("routeB")
                    .to("mock:bar").id("bar")
                    .to("mock:baz").id("baz");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        factory = new MicroProfileMetricsMessageHistoryFactory();
        factory.setMetricRegistry(metricRegistry);

        CamelContext context = super.createCamelContext();
        context.setMessageHistoryFactory(factory);
        return context;
    }

    private Tag[] getTags(String[] tagStrings) {
        return TagsUtils.parseTagsAsArray(tagStrings);
    }
}
