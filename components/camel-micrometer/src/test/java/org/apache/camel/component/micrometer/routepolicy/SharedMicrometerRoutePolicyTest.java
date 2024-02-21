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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SharedMicrometerRoutePolicyTest extends CamelTestSupport {

    protected MeterRegistry meterRegistry = new SimpleMeterRegistry();

    protected MicrometerRoutePolicy singletonPolicy = new MicrometerRoutePolicy();

    @Test
    public void testSharedPolicy() throws Exception {
        template.request("direct:foo", x -> {
        });
        template.request("direct:bar", x -> {
        });
        List<Meter> meters = meterRegistry.getMeters();
        long timers = meters.stream()
                .filter(it -> it instanceof Timer)
                .count();
        assertEquals(2L, timers, "timers count incorrect");
    }

    @BindToRegistry(MicrometerConstants.METRICS_REGISTRY_NAME)
    public MeterRegistry addRegistry() {
        return meterRegistry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo").routePolicy(singletonPolicy)
                        .to("mock:result");

                from("direct:bar").routeId("bar").routePolicy(singletonPolicy)
                        .to("mock:result");
            }
        };
    }
}
