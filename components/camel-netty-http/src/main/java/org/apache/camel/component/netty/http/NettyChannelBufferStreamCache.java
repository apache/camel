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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

import io.netty.buffer.ByteBuf;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.util.IOHelper;

/**
 * A {@link ByteBuf} which is exposed as an {@link InputStream} which makes it very easy to use by Camel and other Camel
 * components. Also supported is {@link StreamCache} which allows the data to be re-read for example when doing content
 * based routing with XPath.
 * <p/>
 * <b>Important:</b> All the classes from the Camel release that implements {@link StreamCache} is NOT intended for end
 * users to create as instances, but they are part of Camels
 * <a href="https://camel.apache.org/manual/stream-caching.html">stream-caching</a> functionality.
 */
public final class NettyChannelBufferStreamCache extends InputStream implements StreamCache {

    private final ReentrantLock lock = new ReentrantLock();
    private final ByteBuf buffer;

    public NettyChannelBufferStreamCache(ByteBuf buffer) {
        // retain the buffer so we keep it in use until we release it when we are done
        this.buffer = buffer.retain();
        this.buffer.markReaderIndex();
    }

    /**
     * Marking is not supported: {@link #mark(int)} does nothing, and {@link #reset()} always goes back to the start of
     * the body, which is what {@link StreamCache#reset()} is for. A reader that needs mark support has to wrap this
     * stream (for example in a {@link java.io.BufferedInputStream}), and so cannot change where a reset goes back to.
     */
    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read() {
        // an unsigned byte, and -1 at the end, as InputStream requires
        return buffer.isReadable() ? buffer.readByte() & 0xFF : -1;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) {
        // are we at end, then return -1
        int max = buffer.readableBytes();
        if (max == 0) {
            return -1;
        }

        // ensure we don't read more than what we have in the buffer
        len = Math.min(max, len);

        buffer.readBytes(b, off, len);
        return len;
    }

    @Override
    public int available() {
        // the whole body is in memory, so what is left of it can be read without blocking
        return buffer.readableBytes();
    }

    @Override
    public void reset() {
        lock.lock();
        try {
            buffer.resetReaderIndex();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        // must remember current index so we can reset back to it after the copy
        int idx = buffer.readerIndex();
        try {
            buffer.resetReaderIndex();
            IOHelper.copy(this, os);
        } finally {
            buffer.readerIndex(idx);
        }
    }

    @Override
    public StreamCache copy(Exchange exchange) {
        return new NettyChannelBufferStreamCache(buffer.copy());
    }

    @Override
    public boolean inMemory() {
        return true;
    }

    @Override
    public long length() {
        return buffer.readableBytes();
    }

    @Override
    public long position() {
        return buffer.readerIndex();
    }

    /**
     * Release the buffer when we are done using it.
     */
    public void release() {
        buffer.release();
    }

}
