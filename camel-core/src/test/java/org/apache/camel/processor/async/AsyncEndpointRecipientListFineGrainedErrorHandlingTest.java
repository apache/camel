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
package org.apache.camel.processor.async;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class AsyncEndpointRecipientListFineGrainedErrorHandlingTest extends ContextTestSupport {

    private static int counter;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("fail", new MyFailBean());
        return jndi;
    }

    public void testAsyncEndpointOK() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                onException(Exception.class).redeliveryDelay(0).maximumRedeliveries(2);

                from("direct:start")
                    .to("mock:a")
                    .recipientList(header("foo")).stopOnException();
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:baz").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "mock:foo,async:bye:camel,mock:bar,mock:baz");

        assertMockEndpointsSatisfied();
    }

    public void testAsyncEndpointERROR() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                onException(Exception.class).redeliveryDelay(0).maximumRedeliveries(2);

                from("direct:start")
                    .to("mock:a")
                    .recipientList(header("foo")).stopOnException();
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:baz").expectedMessageCount(0);

        try {
            template.sendBodyAndHeader("direct:start", "Hello World", "foo", "mock:foo,mock:bar,bean:fail,mock:baz");
            fail("Should throw exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();

        assertEquals(3, counter);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public static class MyFailBean {

        public String doSomething(Exchange exchange) throws Exception {
            counter++;
            assertEquals("bean://fail", exchange.getProperty(Exchange.TO_ENDPOINT, String.class));
            throw new IllegalArgumentException("Damn");
        }
    }
}