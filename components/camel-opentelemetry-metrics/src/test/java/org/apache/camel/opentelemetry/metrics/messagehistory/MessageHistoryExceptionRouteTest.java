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
package org.apache.camel.opentelemetry.metrics.messagehistory;

import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.AbstractOpenTelemetryTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MessageHistoryExceptionRouteTest extends AbstractOpenTelemetryTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        OpenTelemetryMessageHistoryFactory factory = new OpenTelemetryMessageHistoryFactory();
        factory.setMeter(otelExtension.getOpenTelemetry().getMeter("meterTest"));
        context.setMessageHistoryFactory(factory);
        return context;
    }

    @Test
    public void testMetricsHistory() throws Exception {
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

        MockEndpoint.assertIsSatisfied(context);

        // there should be 3 names for the message history (foo, bar, exception)
        assertEquals(3, getAllPointData(DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME).size());
        assertEquals(5, getPointData("route1", "foo").getCount());
        assertEquals(5, getPointData("route2", "bar").getCount());
        // exception process node
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(5, getPointData("route2", "process1").getCount()));
    }

    private HistogramPointData getPointData(String routeId, String nodeId) {
        PointData pd = getAllPointDataForRouteId(DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME, routeId)
                .stream()
                .filter(point -> nodeId.equals(point.getAttributes().get(AttributeKey.stringKey("nodeId"))))
                .findFirst().orElse(null);
        assertNotNull(pd);
        assertInstanceOf(HistogramPointData.class, pd);
        return (HistogramPointData) pd;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                        .routeId("ExceptionRoute")
                        .log("Exception received.")
                        .to("mock:exception").id("exception");
                from("seda:foo")
                        .routeId("route1")

                        .to("mock:foo").id("foo");

                from("seda:bar")
                        .routeId("route2")
                        .to("mock:bar").id("bar")
                        .process(exchange -> {
                            throw new Exception("Metrics Exception");
                        })
                        .to("mock:baz").id("baz");
            }
        };
    }
}
