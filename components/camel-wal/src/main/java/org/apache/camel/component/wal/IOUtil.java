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

package org.apache.camel.component.wal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.camel.component.wal.exceptions.BufferOverflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * I/O utilities for the write-ahead log
 */
final class IOUtil {
    private static final Logger LOG = LoggerFactory.getLogger(IOUtil.class);

    private IOUtil() {

    }

    /**
     * Writes to the channel at a given position, clearing the source after completion
     *
     * @param  fileChannel the channel to write to
     * @param  byteBuffer  the buffer containing the bytes to write to the channel
     * @param  position    the position to write to
     * @return             the number of bytes written
     * @throws IOException for any lower-level I/O failure
     */
    static long write(FileChannel fileChannel, ByteBuffer byteBuffer, long position) throws IOException {
        long bytesWritten = 0;
        byteBuffer.flip();

        while (byteBuffer.hasRemaining()) {
            bytesWritten += fileChannel.write(byteBuffer, position + bytesWritten);
        }

        byteBuffer.flip();
        byteBuffer.clear();

        return bytesWritten;
    }

    /**
     * Writes to the channel by appending the data at the end and clearing the source after completion
     *
     * @param  fileChannel the channel to write to
     * @param  byteBuffer  the buffer containing the bytes to write to the channel
     * @return             the number of bytes written
     * @throws IOException for any lower-level I/O failure
     */
    static long write(FileChannel fileChannel, ByteBuffer byteBuffer) throws IOException {
        long bytesWritten = 0;
        byteBuffer.flip();

        while (byteBuffer.hasRemaining()) {
            bytesWritten += fileChannel.write(byteBuffer);
        }

        byteBuffer.flip();
        byteBuffer.clear();

        return bytesWritten;
    }

    /**
     * Serializes a entry to the buffer
     *
     * @param  buffer         the buffer where the entry will be serialized too
     * @param  entry          the entry to serialize
     * @throws BufferOverflow if the buffer is too small for the entry
     */
    static void serialize(ByteBuffer buffer, LogEntry entry) throws BufferOverflow {
        serialize(buffer, entry.getEntryState().getCode(), entry.getKeyMetadata(), entry.getKey(), entry.getValueMetadata(),
                entry.getValue());
    }

    /**
     * Serializes a entry to the buffer
     *
     * @param  buffer         the buffer where the entry will be serialized too
     * @param  entryState     the entry state
     * @param  keyMetadata    the entry metadata
     * @param  key            the entry key
     * @param  valueMetadata  the entry value metadata
     * @param  value          the entry value
     * @throws BufferOverflow if the buffer is too small for the entry
     */
    static void serialize(
            ByteBuffer buffer, int entryState, int keyMetadata, byte[] key, int valueMetadata, byte[] value)
            throws BufferOverflow {
        checkBufferCapacity(buffer,
                Integer.BYTES + Integer.BYTES + key.length + Integer.BYTES + Integer.BYTES + value.length);

        buffer.putInt(entryState);
        buffer.putInt(keyMetadata);
        buffer.putInt(key.length);
        buffer.put(key);
        buffer.putInt(valueMetadata);
        buffer.putInt(value.length);
        buffer.put(value);
    }

    private static void checkBufferCapacity(ByteBuffer byteBuffer, int requestedSize) throws BufferOverflow {
        final int remaining = byteBuffer.remaining();

        if (remaining < requestedSize) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("There is not enough space on the buffer for an offset entry: {} bytes remaining, {} bytes needed",
                        remaining, requestedSize);
            }

            throw new BufferOverflow(remaining, requestedSize);
        }
    }
}
