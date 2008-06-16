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
package org.apache.camel.component.mina;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version $Revision$
 */
public class MinaTcpWithInOutTest extends TestCase {

    private String uri;
    private Exchange receivedExchange;
    private CountDownLatch latch;
    private CamelContext container;

    public void testMinaRouteWithInOut() throws Exception {
        container = new DefaultCamelContext();
        latch = new CountDownLatch(1);
        uri = "mina:tcp://localhost:6321?textline=true";

        ReverserServer server = new ReverserServer();
        server.start();

        container.addRoutes(createRouteBuilder());
        container.start();

        // now lets fire in a message
        Endpoint endpoint = container.getEndpoint("direct:x");
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
        Message message = exchange.getIn();
        message.setBody("Hello!");
        message.setHeader("cheese", 123);

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);

        // now lets sleep for a while
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not receive the message!", received);
        assertNotNull(receivedExchange.getIn());
        assertEquals("!olleH", receivedExchange.getIn().getBody());

        producer.stop();
        container.stop();
        server.stop();
    }

    public void testMinaRouteWithInOutLazy() throws Exception {
        container = new DefaultCamelContext();
        latch = new CountDownLatch(1);
        uri = "mina:tcp://localhost:6321?textline=true&lazySessionCreation=true";

        container.addRoutes(createRouteBuilder());
        container.start();

        // The server is activated after Camel to check if the lazyness is working
        ReverserServer server = new ReverserServer();
        server.start();

        // now lets fire in a message
        Endpoint endpoint = container.getEndpoint("direct:x");
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
        Message message = exchange.getIn();
        message.setBody("Hello!");
        message.setHeader("cheese", 123);

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);

        // now lets sleep for a while
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not receive the message!", received);
        assertNotNull(receivedExchange.getIn());
        assertEquals("!olleH", receivedExchange.getIn().getBody());

        producer.stop();
        container.stop();
        server.stop();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:x").to(uri).process(new Processor() {
                    public void process(Exchange e) {
                        receivedExchange = e;
                        latch.countDown();
                    }
                });
            }
        };
    }

}
