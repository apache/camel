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
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RouteAware;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ServiceSupport;

public class RouteAwareRouteTest extends ContextTestSupport {

    public void testRouteAware() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("foo");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                    .process(new MyProcessor())
                    .to("mock:result");
            }
        };
    }

    private static final class MyProcessor extends ServiceSupport implements Processor, RouteAware {

        private Route route;

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setBody(route.getId());
        }

        @Override
        public void setRoute(Route route) {
            this.route = route;
        }

        @Override
        public Route getRoute() {
            return route;
        }

        @Override
        protected void doStart() throws Exception {
            // noop
        }

        @Override
        protected void doStop() throws Exception {
            // noop
        }
    }
}
