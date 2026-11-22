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
package org.apache.camel.component.mllp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A connection that the consumer accepted, and was still validating (its first read) when the route stopped, must not
 * be served after the stop, nor next to the connections of the next start.
 */
class MllpTcpServerConsumerStopTest extends CamelTestSupport {

    static final String MESSAGE = "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20160902123950|RISTECH|ADT^A08|00001|D|2.3|||||||\r";
    static final String ROUTE_ID = "mllp-stop";

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("mllp://localhost:%d?maxConcurrentConsumers=1&reuseAddress=true&receiveTimeout=2000&autoAck=true",
                        port.getPort())
                        .routeId(ROUTE_ID).to("mock:result");
            }
        };
    }

    @Test
    void testConnectionValidatedDuringStopIsNotServed() throws Exception {
        MllpTcpServerConsumer consumer = (MllpTcpServerConsumer) context.getRoute(ROUTE_ID).getConsumer();
        try (Socket client = connectAndStopDuringValidation()) {
            // the validation read (500 ms) ends after the stop: the connection is reset, not served
            await().atMost(10, TimeUnit.SECONDS).until(() -> threads("TcpServerConsumerValidationRunnable") == 0);
            assertEquals(0, threads("TcpSocketConsumerRunnable"));
            assertTrue(consumer.getConsumerRunnables().isEmpty());
            assertEquals("", sendAndReadAck(client), "the stopped route must not answer");
        }
    }

    @Test
    void testConnectionValidatedDuringStopIsNotServedAfterRestart() throws Exception {
        try (Socket client1 = connectAndStopDuringValidation()) {
            await().atMost(10, TimeUnit.SECONDS).until(() -> threads("TcpServerConsumerValidationRunnable") == 0);
            context.getRouteController().startRoute(ROUTE_ID);

            // a new connection is served after the restart, and the old one is not (maxConcurrentConsumers=1)
            try (Socket client2 = new Socket("localhost", port.getPort())) {
                assertTrue(sendAndReadAck(client2).contains("MSA|AA"));
                assertEquals("", sendAndReadAck(client1), "the connection of the previous start must not be served");
                assertEquals(1, threads("TcpSocketConsumerRunnable"));
            }
        }
    }

    @Test
    void testConnectionServedBeforeStopEndsWithinReceiveTimeout() throws Exception {
        try (Socket client = new Socket("localhost", port.getPort())) {
            assertTrue(sendAndReadAck(client).contains("MSA|AA"));
            assertEquals(1, threads("TcpSocketConsumerRunnable"));

            context.getRouteController().stopRoute(ROUTE_ID);

            await().atMost(10, TimeUnit.SECONDS).until(() -> threads("TcpSocketConsumerRunnable") == 0);
        }
    }

    private Socket connectAndStopDuringValidation() throws Exception {
        // connect without sending: the accept thread hands the socket to a validation runnable (500 ms read)
        Socket client = new Socket("localhost", port.getPort());
        await().atMost(5, TimeUnit.SECONDS).until(() -> threads("TcpServerConsumerValidationRunnable") == 1);
        context.getRouteController().stopRoute(ROUTE_ID);
        return client;
    }

    private long threads(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith(prefix)
                        && t.getName().contains(":" + port.getPort() + "]"))
                .count();
    }

    /**
     * Sends a message and returns the acknowledgement, or an empty string when the connection is closed or reset.
     */
    private static String sendAndReadAck(Socket socket) throws IOException {
        ByteArrayOutputStream ack = new ByteArrayOutputStream();
        try {
            OutputStream out = socket.getOutputStream();
            out.write(0x0b);
            out.write(MESSAGE.getBytes(StandardCharsets.ISO_8859_1));
            out.write(0x1c);
            out.write(0x0d);
            out.flush();
            socket.setSoTimeout(5000);
            InputStream in = socket.getInputStream();
            int b;
            while ((b = in.read()) != -1 && b != 0x1c) {
                if (b != 0x0b) {
                    ack.write(b);
                }
            }
        } catch (IOException e) {
            // reset by the consumer
            return "";
        }
        return ack.toString(StandardCharsets.ISO_8859_1);
    }
}
