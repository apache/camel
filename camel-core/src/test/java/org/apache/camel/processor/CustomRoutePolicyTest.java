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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.RoutePolicySupport;

/**
 * @version $Revision$
 */
public class CustomRoutePolicyTest extends ContextTestSupport {

    private final MyCustomRoutePolicy policy = new MyCustomRoutePolicy();

    private class MyCustomRoutePolicy extends RoutePolicySupport {

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            String body = exchange.getIn().getBody(String.class);
            if ("stop".equals(body)) {
                try {
                    stopConsumer(route.getConsumer());
                } catch (Exception e) {
                    handleException(e);
                }
            }
        }
    }

    public void testCustomPolicy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("stop");

        // we send stop command so we should only get 1 message
        template.sendBody("seda:foo", "stop");

        Thread.sleep(500);

        template.sendBody("seda:foo", "Bye World");

        assertMockEndpointsSatisfied();

        // we reset and prepare for the last message to arrive
        mock.reset();
        mock.expectedBodiesReceived("Bye World");

        // start the route consumer again
        context.getRoutes().get(0).getConsumer().start();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routePolicy(policy).to("mock:result");
            }
        };
    }
}

