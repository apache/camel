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
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A writer for write-ahead log files
 */
public final class LogWriter implements AutoCloseable {
    /**
     * The default buffer capacity: 512 KiB
     */
    public static final int DEFAULT_CAPACITY = 1024 * 512;
    private static final Logger LOG = LoggerFactory.getLogger(LogWriter.class);

    private final FileChannel fileChannel;

    private final LogSupervisor flushPolicy;
    private final TransactionLog transactionLog;

    private long startOfRecords;

    /**
     * Constructs a new log writer with the default capacity {@link LogWriter#DEFAULT_CAPACITY} (512 KiB). If the file
     * already exists, it will be truncated.
     *
     * @param  logFile       the transaction log file
     * @param  logSupervisor the log supervisor {@link LogSupervisor} for the writer
     * @throws IOException   in case of I/O errors
     */
    public LogWriter(File logFile, LogSupervisor logSupervisor) throws IOException {
        this(logFile, logSupervisor, DEFAULT_CAPACITY);
    }

    /**
     * Constructs a new log writer with the default capacity {@link LogWriter#DEFAULT_CAPACITY} (512 KiB). If the file
     * already exists, it will be truncated.
     *
     * @param  logFile        the transaction log file
     * @param  logSupervisor  the log supervisor {@link LogSupervisor} for the writer
     * @param  maxRecordCount the maximum number of records to keep in the file. Beyond this count, entries will be
     *                        rolled-over.
     * @throws IOException    in case of I/O errors
     */
    LogWriter(File logFile, LogSupervisor logSupervisor, int maxRecordCount) throws IOException {
        this.fileChannel = FileChannel.open(logFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);

        final Header header = Header.WA_DEFAULT_V1;
        writeHeader(header);

        this.flushPolicy = logSupervisor;
        this.transactionLog = new TransactionLog(maxRecordCount);
        this.flushPolicy.start(this::tryFlush);
    }

    /**
     * Flushes the data to disk
     *
     * @throws IOException in case of I/O errors
     */
    void flush() throws IOException {
        fileChannel.force(true);
    }

    private synchronized void tryFlush() {
        try {
            flush();
        } catch (IOException e) {
            LOG.error("Unable to save record: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void reset() throws IOException {
        fileChannel.truncate(startOfRecords);
        fileChannel.position(startOfRecords);
    }

    @Override
    public void close() {
        try {
            flushPolicy.stop();
            flush();

            fileChannel.close();
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void writeHeader(final Header header) throws IOException {
        ByteBuffer headerBuffer = ByteBuffer.allocate(Header.BYTES);

        headerBuffer.put(header.getFormatName().getBytes());
        headerBuffer.putInt(header.getFileVersion());

        IOUtil.write(fileChannel, headerBuffer);

        startOfRecords = fileChannel.position();
    }

    /**
     * Appends an entry to the transaction log file
     *
     * @param  entry       the entry to write to the transaction log
     * @return             An entry info instance with the metadata for the appended log entry
     * @throws IOException for lower-level I/O errors
     */
    public EntryInfo.CachedEntryInfo append(LogEntry entry) throws IOException {
        final TransactionLog.LayerInfo layerInfo = transactionLog.add(entry);
        if (layerInfo.getLayer() == 0) {
            return persist(layerInfo, entry);
        }

        if (layerInfo.isRollingOver()) {
            reset();
        }

        LOG.trace("Writing at position {}", fileChannel.position());
        EntryInfo.CachedEntryInfo spear = persist(layerInfo, entry);

        final List<EntryInfo> collect = transactionLog.stream()
                .filter(c -> c != null && c.layerInfo.getLayer() != transactionLog.currentLayer())
                .map(e -> tryPersist(layerInfo, e.logEntry)).collect(Collectors.toList());

        if (!collect.isEmpty()) {
            final EntryInfo lastOnLayer = collect.get(0);

            LOG.trace("Current pos is: {}", fileChannel.position());
            LOG.trace("Next pos should be: {}", lastOnLayer.getPosition());

            fileChannel.position(lastOnLayer.getPosition());
            LOG.trace("Current pos now is: {}", fileChannel.position());
        }

        return spear;
    }

    /**
     * Persists an entry to the log
     *
     * @param  layerInfo   the in-memory layer information about the record being persisted
     * @param  entry       the entry to persist
     * @param  position    the position in the channel where the entry will be persisted
     * @throws IOException in case of lower-level I/O errors
     */
    private void persist(TransactionLog.LayerInfo layerInfo, LogEntry entry, long position) throws IOException {
        ByteBuffer updateBuffer = ByteBuffer.allocate(entry.size());

        IOUtil.serialize(updateBuffer, entry);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Position: {} for record {} with key {}", position, layerInfo, new String(entry.getKey()));
        }

        long size = IOUtil.write(fileChannel, updateBuffer, position);

        if (size == 0) {
            LOG.warn("No bytes written for the given record!");
        }
    }

    /**
     * Persists an entry to the log
     *
     * @param  layerInfo   the in-memory layer information about the record being persisted
     * @param  entry       the entry to persist
     * @return             an {@link EntryInfo} instance with details of the entry that was just persisted
     * @throws IOException in case of lower-level I/O errors
     */
    private EntryInfo.CachedEntryInfo persist(TransactionLog.LayerInfo layerInfo, LogEntry entry) throws IOException {
        final byte[] key = entry.getKey();
        final byte[] value = entry.getValue();

        ByteBuffer writeBuffer = ByteBuffer.allocate(LogEntry.size(key, value));
        IOUtil.serialize(writeBuffer, entry);

        long recordPosition = fileChannel.position();
        IOUtil.write(fileChannel, writeBuffer);

        return EntryInfo.createForCached(recordPosition, layerInfo);
    }

    /**
     * A wrapper for {@link LogWriter#persist(TransactionLog.LayerInfo, LogEntry)} that throws runtime errors on failure
     *
     * @param  layerInfo the in-memory layer information about the record being persisted
     * @param  entry     the entry to persist
     * @return           an {@link EntryInfo} instance with details of the entry that was just persisted
     */
    private EntryInfo tryPersist(TransactionLog.LayerInfo layerInfo, LogEntry entry) {
        try {
            return persist(layerInfo, entry);
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Updates the state of af entry (i.e.: to mark them after they have seen successfully processed)
     *
     * @param  entryInfo   the entry information about the entry being updated
     * @param  state       the state to update the entry to
     * @throws IOException in case of lower-level I/O errors
     */
    public void updateState(EntryInfo.CachedEntryInfo entryInfo, LogEntry.EntryState state) throws IOException {
        final TransactionLog.LayerInfo layerInfo = entryInfo.getLayerInfo();

        /*
         If it has layer information, then it's a hot record kept in the cache. In this case, just
         update the cache and let the LogSupervisor flush to disk.

         Trying to update a persisted entry here is not acceptable
         */
        assert layerInfo != null;

        final LogEntry logEntry = transactionLog.update(layerInfo, state);

        if (logEntry != null) {
            persist(layerInfo, logEntry, entryInfo.getPosition());
        }
    }

    /**
     * Updates the state of af entry that has been already persisted to disk. Wraps any lower-level I/O errors in
     * runtime exceptions
     *
     * @param  entry       the entry to update
     * @param  state       the state to update the entry to
     * @throws IOException if the buffer is too small for the entry or in case of lower-level I/O errors
     */
    public void updateState(PersistedLogEntry entry, LogEntry.EntryState state) throws IOException {
        ByteBuffer updateBuffer = ByteBuffer.allocate(entry.size());

        IOUtil.serialize(updateBuffer, state.getCode(), entry.getKeyMetadata(), entry.getKey(), entry.getValueMetadata(),
                entry.getValue());

        final EntryInfo entryInfo = entry.getEntryInfo();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Position: {} with key {}", entryInfo.getPosition(), new String(entry.getKey()));
        }

        long size = IOUtil.write(fileChannel, updateBuffer, entryInfo.getPosition());

        if (size == 0) {
            LOG.warn("No bytes written for the given record!");
        }
    }

}
