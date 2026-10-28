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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpConverterGzipTest {

    private static final String TEXT = "Hello World";

    @Test
    void gzipBodyIsUncompressedWhenNoByteIsAvailableYet() throws Exception {
        HttpServletRequest request = request("gzip", nothingAvailable(gzip(TEXT)));

        try (InputStream is = HttpConverter.toInputStream(request, null)) {
            assertEquals(TEXT, new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void gzipBodyIsUncompressedWhenNoByteIsAvailableYetAndTheExchangeDoesNotSkipGzip() throws Exception {
        HttpServletRequest request = request("gzip", nothingAvailable(gzip(TEXT)));

        try (InputStream is = HttpConverter.toInputStream(request, exchange(null))) {
            assertEquals(TEXT, new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void gzipBodyIsUncompressedWhenItIsAvailable() throws Exception {
        HttpServletRequest request = request("gzip", allAvailable(gzip(TEXT)));

        try (InputStream is = HttpConverter.toInputStream(request, exchange(Boolean.FALSE))) {
            assertEquals(TEXT, new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void emptyBodyWithGzipContentEncodingIsEmpty() throws Exception {
        HttpServletRequest request = request("gzip", nothingAvailable(new byte[0]));

        try (InputStream is = HttpConverter.toInputStream(request, null)) {
            assertEquals(0, is.readAllBytes().length);
        }
    }

    @Test
    void skipGzipEncodingLeavesTheBodyCompressedWhenNoByteIsAvailableYet() throws Exception {
        byte[] compressed = gzip(TEXT);
        ServletInputStream stream = nothingAvailable(compressed);
        HttpServletRequest request = request("gzip", stream);

        InputStream is = HttpConverter.toInputStream(request, exchange(Boolean.TRUE));
        // the stream of the request itself, of which nothing was read
        assertSame(stream, is);
        assertArrayEquals(compressed, is.readAllBytes());
    }

    @Test
    void skipGzipEncodingLeavesTheBodyCompressedWhenItIsAvailable() throws Exception {
        byte[] compressed = gzip(TEXT);
        ServletInputStream stream = allAvailable(compressed);
        HttpServletRequest request = request("gzip", stream);

        InputStream is = HttpConverter.toInputStream(request, exchange(Boolean.TRUE));
        assertSame(stream, is);
        assertArrayEquals(compressed, is.readAllBytes());
    }

    @Test
    void bodyWithoutGzipContentEncodingIsNotRead() throws Exception {
        byte[] data = TEXT.getBytes(StandardCharsets.UTF_8);
        ServletInputStream stream = nothingAvailable(data);
        HttpServletRequest request = request(null, stream);

        InputStream is = HttpConverter.toInputStream(request, exchange(null));
        assertSame(stream, is);
        assertArrayEquals(data, is.readAllBytes());
    }

    @Test
    void bodyThatIsNotGzipFailsWhenItIsAvailable() throws Exception {
        HttpServletRequest request = request("gzip", allAvailable(TEXT.getBytes(StandardCharsets.UTF_8)));

        assertThrows(ZipException.class, () -> HttpConverter.toInputStream(request, exchange(null)));
    }

    @Test
    void bodyThatIsNotGzipFailsWhenNoByteIsAvailableYet() throws Exception {
        HttpServletRequest request = request("gzip", nothingAvailable(TEXT.getBytes(StandardCharsets.UTF_8)));

        assertThrows(ZipException.class, () -> HttpConverter.toInputStream(request, exchange(null)));
    }

    private static HttpServletRequest request(String contentEncoding, ServletInputStream stream) throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(Exchange.CONTENT_ENCODING)).thenReturn(contentEncoding);
        when(request.getInputStream()).thenReturn(stream);
        return request;
    }

    /**
     * An exchange with the given value of the CamelSkipGzipEncoding property; null is an exchange without it.
     */
    private static Exchange exchange(Boolean skipGzipEncoding) {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        if (skipGzipEncoding != null) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, skipGzipEncoding);
        }
        return exchange;
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    /**
     * The stream of a request whose body has not arrived when the consumer starts to read it: available() is 0, and
     * read() waits for the data.
     */
    private static ServletInputStream nothingAvailable(byte[] data) {
        return stream(data, false);
    }

    /**
     * The stream of a request whose body has arrived: available() is the number of bytes that are left.
     */
    private static ServletInputStream allAvailable(byte[] data) {
        return stream(data, true);
    }

    private static ServletInputStream stream(byte[] data, boolean available) {
        ByteArrayInputStream delegate = new ByteArrayInputStream(data);
        return new ServletInputStream() {
            @Override
            public int read() {
                return delegate.read();
            }

            @Override
            public int available() {
                return available ? delegate.available() : 0;
            }

            @Override
            public boolean isFinished() {
                return delegate.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
