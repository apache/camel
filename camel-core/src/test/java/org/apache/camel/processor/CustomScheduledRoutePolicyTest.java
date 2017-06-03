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
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.RoutePolicySupport;

/**
 * @version 
 */
public class CustomScheduledRoutePolicyTest extends ContextTestSupport {

    private final MyCustomRoutePolicy policy = new MyCustomRoutePolicy();

    private static class MyCustomRoutePolicy extends RoutePolicySupport {

        private Route route;

        @Override
        public void onInit(Route route) {
            this.route = route;
        }

        public void startRoute() throws Exception {
            startRoute(route);
        }

    }

    public void testCustomPolicy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.setResultWaitTime(2000);

        template.sendBody("seda:foo", "Hello World");

        // wait 2 sec but the route is not started
        mock.assertIsNotSatisfied();

        // now start it using our policy
        policy.startRoute();

        // now the message should be routed
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").noAutoStartup().routePolicy(policy).to("mock:result");
            }
        };
    }
}