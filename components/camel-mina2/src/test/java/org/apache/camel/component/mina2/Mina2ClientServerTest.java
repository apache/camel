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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.junit.Test;

public class Mina2ClientServerTest extends BaseMina2Test {

    private CountDownLatch latch = null;
    private CloseIoHandler closeIoHandler = new CloseIoHandler();
    private NoCloseIoHandler noCloseIoHandler = new NoCloseIoHandler();

    @Test
    public void testSendToServer() throws InterruptedException {
        // START SNIPPET: e3
        String out = (String) template.requestBody(String.format("mina2:tcp://localhost:%1$s?textline=true", getPort()), "Chad");
        assertEquals("Hello Chad", out);
        // END SNIPPET: e3
    }

    @Test
    public void testSendOneCloseToServer() throws InterruptedException {
        latch = new CountDownLatch(1);
        closeIoHandler.setLatch(latch);
        template.sendBody(String.format("mina2:tcp://localhost:%1$s?textline=true&sync=false&ioHandler=#closeIoHandler", getPort()), "Chad");
        latch.await(2, TimeUnit.SECONDS);
        assertEquals("Hello Chad", closeIoHandler.getMessage());
    }

    @Test
    public void testSendTwoCloseToServer() throws InterruptedException {
        latch = new CountDownLatch(1);
        closeIoHandler.setLatch(latch);
        template.sendBody(String.format("mina2:tcp://localhost:%1$s?textline=true&sync=false&ioHandler=#closeIoHandler", getPort()), "Chad");
        latch.await(2, TimeUnit.SECONDS);
        assertEquals("Hello Chad", closeIoHandler.getMessage());
        latch = new CountDownLatch(1);
        closeIoHandler.setLatch(latch);
        template.sendBody(String.format("mina2:tcp://localhost:%1$s?textline=true&sync=false&ioHandler=#closeIoHandler", getPort()), "Alexander");
        latch.await(2, TimeUnit.SECONDS);
        assertEquals("Hello Alexander", closeIoHandler.getMessage());
    }

    @Test
    public void testSendTwoNoCloseToServer() throws InterruptedException {
        latch = new CountDownLatch(1);
        noCloseIoHandler.setLatch(latch);
        template.sendBody(String.format("mina2:tcp://localhost:%1$s?textline=true&sync=false&ioHandler=#noCloseIoHandler", getPort()), "Chad");
        latch.await(2, TimeUnit.SECONDS);
        assertEquals("Hello Chad", noCloseIoHandler.getMessage());
        latch = new CountDownLatch(1);
        noCloseIoHandler.setLatch(latch);
        template.sendBody(String.format("mina2:tcp://localhost:%1$s?textline=true&sync=false&ioHandler=#noCloseIoHandler", getPort()), "Alexander");
        latch.await(2, TimeUnit.SECONDS);
        assertEquals("Hello Alexander", noCloseIoHandler.getMessage());
    }

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        jndi.bind("closeIoHandler", closeIoHandler);
        jndi.bind("noCloseIoHandler", noCloseIoHandler);
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                // lets setup a server on port %1$s
                // and we let the request-reply be processed in the MyServerProcessor
                from(String.format("mina2:tcp://localhost:%1$s?textline=true", getPort())).process(new MyServerProcessor());
            }
        };
    }

    private static class MyServerProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            // get the input from the IN body
            String name = exchange.getIn().getBody(String.class);
            // Ignore sessionCreated and sessionOpened events with null body
            if (name != null) {
                // send back a response on the OUT body
                exchange.getOut().setBody("Hello " + name);
            }
        }
    }

    /**
     * Handles response from session writes
     */
    private class CloseIoHandler extends IoHandlerAdapter {

        protected Object message;
        protected Throwable cause;
        protected boolean messageReceived;
        protected CountDownLatch latch;

        public CloseIoHandler() {
        }

        public void setLatch(CountDownLatch cdl) {
            latch = cdl;
        }

        @Override
        public void messageReceived(IoSession ioSession, Object message) throws Exception {
            CloseFuture closeFuture = ioSession.close(true);
            closeFuture.awaitUninterruptibly();
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
            log.debug("CloseIoHandler Session closed");
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

    private class NoCloseIoHandler extends CloseIoHandler {

        @Override
        public void messageReceived(IoSession ioSession, Object message) throws Exception {
            this.message = message;
            messageReceived = true;
            cause = null;
            countDown();
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
            log.debug("NoCloseIoHandler Session closed");
        }
    }
}