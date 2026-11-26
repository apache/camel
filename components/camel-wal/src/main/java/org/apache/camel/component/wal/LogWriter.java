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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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

    private final Lock lock = new ReentrantLock();
    private final FileChannel fileChannel;

    private final LogSupervisor flushPolicy;
    private final TransactionLog transactionLog;
    private final List<EntryInfo.CachedEntryInfo> initialEntryInfos;

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
        this(logFile, logSupervisor, maxRecordCount, List.of());
    }

    /**
     * Constructs a new log writer whose new log holds the given entries first. Without entries, the file is truncated,
     * as with the other constructors. With entries, the file is replaced only once the new log is on disk: the header
     * and the entries are written to a temporary file in the same directory, which is forced to disk and then moved
     * atomically over the file. If that is not possible (the temporary file cannot be written, or the file system
     * cannot move it atomically over the file), a WARN is logged and the file is truncated and written in place, as
     * without entries, and forced to disk right after the entries.
     *
     * @param  logFile        the transaction log file
     * @param  logSupervisor  the log supervisor {@link LogSupervisor} for the writer
     * @param  maxRecordCount the maximum number of records to keep in the file. Beyond this count, entries will be
     *                        rolled-over.
     * @param  entries        the entries the new log holds first (fewer than maxRecordCount)
     * @throws IOException    in case of I/O errors
     */
    LogWriter(File logFile, LogSupervisor logSupervisor, int maxRecordCount, List<LogEntry> entries) throws IOException {
        this.flushPolicy = logSupervisor;
        this.transactionLog = new TransactionLog(maxRecordCount);

        // with entries, the new log replaces the file a symbolic link points to, not the link
        final Path path = entries.isEmpty() || !Files.exists(logFile.toPath())
                ? logFile.toPath() : logFile.toPath().toRealPath();
        final boolean replaced = !entries.isEmpty() && replace(path, entries);
        // after the replacement, the file already holds the header and the entries: they are written again below at
        // the same positions (the same bytes), so the positions and the layers are built as for a new log
        this.fileChannel = replaced
                ? FileChannel.open(path, StandardOpenOption.WRITE)
                : FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
        try {
            final Header header = Header.WA_DEFAULT_V1;
            writeHeader(header);

            final List<EntryInfo.CachedEntryInfo> entryInfos = new ArrayList<>(entries.size());
            for (LogEntry entry : entries) {
                entryInfos.add(append(entry));
            }
            this.initialEntryInfos = List.copyOf(entryInfos);

            if (!entries.isEmpty()) {
                // without the replacement, the entries are on disk only from here on
                flush();
            }

            this.flushPolicy.start(this::tryFlush);
        } catch (IOException | RuntimeException e) {
            try {
                fileChannel.close();
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /**
     * Replaces the file by a new log with the header and the entries: writes them to a temporary file in the same
     * directory, forces it to disk and moves it atomically over the file. Until the move the file is unchanged.
     *
     * @return false, after a WARN, if the file could not be replaced (it is then unchanged)
     */
    private static boolean replace(Path target, List<LogEntry> entries) {
        final Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        boolean created = false;
        try {
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                created = true;
                write(channel, Header.WA_DEFAULT_V1);
                for (LogEntry entry : entries) {
                    write(channel, entry);
                }
                channel.force(true);
            }

            // the new log keeps the permissions of the previous one, as a truncated file would. A file system that
            // cannot change them (such as FAT) does not prevent the replacement
            if (Files.exists(target)) {
                try {
                    Files.setPosixFilePermissions(tmp, Files.getPosixFilePermissions(target));
                } catch (UnsupportedOperationException | IOException e) {
                    LOG.debug("Cannot give {} the permissions of {}: {}", tmp, target, e.getMessage());
                }
            }

            // atomically or not at all: a move that is not atomic deletes the log first and may copy the file
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            if (created) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException suppressed) {
                    LOG.debug("Cannot delete {}: {}", tmp, suppressed.getMessage());
                }
            }
            LOG.warn("Cannot replace the transaction log {} atomically ({}): truncating and writing it in place instead."
                     + " A crash before its unconfirmed entries are written again loses them",
                    target, e.toString());
            return false;
        }

        // make the move itself durable, where the file system allows a directory to be forced
        final Path directory = target.toAbsolutePath().getParent();
        if (directory != null) {
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                channel.force(true);
            } catch (IOException e) {
                LOG.debug("Cannot force the directory {} to disk: {}", directory, e.getMessage());
            }
        }
        return true;
    }

    /**
     * The entry information of the entries the new log was started with, in their order
     */
    List<EntryInfo.CachedEntryInfo> getInitialEntryInfos() {
        return initialEntryInfos;
    }

    /**
     * Flushes the data to disk
     *
     * @throws IOException in case of I/O errors
     */
    void flush() throws IOException {
        fileChannel.force(true);
    }

    private void tryFlush() {
        lock.lock();
        try {
            flush();
        } catch (IOException e) {
            LOG.error("Unable to save record: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public void reset() throws IOException {
        lock.lock();
        try {
            fileChannel.truncate(startOfRecords);
            fileChannel.position(startOfRecords);
        } finally {
            lock.unlock();
        }
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
        write(fileChannel, header);

        startOfRecords = fileChannel.position();
    }

    private static void write(FileChannel channel, Header header) throws IOException {
        ByteBuffer headerBuffer = ByteBuffer.allocate(Header.BYTES);

        headerBuffer.put(header.getFormatName().getBytes());
        headerBuffer.putInt(header.getFileVersion());

        IOUtil.write(channel, headerBuffer);
    }

    private static void write(FileChannel channel, LogEntry entry) throws IOException {
        ByteBuffer writeBuffer = ByteBuffer.allocate(LogEntry.size(entry.getKey(), entry.getValue()));
        IOUtil.serialize(writeBuffer, entry);

        IOUtil.write(channel, writeBuffer);
    }

    /**
     * Appends an entry to the transaction log file
     *
     * @param  entry       the entry to write to the transaction log
     * @return             An entry info instance with the metadata for the appended log entry
     * @throws IOException for lower-level I/O errors
     */
    public EntryInfo.CachedEntryInfo append(LogEntry entry) throws IOException {
        // an update of the state writes at the position of the entry, which an append after a roll-over moves
        lock.lock();
        try {
            final TransactionLog.EntryContainer container = transactionLog.add(entry);
            final TransactionLog.LayerInfo layerInfo = container.layerInfo;
            if (layerInfo.getLayer() == 0) {
                return persist(container);
            }

            if (layerInfo.isRollingOver()) {
                reset();
            }

            LOG.trace("Writing at position {}", fileChannel.position());
            EntryInfo.CachedEntryInfo spear = persist(container);

            final List<EntryInfo> collect = transactionLog.stream()
                    .filter(c -> c != null && c.layerInfo.getLayer() != transactionLog.currentLayer())
                    .map(this::tryPersist).collect(Collectors.toList());

            // the entries of the previous layer may be shorter than before in total
            fileChannel.truncate(fileChannel.position());

            if (!collect.isEmpty()) {
                final EntryInfo lastOnLayer = collect.get(0);

                LOG.trace("Current pos is: {}", fileChannel.position());
                LOG.trace("Next pos should be: {}", lastOnLayer.getPosition());

                fileChannel.position(lastOnLayer.getPosition());
                LOG.trace("Current pos now is: {}", fileChannel.position());
            }

            return spear;
        } finally {
            lock.unlock();
        }
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
     * Persists an entry to the log at the current position of the channel, and keeps that position in the container
     *
     * @param  container   the in-memory container of the entry to persist
     * @return             an {@link EntryInfo} instance with details of the entry that was just persisted
     * @throws IOException in case of lower-level I/O errors
     */
    private EntryInfo.CachedEntryInfo persist(TransactionLog.EntryContainer container) throws IOException {
        long recordPosition = fileChannel.position();
        write(fileChannel, container.logEntry);
        container.position = recordPosition;

        return EntryInfo.createForCached(recordPosition, container.layerInfo);
    }

    /**
     * A wrapper for {@link LogWriter#persist(TransactionLog.EntryContainer)} that throws runtime errors on failure
     *
     * @param  container the in-memory container of the entry to persist
     * @return           an {@link EntryInfo} instance with details of the entry that was just persisted
     */
    private EntryInfo tryPersist(TransactionLog.EntryContainer container) {
        try {
            return persist(container);
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

        lock.lock();
        try {
            final TransactionLog.EntryContainer container = transactionLog.update(layerInfo, state);

            if (container != null) {
                if (container.position < 0) {
                    // the entry was never written, so there is nothing to update in the file. Defensive: the layer
                    // information of an entry whose append failed is not given to a caller
                    LOG.warn("Not updating the state of a record that was not written to the transaction log: {}",
                            layerInfo);
                    return;
                }
                // not the position of the entry info: a roll-over may have written the entry again elsewhere
                persist(layerInfo, container.logEntry, container.position);
            }
        } finally {
            lock.unlock();
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

        lock.lock();
        try {
            long size = IOUtil.write(fileChannel, updateBuffer, entryInfo.getPosition());

            if (size == 0) {
                LOG.warn("No bytes written for the given record!");
            }
        } finally {
            lock.unlock();
        }
    }

}
