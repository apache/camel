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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.AsyncProcessorHelper;

/**
 * @version 
 */
public class AsyncEndpointPolicyTest extends ContextTestSupport {

    private static String beforeThreadName;
    private static String afterThreadName;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyPolicy("foo"));
        return jndi;
    }

    public void testAsyncEndpoint() throws Exception {
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

        assertFalse("Should use different threads", beforeThreadName.equalsIgnoreCase(afterThreadName));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start")
                    // wraps the entire route in the same policy
                    .policy("foo")
                        .to("mock:foo")
                        .to("async:bye:camel")
                        .to("mock:bar")
                        .to("mock:result");

                from("direct:send")
                    .to("mock:before")
                    .to("log:before")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            beforeThreadName = Thread.currentThread().getName();
                        }
                    })
                    .to("direct:start")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            afterThreadName = Thread.currentThread().getName();
                        }
                    })
                    .to("log:after")
                    .to("mock:after")
                    .to("mock:response");
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
            return new AsyncProcessor() {
                public boolean process(final Exchange exchange, final AsyncCallback callback) {
                    invoked++;
                    // let the original processor continue routing
                    exchange.getIn().setHeader(name, "was wrapped");
                    AsyncProcessor ap = AsyncProcessorConverterHelper.convert(processor);
                    boolean sync = ap.process(exchange, new AsyncCallback() {
                        public void done(boolean doneSync) {
                            // we only have to handle async completion of this policy
                            if (doneSync) {
                                return;
                            }

                            exchange.getIn().setHeader(name, "policy finished execution");
                            callback.done(false);
                        }
                    });

                    if (!sync) {
                        // continue routing async
                        return false;
                    }

                    // we are done synchronously, so do our after work and invoke the callback
                    exchange.getIn().setHeader(name, "policy finished execution");
                    callback.done(true);
                    return true;
                }

                public void process(Exchange exchange) throws Exception {
                    AsyncProcessorHelper.process(this, exchange);
                }
            };
        }

        public int getInvoked() {
            return invoked;
        }
    }


}