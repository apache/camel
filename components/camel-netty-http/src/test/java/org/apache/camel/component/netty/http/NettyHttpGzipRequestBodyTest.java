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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The request body of a netty-http consumer is a {@link NettyChannelBufferStreamCache}; a route reads it as an
 * {@link InputStream}.
 */
class NettyHttpGzipRequestBodyTest extends BaseNettyTestSupport {

    @Test
    void gzipDataFormatUnmarshalsTheRequestBody() throws Exception {
        String out = template.requestBody("netty-http:http://localhost:{{port}}/unmarshal",
                gzip("Hello World".getBytes(StandardCharsets.UTF_8)), String.class);
        assertEquals("Hello World", out);
    }

    @Test
    void resetReturnsTheWholeRequestBodyAfterTheGzipUnmarshal() throws Exception {
        byte[] gzip = gzip(largeData());
        byte[] out = template.requestBody("netty-http:http://localhost:{{port}}/reset", gzip, byte[].class);
        assertArrayEquals(gzip, out);
    }

    @Test
    void writeToReturnsTheWholeRequestBodyAfterTheGzipUnmarshal() throws Exception {
        byte[] gzip = gzip(largeData());
        byte[] out = template.requestBody("netty-http:http://localhost:{{port}}/writeTo", gzip, byte[].class);
        assertArrayEquals(gzip, out);
    }

    @Test
    void processorReadsTheRequestBodyByteByByte() {
        byte[] binary = { 0x10, 0x20, (byte) 0xFF, 0x30, 0x40 };
        String out = template.requestBody("netty-http:http://localhost:{{port}}/count", binary, String.class);
        assertEquals("5", out);
    }

    private static byte[] largeData() {
        // random bytes do not compress: the gzip body (20028 bytes) is larger than the 8192 byte buffer of the gzip
        // reader
        byte[] data = new byte[20000];
        new Random(42).nextBytes(data);
        return data;
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/unmarshal")
                        .unmarshal().gzipDeflater()
                        .convertBodyTo(String.class, "UTF-8");

                // the compressed request body is kept and read again after the unmarshal has read it
                from("netty-http:http://0.0.0.0:{{port}}/reset")
                        .setProperty("compressed", body())
                        .unmarshal().gzipDeflater()
                        .process(exchange -> {
                            StreamCache cache = exchange.getProperty("compressed", StreamCache.class);
                            cache.reset();
                            exchange.getMessage().setBody(((InputStream) cache).readAllBytes());
                        });

                // the type converter from a stream cache to byte[] calls writeTo
                from("netty-http:http://0.0.0.0:{{port}}/writeTo")
                        .setProperty("compressed", body())
                        .unmarshal().gzipDeflater()
                        .setBody(exchangeProperty("compressed"))
                        .convertBodyTo(byte[].class);

                from("netty-http:http://0.0.0.0:{{port}}/count")
                        .process(exchange -> {
                            InputStream is = exchange.getIn().getBody(InputStream.class);
                            int count = 0;
                            while (is.read() != -1) {
                                count++;
                            }
                            exchange.getMessage().setBody(String.valueOf(count));
                        });
            }
        };
    }
}
