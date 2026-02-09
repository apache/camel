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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.AbstractOpenTelemetryTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MessageHistoryTest extends AbstractOpenTelemetryTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        OpenTelemetryMessageHistoryFactory factory = new OpenTelemetryMessageHistoryFactory();
        factory.setMeter(otelExtension.getOpenTelemetry().getMeter("meterTest"));
        context.setMessageHistoryFactory(factory);
        return context;
    }

    @Test
    public void testMessageHistory() throws Exception {
        int count = 10;
        getMockEndpoint("mock:foo").expectedMessageCount(count / 2);
        getMockEndpoint("mock:bar").expectedMessageCount(count / 2);
        getMockEndpoint("mock:baz").expectedMessageCount(count / 2);

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("seda:foo", "Hello " + i);
            } else {
                template.sendBody("seda:bar", "Hello " + i);
            }
        }

        MockEndpoint.assertIsSatisfied(context);

        // there should be 3 names
        assertEquals(3, getAllPointData(DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME).size());

        assertEquals(count / 2, getPointData("route1", "foo").getCount());
        assertEquals(count / 2, getPointData("route2", "bar").getCount());
        assertEquals(count / 2, getPointData("route2", "baz").getCount());
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
                from("seda:foo")
                        .routeId("route1")
                        .to("mock:foo").id("foo");

                from("seda:bar")
                        .routeId("route2")
                        .to("mock:bar").id("bar")
                        .to("mock:baz").id("baz");
            }
        };
    }
}
