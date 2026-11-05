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
package org.apache.camel.component.netty;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With sync=false the producer continues routing as soon as the request is written, so a reply that the remote server
 * sends later must not be set on the exchange.
 */
class NettyProducerSyncFalseReplyTest extends BaseNettyTest {

    @Test
    void theReplyOfTheServerIsNotSetOnTheExchange() throws Exception {
        getMockEndpoint("mock:server").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").setAssertPeriod(500);

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void anInOutExchangeKeepsItsMessage() throws Exception {
        getMockEndpoint("mock:server").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").setAssertPeriod(500);

        // no reply is received, so the producer must not leave an empty out message on an InOut exchange
        Object reply = template.requestBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals("Hello World", reply);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("netty:tcp://localhost:{{port}}?sync=false")
                        // continue on another thread so the netty I/O thread is free to read the reply of the server
                        .threads(1)
                        // the body must stay the request, also after the reply of the server has arrived
                        .process(exchange -> await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                                .until(() -> "Hello World".equals(exchange.getMessage().getBody(String.class))))
                        .to("mock:result");

                from("netty:tcp://localhost:{{port}}?sync=true")
                        .to("mock:server")
                        .setBody(constant("Bye World"));
            }
        };
    }
}
