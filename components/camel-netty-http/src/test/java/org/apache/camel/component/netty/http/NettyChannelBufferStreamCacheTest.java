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
package org.apache.camel.component.netty.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link NettyChannelBufferStreamCache} read as a plain {@link java.io.InputStream}.
 */
class NettyChannelBufferStreamCacheTest {

    @Test
    void readReturnsEveryByteAsUnsignedValue() throws IOException {
        byte[] data = { 0x00, 0x7F, (byte) 0x80, (byte) 0xFF, 0x01 };
        NettyChannelBufferStreamCache cache = new NettyChannelBufferStreamCache(Unpooled.wrappedBuffer(data));
        try {
            assertEquals(0x00, cache.read());
            assertEquals(0x7F, cache.read());
            assertEquals(0x80, cache.read());
            assertEquals(0xFF, cache.read());
            assertEquals(0x01, cache.read());
            assertEquals(-1, cache.read());
        } finally {
            release(cache);
        }
    }

    @Test
    void readLoopCopiesBinaryContent() throws IOException {
        byte[] data = { 0x10, 0x20, (byte) 0xFF, 0x30, 0x40 };
        NettyChannelBufferStreamCache cache = new NettyChannelBufferStreamCache(Unpooled.wrappedBuffer(data));
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int b;
            while ((b = cache.read()) != -1) {
                out.write(b);
            }
            assertArrayEquals(data, out.toByteArray());
        } finally {
            release(cache);
        }
    }

    @Test
    void readLoopEndsAtTheEndOfText() throws IOException {
        NettyChannelBufferStreamCache cache
                = new NettyChannelBufferStreamCache(Unpooled.copiedBuffer("Hello", StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = cache.read()) != -1) {
                sb.append((char) b);
            }
            assertEquals("Hello", sb.toString());
        } finally {
            release(cache);
        }
    }

    @Test
    void markIsNotSupported() {
        NettyChannelBufferStreamCache cache
                = new NettyChannelBufferStreamCache(Unpooled.wrappedBuffer(new byte[] { 1, 2, 3 }));
        try {
            assertFalse(cache.markSupported());
        } finally {
            release(cache);
        }
    }

    @Test
    void resetAndWriteToReturnTheWholeBodyAfterAReaderThatMarks() throws IOException {
        // larger than the 8192 byte buffer that the gzip reader of commons-compress marks and reads
        byte[] data = new byte[20000];
        new Random(42).nextBytes(data);
        NettyChannelBufferStreamCache cache = new NettyChannelBufferStreamCache(Unpooled.wrappedBuffer(data));
        try {
            byte[] buf = new byte[8192];
            do {
                cache.mark(buf.length);
            } while (cache.read(buf) != -1);

            cache.reset();
            assertArrayEquals(data, cache.readAllBytes());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            cache.writeTo(out);
            assertArrayEquals(data, out.toByteArray());
        } finally {
            release(cache);
        }
    }

    @Test
    void readStopsAtTheLastWrittenByte() throws IOException {
        // the capacity (16) is larger than the content (3 bytes)
        ByteBuf buffer = Unpooled.buffer(16);
        buffer.writeBytes(new byte[] { 1, 2, 3 });
        NettyChannelBufferStreamCache cache = new NettyChannelBufferStreamCache(buffer);
        try {
            assertArrayEquals(new byte[] { 1, 2, 3 }, cache.readAllBytes());
            assertEquals(-1, cache.read(new byte[4], 0, 4));
        } finally {
            release(cache);
        }
    }

    @Test
    void gzipInputStreamReadsItsHeader() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write("Hello World".getBytes(StandardCharsets.UTF_8));
        }
        NettyChannelBufferStreamCache cache = new NettyChannelBufferStreamCache(Unpooled.wrappedBuffer(bos.toByteArray()));
        try (GZIPInputStream gzip = new GZIPInputStream(cache)) {
            assertEquals("Hello World", new String(gzip.readAllBytes(), StandardCharsets.UTF_8));
        } finally {
            release(cache);
        }
    }

    @Test
    void availableIsTheNumberOfBytesLeft() throws IOException {
        // the capacity (16) is larger than the content (5 bytes)
        ByteBuf buffer = Unpooled.buffer(16);
        buffer.writeBytes(new byte[] { 1, 2, 3, 4, 5 });
        NettyChannelBufferStreamCache cache = new NettyChannelBufferStreamCache(buffer);
        try {
            assertEquals(5, cache.available());
            cache.read();
            assertEquals(4, cache.available());
            cache.read(new byte[3]);
            assertEquals(1, cache.available());
            cache.read();
            assertEquals(0, cache.available());
            assertEquals(-1, cache.read());
            assertEquals(0, cache.available());

            cache.reset();
            assertEquals(5, cache.available());
        } finally {
            release(cache);
        }
    }

    private static void release(NettyChannelBufferStreamCache cache) {
        // once for the buffer that the test created and once for the retain of the cache
        cache.release();
        cache.release();
    }
}
