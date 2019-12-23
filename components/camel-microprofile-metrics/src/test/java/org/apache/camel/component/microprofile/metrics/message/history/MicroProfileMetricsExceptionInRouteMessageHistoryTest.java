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

import java.util.SortedMap;

import io.smallrye.metrics.exporters.JsonExporter;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.microprofile.metrics.MicroProfileMetricsTestSupport;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.Test;

public class MicroProfileMetricsExceptionInRouteMessageHistoryTest extends MicroProfileMetricsTestSupport {

    @Test
    public void testMetricsHistoryWhenRouteThrowsException() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(5);
        getMockEndpoint("mock:bar").expectedMessageCount(5);
        getMockEndpoint("mock:baz").expectedMessageCount(0);
        getMockEndpoint("mock:exception").expectedMessageCount(5);

        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                template.sendBody("seda:foo", "Hello " + i);
            } else {
                template.sendBody("seda:bar", "Hello " + i);
            }
        }

        assertMockEndpointsSatisfied();

        SortedMap<MetricID, Timer> timers = metricRegistry.getTimers();
        assertEquals(3, timers.size());

        MicroProfileMetricsMessageHistoryService service = context.hasService(MicroProfileMetricsMessageHistoryService.class);
        assertNotNull(service);

        JsonExporter exporter = new JsonExporter();
        String json = exporter.exportOneScope(Type.APPLICATION).toString();
        assertNotNull(json);
        assertTrue(json.contains("nodeId=foo"));
        assertTrue(json.contains("nodeId=bar"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                    .routeId("ExceptionRoute")
                    .to("mock:exception").id("exception");

                from("seda:foo")
                    .to("mock:foo").id("foo");

                from("seda:bar")
                    .to("mock:bar").id("bar")
                    .process(exchange -> {
                        throw new Exception("Metrics Exception");
                    })
                    .to("mock:baz").id("baz");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MicroProfileMetricsMessageHistoryFactory factory = new MicroProfileMetricsMessageHistoryFactory();
        factory.setMetricRegistry(metricRegistry);

        CamelContext context = super.createCamelContext();
        context.setMessageHistoryFactory(factory);
        return context;
    }
}
