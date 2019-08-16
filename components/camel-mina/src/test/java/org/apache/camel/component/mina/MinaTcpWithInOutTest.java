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
package org.apache.camel.component.mina;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MinaTcpWithInOutTest extends BaseMinaTest {

    private String uri;
    private Exchange receivedExchange;
    private CountDownLatch latch;

    @Test
    public void testMinaRouteWithInOut() throws Exception {
        latch = new CountDownLatch(1);
        uri = String.format("mina:tcp://localhost:%1$s?textline=true", getPort());

        MinaReverserServer server = new MinaReverserServer(getPort());
        server.start();

        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:x").to(uri).process(new Processor() {

                    public void process(Exchange e) {
                        receivedExchange = e;
                        latch.countDown();
                    }
                });
            }
        });
        context.start();

        // now lets fire in a message
        Endpoint endpoint = context.getEndpoint("direct:x");
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
        Message message = exchange.getIn();
        message.setBody("Hello!");
        message.setHeader("cheese", 123);

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);

        // now lets sleep for a while
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(receivedExchange.getIn());
        assertEquals("!olleH", receivedExchange.getIn().getBody());

        producer.stop();
        context.stop();
        server.stop();
    }

    @Test
    public void testMinaRouteWithInOutLazy() throws Exception {
        latch = new CountDownLatch(1);
        uri = String.format("mina:tcp://localhost:%1$s?textline=true&lazySessionCreation=true", getPort());

        // The server is activated after Camel to check if the lazyness is working
        MinaReverserServer server = new MinaReverserServer(getPort());
        server.start();

        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:x").to(uri).process(new Processor() {

                    public void process(Exchange e) {
                        receivedExchange = e;
                        latch.countDown();
                    }
                });
            }
        });
        context.start();

        // now lets fire in a message
        Endpoint endpoint = context.getEndpoint("direct:x");
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
        Message message = exchange.getIn();
        message.setBody("Hello!");
        message.setHeader("cheese", 123);

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);

        // now lets sleep for a while
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(receivedExchange.getIn());
        assertEquals("!olleH", receivedExchange.getIn().getBody());

        producer.stop();
        context.stop();
        server.stop();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
