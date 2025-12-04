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

import static org.apache.camel.util.BufferCaster.cast;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

import org.apache.camel.util.ObjectHelper;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.io.HttpTransportMetrics;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;

public class AS2SessionInputBuffer implements SessionInputBuffer {

    private static final int CR = 13; // <US-ASCII CR, carriage return (13)>
    private static final int LF = 10; // <US-ASCII LF, linefeed (10)>

    private final BasicHttpTransportMetrics metrics;
    private final byte[] buffer;
    private final ByteArrayBuffer linebuffer;
    private final int minChunkLimit;

    private CharsetDecoder decoder;

    private int bufferpos;
    private int bufferlen;
    private CharBuffer cbuf;

    private boolean lastLineReadEnrichedByCarriageReturn;
    private boolean lastLineReadTerminatedByLineFeed;

    public AS2SessionInputBuffer(
            final BasicHttpTransportMetrics metrics, final int buffersize, final int minChunkLimit) {
        this.metrics = ObjectHelper.notNull(metrics, "metrics");
        Args.positive(buffersize, "buffersize");
        this.buffer = new byte[buffersize];
        this.bufferpos = 0;
        this.bufferlen = 0;
        this.minChunkLimit = minChunkLimit >= 0 ? minChunkLimit : 512;
        this.linebuffer = new ByteArrayBuffer(buffersize);
    }

    public AS2SessionInputBuffer(BasicHttpTransportMetrics metrics, int buffersize) {
        this(metrics, buffersize, buffersize);
    }

    public CharsetDecoder getCharsetDecoder() {
        return decoder;
    }

    public void setCharsetDecoder(CharsetDecoder chardecoder) {
        this.decoder = chardecoder;
    }

    @Override
    public int length() {
        return this.bufferlen - this.bufferpos;
    }

    @Override
    public int capacity() {
        return this.buffer.length;
    }

    @Override
    public int available() {
        return capacity() - length();
    }

    public int fillBuffer(InputStream inputStream) throws IOException {
        // compact the buffer if necessary
        if (this.bufferpos > 0) {
            final int len = this.bufferlen - this.bufferpos;
            if (len > 0) {
                System.arraycopy(this.buffer, this.bufferpos, this.buffer, 0, len);
            }
            this.bufferpos = 0;
            this.bufferlen = len;
        }
        final int l;
        final int off = this.bufferlen;
        final int len = this.buffer.length - off;
        l = inputStream.read(this.buffer, off, len);
        if (l == -1) {
            return -1;
        } else {
            this.bufferlen = off + l;
            this.metrics.incrementBytesTransferred(l);
            return l;
        }
    }

    public boolean hasBufferedData() {
        return this.bufferpos < this.bufferlen;
    }

    @Override
    public int read(byte[] b, int off, int len, InputStream inputStream) throws IOException {
        if (b == null) {
            return 0;
        }
        if (hasBufferedData()) {
            final int chunk = Math.min(len, this.bufferlen - this.bufferpos);
            System.arraycopy(this.buffer, this.bufferpos, b, off, chunk);
            this.bufferpos += chunk;
            return chunk;
        }
        // If the remaining capacity is big enough, read directly from the
        // underlying input stream bypassing the buffer.
        if (len > this.minChunkLimit) {
            final int read = inputStream.read(b, off, len);
            if (read > 0) {
                this.metrics.incrementBytesTransferred(read);
            }
            return read;
        } else {
            // otherwise read to the buffer first
            while (!hasBufferedData()) {
                final int noRead = fillBuffer(inputStream);
                if (noRead == -1) {
                    return -1;
                }
            }
            final int chunk = Math.min(len, this.bufferlen - this.bufferpos);
            System.arraycopy(this.buffer, this.bufferpos, b, off, chunk);
            this.bufferpos += chunk;
            return chunk;
        }
    }

    @Override
    public int read(byte[] b, InputStream inputStream) throws IOException {
        if (b == null) {
            return 0;
        }
        return inputStream.read(b, 0, b.length);
    }

    @Override
    public int read(InputStream inputStream) throws IOException {
        int noRead;
        while (!hasBufferedData()) {
            noRead = fillBuffer(inputStream);
            if (noRead == -1) {
                return -1;
            }
        }
        return this.buffer[this.bufferpos++] & 0xff;
    }

    @Override
    public int readLine(CharArrayBuffer charbuffer, InputStream inputStream) throws IOException {
        ObjectHelper.notNull(charbuffer, "Char array buffer");
        int noRead = 0;
        boolean retry = true;
        this.lastLineReadEnrichedByCarriageReturn = false;
        this.lastLineReadTerminatedByLineFeed = false;
        while (retry) {
            // attempt to find end of line (LF)
            int pos = -1;
            for (int i = this.bufferpos; i < this.bufferlen; i++) {
                if (this.buffer[i] == LF) {
                    pos = i;
                    this.lastLineReadTerminatedByLineFeed = true;
                    if (i > 0 && this.buffer[i - 1] == CR) {
                        this.lastLineReadEnrichedByCarriageReturn = true;
                    }
                    break;
                }
            }

            if (pos != -1) {
                // end of line found.
                if (this.linebuffer.isEmpty()) {
                    // the entire line is preset in the read buffer
                    return lineFromReadBuffer(charbuffer, pos);
                }
                retry = false;
                addBytesToLinebuffer(pos);
            } else {
                // end of line not found
                if (hasBufferedData()) {
                    addBytesToLinebuffer(pos);
                }
                noRead = fillBuffer(inputStream);
                if (noRead == -1) {
                    // end of stream reached.
                    retry = false;
                }
            }
        }
        if (noRead == -1 && this.linebuffer.isEmpty()) {
            // end of stream reached with no further data in line buffer
            return -1;
        }

        return lineFromLineBuffer(charbuffer);
    }

    public boolean isLastLineReadTerminatedByLineFeed() {
        return lastLineReadTerminatedByLineFeed;
    }

    public boolean isLastLineReadEnrichedByCarriageReturn() {
        return lastLineReadEnrichedByCarriageReturn;
    }

    @Override
    public HttpTransportMetrics getMetrics() {
        return this.metrics;
    }

    private int lineFromLineBuffer(final CharArrayBuffer charbuffer) throws IOException {
        // discard LF if found
        int len = this.linebuffer.length();
        if (len > 0) {
            if (this.linebuffer.byteAt(len - 1) == LF) {
                len--;
            }
            // discard CR if found
            if (len > 0) {
                if (this.linebuffer.byteAt(len - 1) == CR) {
                    len--;
                }
            }
        }

        if (this.decoder == null) {
            charbuffer.append(this.linebuffer, 0, len);
        } else {
            final ByteBuffer bbuf = ByteBuffer.wrap(linebuffer.toByteArray(), 0, len);
            len = appendDecoded(charbuffer, bbuf);
        }
        this.linebuffer.clear();
        return len;
    }

    private int lineFromReadBuffer(final CharArrayBuffer charbuffer, final int position) throws IOException {
        int pos = position;
        final int off = this.bufferpos;
        int len;
        this.bufferpos = pos + 1;
        if (pos > off && this.buffer[pos - 1] == CR) {
            // skip CR if found
            pos--;
        }
        len = pos - off;

        if (this.decoder == null) {
            charbuffer.append(this.buffer, off, len);
        } else {
            final ByteBuffer bbuf = ByteBuffer.wrap(this.buffer, off, len);
            len = appendDecoded(charbuffer, bbuf);
        }
        return len;
    }

    private int appendDecoded(final CharArrayBuffer charbuffer, final ByteBuffer bbuf) throws IOException {
        if (!bbuf.hasRemaining()) {
            return 0;
        }
        if (this.cbuf == null) {
            this.cbuf = CharBuffer.allocate(1024);
        }
        this.decoder.reset();
        int len = 0;
        while (bbuf.hasRemaining()) {
            final CoderResult result = this.decoder.decode(bbuf, this.cbuf, true);
            len += handleDecodingResult(result, charbuffer);
        }
        final CoderResult result = this.decoder.flush(this.cbuf);
        len += handleDecodingResult(result, charbuffer);
        cast(this.cbuf).clear();
        return len;
    }

    private int handleDecodingResult(final CoderResult result, final CharArrayBuffer charbuffer) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
        cast(this.cbuf).flip();
        final int len = this.cbuf.remaining();
        while (this.cbuf.hasRemaining()) {
            charbuffer.append(this.cbuf.get());
        }
        this.cbuf.compact();
        return len;
    }

    private void addBytesToLinebuffer(int pos) throws IOException {
        try {
            int len;
            if (pos != -1) {
                len = pos + 1 - this.bufferpos;
            } else {
                len = this.bufferlen - this.bufferpos;
            }
            this.linebuffer.append(this.buffer, this.bufferpos, len);
            if (pos != -1) {
                this.bufferpos = pos + 1;
            } else {
                this.bufferpos = this.bufferlen;
            }
        } catch (Exception e) {
            throw new IOException("failed to decode transfer encoding", e);
        }
    }
}
