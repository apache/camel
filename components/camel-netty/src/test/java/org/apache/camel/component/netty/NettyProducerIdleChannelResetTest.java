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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The server resets the connection after the call, while the channel is idle in the producer pool and the exchange of
 * that call is still routing (on another thread): the exchange is not failed by that reset.
 */
class NettyProducerIdleChannelResetTest extends BaseNettyTest {

    private final CountDownLatch resetNow = new CountDownLatch(1);
    private ServerSocket server;

    @AfterEach
    void stopServer() throws Exception {
        resetNow.countDown();
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testResetOfIdleChannelDoesNotFailTheExchange() throws Exception {
        startServer(true);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        String out = template.requestBody("direct:sync", "Hello World", String.class);
        assertEquals("Bye World", out);
        mock.assertIsSatisfied();
    }

    @Test
    void testResetOfIdleChannelDoesNotFailTheExchangeWhenNotSync() throws Exception {
        startServer(false);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Exchange out = template.send("direct:async", e -> e.getIn().setBody("Hello World"));
        assertNull(out.getException());
        mock.assertIsSatisfied();
    }

    private void startServer(boolean reply) throws Exception {
        server = new ServerSocket(getPort());
        Thread serverThread = new Thread(() -> serveOneRequestThenReset(reply), "reset-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void serveOneRequestThenReset(boolean reply) {
        try (Socket socket = server.accept()) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            in.readLine();
            if (reply) {
                OutputStream out = socket.getOutputStream();
                out.write("Bye World\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            // reset the connection (RST) once the call is done and the exchange routes on
            if (!resetNow.await(20, TimeUnit.SECONDS)) {
                return;
            }
            socket.setSoLinger(true, 0);
        } catch (Exception e) {
            // the test ended
        }
    }

    private void resetAndWaitUntilChannelClosed(Exchange exchange) {
        // the next step of the route runs (on another thread than the event loop of the channel) while the server
        // resets the channel of the call, which is back in the producer pool
        resetNow.countDown();
        // until the producer closed the channel, or the reset failed this exchange
        await().atMost(10, TimeUnit.SECONDS).until(
                () -> exchange.getException() != null
                        || context.hasServices(NettyProducer.class).stream().allMatch(p -> p.getAllChannels().isEmpty()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sync")
                        .to("netty:tcp://localhost:{{port}}?textline=true&sync=true")
                        .threads(1)
                        .process(exchange -> resetAndWaitUntilChannelClosed(exchange))
                        .to("mock:result");

                from("direct:async")
                        .to("netty:tcp://localhost:{{port}}?textline=true&sync=false")
                        .threads(1)
                        .process(exchange -> resetAndWaitUntilChannelClosed(exchange))
                        .to("mock:result");
            }
        };
    }
}
