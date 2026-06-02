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
package org.apache.camel.component.log;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.MaskingFormatter;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogMaskRouteOverrideTest {

    protected Registry registry;

    protected CamelContext createCamelContext() throws Exception {
        registry = new DefaultRegistry();
        CamelContext context = new DefaultCamelContext(registry);
        context.addRoutes(createRouteBuilder());
        return context;
    }

    @Test
    public void testRouteLogMaskFalseOverridesContextTrue() throws Exception {
        CamelContext context = createCamelContext();
        TrackingMaskingFormatter tracker = new TrackingMaskingFormatter();
        registry.bind(MaskingFormatter.CUSTOM_LOG_MASK_REF, tracker);
        context.setLogMask(true);
        context.start();

        MockEndpoint masked = context.getEndpoint("mock:masked", MockEndpoint.class);
        masked.expectedMessageCount(1);
        MockEndpoint noMask = context.getEndpoint("mock:noMask", MockEndpoint.class);
        noMask.expectedMessageCount(1);

        // send to the route WITH masking (context default)
        tracker.received = null;
        context.createProducerTemplate().sendBody("direct:masked", "password=secret123");
        masked.assertIsSatisfied();
        assertNotNull(tracker.received, "Masking formatter should have been called for the masked route");

        // send to the route with logMask=false override
        tracker.received = null;
        context.createProducerTemplate().sendBody("direct:noMask", "password=secret123");
        noMask.assertIsSatisfied();
        assertNull(tracker.received, "Masking formatter should NOT have been called for the noMask route");

        context.stop();
    }

    @Test
    public void testRouteLogMaskTrueOverridesContextFalse() throws Exception {
        CamelContext context = createCamelContext();
        TrackingMaskingFormatter tracker = new TrackingMaskingFormatter();
        registry.bind(MaskingFormatter.CUSTOM_LOG_MASK_REF, tracker);
        context.setLogMask(false);
        context.start();

        MockEndpoint forceMask = context.getEndpoint("mock:forceMask", MockEndpoint.class);
        forceMask.expectedMessageCount(1);

        tracker.received = null;
        context.createProducerTemplate().sendBody("direct:forceMask", "password=secret123");
        forceMask.assertIsSatisfied();
        assertNotNull(tracker.received, "Masking formatter should have been called for the forceMask route");
        assertTrue(tracker.received.contains("password=secret123"));

        context.stop();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:masked").routeId("masked")
                        .to("log:masked.logger")
                        .to("mock:masked");

                from("direct:noMask").routeId("noMask").logMask("false")
                        .to("log:noMask.logger")
                        .to("mock:noMask");

                from("direct:forceMask").routeId("forceMask").logMask("true")
                        .to("log:forceMask.logger")
                        .to("mock:forceMask");
            }
        };
    }

    public static class TrackingMaskingFormatter implements MaskingFormatter {
        private String received;

        @Override
        public String format(String source) {
            received = source;
            return source;
        }
    }
}
