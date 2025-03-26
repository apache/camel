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
package org.apache.camel.component.micrometer.routepolicy;

import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Must run last
 */
public class ZMicrometerRoutePolicyExcludePatternTest extends AbstractMicrometerRoutePolicyTest {

    @Override
    protected MicrometerRoutePolicyFactory createMicrometerRoutePolicyFactory() {
        MicrometerRoutePolicyFactory factory = super.createMicrometerRoutePolicyFactory();
        factory.getPolicyConfiguration().setContextEnabled(false);
        factory.getPolicyConfiguration().setRouteEnabled(true);
        factory.getPolicyConfiguration().setExcludePattern("bar");
        return factory;
    }

    @Test
    public void testMetricsRoutePolicy() throws Exception {
        int count = 10;
        getMockEndpoint("mock:foo").expectedMessageCount(count);
        getMockEndpoint("mock:bar").expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            template.sendBody("direct:foo", "Hello World");
        }

        MockEndpoint.assertIsSatisfied(context);

        // there should be 6 metrics per route (only 1 route as bar is excluded)
        // additionally one for App info gauge
        List<Meter> meters = meterRegistry.getMeters();
        assertEquals(7, meters.size());
        meters.forEach(meter -> assertTrue(meter instanceof Timer || meter instanceof Counter || meter instanceof Gauge));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo")
                        .to("direct:bar")
                        .to("mock:foo");

                from("direct:bar").routeId("bar")
                        .to("mock:bar");
            }
        };
    }
}
