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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import org.apache.camel.component.wal.exceptions.BufferTooSmallException;
import org.apache.camel.component.wal.exceptions.InvalidRecordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A reader for write-ahead log files
 */
public class LogReader implements AutoCloseable {
    public static final int DEFAULT_CAPACITY = 1024 * 512;
    private static final Logger LOG = LoggerFactory.getLogger(LogReader.class);

    private final FileChannel fileChannel;
    private final ByteBuffer ioBuffer;
    private final Header header;

    /**
     * Constructor
     *
     * @param  logFile     the report file name
     * @throws IOException in case of I/O errors
     */
    public LogReader(final File logFile) throws IOException {
        this(logFile, DEFAULT_CAPACITY);
    }

    /**
     * Constructor
     *
     * @param  logFile     the report file name
     * @throws IOException in case of I/O errors
     */
    public LogReader(final File logFile, int capacity) throws IOException {
        this.fileChannel = FileChannel.open(logFile.toPath(), StandardOpenOption.READ);
        ioBuffer = ByteBuffer.allocateDirect(capacity);

        header = readHeader();
    }

    /**
     * Gets the file header
     *
     * @return the file header
     */
    public Header getHeader() {
        return header;
    }

    /**
     * Reads the header from the file
     *
     * @return             the header or null if the file is empty
     * @throws IOException in case of lower-level I/O errors
     */
    private Header readHeader() throws IOException {
        if (fileChannel.size() == 0) {
            return null;
        }

        ioBuffer.clear();
        int bytesRead = fileChannel.read(ioBuffer);

        if (bytesRead <= 0) {
            throw new IllegalArgumentException("The file does not contain a valid header");
        }

        LOG.trace("Read {} bytes from the file channel", bytesRead);
        ioBuffer.flip();

        byte[] name = new byte[Header.FORMAT_NAME_SIZE];
        ioBuffer.get(name, 0, Header.FORMAT_NAME_SIZE);
        LOG.trace("File format name: '{}'", new String(name));

        int fileVersion = ioBuffer.getInt();
        LOG.trace("File format version: '{}'", fileVersion);

        return new Header(new String(name), fileVersion);
    }

    /**
     * Read an entry from the file.
     *
     * @return             A log entry from the file or null when reaching the end-of-file or if the file is empty
     * @throws IOException if unable to read the entry
     */
    public PersistedLogEntry readEntry() throws IOException {
        if (header == null) {
            return null;
        }

        logBufferInfo();

        if (ioBuffer.hasRemaining()) {
            return doReadEntry();
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Read it all from the buffer. Fetching again from the channel");
        }

        if (!reload()) {
            return null;
        }

        return doReadEntry();
    }

    /**
     * A lower-level routine to read a single entry from the transaction log
     *
     * @return             A log entry from the file or null when reaching the end-of-file or if the file is empty
     * @throws IOException if unable to read the entry
     */
    private PersistedLogEntry doReadEntry() throws IOException {
        if (ioBuffer.remaining() < Integer.BYTES) {
            if (!reload()) {
                return null;
            }
        }

        int state = ioBuffer.getInt();

        Slot keySlot = readSlot();
        Slot valueSlot = readSlot();

        EntryInfo entryInfo = EntryInfo.createForPersisted(fileChannel.position());

        return new PersistedLogEntry(
                entryInfo, LogEntry.EntryState.fromInt(state), keySlot.metadata, keySlot.data,
                valueSlot.metadata, valueSlot.data);
    }

    /**
     * Reads a data slot (i.e.: containing a key or a value)
     *
     * @return             the data slot
     * @throws IOException if the record is invalid or the data too large for the buffer
     */
    private Slot readSlot() throws IOException {
        Slot slot = new Slot();

        // The buffer needs to have enough space for the metadata and length.
        if (ioBuffer.remaining() < (Integer.BYTES * 2)) {
            if (!reload()) {
                throw new InvalidRecordException("A data slot within a record is incomplete or malformed");
            }
        }
        slot.metadata = ioBuffer.getInt();
        slot.length = ioBuffer.getInt();

        if (ioBuffer.capacity() < slot.length) {
            throw new BufferTooSmallException(ioBuffer.capacity(), slot.length);
        }

        if (ioBuffer.remaining() < slot.length) {
            if (!reload()) {
                throw new InvalidRecordException("A data slot within a record is incomplete or malformed");
            }
        }

        slot.data = new byte[slot.length];
        ioBuffer.get(slot.data);

        return slot;
    }

    /**
     * Reloads data into the intermediate buffer, compacting it on the process
     *
     * @return             true if has read data into the buffer (reloaded) or false otherwise
     * @throws IOException in case of lower-level I/O errors
     */
    private boolean reload() throws IOException {
        try {
            ioBuffer.compact();

            int read = fileChannel.read(ioBuffer);
            if (read > 0) {
                return true;
            }
        } finally {
            ioBuffer.flip();
        }
        return false;
    }

    private void logBufferInfo() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Remaining: {}", ioBuffer.remaining());
            LOG.trace("Position: {}", ioBuffer.position());
            LOG.trace("Has Remaining: {}", ioBuffer.hasRemaining());
        }
    }

    /**
     * Close the reader and release resources
     */
    @Override
    public void close() {
        try {
            fileChannel.close();
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    /**
     * A wrapper for a data slot
     */
    private static class Slot {
        int metadata;
        int length;

        byte[] data;
    }
}
