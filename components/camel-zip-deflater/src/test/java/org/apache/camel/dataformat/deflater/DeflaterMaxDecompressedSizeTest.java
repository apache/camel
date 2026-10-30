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
package org.apache.camel.dataformat.deflater;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the maxDecompressedSize option of the gzipDeflater and zipDeflater data formats, which works as the option of
 * the zipFile and tarFile data formats.
 */
class DeflaterMaxDecompressedSizeTest extends CamelTestSupport {

    private static final byte[] TEXT = "The Cow in Apple Time, by Robert Frost".getBytes(StandardCharsets.UTF_8);
    private static final String EXCEEDED = "The InputStream entry being copied exceeds the maximum allowed size";
    private static final long ONE_GIB = 1073741824L;
    // more than Integer.MAX_VALUE, so a copy that counts in an int would not see the limit
    private static final long OVER_TWO_GIB = (1L << 31) + 1;
    // an int cast of this value is 10
    private static final long OVER_FOUR_GIB = (1L << 32) + 10;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:gzip10").unmarshal(dataFormat().gzipDeflater().maxDecompressedSize(10).end())
                        .to("mock:result");
                from("direct:gzipText").unmarshal(dataFormat().gzipDeflater().maxDecompressedSize(TEXT.length).end())
                        .to("mock:result");
                from("direct:gzipTextMinusOne")
                        .unmarshal(dataFormat().gzipDeflater().maxDecompressedSize(TEXT.length - 1).end())
                        .to("mock:result");
                from("direct:gzipNoLimit").unmarshal(dataFormat().gzipDeflater().maxDecompressedSize(-1).end())
                        .to("mock:result");
                from("direct:gzipOverFourGib")
                        .unmarshal(dataFormat().gzipDeflater().maxDecompressedSize(OVER_FOUR_GIB).end())
                        .to("mock:result");

                from("direct:zip10").unmarshal(dataFormat().zipDeflater().maxDecompressedSize(10).end())
                        .to("mock:result");
                from("direct:zipText").unmarshal(dataFormat().zipDeflater().maxDecompressedSize(TEXT.length).end())
                        .to("mock:result");
                from("direct:zipTextMinusOne")
                        .unmarshal(dataFormat().zipDeflater().maxDecompressedSize(TEXT.length - 1).end())
                        .to("mock:result");
                from("direct:zipNoLimit").unmarshal(dataFormat().zipDeflater().maxDecompressedSize(-1).end())
                        .to("mock:result");
                from("direct:zipOverFourGib")
                        .unmarshal(dataFormat().zipDeflater().maxDecompressedSize(OVER_FOUR_GIB).end())
                        .to("mock:result");
            }
        };
    }

    @Test
    void gzipUnmarshalOverTheLimitFails() throws Exception {
        assertUnmarshalFails("direct:gzip10", gzip(TEXT));
        assertUnmarshalFails("direct:gzipTextMinusOne", gzip(TEXT));
    }

    @Test
    void gzipUnmarshalAtTheLimitOrWithoutLimitWorks() throws Exception {
        assertUnmarshalled("direct:gzipText", gzip(TEXT));
        assertUnmarshalled("direct:gzipNoLimit", gzip(TEXT));
        assertUnmarshalled("direct:gzipOverFourGib", gzip(TEXT));
    }

    @Test
    void zipUnmarshalOverTheLimitFails() throws Exception {
        assertUnmarshalFails("direct:zip10", deflate(TEXT));
        assertUnmarshalFails("direct:zipTextMinusOne", deflate(TEXT));
    }

    @Test
    void zipUnmarshalAtTheLimitOrWithoutLimitWorks() throws Exception {
        assertUnmarshalled("direct:zipText", deflate(TEXT));
        assertUnmarshalled("direct:zipNoLimit", deflate(TEXT));
        assertUnmarshalled("direct:zipOverFourGib", deflate(TEXT));
    }

    @Test
    void defaultLimitIsOneGibibyte() throws Exception {
        GzipDeflaterDataFormat gzip = new GzipDeflaterDataFormat();
        ZipDeflaterDataFormat zip = new ZipDeflaterDataFormat();
        assertEquals(ONE_GIB, gzip.getMaxDecompressedSize());
        assertEquals(ONE_GIB, zip.getMaxDecompressedSize());

        assertEquals(ONE_GIB, copy(gzip, ONE_GIB));
        assertEquals(ONE_GIB, copy(zip, ONE_GIB));
        assertCopyFails(gzip, ONE_GIB + 1);
        assertCopyFails(zip, ONE_GIB + 1);
    }

    @Test
    void limitOverTwoGibibytesIsEnforced() throws Exception {
        GzipDeflaterDataFormat gzip = new GzipDeflaterDataFormat();
        gzip.setMaxDecompressedSize(OVER_TWO_GIB);
        ZipDeflaterDataFormat zip = new ZipDeflaterDataFormat();
        zip.setMaxDecompressedSize(OVER_TWO_GIB);

        assertEquals(OVER_TWO_GIB, copy(gzip, OVER_TWO_GIB));
        assertEquals(OVER_TWO_GIB, copy(zip, OVER_TWO_GIB));
        assertCopyFails(gzip, OVER_TWO_GIB + 1);
        assertCopyFails(zip, OVER_TWO_GIB + 1);
    }

    @Test
    void noLimitCopiesMoreThanTheDefault() throws Exception {
        GzipDeflaterDataFormat gzip = new GzipDeflaterDataFormat();
        gzip.setMaxDecompressedSize(-1);
        ZipDeflaterDataFormat zip = new ZipDeflaterDataFormat();
        zip.setMaxDecompressedSize(-1);

        assertEquals(ONE_GIB + 1, copy(gzip, ONE_GIB + 1));
        assertEquals(ONE_GIB + 1, copy(zip, ONE_GIB + 1));
    }

    private void assertUnmarshalFails(String uri, byte[] body) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class, () -> template.sendBody(uri, body));
        IOException cause = assertInstanceOf(IOException.class, e.getCause());
        assertEquals(EXCEEDED, cause.getMessage());

        mock.assertIsSatisfied();
    }

    private void assertUnmarshalled(String uri, byte[] body) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(1);

        template.sendBody(uri, body);

        mock.assertIsSatisfied();
        assertArrayEquals(TEXT, mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class));
    }

    private static long copy(GzipDeflaterDataFormat dataFormat, long length) throws IOException {
        CountingOutputStream out = new CountingOutputStream();
        dataFormat.copyDecompressed(new LazyZerosInputStream(length), out);
        return out.count;
    }

    private static long copy(ZipDeflaterDataFormat dataFormat, long length) throws IOException {
        CountingOutputStream out = new CountingOutputStream();
        dataFormat.copyDecompressed(new LazyZerosInputStream(length), out);
        return out.count;
    }

    private static void assertCopyFails(GzipDeflaterDataFormat dataFormat, long length) {
        IOException e = assertThrows(IOException.class, () -> copy(dataFormat, length));
        assertEquals(EXCEEDED, e.getMessage());
    }

    private static void assertCopyFails(ZipDeflaterDataFormat dataFormat, long length) {
        IOException e = assertThrows(IOException.class, () -> copy(dataFormat, length));
        assertEquals(EXCEEDED, e.getMessage());
    }

    private static byte[] gzip(byte[] data) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> {
            try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
                out.write(data);
            }
        });
        return bytes.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> {
            try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) {
                out.write(data);
            }
        });
        return bytes.toByteArray();
    }

    /**
     * Gives the given number of bytes without holding them: read does not write into the buffer (which stays zero after
     * it is allocated).
     */
    private static final class LazyZerosInputStream extends InputStream {
        private long remaining;

        LazyZerosInputStream(long length) {
            this.remaining = length;
        }

        @Override
        public int read() {
            if (remaining <= 0) {
                return -1;
            }
            remaining--;
            return 0;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (remaining <= 0) {
                return -1;
            }
            int n = (int) Math.min(len, remaining);
            remaining -= n;
            return n;
        }

        @Override
        public int available() {
            return (int) Math.min(remaining, Integer.MAX_VALUE);
        }
    }

    /**
     * Counts the bytes written to it and keeps none of them.
     */
    private static final class CountingOutputStream extends OutputStream {
        private long count;

        @Override
        public void write(int b) {
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            count += len;
        }
    }
}
