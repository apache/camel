/**
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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.TestSupport;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.model.RouteDefinitionHelper.from;

public class RoutesFromRegistryTest extends TestSupport {

    public void testRoutes() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("start", from("direct:start").to("mock:stop"));
        registry.put("begin", from("direct:begin").to("mock:end"));

        CamelContext context = new DefaultCamelContext(registry);

        try {
            context.start();

            Route start = context.getRoute("start");
            assertNotNull(start);
            assertEquals("start", start.getId());

            Route begin = context.getRoute("begin");
            assertNotNull(begin);
            assertEquals("begin", begin.getId());

            context.getEndpoint("mock:stop", MockEndpoint.class).expectedMessageCount(1);
            context.getEndpoint("mock:stop", MockEndpoint.class).expectedBodiesReceived("start");
            context.getEndpoint("mock:end", MockEndpoint.class).expectedMessageCount(1);
            context.getEndpoint("mock:end", MockEndpoint.class).expectedBodiesReceived("begin");

            ProducerTemplate template = context.createProducerTemplate();
            template.sendBody("direct:start", "start");
            template.sendBody("direct:begin", "begin");

            MockEndpoint.assertIsSatisfied(context);
        } finally {
            context.stop();
        }
    }

    public void testUpdateRoutes() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("start", from("direct:start").to("mock:stop"));
        registry.put("begin", from("direct:begin").to("mock:end"));

        CamelContext context = new DefaultCamelContext(registry);

        try {
            context.start();

            Route start = context.getRoute("start");
            assertNotNull(start);
            assertEquals("start", start.getId());

            Route begin = context.getRoute("begin");
            assertNotNull(begin);
            assertEquals("begin", begin.getId());

            context.stop();

            registry.remove("start");
            registry.put("test", from("direct:test").to("mock:test"));

            context.start();

            start = context.getRoute("start");
            assertNull(start);

            Route test = context.getRoute("test");
            assertNotNull(test);
            assertEquals("test", test.getId());

        } finally {
            context.stop();
        }
    }
}
