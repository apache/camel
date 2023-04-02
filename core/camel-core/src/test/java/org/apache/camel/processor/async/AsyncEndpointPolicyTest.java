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
package org.apache.camel.processor.async;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.AsyncCallbackToCompletableFutureAdapter;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class AsyncEndpointPolicyTest extends ContextTestSupport {

    private static String beforeThreadName;
    private static String afterThreadName;

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("foo", new MyPolicy("foo"));
        return jndi;
    }

    @Test
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

        assertEquals(1, foo.getInvoked(), "Should only be invoked 1 time");

        assertFalse(beforeThreadName.equalsIgnoreCase(afterThreadName), "Should use different threads");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start")
                        // wraps the entire route in the same policy
                        .policy("foo").to("mock:foo").to("async:bye:camel").to("mock:bar").to("mock:result");

                from("direct:send").to("mock:before").to("log:before").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        beforeThreadName = Thread.currentThread().getName();
                    }
                }).to("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        afterThreadName = Thread.currentThread().getName();
                    }
                }).to("log:after").to("mock:after").to("mock:response");
            }
        };
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
            return new AsyncProcessor() {
                public boolean process(final Exchange exchange, final AsyncCallback callback) {
                    invoked++;
                    // let the original processor continue routing
                    exchange.getIn().setHeader(name, "was wrapped");
                    AsyncProcessor ap = AsyncProcessorConverterHelper.convert(processor);
                    ap.process(exchange, doneSync -> {
                        exchange.getIn().setHeader(name, "policy finished execution");
                        callback.done(false);
                    });
                    return false;
                }

                public void process(Exchange exchange) throws Exception {
                    final AsyncProcessorAwaitManager awaitManager
                            = PluginHelper.getAsyncProcessorAwaitManager(exchange.getContext());
                    awaitManager.process(this, exchange);
                }

                public CompletableFuture<Exchange> processAsync(Exchange exchange) {
                    AsyncCallbackToCompletableFutureAdapter<Exchange> callback
                            = new AsyncCallbackToCompletableFutureAdapter<>(exchange);
                    process(exchange, callback);
                    return callback.getFuture();
                }
            };
        }

        public int getInvoked() {
            return invoked;
        }
    }

}
