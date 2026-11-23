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

import org.apache.camel.AsyncProducer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request that fails while it is set up on its channel (before it is sent) must give the channel it borrowed back, so
 * the next request can borrow one from the producer pool.
 */
class NettyProducerFailedSetupTest extends BaseNettyTest {

    @BindToRegistry("myManager")
    private final MyCorrelationManager myManager = new MyCorrelationManager();

    private AsyncProducer producer;

    @AfterEach
    void stopProducer() {
        ServiceHelper.stopService(producer);
    }

    @Test
    void testInvalidRequestTimeoutHeader() throws Exception {
        startProducer("producerPoolMaxTotal=1&producerPoolMaxWait=2000&requestTimeout=5000");

        Exchange bad = send("abc-hello", "not a number");
        assertNotNull(bad.getException(), "The request with an invalid timeout should fail");

        Exchange good = send("abc-hello", null);
        assertNull(good.getException(), "The next request should get a channel from the pool");
        assertEquals("abc-hello Bye", good.getMessage().getBody(String.class));
    }

    @Test
    void testMissingCorrelationId() throws Exception {
        startProducer("correlationManager=#myManager&producerPoolMaxTotal=1&producerPoolMaxWait=2000");

        // no correlation id: the correlation manager does not accept the request
        Exchange bad = send("", null);
        assertInstanceOf(IllegalArgumentException.class, bad.getException());

        Exchange good = send("abc-hello", null);
        assertNull(good.getException(), "The next request should get a channel from the pool");
        assertEquals("abc-hello Bye", good.getMessage().getBody(String.class));
    }

    @Test
    void testInvalidRequestTimeoutHeaderWithSharedChannel() throws Exception {
        startProducer("producerPoolEnabled=false");

        Exchange bad = send("abc-hello", "not a number");
        assertNotNull(bad.getException(), "The request with an invalid timeout should fail");

        Exchange good = send("abc-hello", null);
        assertNull(good.getException(), "The next request should use the shared channel");
        assertEquals("abc-hello Bye", good.getMessage().getBody(String.class));
    }

    private void startProducer(String options) throws Exception {
        producer = context.getEndpoint("netty:tcp://localhost:{{port}}?textline=true&sync=true&" + options)
                .createAsyncProducer();
        ServiceHelper.startService(producer);
    }

    private Exchange send(String body, String requestTimeout) throws InterruptedException {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getMessage().setBody(body);
        if (requestTimeout != null) {
            exchange.getMessage().setHeader(NettyConstants.NETTY_REQUEST_TIMEOUT, requestTimeout);
        }
        CountDownLatch done = new CountDownLatch(1);
        producer.process(exchange, doneSync -> done.countDown());
        assertTrue(done.await(10, TimeUnit.SECONDS), "The exchange should be completed");
        return exchange;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?textline=true&sync=true").transform(body().append(" Bye"));
            }
        };
    }

    private static final class MyCorrelationManager extends TimeoutCorrelationManagerSupport {
        @Override
        public String getRequestCorrelationId(Object request) {
            String s = request.toString();
            return s.length() >= 3 ? s.substring(0, 3) : null;
        }

        @Override
        public String getResponseCorrelationId(Object response) {
            return response.toString().substring(0, 3);
        }
    }
}
