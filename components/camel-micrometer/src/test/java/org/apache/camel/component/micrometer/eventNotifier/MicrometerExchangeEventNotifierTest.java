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
package org.apache.camel.component.micrometer.eventNotifier;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Timer;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.eventnotifier.AbstractMicrometerEventNotifier;
import org.apache.camel.component.micrometer.eventnotifier.MicrometerExchangeEventNotifier;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT;
import static org.apache.camel.component.micrometer.MicrometerConstants.ROUTE_ID_TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MicrometerExchangeEventNotifierTest extends AbstractMicrometerEventNotifierTest {

    private static final String ROUTE_ID = "test";
    private static final String MOCK_OUT = "mock://out";
    private static final String DIRECT_IN = "direct://in";
    private static final Long SLEEP = 20L;

    @Override
    protected AbstractMicrometerEventNotifier<?> getEventNotifier() {
        MicrometerExchangeEventNotifier eventNotifier = new MicrometerExchangeEventNotifier();
        eventNotifier.setNamingStrategy((exchange, endpoint) -> endpoint.getEndpointUri());
        return eventNotifier;
    }

    @Test
    public void testCamelRouteEvents() throws Exception {
        int count = 10;
        MockEndpoint mock = getMockEndpoint(MOCK_OUT);
        mock.returnReplyBody(new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                try {
                    assertEquals(1.0D, currentInflightExchanges(), 0.1D);
                    Thread.sleep(SLEEP);
                    return exchange.getIn().getBody();
                } catch (InterruptedException e) {
                    throw new CamelExecutionException(e.getMessage(), exchange, e);
                }

            }
        });
        mock.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody(DIRECT_IN, new Object());
        }

        mock.assertIsSatisfied();
        Timer timer = meterRegistry.find(MOCK_OUT).timer();
        assertEquals(count, timer.count());
        assertTrue(timer.mean(TimeUnit.MILLISECONDS) > SLEEP.doubleValue());
        assertEquals(0.0D, currentInflightExchanges(), 0.1D);
    }

    private double currentInflightExchanges() {
        return meterRegistry.find(DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT).tag(ROUTE_ID_TAG, ROUTE_ID).gauge().value();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_IN).routeId(ROUTE_ID).to(MOCK_OUT);
            }
        };
    }

}
