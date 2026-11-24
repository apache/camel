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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * With reuseChannel=true the channel is given back to the producer pool when the exchange is done: also when the
 * exchange failed, or a bounded pool runs out of channels.
 */
class NettyReuseChannelFailedExchangeTest extends BaseNettyTest {

    private static final String PRODUCER_OPTIONS
            = "textline=true&sync=true&reuseChannel=true&producerPoolMaxTotal=1&producerPoolMaxWait=2000";

    private final CountDownLatch releaseSlowReply = new CountDownLatch(1);

    @AfterEach
    void releaseServer() {
        releaseSlowReply.countDown();
    }

    @Test
    void testFailedExchangeGivesBackItsChannel() {
        // the reply is received, and then the exchange fails further on in the route
        Exchange failed = template.request("direct:start", e -> {
            e.getMessage().setBody("World");
            e.getMessage().setHeader("fail", true);
        });
        assertInstanceOf(IllegalStateException.class, failed.getException());

        Exchange next = template.request("direct:start", e -> e.getMessage().setBody("World"));
        assertNull(next.getException(), "The next exchange should get a channel from the pool");
        assertEquals("Hello World", next.getMessage().getBody(String.class));
    }

    @Test
    void testTimedOutRequestGivesBackItsChannel() {
        // the request times out, which closes its channel: the closed channel must be removed from the pool
        Exchange failed = template.request("direct:timeout", e -> e.getMessage().setBody("slow"));
        assertInstanceOf(ReadTimeoutException.class, failed.getException());

        Exchange next = template.request("direct:timeout", e -> e.getMessage().setBody("World"));
        assertNull(next.getException(), "The next exchange should get a channel from the pool");
        assertEquals("Hello World", next.getMessage().getBody(String.class));
    }

    @Test
    void testRequestFailingWhileSetUpGivesBackItsChannel() {
        // the request fails on its channel before it is sent (an invalid request timeout header)
        Exchange failed = template.request("direct:start", e -> {
            e.getMessage().setBody("World");
            e.getMessage().setHeader(NettyConstants.NETTY_REQUEST_TIMEOUT, "not a number");
        });
        assertNotNull(failed.getException(), "The request with an invalid timeout should fail");

        Exchange next = template.request("direct:start", e -> e.getMessage().setBody("World"));
        assertNull(next.getException(), "The next exchange should get a channel from the pool");
        assertEquals("Hello World", next.getMessage().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?textline=true&sync=true")
                        .process(e -> {
                            if ("slow".equals(e.getMessage().getBody(String.class))) {
                                releaseSlowReply.await(5, TimeUnit.SECONDS);
                            }
                        })
                        .transform(body().prepend("Hello "));

                from("direct:start")
                        .to("netty:tcp://localhost:{{port}}?" + PRODUCER_OPTIONS)
                        .process(e -> {
                            if (e.getMessage().getHeader("fail", false, Boolean.class)) {
                                throw new IllegalStateException("Forced");
                            }
                        });

                from("direct:timeout")
                        .to("netty:tcp://localhost:{{port}}?" + PRODUCER_OPTIONS + "&requestTimeout=500");
            }
        };
    }
}
