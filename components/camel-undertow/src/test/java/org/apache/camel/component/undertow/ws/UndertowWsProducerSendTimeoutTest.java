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
package org.apache.camel.component.undertow.ws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.UndertowConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code sendTimeout} of a WebSocket producer applies to every type of message: a message to a peer that stops
 * reading fails the exchange, and the connection to that peer is closed, instead of keeping the exchange and the
 * message in flight forever.
 */
public class UndertowWsProducerSendTimeoutTest extends BaseUndertowTest {

    /**
     * Much more than the socket buffers of the server and of the stalled peer can hold, so the write cannot complete.
     */
    private static final int MESSAGE_SIZE = 64 * 1024 * 1024;

    private static final String PROBE = "probe";

    @Produce("direct:send")
    private ProducerTemplate producer;

    static Stream<Arguments> messages() {
        String text = "x".repeat(MESSAGE_SIZE);
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        return Stream.of(
                Arguments.of("String", text),
                Arguments.of("Reader", new StringReader(text)),
                Arguments.of("byte[]", bytes),
                Arguments.of("InputStream", new ByteArrayInputStream(bytes)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    public void sendToStalledPeerTimesOut(String type, Object body) throws Exception {
        try (Socket peer = connectStalledPeer()) {
            CompletableFuture<Exchange> future = producer.asyncSend("direct:send", exchange -> {
                exchange.getIn().setBody(body);
                exchange.getIn().setHeader(UndertowConstants.SEND_TO_ALL, true);
            });

            // the send timeout is 1 second; without it the exchange would never complete
            Exchange exchange = future.get(30, TimeUnit.SECONDS);

            CamelExchangeException failure = assertInstanceOf(CamelExchangeException.class, exchange.getException(),
                    "A " + type + " message that is not sent within the send timeout should fail the exchange");
            assertInstanceOf(TimeoutException.class, failure.getCause(), failure.getMessage());

            assertTrue(isClosedByServer(peer), "The connection to the stalled peer should be closed");
        }
    }

    /**
     * Reads what the server wrote before it closed the connection.
     */
    private static boolean isClosedByServer(Socket peer) throws IOException {
        peer.setSoTimeout(20000);
        InputStream in = peer.getInputStream();
        byte[] buffer = new byte[64 * 1024];
        try {
            while (in.read(buffer) >= 0) {
                // drain
            }
            return true;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (SocketException e) {
            // reset by the server
            return true;
        }
    }

    /**
     * Opens a WebSocket connection to the producer path and then never reads from it, as a stalled peer does.
     */
    private Socket connectStalledPeer() throws IOException {
        Socket socket = new Socket();
        // a small window, so that the server soon has to wait for the peer to read
        socket.setReceiveBufferSize(4096);
        socket.connect(new InetSocketAddress("localhost", getPort()), 10000);
        socket.setSoTimeout(10000);

        String key = Base64.getEncoder().encodeToString("camel-27569-test".getBytes(StandardCharsets.US_ASCII));
        String request = "GET /slow HTTP/1.1\r\n"
                         + "Host: localhost:" + getPort() + "\r\n"
                         + "Upgrade: websocket\r\n"
                         + "Connection: Upgrade\r\n"
                         + "Sec-WebSocket-Key: " + key + "\r\n"
                         + "Sec-WebSocket-Version: 13\r\n"
                         + "\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        out.flush();

        // read the handshake response, and nothing after it
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        while (!response.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("Connection closed during the handshake: " + response);
            }
            response.write(b);
        }
        String status = response.toString(StandardCharsets.US_ASCII).lines().findFirst().orElse("");
        assertEquals("HTTP/1.1 101 Switching Protocols", status);

        // Undertow registers the peer only after it has sent the handshake response, so send small probes until one
        // arrives; from then on the peer reads nothing more
        socket.setSoTimeout(200);
        await().atMost(20, TimeUnit.SECONDS).until(() -> {
            producer.sendBodyAndHeader("direct:send", PROBE, UndertowConstants.SEND_TO_ALL, true);
            return readProbe(in);
        });
        return socket;
    }

    private static boolean readProbe(InputStream in) throws IOException {
        // an unmasked text frame whose payload is shorter than 126 bytes: FIN + opcode, length, payload
        byte[] expected = new byte[2 + PROBE.length()];
        expected[0] = (byte) 0x81;
        expected[1] = (byte) PROBE.length();
        System.arraycopy(PROBE.getBytes(StandardCharsets.US_ASCII), 0, expected, 2, PROBE.length());
        try {
            assertArrayEquals(expected, in.readNBytes(expected.length));
            return true;
        } catch (SocketTimeoutException e) {
            return false;
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:send")
                        .to("undertow:ws://localhost:" + getPort() + "/slow?sendTimeout=1000");
            }
        };
    }
}
