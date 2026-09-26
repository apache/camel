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
package org.apache.camel.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IOHelperTest {

    private static final long GIGABYTE = 1024L * 1024 * 1024;
    private static final String MAX_SIZE_EXCEEDED = "The InputStream entry being copied exceeds the maximum allowed size";

    @Test
    public void testLookupEnvironmentVariable() {
        assertEquals("8081", IOHelper.lookupEnvironmentVariable("FOO_SERVICE_PORT"));
        assertEquals("8081", IOHelper.lookupEnvironmentVariable("foo-service.port"));
        assertEquals("8081", IOHelper.lookupEnvironmentVariable("foo-service-port"));
        assertEquals("8081", IOHelper.lookupEnvironmentVariable("foo.service.port"));

        assertEquals("mys3arn", IOHelper.lookupEnvironmentVariable("camel.kamelet.aws-s3-source.bucketNameOrArn"));
        assertEquals("mys3arn", IOHelper.lookupEnvironmentVariable("camel.kamelet.aws-s3-source.bucket-name-or-arn"));
        assertEquals("mys3arn", IOHelper.lookupEnvironmentVariable("camel.kamelet.awsS3Source.bucketNameOrArn"));
    }

    @Test
    public void testCharset() {
        assertEquals("UTF-8", IOHelper.getCharsetNameFromContentType("charset=utf-8"));
        assertEquals("UTF-8", IOHelper.getCharsetNameFromContentType("charset=UTF-8"));
        assertEquals("UTF-8", IOHelper.getCharsetNameFromContentType("text/plain; charset=UTF-8"));
        assertEquals("UTF-8", IOHelper.getCharsetNameFromContentType("application/json; charset=utf-8"));
        assertEquals("iso-8859-1", IOHelper.getCharsetNameFromContentType("application/json; charset=iso-8859-1"));
    }

    @Test
    public void testCopyMaxSize() throws IOException {
        byte[] data = new byte[100];

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        assertEquals(100, IOHelper.copy(new ByteArrayInputStream(data), bos, IOHelper.DEFAULT_BUFFER_SIZE, false, 100));
        assertEquals(100, bos.size());

        IOException e = assertThrows(IOException.class,
                () -> IOHelper.copy(new ByteArrayInputStream(data), new ByteArrayOutputStream(),
                        IOHelper.DEFAULT_BUFFER_SIZE, false, 99));
        assertEquals(MAX_SIZE_EXCEEDED, e.getMessage());
    }

    @Test
    public void testCopyMaxSizeOfTwoGigabytesOrMore() {
        // the count of copied bytes must not wrap around once more than Integer.MAX_VALUE bytes have been copied
        for (long maxSize : new long[] { Integer.MAX_VALUE - 100L, Integer.MAX_VALUE, 2 * GIGABYTE, 5 * GIGABYTE }) {
            InputStream is = new ZeroInputStream(maxSize + GIGABYTE);
            IOException e = assertThrows(IOException.class,
                    () -> IOHelper.copy(is, OutputStream.nullOutputStream(), IOHelper.DEFAULT_BUFFER_SIZE, false, maxSize),
                    "maxSize " + maxSize + " was not enforced");
            assertEquals(MAX_SIZE_EXCEEDED, e.getMessage());
        }
    }

    @Test
    public void testCopyMoreThanTwoGigabytes() throws IOException {
        InputStream is = new ZeroInputStream(3 * GIGABYTE);
        assertEquals(Integer.MAX_VALUE,
                IOHelper.copy(is, OutputStream.nullOutputStream(), IOHelper.DEFAULT_BUFFER_SIZE, false, -1));
    }

    /**
     * Returns the given number of zero bytes without holding them in memory.
     */
    private static final class ZeroInputStream extends InputStream {

        private long remaining;

        ZeroInputStream(long size) {
            this.remaining = size;
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
            Arrays.fill(b, off, off + n, (byte) 0);
            remaining -= n;
            return n;
        }
    }
}
