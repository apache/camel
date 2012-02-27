/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.camel.component.mina2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

/**
 * @version
 */
public class Mina2TcpAsyncOutOnly extends BaseMina2Test {

    private String uri;
    private Exchange receivedExchange;
    private CountDownLatch latch;
    Boolean sessionCreated = Boolean.FALSE;

    @Before
    public void setup() {
        sessionCreated = Boolean.FALSE;
    }

    @Test
    public void testMina2SessionCreation() throws Exception {
        latch = new CountDownLatch(1);

        // now lets fire in a message
        Endpoint endpoint = context.getEndpoint("direct:x");
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
        Message message = exchange.getIn();
        //message.setBody("Hello!");

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);

        // now lets sleep for a while
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not receive the message!", received);
        assertTrue("Did not receive session creation event!", sessionCreated.booleanValue());

        producer.stop();
    }

    @Test
    public void testMina2SessionCreatedOpenedClosed() throws Exception {
        latch = new CountDownLatch(3);

        // now lets fire in a message
        template.sendBody("direct:x", "nada");
//        Endpoint endpoint = context.getEndpoint("direct:x");
//        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
//        Message message = exchange.getIn();
//        //message.setBody("Hello!");
//
//
//        Producer producer = endpoint.createProducer();
//        producer.start();
//        producer.process(exchange);
//        producer.stop();

        // now lets sleep for a while
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not receive the message!", received);
        assertTrue("Did not receive session creation event!", sessionCreated.booleanValue());

    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from(String.format("mina2:tcp://localhost:%1$s?minaLogger=true&textline=true",
                                   getPort())).to("log:before?showAll=true").process(new Processor() {

                    public void process(Exchange e) {
                        Boolean prop = (Boolean) e.getIn().getHeader(
                            Mina2Constants.MINA2_SESSION_CREATED);
                        if (prop != null) {
                            sessionCreated = prop;
                            receivedExchange = e;
                            latch.countDown();
                        }
                        prop = (Boolean) e.getIn().getHeader(
                            Mina2Constants.MINA2_SESSION_OPENED);
                        // Received session open. Countdown the latch
                        if (prop != null) {
                            latch.countDown();
                            e.getOut().setHeader(Mina2Constants.MINA2_CLOSE_SESSION_WHEN_COMPLETE,
                                                 true);
                        }
                        prop = (Boolean) e.getIn().getHeader(
                            Mina2Constants.MINA2_SESSION_CLOSED);
                        // Received session closed. Countdown the latch
                        if (prop != null) {
                            latch.countDown();
                        }
                    }
                });
                uri = String.format("mina2:tcp://localhost:%1$s?textline=true", getPort());
                from("direct:x").to(uri);

            }
        };
    }
}
