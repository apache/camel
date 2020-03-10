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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class DefinitionPolicyPerProcessorTest extends ContextTestSupport {

    @Test
    public void testDefintionAugmentationPolicy() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedHeaderReceived("foo", "was wrapped");
        getMockEndpoint("mock:foo").expectedBodyReceived().constant("body was altered");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        MyPolicy foo = context.getRegistry().lookupByNameAndType("foo", MyPolicy.class);
        assertEquals("Should only be invoked 1 time", 1, foo.getInvoked());
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("foo", new MyPolicy("foo"));
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // only wrap policy foo around the to(mock:foo) - notice the
                    // end()
                    .policy("foo").setBody().constant("body not altered").to("mock:foo").end();
            }
        };
    }

    public static class MyPolicy implements Policy {

        private final String name;
        private int invoked;

        public MyPolicy(String name) {
            this.name = name;
        }

        public int getInvoked() {
            return invoked;
        }

        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            SetBodyDefinition bodyDef = (SetBodyDefinition)((ProcessorDefinition<?>)definition).getOutputs().get(0);
            bodyDef.setExpression(new ConstantExpression("body was altered"));
        }

        @Override
        public Processor wrap(final Route route, final Processor processor) {
            return new Processor() {
                public void process(Exchange exchange) throws Exception {
                    invoked++;
                    exchange.getIn().setHeader(name, "was wrapped");
                    processor.process(exchange);
                }
            };
        }
    }
}
