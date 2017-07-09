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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.RoutePolicySupport;

import static org.awaitility.Awaitility.await;

/**
 * @version 
 */
public class AsyncEndpointCustomRoutePolicyTest extends ContextTestSupport {

    private static String beforeThreadName;
    private static String afterThreadName;

    private final MyCustomRoutePolicy policy = new MyCustomRoutePolicy();

    private static class MyCustomRoutePolicy extends RoutePolicySupport {

        private volatile int invoked;
        private volatile AtomicBoolean stopped = new AtomicBoolean();

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            invoked++;
            if (invoked >= 2) {
                try {
                    stopped.set(true);
                    stopConsumer(route.getConsumer());
                } catch (Exception e) {
                    handleException(e);
                }
            }
        }

        public boolean isStopped() {
            return stopped.get();
        }
    }

    public void testAsyncEndpoint() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye Camel");

        getMockEndpoint("mock:before").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:after").expectedBodiesReceived("Bye Camel");

        String reply = template.requestBody("direct:start", "Hello Camel", String.class);
        assertEquals("Bye Camel", reply);

        assertMockEndpointsSatisfied();

        assertFalse("Should use different threads", beforeThreadName.equalsIgnoreCase(afterThreadName));

        mock.reset();
        mock.expectedMessageCount(1);

        // we send a 2nd message which should cause it to stop
        template.sendBody("direct:start", "stop");

        mock.assertIsSatisfied();

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> assertTrue("Should be stopped", policy.isStopped()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start").routeId("foo").routePolicy(policy)
                        .to("mock:before")
                        .to("log:before")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                beforeThreadName = Thread.currentThread().getName();
                            }
                        })
                        .to("async:bye:camel")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                afterThreadName = Thread.currentThread().getName();
                            }
                        })
                        .to("log:after")
                        .to("mock:after")
                        .to("mock:result");
            }
        };
    }

}
