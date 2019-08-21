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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ExpressionAdapter;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.Test;

public class MicroProfileMetricsExchangeEventNotifierTest extends MicroProfileMetricsTestSupport {

    @Test
    public void testMicroProfileMetricsEventNotifier() {
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
            template.sendBody("direct:start", null);
        }

        Timer timer = getTimer("mock://result");
        assertEquals(count, timer.getCount());
        assertTrue(timer.getSnapshot().getMean() > delay.doubleValue());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MicroProfileMetricsExchangeEventNotifier eventNotifier = new MicroProfileMetricsExchangeEventNotifier();
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
                from("direct:start").routeId("test")
                        .to("mock:result");
            }
        };
    }
}
