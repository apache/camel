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
package org.apache.camel.component.stomp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.fusesource.stomp.client.BlockingConnection;
import org.fusesource.stomp.client.Stomp;
import org.fusesource.stomp.codec.StompFrame;
import org.junit.Test;

import static org.fusesource.stomp.client.Constants.DESTINATION;
import static org.fusesource.stomp.client.Constants.ID;
import static org.fusesource.stomp.client.Constants.SUBSCRIBE;

public class StompProducerTest extends StompBaseTest {

    @Test
    public void testProduce() throws Exception {
        if (!canTest()) {
            return;
        }

        context.addRoutes(createRouteBuilder());
        context.start();

        Stomp stomp = new Stomp("tcp://localhost:" + getPort());
        final BlockingConnection subscribeConnection = stomp.connectBlocking();

        StompFrame frame = new StompFrame(SUBSCRIBE);
        frame.addHeader(DESTINATION, StompFrame.encodeHeader("/queue/test"));
        frame.addHeader(ID, subscribeConnection.nextId());
        StompFrame response = subscribeConnection.request(frame);

        final CountDownLatch latch = new CountDownLatch(numberOfMessages);

        Thread thread = new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < numberOfMessages; i++) {
                    try {
                        StompFrame frame = subscribeConnection.receive();
                        frame.contentAsString().startsWith("test message ");
                        latch.countDown();
                    } catch (Exception e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }
        });
        thread.start();

        Producer producer = context.getEndpoint("direct:foo").createProducer();
        for (int i = 0; i < numberOfMessages; i++) {
            Exchange exchange = producer.createExchange();
            exchange.getIn().setBody(("test message " + i).getBytes("UTF-8"));
            producer.process(exchange);
        }
        latch.await(20, TimeUnit.SECONDS);

        assertTrue("Messages not consumed = " + latch.getCount(), latch.getCount() == 0);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:foo").to("stomp:queue:test");
            }
        };
    }

}
