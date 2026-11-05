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
package org.apache.camel.component.mina;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The reply a consumer with sync=true writes back: a textline reply that is not a String, the
 * CamelMinaCloseSessionWhenComplete header, disconnectOnNoReply, and a byte[] reply with the object codec.
 */
public class MinaConsumerReplyTest extends BaseMinaTest {

    private int textlinePort;
    private int noReplyPort;
    private int objectPort;

    @Test
    public void testTextlineBytesReply() {
        Object out = template.requestBody(String.format("mina:tcp://localhost:%d?textline=true", textlinePort), "bytes");
        assertEquals("Bye bytes", out);
    }

    @Test
    public void testTextlineStreamReply() {
        Object out = template.requestBody(String.format("mina:tcp://localhost:%d?textline=true", textlinePort), "stream");
        assertEquals("Bye stream", out);
    }

    @Test
    public void testCloseSessionWhenCompleteHeader() throws Exception {
        try (Socket socket = connect(textlinePort)) {
            BufferedReader reader = send(socket, "close");
            assertEquals("Bye close", reader.readLine());
            // the header set by the route closes the session
            assertNull(reader.readLine());
        }
    }

    @Test
    public void testSessionStaysOpenWithoutCloseHeader() throws Exception {
        try (Socket socket = connect(textlinePort)) {
            BufferedReader reader = send(socket, "bytes");
            assertEquals("Bye bytes", reader.readLine());
            assertThrows(SocketTimeoutException.class, reader::readLine);
        }
    }

    @Test
    public void testDisconnectOnNoReplyFalse() throws Exception {
        try (Socket socket = connect(noReplyPort)) {
            // no reply, and the session stays open
            BufferedReader reader = send(socket, "London");
            assertThrows(SocketTimeoutException.class, reader::readLine);

            // so the next request on the same session gets its reply
            reader = send(socket, "Woodbine");
            assertEquals("Hello Chad", reader.readLine());
        }
    }

    @Test
    public void testObjectCodecBytesReplyWithObjectCodecPattern() {
        // the object codec only accepts the classes of objectCodecPattern, so a byte[] reply needs [B
        Object out = template.requestBody(String.format("mina:tcp://localhost:%d?objectCodecPattern=[B", objectPort),
                "Hello");
        assertArrayEquals("Bye Hello".getBytes(StandardCharsets.UTF_8), (byte[]) out);
    }

    private static Socket connect(int port) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("localhost", port));
        socket.setSoTimeout(1000);
        return socket;
    }

    private static BufferedReader send(Socket socket, String line) throws Exception {
        OutputStream os = socket.getOutputStream();
        os.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
        os.flush();
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                textlinePort = getPort();
                noReplyPort = getNextPort();
                objectPort = getNextPort();

                fromF("mina:tcp://localhost:%d?textline=true&encoding=US-ASCII", textlinePort)
                        .choice()
                        .when(body().isEqualTo("stream"))
                        .process(e -> e.getMessage().setBody(
                                new ByteArrayInputStream("Bye stream".getBytes(StandardCharsets.US_ASCII))))
                        .when(body().isEqualTo("close"))
                        .setBody(constant("Bye close"))
                        .setHeader(MinaConstants.MINA_CLOSE_SESSION_WHEN_COMPLETE, constant(true))
                        .otherwise()
                        .process(e -> e.getMessage().setBody(
                                ("Bye " + e.getMessage().getBody(String.class)).getBytes(StandardCharsets.US_ASCII)));

                fromF("mina:tcp://localhost:%d?textline=true&encoding=US-ASCII&disconnectOnNoReply=false&noReplyLogLevel=OFF",
                        noReplyPort)
                        .choice()
                        .when(body().isEqualTo("Woodbine"))
                        .transform(constant("Hello Chad"))
                        .otherwise()
                        .transform(constant((Object) null));

                fromF("mina:tcp://localhost:%d", objectPort)
                        .process(e -> e.getMessage().setBody(
                                ("Bye " + e.getMessage().getBody(String.class)).getBytes(StandardCharsets.UTF_8)));
            }
        };
    }
}
