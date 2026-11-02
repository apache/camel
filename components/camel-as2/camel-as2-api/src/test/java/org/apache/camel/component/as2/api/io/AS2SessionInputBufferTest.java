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
package org.apache.camel.component.as2.api.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AS2SessionInputBufferTest {

    /**
     * An input stream that returns at most one of the given chunks per read, as a socket stream does.
     */
    private static final class ChunkedInputStream extends InputStream {
        private final byte[][] chunks;
        private int chunk;
        private int offset;

        ChunkedInputStream(String... chunks) {
            this.chunks = new byte[chunks.length][];
            for (int i = 0; i < chunks.length; i++) {
                this.chunks[i] = chunks[i].getBytes(StandardCharsets.US_ASCII);
            }
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            while (chunk < chunks.length && offset == chunks[chunk].length) {
                chunk++;
                offset = 0;
            }
            if (chunk == chunks.length) {
                return -1;
            }
            int n = Math.min(len, chunks[chunk].length - offset);
            System.arraycopy(chunks[chunk], offset, b, off, n);
            offset += n;
            return n;
        }
    }

    private static String readLine(AS2SessionInputBuffer inbuffer, InputStream is) throws IOException {
        CharArrayBuffer line = new CharArrayBuffer(16);
        assertTrue(inbuffer.readLine(line, is) >= 0, "Expected a line");
        return line.toString();
    }

    @Test
    void readLineSeesTheCarriageReturnOfALineEndingSplitAcrossTwoReads() throws Exception {
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), 64);
        InputStream is = new ChunkedInputStream("abc\r", "\ndef\r\n");

        assertEquals("abc", readLine(inbuffer, is));
        assertTrue(inbuffer.isLastLineReadTerminatedByLineFeed(), "Line terminated by LF");
        assertTrue(inbuffer.isLastLineReadEnrichedByCarriageReturn(), "Line terminated by CRLF");

        assertEquals("def", readLine(inbuffer, is));
        assertTrue(inbuffer.isLastLineReadTerminatedByLineFeed(), "Line terminated by LF");
        assertTrue(inbuffer.isLastLineReadEnrichedByCarriageReturn(), "Line terminated by CRLF");
    }

    @Test
    void readLineSeesTheCarriageReturnAtTheEndOfAFullReadBuffer() throws Exception {
        // the CR is the last byte of the first fill of the 8 byte read buffer
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), 8);
        InputStream is = new ByteArrayInputStream("1234567\r\nx\n".getBytes(StandardCharsets.US_ASCII));

        assertEquals("1234567", readLine(inbuffer, is));
        assertTrue(inbuffer.isLastLineReadTerminatedByLineFeed(), "Line terminated by LF");
        assertTrue(inbuffer.isLastLineReadEnrichedByCarriageReturn(), "Line terminated by CRLF");

        assertEquals("x", readLine(inbuffer, is));
        assertTrue(inbuffer.isLastLineReadTerminatedByLineFeed(), "Line terminated by LF");
        assertFalse(inbuffer.isLastLineReadEnrichedByCarriageReturn(), "Line terminated by a bare LF");
    }

    @Test
    void readLineDoesNotTakeACarriageReturnBeforeTheLineForItsLineEnding() throws Exception {
        // the CR is read by read(InputStream); the line read next is empty and ends with a bare LF
        AS2SessionInputBuffer inbuffer = new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), 64);
        InputStream is = new ByteArrayInputStream("\r\nq\r\n".getBytes(StandardCharsets.US_ASCII));

        assertEquals('\r', inbuffer.read(is));
        assertEquals("", readLine(inbuffer, is));
        assertTrue(inbuffer.isLastLineReadTerminatedByLineFeed(), "Line terminated by LF");
        assertFalse(inbuffer.isLastLineReadEnrichedByCarriageReturn(), "Line terminated by a bare LF");

        assertEquals("q", readLine(inbuffer, is));
        assertTrue(inbuffer.isLastLineReadEnrichedByCarriageReturn(), "Line terminated by CRLF");
    }
}
