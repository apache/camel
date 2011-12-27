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
package org.apache.camel.component.context;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Allows for use of the Java DSL to create a black box CamelContext and then test its use from another context
 */
public class JavaDslBlackBoxTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:results")
    private MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    private ProducerTemplate template;

    @Test
    public void testUsingContextComponent() throws Exception {
        resultEndpoint.expectedHeaderReceived("received", "true");
        resultEndpoint.expectedMessageCount(2);

        template.sendBody("<purchaseOrder>one</purchaseOrder>");
        template.sendBody("<purchaseOrder>two</purchaseOrder>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        // let's create our black box as a Camel context and a set of routes
        DefaultCamelContext blackBox = new DefaultCamelContext(registry);
        blackBox.setName("blackBox");
        blackBox.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // we received purchase orders, so let's process it in some way then
                // send an invoice to our invoice endpoint
                from("direct:purchaseOrder").setHeader("received").constant("true").to("direct:invoice");
            }
        });
        blackBox.start();

        registry.bind("accounts", blackBox);
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("accounts:purchaseOrder");

                from("accounts:invoice").to("mock:results");
            }
        };
    }
}
