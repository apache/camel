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
package org.apache.camel.test.junit5;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultRegistry;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class CamelTestSupportOneContextForAllTest extends CamelTestSupport {

    private static final CamelContext CUSTOM_CONTEXT;

    static {
        CUSTOM_CONTEXT = new MockContext();
    }

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return CUSTOM_CONTEXT;
    }

    @Override
    protected void doStopCamelContext(CamelContext context, Service camelContextService) {
        //don't stop
    }

    @Override
    protected void doSetUp() throws Exception {
        if (context == null) {
            super.doSetUp();
        }
    }

    @Test
    @Order(1)
    void initContextTest() throws Exception {
        String expectedBody = "<matched/>";

        resultEndpoint.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader(expectedBody, "foo", "bar");

        resultEndpoint.assertIsSatisfied();
        resultEndpoint.reset();

    }

    @Test
    @Order(2)
    void stopNotEnabledTest() throws Exception {
        String expectedBody = "<matched/>";

        resultEndpoint.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader(expectedBody, "foo", "bar");

        resultEndpoint.assertIsSatisfied();
        resultEndpoint.reset();

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").filter(header("foo").isEqualTo("bar")).to("mock:result");
            }
        };
    }

    private static class MockContext extends DefaultCamelContext {

        private boolean initialized;

        @Override
        protected Registry createRegistry() {
            if (initialized) {
                throw new UnsupportedOperationException();
            }
            initialized = true;
            return new DefaultRegistry();
        }

        @Override
        public void addRoutes(RoutesBuilder builder) throws Exception {
            //if routes are already added, do not add them again
            if (getRoutes().isEmpty()) {
                super.addRoutes(builder);
            }
        }
    }
}
