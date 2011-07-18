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

import org.apache.camel.AsyncCallback;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.loadbalancer.LoadBalancerSupport;

public class CustomLoadBalanceTest extends ContextTestSupport {
    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        y = getMockEndpoint("mock:y");
        z = getMockEndpoint("mock:z");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("direct:start")
                    // using our custom load balancer
                    .loadBalance(new MyLoadBalancer())
                    .to("mock:x", "mock:y", "mock:z");
                // END SNIPPET: e1
            }
        };
    }

    public void testCustomLoadBalancer() throws Exception {
        x.expectedBodiesReceived("x", "x", "x");
        y.expectedBodiesReceived("y", "y");
        z.expectedBodiesReceived("foo", "bar", "baz");

        template.sendBody("direct:start", "x");
        template.sendBody("direct:start", "y");
        template.sendBody("direct:start", "foo");
        template.sendBody("direct:start", "bar");
        template.sendBody("direct:start", "y");
        template.sendBody("direct:start", "x");
        template.sendBody("direct:start", "x");
        template.sendBody("direct:start", "baz");

        assertMockEndpointsSatisfied();
    }

    // START SNIPPET: e2
    public static class MyLoadBalancer extends LoadBalancerSupport {

        public boolean process(Exchange exchange, AsyncCallback callback) {
            String body = exchange.getIn().getBody(String.class);
            try {
                if ("x".equals(body)) {
                    getProcessors().get(0).process(exchange);
                } else if ("y".equals(body)) {
                    getProcessors().get(1).process(exchange);
                } else {
                    getProcessors().get(2).process(exchange);
                }
            } catch (Throwable e) {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }
    }
    // END SNIPPET: e2

}