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

    private final ByteBuf buffer;

    public NettyChannelBufferStreamCache(ByteBuf buffer) {
        // retain the buffer so we keep it in use until we release it when we are done
        this.buffer = buffer.retain();
        this.buffer.markReaderIndex();
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public int read() {
        return buffer.readByte();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) {
        // are we at end, then return -1
        if (buffer.readerIndex() == buffer.capacity()) {
            return -1;
        }

        // ensure we don't read more than what we have in the buffer
        int before = buffer.readerIndex();
        int max = buffer.capacity() - before;
        len = Math.min(max, len);

        buffer.readBytes(b, off, len);
        return buffer.readerIndex() - before;
    }

    @Override
    public synchronized void reset() {
        buffer.resetReaderIndex();
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
