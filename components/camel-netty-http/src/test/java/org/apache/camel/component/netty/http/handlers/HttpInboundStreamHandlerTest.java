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
package org.apache.camel.component.netty.http.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.camel.component.netty.http.InboundStreamHttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stream of the body of a chunked request never makes the I/O thread wait: it stops reading from the connection
 * while too much of the body waits to be read, discards the rest of the body once closed, and fails rather than waits
 * when it is read on the I/O thread (an embedded channel runs everything on the I/O thread).
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class HttpInboundStreamHandlerTest {

    private final EmbeddedChannel channel = new EmbeddedChannel(new HttpInboundStreamHandler());

    @AfterEach
    void close() {
        channel.finishAndReleaseAll();
    }

    @Test
    void testBodyIsStreamed() throws Exception {
        InputStream body = sendRequest();
        channel.writeInbound(content("Hello "));
        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("World", StandardCharsets.US_ASCII)));

        assertEquals("Hello World", new String(body.readAllBytes(), StandardCharsets.US_ASCII));
    }

    @Test
    void testStopsReadingWhileTheBodyWaitsToBeRead() throws Exception {
        InputStream body = sendRequest();
        byte[] chunk = new byte[1000];
        int chunks = HttpInboundStreamHandler.HIGH_WATER_MARK / chunk.length + 1;
        for (int i = 0; i < chunks; i++) {
            channel.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(chunk)));
        }
        assertFalse(channel.config().isAutoRead(), "the connection is not read while the body waits to be read");

        byte[] read = body.readNBytes(chunks * chunk.length - HttpInboundStreamHandler.LOW_WATER_MARK);
        assertArrayEquals(new byte[read.length], read);
        assertTrue(channel.config().isAutoRead(), "the connection is read again once the body is read");
    }

    @Test
    void testCloseDiscardsTheRestOfTheBody() throws Exception {
        InputStream body = sendRequest();
        byte[] chunk = new byte[1000];
        for (int i = 0; i <= HttpInboundStreamHandler.HIGH_WATER_MARK / chunk.length; i++) {
            channel.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(chunk)));
        }
        assertFalse(channel.config().isAutoRead());

        body.close();
        assertTrue(channel.config().isAutoRead(), "the connection is read again once the body is closed");
        channel.writeInbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(chunk)));
        assertThrows(IOException.class, body::read);

        // the next request on the connection has a body of its own
        InputStream next = sendRequest();
        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("next", StandardCharsets.US_ASCII)));
        assertEquals("next", new String(next.readAllBytes(), StandardCharsets.US_ASCII));
    }

    @Test
    void testReadOnTheIoThreadFailsInsteadOfWaiting() throws Exception {
        InputStream body = sendRequest();
        channel.writeInbound(content("Hello"));

        assertEquals("Hello", new String(body.readNBytes(5), StandardCharsets.US_ASCII));
        assertThrows(IOException.class, body::read);
    }

    @Test
    void testConnectionClosedBeforeTheEndOfTheBody() throws Exception {
        InputStream body = sendRequest();
        channel.writeInbound(content("Hello"));
        channel.close();

        assertEquals("Hello", new String(body.readNBytes(5), StandardCharsets.US_ASCII));
        assertThrows(IOException.class, body::read);
    }

    private InputStream sendRequest() {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/foo");
        request.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        channel.writeInbound(request);
        InboundStreamHttpRequest received = channel.readInbound();
        return received.getInputStream();
    }

    private static DefaultHttpContent content(String text) {
        return new DefaultHttpContent(Unpooled.copiedBuffer(text, StandardCharsets.US_ASCII));
    }
}
