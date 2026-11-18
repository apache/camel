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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server resets the connection after it replied, while the channel is idle in the producer pool and the exchange of
 * that reply is still routing: the exchange is not failed by that reset.
 */
class NettyProducerIdleChannelResetTest extends BaseNettyTest {

    private final CountDownLatch resetNow = new CountDownLatch(1);
    private ServerSocket server;
    private NettyProducer producer;

    @AfterEach
    void stopServer() throws Exception {
        resetNow.countDown();
        if (producer != null) {
            producer.stop();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testResetOfIdleChannelDoesNotFailTheExchange() throws Exception {
        server = new ServerSocket(getPort());
        Thread serverThread = new Thread(this::serveOneRequestThenReset, "reset-server");
        serverThread.setDaemon(true);
        serverThread.start();

        producer = (NettyProducer) context.getEndpoint(
                "netty:tcp://localhost:" + getPort() + "?textline=true&sync=true").createProducer();
        producer.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        String out = template.requestBody("direct:start", "Hello World", String.class);
        assertEquals("Bye World", out);
        mock.assertIsSatisfied();
    }

    private void serveOneRequestThenReset() {
        try (Socket socket = server.accept()) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            in.readLine();
            OutputStream out = socket.getOutputStream();
            out.write("Bye World\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            // reset the connection (RST) once the reply was received and the exchange routes on
            assertTrue(resetNow.await(20, TimeUnit.SECONDS));
            socket.setSoLinger(true, 0);
        } catch (Exception e) {
            // the test ended
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        // the call of .to("netty:...") with the producer of this test, which shows its channels
                        .process(exchange -> producer.process(exchange))
                        .process(exchange -> {
                            // the next step of the route runs while the server resets the channel of the reply,
                            // which is back in the producer pool
                            resetNow.countDown();
                            await().atMost(10, TimeUnit.SECONDS).until(() -> producer.getAllChannels().isEmpty());
                        })
                        .to("mock:result");
            }
        };
    }
}
