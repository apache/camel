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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

import org.apache.http.MessageConstraintException;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.io.BufferInfo;
import org.apache.http.io.HttpTransportMetrics;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;
import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.CharArrayBuffer;

import static org.apache.camel.util.BufferCaster.cast;

public class AS2SessionInputBuffer implements SessionInputBuffer, BufferInfo {

    private final HttpTransportMetricsImpl metrics;
    private final byte[] buffer;
    private final ByteArrayBuffer linebuffer;
    private final int minChunkLimit;
    private final MessageConstraints constraints;

    private CharsetDecoder decoder;

    private InputStream instream;
    private int bufferpos;
    private int bufferlen;
    private CharBuffer cbuf;
    
    private boolean lastLineReadTerminatedByLineFeed;

    public AS2SessionInputBuffer(final HttpTransportMetricsImpl metrics,
                                 final int buffersize,
                                 final int minChunkLimit,
                                 MessageConstraints constraints) {
        this.metrics = Args.notNull(metrics, "metrics");
        Args.positive(buffersize, "buffersize");
        this.buffer = new byte[buffersize];
        this.bufferpos = 0;
        this.bufferlen = 0;
        this.minChunkLimit = minChunkLimit >= 0 ? minChunkLimit : 512;
        this.constraints = constraints != null ? constraints : MessageConstraints.DEFAULT;
        this.linebuffer = new ByteArrayBuffer(buffersize);
    }

    public AS2SessionInputBuffer(final HttpTransportMetricsImpl metrics, final int buffersize) {
        this(metrics, buffersize, buffersize, null);
    }

    public CharsetDecoder getCharsetDecoder() {
        return decoder;
    }

    public void setCharsetDecoder(CharsetDecoder chardecoder) {
        this.decoder = chardecoder;
    }

    public void bind(final InputStream instream) {
        this.instream = instream;
    }

    public boolean isBound() {
        return this.instream != null;
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

    public int fillBuffer() throws IOException {
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
        l = streamRead(this.buffer, off, len);
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
    public int read(byte[] b, int off, int len) throws IOException {
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
            final int read = streamRead(b, off, len);
            if (read > 0) {
                this.metrics.incrementBytesTransferred(read);
            }
            return read;
        } else {
            // otherwise read to the buffer first
            while (!hasBufferedData()) {
                final int noRead = fillBuffer();
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
    public int read(byte[] b) throws IOException {
        if (b == null) {
            return 0;
        }
        return read(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        int noRead;
        while (!hasBufferedData()) {
            noRead = fillBuffer();
            if (noRead == -1) {
                return -1;
            }
        }
        return this.buffer[this.bufferpos++] & 0xff;
    }

    @Override
    public int readLine(CharArrayBuffer charbuffer) throws IOException {
        Args.notNull(charbuffer, "Char array buffer");
        final int maxLineLen = this.constraints.getMaxLineLength();
        int noRead = 0;
        boolean retry = true;
        this.lastLineReadTerminatedByLineFeed = false;
        while (retry) {
            // attempt to find end of line (LF)
            int pos = -1;
            for (int i = this.bufferpos; i < this.bufferlen; i++) {
                if (this.buffer[i] == HTTP.LF) {
                    pos = i;
                    this.lastLineReadTerminatedByLineFeed = true;
                    break;
                }
            }

            if (maxLineLen > 0) {
                final int currentLen = this.linebuffer.length() + (pos > 0 ? pos : this.bufferlen) - this.bufferpos;
                if (currentLen >= maxLineLen) {
                    throw new MessageConstraintException("Maximum line length limit exceeded");
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
                noRead = fillBuffer();
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

    @Override
    public String readLine() throws IOException {
        final CharArrayBuffer charbuffer = new CharArrayBuffer(64);
        final int l = readLine(charbuffer);
        if (l != -1) {
            return charbuffer.toString();
        } else {
            return null;
        }
    }

    public boolean isLastLineReadTerminatedByLineFeed() {
        return lastLineReadTerminatedByLineFeed;
    }

    @Override
    public boolean isDataAvailable(int timeout) throws IOException {
        return hasBufferedData();
    }

    @Override
    public HttpTransportMetrics getMetrics() {
        return this.metrics;
    }

    private int streamRead(final byte[] b, final int off, final int len) throws IOException {
        Asserts.notNull(this.instream, "Input stream");
        return this.instream.read(b, off, len);
    }

    private int lineFromLineBuffer(final CharArrayBuffer charbuffer) throws IOException {
        // discard LF if found
        int len = this.linebuffer.length();
        if (len > 0) {
            if (this.linebuffer.byteAt(len - 1) == HTTP.LF) {
                len--;
            }
            // discard CR if found
            if (len > 0) {
                if (this.linebuffer.byteAt(len - 1) == HTTP.CR) {
                    len--;
                }
            }
        }
        
        if (this.decoder == null) {
            charbuffer.append(this.linebuffer, 0, len);
        } else {
            final ByteBuffer bbuf = ByteBuffer.wrap(this.linebuffer.buffer(), 0, len);
            len = appendDecoded(charbuffer, bbuf);
        }
        this.linebuffer.clear();
        return len;
    }

    private int lineFromReadBuffer(final CharArrayBuffer charbuffer, final int position)
            throws IOException {
        int pos = position;
        final int off = this.bufferpos;
        int len;
        this.bufferpos = pos + 1;
        if (pos > off && this.buffer[pos - 1] == HTTP.CR) {
            // skip CR if found
            pos--;
        }
        len = pos - off;
        
        if (this.decoder == null) {
            charbuffer.append(this.buffer, off, len);
        } else {
            final ByteBuffer bbuf =  ByteBuffer.wrap(this.buffer, off, len);
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
            len += handleDecodingResult(result, charbuffer, bbuf);
        }
        final CoderResult result = this.decoder.flush(this.cbuf);
        len += handleDecodingResult(result, charbuffer, bbuf);
        this.cbuf.clear();
        return len;
    }

    private int handleDecodingResult(final CoderResult result, final CharArrayBuffer charbuffer, final ByteBuffer bbuf)
            throws IOException {
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
