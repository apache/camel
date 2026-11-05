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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void rawCaptureKeepsTheBytesReadLineConsumes() throws Exception {
        // CRLF, bare LF and a last line without terminator, plus a line longer than the buffer
        String longLine = "x".repeat(50);
        String content = "first\r\n" + longLine + "\nlast";
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII));
        AS2SessionInputBuffer buffer = new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), 16);

        assertNull(buffer.stopRawCapture(), "no capture open");

        buffer.startRawCapture();
        assertEquals("first", readLine(buffer, is));
        assertEquals(longLine, readLine(buffer, is), "a line assembled across buffer refills");
        assertEquals("last", readLine(buffer, is), "no terminator at the end of the stream");
        assertEquals(-1, buffer.readLine(new CharArrayBuffer(16), is));

        assertArrayEquals(content.getBytes(StandardCharsets.US_ASCII), buffer.stopRawCapture(),
                "every byte consumed, exactly as received");
        assertNull(buffer.stopRawCapture(), "a capture can only be stopped once");
    }

    @Test
    void rawCaptureKeepsALineEndingSplitAcrossTwoReads() throws Exception {
        // the CR arrives with one read, the LF with the next: both belong to the first line
        AS2SessionInputBuffer buffer = new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), 64);
        InputStream is = new ChunkedInputStream("abc\r", "\ndef\r\n");

        buffer.startRawCapture();
        assertEquals("abc", readLine(buffer, is));
        assertEquals("def", readLine(buffer, is));

        assertArrayEquals("abc\r\ndef\r\n".getBytes(StandardCharsets.US_ASCII), buffer.stopRawCapture());
    }

    @Test
    void rawCaptureStartsAndEndsWhereAsked() throws Exception {
        String content = "skipped\r\ncaptured\r\nafter\r\n";
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII));
        AS2SessionInputBuffer buffer = new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), 1024);

        readLine(buffer, is);
        buffer.startRawCapture();
        readLine(buffer, is);
        byte[] captured = buffer.stopRawCapture();

        assertArrayEquals("captured\r\n".getBytes(StandardCharsets.US_ASCII), captured);
        assertEquals("after", readLine(buffer, is), "reading goes on after the capture");
    }

    @Test
    void rawCapturesNest() throws Exception {
        // a part inside a part: the inner capture is a piece of the outer one, which goes on after it
        String content = "outer 1\r\ninner\r\nouter 2\r\n";
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII));
        AS2SessionInputBuffer buffer = new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), 1024);

        buffer.startRawCapture();
        readLine(buffer, is);
        buffer.startRawCapture();
        readLine(buffer, is);
        byte[] inner = buffer.stopRawCapture();
        readLine(buffer, is);
        byte[] outer = buffer.stopRawCapture();

        assertArrayEquals("inner\r\n".getBytes(StandardCharsets.US_ASCII), inner, "the inner capture");
        assertArrayEquals(content.getBytes(StandardCharsets.US_ASCII), outer, "the outer capture, inner part included");
        assertNull(buffer.stopRawCapture(), "both captures are closed");
    }

    @Test
    void excludedLastLineIsLeftOutOfTheCaptureStoppedNext() throws Exception {
        // a body-part parser read the boundary delimiter line: it and the CRLF before it belong to the boundary
        String content = "data\r\n--boundary\r\nmore\r\n--boundary\r\n";
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII));
        AS2SessionInputBuffer buffer = new AS2SessionInputBuffer(new BasicHttpTransportMetrics(), 1024);

        buffer.startRawCapture();
        assertEquals("data", readLine(buffer, is));
        assertEquals("--boundary", readLine(buffer, is));
        buffer.excludeLastLineFromRawCapture(2);
        assertArrayEquals("data".getBytes(StandardCharsets.US_ASCII), buffer.stopRawCapture(),
                "the delimiter line and the line break before it are not part of the part");

        // the exclusion holds for the capture stopped next only, and the next readLine forgets it
        buffer.startRawCapture();
        assertEquals("more", readLine(buffer, is));
        buffer.excludeLastLineFromRawCapture(2);
        assertEquals("--boundary", readLine(buffer, is));
        assertArrayEquals("more\r\n--boundary\r\n".getBytes(StandardCharsets.US_ASCII), buffer.stopRawCapture(),
                "an exclusion followed by another readLine is void");
    }
}
