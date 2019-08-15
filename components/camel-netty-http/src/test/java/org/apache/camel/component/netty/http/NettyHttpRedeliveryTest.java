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
package org.apache.camel.component.netty.http;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyHttpRedeliveryTest extends BaseNettyTest {

    private final CountDownLatch latch = new CountDownLatch(5);

    @Test
    public void testHttpRedelivery() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        context.getRouteController().startRoute("bar");

        assertMockEndpointsSatisfied();

        context.getRouteController().stopRoute("foo");

        assertEquals(0, context.getInflightRepository().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                    .maximumRedeliveries(50).redeliveryDelay(100).onExceptionOccurred(
                        new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // signal to start the route (after 5 attempts)
                                latch.countDown();
                                // and there is only 1 inflight
                                assertEquals(1, context.getInflightRepository().size());
                            }
                        });

                from("timer:foo").routeId("foo")
                        .to("netty-http:http://0.0.0.0:{{port}}/bar?keepAlive=false&disconnect=true")
                        .to("mock:result");

                from("netty-http:http://0.0.0.0:{{port}}/bar").routeId("bar").autoStartup(false)
                        .setBody().constant("Bye World");
            }
        };
    }

}
