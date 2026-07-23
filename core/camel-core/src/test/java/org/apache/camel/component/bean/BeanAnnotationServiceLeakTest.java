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
package org.apache.camel.component.bean;

import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RoutingSlip;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that @RoutingSlip/@RecipientList/@DynamicRouter annotation processors are not registered as CamelContext
 * services, which would cause a service leak when BeanInfo cache entries are evicted and re-created.
 */
public class BeanAnnotationServiceLeakTest extends ContextTestSupport {

    @Test
    public void testRoutingSlipAnnotationDoesNotLeakServices() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();

        Set<org.apache.camel.processor.RoutingSlip> services
                = context.hasServices(org.apache.camel.processor.RoutingSlip.class);
        assertTrue(services.isEmpty(),
                "RoutingSlip processor should not be registered as a CamelContext service, but found: " + services.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").bean(new MyRoutingSlipBean());
            }
        };
    }

    public static class MyRoutingSlipBean {
        @RoutingSlip
        public String[] route(String body) {
            return new String[] { "mock:foo", "mock:result" };
        }
    }
}
