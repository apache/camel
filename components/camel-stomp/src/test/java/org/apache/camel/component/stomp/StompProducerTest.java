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
package org.apache.camel.component.stomp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.fusesource.hawtbuf.AsciiBuffer;
import org.fusesource.stomp.client.BlockingConnection;
import org.fusesource.stomp.client.Stomp;
import org.fusesource.stomp.codec.StompFrame;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.fusesource.stomp.client.Constants.DESTINATION;
import static org.fusesource.stomp.client.Constants.ID;
import static org.fusesource.stomp.client.Constants.SUBSCRIBE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StompProducerTest extends StompBaseTest {
    private static final Logger LOG = LoggerFactory.getLogger(StompProducerTest.class);

    private static final String HEADER = "testheader1";
    private static final String HEADER_VALUE = "testheader1";

    @Test
    public void testProduce() throws Exception {
        context.addRoutes(createRouteBuilder());
        context.start();

        Stomp stomp = createStompClient();
        final BlockingConnection subscribeConnection = stomp.connectBlocking();

        StompFrame frame = new StompFrame(SUBSCRIBE);
        frame.addHeader(DESTINATION, StompFrame.encodeHeader("test"));
        frame.addHeader(ID, subscribeConnection.nextId());
        subscribeConnection.request(frame);

        final CountDownLatch latch = new CountDownLatch(numberOfMessages);

        Thread thread = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < numberOfMessages; i++) {
                    try {
                        StompFrame frame = subscribeConnection.receive();
                        assertTrue(frame.contentAsString().startsWith("test message "));
                        assertTrue(frame.getHeader(new AsciiBuffer(HEADER)).ascii().toString().startsWith(HEADER_VALUE));
                        latch.countDown();
                    } catch (Exception e) {
                        LOG.warn("Unhandled exception receiving STOMP data: {}", e.getMessage(), e);
                        break;
                    }
                }
            }
        });
        thread.start();

        Endpoint endpoint = context.getEndpoint("direct:foo");
        Producer producer = endpoint.createProducer();
        for (int i = 0; i < numberOfMessages; i++) {
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setBody(("test message " + i).getBytes("UTF-8"));
            exchange.getIn().setHeader(HEADER, HEADER_VALUE);
            producer.process(exchange);
        }
        latch.await(20, TimeUnit.SECONDS);

        assertEquals(0, latch.getCount(), "Messages not consumed = " + latch.getCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:foo").toF("stomp:test?brokerURL=tcp://localhost:%s", servicePort);
            }
        };
    }

}
