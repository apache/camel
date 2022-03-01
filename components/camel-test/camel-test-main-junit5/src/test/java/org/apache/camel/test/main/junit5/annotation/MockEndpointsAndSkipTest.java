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
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.test.main.junit5.CamelMainTest;
import org.apache.camel.test.main.junit5.Configure;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test ensuring that endpoints matching with a pattern can be mocked and skipped.
 */
@CamelMainTest(mockEndpointsAndSkip = "direct:foo")
class MockEndpointsAndSkipTest {

    @EndpointInject("mock:result")
    MockEndpoint mock1;

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
        // notice we have automatically mocked the direct:foo endpoints and the name
        // of the endpoints is "mock:uri"
        mock1.expectedBodiesReceived("Hello World");
        MockEndpoint mock2 = context.getEndpoint("mock:direct:foo", MockEndpoint.class);
        mock2.expectedMessageCount(1);

        template.sendBody("Hello World");

        assertIsSatisfied(context);

        // the message was not send to the direct:foo route and thus not sent to
        // the seda endpoint
        SedaEndpoint seda = context.getEndpoint("seda:foo", SedaEndpoint.class);
        assertEquals(0, seda.getCurrentQueueSize());
        assertNotNull(context.hasEndpoint("mock:direct:foo"));
        assertNull(context.hasEndpoint("mock:direct:foo2"));
    }

    @CamelMainTest(mockEndpointsAndSkip = "direct:foo2")
    @Nested
    class NestedTest {

        @EndpointInject("direct:start2")
        ProducerTemplate template2;

        @Test
        void shouldSupportNestedTest() throws Exception {
            // notice we have automatically mocked the direct:foo endpoints and the name
            // of the endpoints is "mock:uri"
            mock1.expectedBodiesReceived("Hello World");
            MockEndpoint mock2 = context.getEndpoint("mock:direct:foo2", MockEndpoint.class);
            mock2.expectedMessageCount(1);

            template2.sendBody("Hello World");

            assertIsSatisfied(context);

            // the message was not send to the direct:foo route and thus not sent to
            // the seda endpoint
            SedaEndpoint seda = context.getEndpoint("seda:foo", SedaEndpoint.class);
            assertEquals(0, seda.getCurrentQueueSize());
            assertNull(context.hasEndpoint("mock:direct:foo"));
            assertNotNull(context.hasEndpoint("mock:direct:foo2"));
        }
    }

    static class MyRouteBuilder extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            from("direct:start").to("direct:foo").to("mock:result");

            from("direct:foo").transform(constant("Bye World")).to("seda:foo");
            from("direct:start2").to("direct:foo2").to("mock:result");

            from("direct:foo2").transform(constant("Bye World")).to("seda:foo");
        }
    }
}
