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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The consumer used to write the route's exception back to the remote peer as the reply: over the textline codec its
 * class name and message, and over an encoder that accepts any object its serialised form.
 * <p>
 * {@code muteException} defaults to true, as in camel-mina and the http consumers aligned by CAMEL-23651.
 */
class NettyMuteExceptionTest extends BaseNettyTest {

    private static final String DETAIL = "the-internal-detail-a-peer-must-not-see";
    private static final String MUTED_REPLY = "java.lang.Exception: Exchange processing failed";

    @RegisterExtension
    AvailablePortFinder.Port unmutedPort = AvailablePortFinder.find();

    @RegisterExtension
    AvailablePortFinder.Port udpPort = AvailablePortFinder.find();

    @RegisterExtension
    AvailablePortFinder.Port objectPort = AvailablePortFinder.find();

    @BindToRegistry("line-decoder")
    private final ChannelHandlerFactory lineDecoder
            = ChannelHandlerFactories.newDelimiterBasedFrameDecoder(1024, Delimiters.lineDelimiter(), "tcp");

    @BindToRegistry("string-decoder")
    private final ChannelHandlerFactory stringDecoder
            = ChannelHandlerFactories.newStringDecoder(StandardCharsets.UTF_8, "tcp");

    @BindToRegistry("capturing-encoder")
    private final CapturingEncoder capturingEncoder = new CapturingEncoder();

    @Test
    void aFailedExchangeDoesNotSendTheExceptionByDefault() throws IOException {
        assertEquals(MUTED_REPLY, sendLineOverTcp(getPort()),
                "the reply must carry neither the class nor the message of the route's exception");
    }

    @Test
    void aFailedExchangeDoesNotSendTheExceptionByDefaultOverUdp() throws IOException {
        assertEquals(MUTED_REPLY, sendLineOverUdp(udpPort.getPort()),
                "the reply must carry neither the class nor the message of the route's exception");
    }

    @Test
    void muteExceptionFalseSendsTheExceptionAsBefore() throws IOException {
        assertEquals("java.lang.IllegalStateException: " + DETAIL, sendLineOverTcp(unmutedPort.getPort()));
    }

    @Test
    void theExceptionHandedToAnObjectEncoderIsAPlainExceptionWithoutStackTraceOrCause() throws IOException {
        // an encoder that accepts any object serialises the stand-in itself, not only its toString()
        assertEquals(MUTED_REPLY, sendLineOverTcp(objectPort.getPort()));

        Object written = capturingEncoder.written.get();
        assertNotNull(written, "the consumer must have written a reply");
        assertEquals(Exception.class, written.getClass());
        Exception muted = (Exception) written;
        assertEquals("Exchange processing failed", muted.getMessage());
        assertNull(muted.getCause());
        assertEquals(0, muted.getStackTrace().length);
    }

    private static String sendLineOverTcp(int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 5000);
            socket.setSoTimeout(5000);
            socket.getOutputStream().write("hello\n".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();

            InputStream in = socket.getInputStream();
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int b = in.read();
            while (b != -1 && b != '\n') {
                line.write(b);
                b = in.read();
            }
            return line.toString(StandardCharsets.UTF_8);
        }
    }

    private static String sendLineOverUdp(int port) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);
            byte[] request = "hello\n".getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(request, request.length, InetAddress.getByName("127.0.0.1"), port));

            byte[] buffer = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            return new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8).trim();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?textline=true&sync=true")
                        .throwException(new IllegalStateException(DETAIL));

                fromF("netty:tcp://localhost:%s?textline=true&sync=true&muteException=false", unmutedPort.getPort())
                        .throwException(new IllegalStateException(DETAIL));

                fromF("netty:udp://127.0.0.1:%s?textline=true&sync=true", udpPort.getPort())
                        .throwException(new IllegalStateException(DETAIL));

                fromF("netty:tcp://localhost:%s?sync=true&encoders=#capturing-encoder&decoders=#line-decoder,#string-decoder",
                        objectPort.getPort())
                        .throwException(new IllegalStateException(DETAIL));
            }
        };
    }

    /**
     * Stands in for an object encoder: records the object the consumer writes, and sends its text as a line.
     */
    @ChannelHandler.Sharable
    static final class CapturingEncoder extends MessageToMessageEncoder<Object> {

        final AtomicReference<Object> written = new AtomicReference<>();

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) {
            written.set(msg);
            out.add(ByteBufUtil.encodeString(ctx.alloc(), CharBuffer.wrap(msg + "\n"), StandardCharsets.UTF_8));
        }
    }
}
