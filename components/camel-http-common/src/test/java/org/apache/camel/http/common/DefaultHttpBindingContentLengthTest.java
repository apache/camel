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
package org.apache.camel.http.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A non-chunked response is copied into a {@link CachedOutputStream} to learn its length, which must be set as a long:
 * the int byte count of the copy cannot hold the length of a response of 2 GiB or more.
 */
class DefaultHttpBindingContentLengthTest {

    private static final long THREE_GIGABYTES = 3L * 1024 * 1024 * 1024;

    @TempDir
    Path spoolDirectory;

    private DefaultCamelContext context;
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private Exchange exchange(boolean spool, byte[] body) {
        context = new DefaultCamelContext();
        if (spool) {
            StreamCachingStrategy strategy = context.getStreamCachingStrategy();
            strategy.setSpoolEnabled(true);
            strategy.setSpoolThreshold(1);
            strategy.setSpoolDirectory(spoolDirectory.toFile());
        }
        context.start();

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/octet-stream");
        exchange.getMessage().setHeader(Exchange.HTTP_CHUNKED, false);
        return exchange;
    }

    private HttpServletResponse response() throws IOException {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getBufferSize()).thenReturn(8192);
        when(response.getOutputStream()).thenReturn(new CapturingServletOutputStream(written));
        return response;
    }

    private static byte[] body() {
        return "a response body that is not chunked".getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The length of a response of 3 GiB, without writing 3 GiB anywhere: the {@link CachedOutputStream} the body is
     * spooled into reports that length, and only a few bytes are actually sent.
     */
    @Test
    void aResponseOfTwoGigabytesOrMoreGetsItsLengthAsALong() throws Exception {
        byte[] body = body();
        Exchange exchange = exchange(true, body);
        HttpServletResponse response = response();

        SpooledCache spooled = new SpooledCache(body, THREE_GIGABYTES);

        try (MockedConstruction<CachedOutputStream> spool = mockConstruction(CachedOutputStream.class, (stream, ctx) -> {
            // a spooled cache: the current stream is a file, not a byte array, and the body is read back from the cache
            when(stream.getCurrentStream()).thenReturn(OutputStream.nullOutputStream());
            when(stream.newStreamCache()).thenReturn(spooled);
            when(stream.getInputStream()).thenReturn(spooled);
        })) {
            new DefaultHttpBinding().doWriteDirectResponse(exchange.getMessage(), response, exchange);
        }

        verify(response).setContentLengthLong(THREE_GIGABYTES);
        verify(response, never()).setContentLength(anyInt());
        assertArrayEquals(body, written.toByteArray());
    }

    @Test
    void aSpooledResponseGetsItsLength() throws Exception {
        byte[] body = body();
        Exchange exchange = exchange(true, body);
        HttpServletResponse response = response();

        new DefaultHttpBinding().doWriteDirectResponse(exchange.getMessage(), response, exchange);

        try (Stream<Path> files = Files.walk(spoolDirectory)) {
            assertTrue(files.anyMatch(Files::isRegularFile), "the body should have been spooled to a file");
        }
        verify(response).setContentLengthLong(body.length);
        verify(response, never()).setContentLength(anyInt());
        assertArrayEquals(body, written.toByteArray());
    }

    @Test
    void anInMemoryResponseGetsItsLength() throws Exception {
        byte[] body = body();
        Exchange exchange = exchange(false, body);
        HttpServletResponse response = response();

        new DefaultHttpBinding().doWriteDirectResponse(exchange.getMessage(), response, exchange);

        verify(response).setContentLengthLong(body.length);
        verify(response, never()).setContentLength(anyInt());
        assertArrayEquals(body, written.toByteArray());
    }

    /**
     * The cache of a spooled body that reports a length larger than the bytes it holds.
     */
    private static final class SpooledCache extends ByteArrayInputStream implements StreamCache {

        private final long length;

        private SpooledCache(byte[] data, long length) {
            super(data);
            this.length = length;
        }

        @Override
        public void writeTo(OutputStream os) throws IOException {
            os.write(buf, 0, count);
        }

        @Override
        public StreamCache copy(Exchange exchange) {
            return new SpooledCache(buf, length);
        }

        @Override
        public boolean inMemory() {
            return false;
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public long position() {
            return pos;
        }
    }

    private static final class CapturingServletOutputStream extends ServletOutputStream {

        private final ByteArrayOutputStream target;

        private CapturingServletOutputStream(ByteArrayOutputStream target) {
            this.target = target;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // not used
        }

        @Override
        public void write(int b) {
            target.write(b);
        }
    }
}
