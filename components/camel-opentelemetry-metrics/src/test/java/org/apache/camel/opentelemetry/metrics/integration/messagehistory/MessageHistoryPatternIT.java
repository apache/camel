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
package org.apache.camel.opentelemetry.metrics.integration.messagehistory;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.PointData;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.opentelemetry.metrics.AbstractOpenTelemetryTestSupport;
import org.apache.camel.opentelemetry.metrics.messagehistory.OpenTelemetryMessageHistoryFactory;
import org.junit.jupiter.api.Test;

import static org.apache.camel.opentelemetry.metrics.OpenTelemetryConstants.DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MessageHistoryPatternIT extends AbstractOpenTelemetryTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        OpenTelemetryMessageHistoryFactory factory = new OpenTelemetryMessageHistoryFactory();
        factory.setMeter(otelExtension.getOpenTelemetry().getMeter("meterTest"));
        factory.setNodePattern("step");
        context.setMessageHistoryFactory(factory);
        return context;
    }

    @Test
    public void testMessageHistory() throws Exception {
        int count = 3;
        getMockEndpoint("mock:a").expectedMessageCount(count);
        getMockEndpoint("mock:b").expectedMessageCount(count);
        getMockEndpoint("mock:bar").expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.request("direct:start", e -> {
                e.getMessage().setBody("Hello World");
            });
        }

        MockEndpoint.assertIsSatisfied(context);

        // there should be 3 names
        assertEquals(3, getAllPointData(DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME).size());
        assertEquals(count, getPointData("route1", "a").getCount());
        assertEquals(count, getPointData("route1", "b").getCount());
        assertEquals(count, getPointData("route2", "bar").getCount());
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
                from("direct:start").step("a").transform().constant("Bye World").to("mock:a").end().step("b").transform()
                        .constant("Hi World").to("direct:bar").to("mock:b").end();

                from("direct:bar").step("bar").to("log:bar").to("mock:bar").end();
            }
        };
    }
}
