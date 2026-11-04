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

import io.micrometer.core.instrument.Gauge;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that when {@code recordLastExchangeTimestamp} is set to {@code false} the gauge
 * {@code camel.exchanges.last.timestamp} is never registered and the backing value is never written, regardless of how
 * many exchanges complete.
 */
public class MicrometerExchangeEventNotifierLastTimestampDisabledTest extends AbstractMicrometerEventNotifierTest {

    private static final String DIRECT_IN = "direct://in";
    private static final String MOCK_OUT = "mock://out";
    private static final String ROUTE_ID = "test";

    @Override
    protected AbstractMicrometerEventNotifier<?> getEventNotifier() {
        MicrometerExchangeEventNotifier notifier = new MicrometerExchangeEventNotifier();
        notifier.setRecordLastExchangeTimestamp(false);
        return notifier;
    }

    @Test
    public void testLastTimestampGaugeAbsentWhenDisabled() throws Exception {
        int count = 5;
        MockEndpoint mock = getMockEndpoint(MOCK_OUT);
        mock.expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody(DIRECT_IN, "msg-" + i);
        }
        mock.assertIsSatisfied();

        // Gauge must not exist in the registry at all
        Gauge gauge = meterRegistry.find(MicrometerConstants.CAMEL_EXCHANGE_LAST_TIME_METER_NAME).gauge();
        assertThat(gauge)
                .as("last-timestamp gauge must NOT be registered when recordLastExchangeTimestamp=false")
                .isNull();
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
