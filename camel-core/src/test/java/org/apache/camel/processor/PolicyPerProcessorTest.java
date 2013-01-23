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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;

/**
 * @version 
 */
public class PolicyPerProcessorTest extends ContextTestSupport {

    public void testPolicy() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedHeaderReceived("foo", "was wrapped");
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedHeaderReceived("foo", "police finished execution");
        getMockEndpoint("mock:bar").expectedHeaderReceived("bar", "was wrapped");
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", "police finished execution");
        getMockEndpoint("mock:result").expectedHeaderReceived("bar", "police finished execution");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        MyPolicy foo = context.getRegistry().lookupByNameAndType("foo", MyPolicy.class);
        MyPolicy bar = context.getRegistry().lookupByNameAndType("bar", MyPolicy.class);

        assertEquals("Should only be invoked 1 time", 1, foo.getInvoked());
        assertEquals("Should only be invoked 1 time", 1, bar.getInvoked());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyPolicy("foo"));
        jndi.bind("bar", new MyPolicy("bar"));
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // only wrap policy foo around the to(mock:foo) - notice the end()
                    .policy("foo").to("mock:foo").end()
                    // only wrap policy bar around the to(mock:bar) - notice the end()
                    .policy("bar").to("mock:bar").end()
                    // and this has no policy
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

    public static class MyPolicy implements Policy {

        private final String name;
        private int invoked;

        public MyPolicy(String name) {
            this.name = name;
        }

        public void beforeWrap(RouteContext routeContext,
                ProcessorDefinition<?> definition) {
            // no need to modify the route
        }

        public Processor wrap(RouteContext routeContext, final Processor processor) {
            return new Processor() {
                public void process(Exchange exchange) throws Exception {
                    invoked++;
                    
                    exchange.getIn().setHeader(name, "was wrapped");
                    // let the original processor continue routing
                    processor.process(exchange);
                    exchange.getIn().setHeader(name, "police finished execution");
                }
            };
        }

        public int getInvoked() {
            return invoked;
        }
    }
}
