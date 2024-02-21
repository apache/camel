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
package org.apache.camel.test.main.junit5.annotation;

import org.apache.camel.BeanInject;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.test.main.junit5.CamelMainTest;
import org.apache.camel.test.main.junit5.Configure;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test ensuring that endpoints matching with a pattern can be mocked.
 */
@CamelMainTest(mockEndpoints = "*")
class MockEndpointTest {

    @EndpointInject("direct:start")
    ProducerTemplate template;

    @BeanInject
    CamelContext context;

    @Configure
    protected void configure(MainConfigurationProperties configuration) {
        configuration.withAutoConfigurationEnabled(false);
        configuration.addRoutesBuilder(new MyRouteBuilder());
    }

    @Test
    void shouldMockEndpoints() throws Exception {
        // notice we have automatically mocked all endpoints and the name of the
        // endpoints is "mock:uri"
        MockEndpoint mock1 = context.getEndpoint("mock:direct:start", MockEndpoint.class);
        MockEndpoint mock2 = context.getEndpoint("mock:direct:foo", MockEndpoint.class);
        MockEndpoint mock3 = context.getEndpoint("mock:log:foo", MockEndpoint.class);
        MockEndpoint mock4 = context.getEndpoint("mock:result", MockEndpoint.class);
        mock1.expectedBodiesReceived("Hello World");
        mock2.expectedBodiesReceived("Hello World");
        mock3.expectedBodiesReceived("Bye World");
        mock4.expectedBodiesReceived("Bye World");

        template.sendBody("Hello World");

        assertIsSatisfied(context);

        // additional test to ensure correct endpoints in registry
        assertNotNull(context.hasEndpoint("direct:start"));
        assertNotNull(context.hasEndpoint("direct:foo"));
        assertNotNull(context.hasEndpoint("log:foo"));
        assertNotNull(context.hasEndpoint("mock:result"));
        // all the endpoints was mocked
        assertNotNull(context.hasEndpoint("mock:direct:start"));
        assertNotNull(context.hasEndpoint("mock:direct:foo"));
        assertNotNull(context.hasEndpoint("mock:log:foo"));
    }

    @CamelMainTest(mockEndpoints = "direct:*")
    @Nested
    class NestedTest {

        @Test
        void shouldSupportNestedTest() throws Exception {
            // notice we have automatically mocked all direct endpoints and the name of the
            // endpoints is "mock:uri"
            MockEndpoint mock1 = context.getEndpoint("mock:direct:start", MockEndpoint.class);
            MockEndpoint mock2 = context.getEndpoint("mock:direct:foo", MockEndpoint.class);
            MockEndpoint mock4 = context.getEndpoint("mock:result", MockEndpoint.class);
            mock1.expectedBodiesReceived("Hello World");
            mock2.expectedBodiesReceived("Hello World");
            mock4.expectedBodiesReceived("Bye World");

            template.sendBody("Hello World");

            assertIsSatisfied(context);

            // additional test to ensure correct endpoints in registry
            assertNotNull(context.hasEndpoint("direct:start"));
            assertNotNull(context.hasEndpoint("direct:foo"));
            assertNotNull(context.hasEndpoint("log:foo"));
            assertNotNull(context.hasEndpoint("mock:result"));
            // all the endpoints was mocked
            assertNotNull(context.hasEndpoint("mock:direct:start"));
            assertNotNull(context.hasEndpoint("mock:direct:foo"));
            assertNull(context.hasEndpoint("mock:log:foo"));
        }
    }

    static class MyRouteBuilder extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("direct:start").to("direct:foo").to("log:foo").to("mock:result");

            from("direct:foo").transform(constant("Bye World"));
        }
    }
}
