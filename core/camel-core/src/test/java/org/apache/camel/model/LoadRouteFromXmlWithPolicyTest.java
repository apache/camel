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
package org.apache.camel.model;

import java.io.InputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class LoadRouteFromXmlWithPolicyTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("foo", new MyPolicy("foo"));
        return jndi;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testLoadRouteFromXmlWitPolicy() throws Exception {
        InputStream is = getClass().getResourceAsStream("barPolicyRoute.xml");
        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        RoutesDefinition routes = (RoutesDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(ecc, is);
        context.addRouteDefinitions(routes.getRoutes());
        context.start();

        assertNotNull("Loaded foo route should be there", context.getRoute("foo"));
        assertNotNull("Loaded bar route should be there", context.getRoute("bar"));
        assertEquals(2, context.getRoutes().size());

        // test that loaded route works
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedHeaderReceived("foo", "was wrapped");
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedHeaderReceived("foo", "was wrapped");
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", "was wrapped");

        getMockEndpoint("mock:response").expectedMessageCount(1);
        getMockEndpoint("mock:response").expectedHeaderReceived("foo", "policy finished execution");
        template.sendBody("direct:send", "Hello World");

        assertMockEndpointsSatisfied();

        MyPolicy foo = context.getRegistry().lookupByNameAndType("foo", MyPolicy.class);

        assertEquals("Should only be invoked 1 time", 1, foo.getInvoked());
    }

    public static class MyPolicy implements Policy {

        private final String name;
        private int invoked;

        public MyPolicy(String name) {
            this.name = name;
        }

        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // no need to modify the route
        }

        @Override
        public Processor wrap(Route route, final Processor processor) {
            return new Processor() {
                public void process(Exchange exchange) throws Exception {
                    invoked++;
                    // let the original processor continue routing
                    exchange.getIn().setHeader(name, "was wrapped");
                    processor.process(exchange);
                    exchange.getIn().setHeader(name, "policy finished execution");
                }
            };
        }

        public int getInvoked() {
            return invoked;
        }
    }

}
