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
package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.Test;

public class RouteServicesStartupOrderTest extends ContextTestSupport {

    private static String startOrder = "";

    private MyServiceBean service1 = new MyServiceBean("1");
    private MyServiceBean service2 = new MyServiceBean("2");
    private MyServiceBean service3 = new MyServiceBean("3");
    private MyServiceBean service4 = new MyServiceBean("4");

    @Test
    public void testRouteServiceStartupOrder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // assert correct order
        DefaultCamelContext dcc = (DefaultCamelContext)context;
        List<RouteStartupOrder> order = dcc.getRouteStartupOrder();

        assertEquals(4, order.size());
        assertEquals("seda://foo", order.get(0).getRoute().getEndpoint().getEndpointUri());
        assertEquals("direct://start", order.get(1).getRoute().getEndpoint().getEndpointUri());
        assertEquals("seda://bar", order.get(2).getRoute().getEndpoint().getEndpointUri());
        assertEquals("direct://bar", order.get(3).getRoute().getEndpoint().getEndpointUri());

        // assert route service was started in order as well
        assertEquals("2143", startOrder);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").startupOrder(2).process(service1).to("seda:foo");

                from("seda:foo").startupOrder(1).process(service2).to("mock:result");

                from("direct:bar").startupOrder(9).process(service3).to("seda:bar");

                from("seda:bar").startupOrder(5).process(service4).to("mock:other");
            }
        };
    }

    public class MyServiceBean extends ServiceSupport implements Processor {

        private String name;
        private boolean started;

        public MyServiceBean(String name) {
            this.name = name;
        }

        @Override
        protected void doStart() throws Exception {
            startOrder += name;
            started = true;
        }

        @Override
        protected void doStop() throws Exception {
            started = false;
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        public String getName() {
            return name;
        }

        public void setStarted(boolean started) {
            this.started = started;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
        }
    }
}
