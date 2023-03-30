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
package org.apache.camel.impl;

import org.apache.camel.BeanInject;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

public class BeanInjectRouteBuilderTest extends ContextTestSupport {

    @BeanInject
    private FooBar foo;

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("foo", new FooBar());
        return registry;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // manual post process us as ContextTestSupport in camel-core doesn't do
        // that out of the box
        CamelBeanPostProcessor post = PluginHelper.getBeanPostProcessor(context);
        post.postProcessBeforeInitialization(this, "MyRoute");
        post.postProcessAfterInitialization(this, "MyRoute");
        return context;
    }

    @Test
    public void testBeanInject() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String out = foo.hello(exchange.getIn().getBody(String.class));
                        exchange.getIn().setBody(out);
                    }
                }).to("mock:result");
            }
        };
    }
}
