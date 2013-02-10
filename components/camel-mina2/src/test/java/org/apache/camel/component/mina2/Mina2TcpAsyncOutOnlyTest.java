/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
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
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.junit.Before;
import org.junit.Test;

/**
 * @version
 */
public class Mina2TcpAsyncOutOnlyTest extends BaseMina2Test {

    private String uri;
    private Exchange receivedExchange;
    private CountDownLatch latch;
    private Boolean sessionCreated = Boolean.FALSE;
    private int port2 = getNextPort();

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

        // now lets sleep for a while
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not receive the message!", received);
        assertTrue("Did not receive session creation event!", sessionCreated.booleanValue());
    }

    @Test
    public void testMina2ProducerWithIoHandler() throws Exception {
        // Get the Mina2 endpoint for this test.
        Mina2Endpoint mina2Endpoint = (Mina2Endpoint) context.getEndpoint(String.format(
                "mina2:tcp://localhost:%1$s?minaLogger=true&sync=false&textline=true", port2));
        // Create a CountDownLatch with a counter of 300
        latch = new CountDownLatch(300);
        // Create an IoHandler to configure for the Mina2Producer to use.
        MyIoHandler myIoHandler = new MyIoHandler(latch);
        mina2Endpoint.getConfiguration().setIoHandler(myIoHandler);

        Exchange exchange = mina2Endpoint.createExchange(ExchangePattern.InOnly);
        Message message = exchange.getIn();
        message.setBody("Hello!");
        // Create the producer
        Producer producer = mina2Endpoint.createProducer();
        producer.start();
        // Process the exchenage.
        producer.process(exchange);
        // Now lets sleep for awhile waiting to receive 300 messages. 
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not receive the messages!", received);
        assertTrue("Did not receive session creation event!", sessionCreated.booleanValue());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Route with processor to test session creation
                from(String.format("mina2:tcp://localhost:%1$s?minaLogger=true&sync=false&textline=true",
                        getPort())).to("log:before?showAll=true").process(new Processor() {
                    public void process(Exchange e) {
                        Boolean prop = (Boolean) e.getProperty(
                                Mina2Constants.MINA2_SESSION_CREATED);
                        if (prop != null) {
                            sessionCreated = prop;
                            receivedExchange = e;
                            latch.countDown();
                        }
                        prop = (Boolean) e.getProperty(
                                Mina2Constants.MINA2_SESSION_OPENED);
                        // Received session open. Countdown the latch
                        if (prop != null) {
                            latch.countDown();
                        }
                        prop = (Boolean) e.getProperty(
                                Mina2Constants.MINA2_SESSION_CLOSED);
                        // Received session closed. Countdown the latch
                        if (prop != null) {
                            latch.countDown();
                        }
                    }
                });
                // Route with processor to test sending asynchronous messages after session creation
                from(String.format("mina2:tcp://localhost:%1$s?minaLogger=true&sync=false&textline=true",
                        port2)).to("log:before?showAll=true").process(new Processor() {
                    public void process(Exchange e) {
                        log.debug("Inside process...");
                        Boolean prop = (Boolean) e.getProperty(
                                Mina2Constants.MINA2_SESSION_CREATED);
                        if (prop != null) {
                            log.debug("process - session created");
                            sessionCreated = prop;
                            receivedExchange = e;
                        }
                        prop = (Boolean) e.getProperty(
                                Mina2Constants.MINA2_SESSION_OPENED);
                        // Received session open. Countdown the latch
                        if (prop != null) {
                            log.debug("process - session opened");
                            // The IoSession has been created. Send 300 messages back to the Producer.
                            IoSession session = (IoSession) e.getIn().getHeader(
                                    Mina2Constants.MINA2_IOSESSION);
                            for (int i = 0; i < 300; i++) {
                                String msg = "message " + i;
                                session.write(msg);

                            }
                        }
                    }
                });

                // Direct route to used to hit a Mina2 consumer 
                uri = String.format("mina2:tcp://localhost:%1$s?textline=true&sync=false&textline=true&disconnect=true", getPort());
                from("direct:x").to(uri);

            }
        };
    }

    /**
     * Handles response from session writes
     */
    private final class MyIoHandler extends IoHandlerAdapter {

        private Object message;
        private Throwable cause;
        private boolean messageReceived;
        private CountDownLatch latch;

        public MyIoHandler(CountDownLatch arg) {
            latch = arg;
        }

        @Override
        public void messageReceived(IoSession ioSession, Object message) throws Exception {
            this.message = message;
            messageReceived = true;
            cause = null;
            countDown();
        }

        protected void countDown() {
            CountDownLatch downLatch = latch;
            if (downLatch != null) {
                downLatch.countDown();
            }
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
            log.debug("MyIoHandler Session closed");
        }

        @Override
        public void exceptionCaught(IoSession ioSession, Throwable cause) {
            log.error("Exception on receiving message from address: " + ioSession.getLocalAddress(),
                    cause);
            this.message = null;
            this.messageReceived = false;
            this.cause = cause;
            if (ioSession != null) {
                ioSession.close(true);
            }
        }

        public Throwable getCause() {
            return this.cause;
        }

        public Object getMessage() {
            return this.message;
        }

        public boolean isMessageReceived() {
            return messageReceived;
        }
    }
}
