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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Timer;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ExpressionAdapter;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTES_EXCHANGES_INFLIGHT;
import static org.apache.camel.component.micrometer.MicrometerConstants.ROUTE_ID_TAG;
import static org.assertj.core.api.Assertions.withPrecision;

public class MicrometerExchangeEventNotifierTest extends AbstractMicrometerEventNotifierTest {

    private static final String ROUTE_ID = "test";
    private static final String MOCK_OUT = "mock://out";
    private static final String DIRECT_IN = "direct://in";
    private static final Long SLEEP = 20L;

    @Override
    protected AbstractMicrometerEventNotifier<?> getEventNotifier() {
        MicrometerExchangeEventNotifier eventNotifier = new MicrometerExchangeEventNotifier();
        // use sanitized uri to not reveal sensitive information
        eventNotifier.setNamingStrategy((exchange, endpoint) -> endpoint.toString());
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
                    Awaitility.await().pollDelay(SLEEP, TimeUnit.MILLISECONDS).catchUncaughtExceptions().untilAsserted(
                            () -> Assertions.assertThat(currentInflightExchanges()).isEqualTo(1.0D, withPrecision(0.1D)));
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
        mock.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody(DIRECT_IN, new Object());
        }

        mock.assertIsSatisfied();
        Timer timer = meterRegistry.find(MOCK_OUT).timer();
        Assertions.assertThat(timer.count()).isEqualTo(count);
        Assertions.assertThat(timer.mean(TimeUnit.MILLISECONDS)).isGreaterThan(SLEEP.doubleValue());
        Assertions.assertThat(currentInflightExchanges()).isEqualTo(0.0D, withPrecision(0.1D));
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
