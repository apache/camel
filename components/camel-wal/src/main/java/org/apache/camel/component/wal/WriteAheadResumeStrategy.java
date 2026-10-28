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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.wal.exceptions.InvalidRecordException;
import org.apache.camel.resume.Deserializable;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.resume.ResumeStrategyConfiguration;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.resume.OffsetKeys;
import org.apache.camel.support.resume.Offsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A resume strategy that uses a write-ahead strategy to keep a transaction log of the in-processing and processed
 * records. This strategy works by wrapping another strategy. This increases the reliability of the resume API by
 * ensuring that records are saved locally before being sent to the remote data storage used by the resume strategy,
 * thus guaranteeing that records can re recovered in case of crash of that system.
 *
 * Among other things, it implements data recovery on startup, so that records cached locally, are automatically
 * recovered
 */
@JdkService("write-ahead-resume-strategy")
public class WriteAheadResumeStrategy implements ResumeStrategy, CamelContextAware {

    /**
     * An update callback that works for this strategy as well as for the delegate resume strategy that is wrapped in
     * the WriteAheadResumeStrategy
     */
    private static class DelegateCallback implements UpdateCallBack {
        private final UpdateCallBack updateCallBack;
        private final UpdateCallBack flushCallBack;

        public DelegateCallback(UpdateCallBack updateCallBack, UpdateCallBack flushCallBack) {
            this.updateCallBack = updateCallBack;
            this.flushCallBack = flushCallBack;
        }

        @Override
        public void onUpdate(Throwable throwable) {
            flushCallBack.onUpdate(throwable);
            updateCallBack.onUpdate(throwable);
        }
    }

    /**
     * An entry of the previous run that was not confirmed by the delegate resume strategy, and where it is in the new
     * transaction log
     */
    private record UnconfirmedEntry(LogEntry logEntry, EntryInfo.CachedEntryInfo entryInfo) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(WriteAheadResumeStrategy.class);
    private File logFile;
    private LogWriter logWriter;
    private List<UnconfirmedEntry> unconfirmedEntries = List.of();
    private ResumeStrategy resumeStrategy;
    private WriteAheadResumeStrategyConfiguration resumeStrategyConfiguration;
    private CamelContext camelContext;
    // the number of entries the log keeps before it rolls over (visible for testing)
    int maxRecordCount = LogWriter.DEFAULT_CAPACITY;

    /**
     * Creates a new write-ahead resume strategy
     */
    public WriteAheadResumeStrategy() {

    }

    /**
     * Creates a new write-ahead resume strategy
     *
     * @param resumeStrategyConfiguration the configuration to use for this strategy instance
     */
    public WriteAheadResumeStrategy(WriteAheadResumeStrategyConfiguration resumeStrategyConfiguration) {
        this.resumeStrategyConfiguration = resumeStrategyConfiguration;
    }

    @Override
    public void setAdapter(ResumeAdapter adapter) {
        resumeStrategy.setAdapter(adapter);
    }

    @Override
    public ResumeAdapter getAdapter() {
        return resumeStrategy.getAdapter();
    }

    @Override
    public <T extends Resumable> void updateLastOffset(T offset) throws Exception {
        updateLastOffset(offset, null);
    }

    @Override
    public <T extends Resumable> void updateLastOffset(T offset, UpdateCallBack updateCallBack) throws Exception {
        OffsetKey<?> offsetKey = offset.getOffsetKey();
        Offset<?> offsetValue = offset.getLastOffset();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Updating offset on Kafka with key {} to {}", offsetKey.getValue(), offsetValue.getValue());
        }

        updateLastOffset(offsetKey, offsetValue, updateCallBack);
    }

    /**
     * Handles the result of an offset update for cached entries (i.e.: those kept on the in-memory transaction log)
     *
     * @param entryInfo the information about the entry that was updated
     * @param t         a instance of any throwable class that was thrown by the delegate resume strategy during update,
     *                  if none, then can be null
     */
    private void handleResult(EntryInfo.CachedEntryInfo entryInfo, Throwable t) {
        try {
            if (t == null) {
                logWriter.updateState(entryInfo, LogEntry.EntryState.PROCESSED);
            } else {
                logWriter.updateState(entryInfo, LogEntry.EntryState.FAILED);
            }
        } catch (IOException e) {
            if (t == null) {
                LOG.error("Unable to update state: {}", e.getMessage(), e);
            } else {
                LOG.error("Unable to mark the record as failed: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offsetValue) throws Exception {
        updateLastOffset(offsetKey, offsetValue, null);
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offsetValue, UpdateCallBack updateCallBack)
            throws Exception {
        ByteBuffer keyBuffer = offsetKey.serialize();
        ByteBuffer valueBuffer = offsetValue.serialize();

        EntryInfo.CachedEntryInfo entryInfo;
        try {
            LogEntry entry = new LogEntry(
                    LogEntry.EntryState.NEW, 0,
                    keyBuffer.array(), 0, valueBuffer.array());

            entryInfo = logWriter.append(entry);
        } catch (IOException e) {
            LOG.error("Unable to append a new record to the transaction log. The system will try to update the record " +
                      "on the delegate strategy before forcing the failure");

            tryUpdateDelegate(offsetKey, offsetValue, (EntryInfo.CachedEntryInfo) null, updateCallBack);
            throw e;
        }

        tryUpdateDelegate(offsetKey, offsetValue, entryInfo, updateCallBack);
    }

    /**
     * Tries to update the offset in the delegate strategy, ensuring the entry on log reflects the success or failure of
     * the update request
     *
     * @param  offsetKey      the offset key to update
     * @param  offsetValue    the offset value to update
     * @param  entryInfo      the information about the entry being updated
     * @param  updateCallBack a callback to be executed after the updated has occurred (null if not available)
     * @throws Exception
     */
    private void tryUpdateDelegate(
            OffsetKey<?> offsetKey, Offset<?> offsetValue, EntryInfo.CachedEntryInfo entryInfo, UpdateCallBack updateCallBack)
            throws Exception {
        try {
            UpdateCallBack delegateCallback = resolveUpdateCallBack(entryInfo, updateCallBack);

            resumeStrategy.updateLastOffset(offsetKey, offsetValue, delegateCallback);
        } catch (Throwable throwable) {
            if (entryInfo != null) {
                logWriter.updateState(entryInfo, LogEntry.EntryState.FAILED);
            } else {
                LOG.warn("Not updating the state on the transaction log before there's no entry information: it's likely " +
                         "that a previous attempt to append the record has failed and the system is now in error");
            }

            throw throwable;
        }
    }

    private UpdateCallBack resolveUpdateCallBack(EntryInfo.CachedEntryInfo entryInfo, UpdateCallBack updateCallBack) {
        if (updateCallBack == null) {
            return t -> handleResult(entryInfo, t);
        } else {
            return new DelegateCallback(updateCallBack, t -> handleResult(entryInfo, t));
        }
    }

    @Override
    public void loadCache() throws Exception {
        LOG.debug("Loading cache for the delegate strategy");
        resumeStrategy.loadCache();
        LOG.debug("Done loading cache for the delegate strategy");

        final List<UnconfirmedEntry> entries = unconfirmedEntries;
        unconfirmedEntries = List.of();
        if (entries.isEmpty()) {
            return;
        }

        final ResumeAdapter adapter = resumeStrategy.getAdapter();
        if (!(adapter instanceof Deserializable deserializable)) {
            LOG.warn("Not replaying the {} unconfirmed entries of the transaction log {}: the resume adapter {} cannot "
                     + "deserialize them. They are kept in the log",
                    entries.size(), logFile, adapter);
            return;
        }

        LOG.info("Replaying {} unconfirmed entries of the transaction log {}", entries.size(), logFile);
        for (UnconfirmedEntry entry : entries) {
            // as for a new offset, a failure is logged and does not stop the others (nor the start of the route)
            try {
                replay(deserializable, entry);
            } catch (Exception e) {
                LOG.warn("Unable to replay an unconfirmed entry of the transaction log {}: it is kept in the log and "
                         + "replayed again on the next start. Reason: {}",
                        logFile, e.toString(), e);
            }
        }
        LOG.trace("Finished replaying the unconfirmed entries");
    }

    private void replay(Deserializable deserializable, UnconfirmedEntry entry) throws Exception {
        Object oKey = deserializable.deserializeKey(ByteBuffer.wrap(entry.logEntry().getKey()));
        Object value = deserializable.deserializeValue(ByteBuffer.wrap(entry.logEntry().getValue()));
        if (oKey == null || value == null) {
            LOG.warn("Not replaying an unconfirmed entry of the transaction log {}: the resume adapter cannot "
                     + "deserialize its key or value. It is kept in the log",
                    logFile);
            return;
        }

        tryUpdateDelegate(OffsetKeys.of(oKey), Offsets.of(value), entry.entryInfo(), null);
    }

    /**
     * Reads the entries that were not confirmed by the delegate resume strategy from the transaction log of the
     * previous run. As the delegate keeps the last offset of each key, only the last entry of each key in the log
     * counts: it is unconfirmed if it is in state NEW or FAILED. An earlier entry of the same key is superseded, so a
     * FAILED offset does not replace a later one that the delegate confirmed. The log ends where a record is incomplete
     * or invalid (i.e.: the last one, when the system crashed while writing it): the entries before it are kept.
     * <p/>
     * A log that holds as many entries as it keeps before it rolls over may have rolled over. Then the entries of the
     * newer layer come first in the file, and the file has no layer information, so the last entry of a key in the file
     * may be an older offset of the key. Such a log is not replayed: replaying an older offset would make the consumer
     * resume from it. This does not cover a log that rolled over and was cut short while an append rewrote it (the
     * system crashed then): it holds fewer entries, the newer ones first.
     *
     * @param  logFile        the transaction log file, which may not exist
     * @param  maxRecordCount the number of entries the log keeps before it rolls over
     * @return                the unconfirmed entries, in the order of the log
     */
    private static List<LogEntry> readUnconfirmedEntries(File logFile, int maxRecordCount) {
        final Map<ByteBuffer, LogEntry> lastEntries = new LinkedHashMap<>();
        if (!logFile.isFile()) {
            return List.of();
        }

        int count = 0;

        try (LogReader reader = new LogReader(logFile)) {
            final Header header = reader.getHeader();
            if (header == null) {
                return List.of();
            }

            if (!Header.FORMAT_NAME.equals(header.getFormatName().trim())
                    || header.getFileVersion() != Header.CURRENT_FILE_VERSION) {
                LOG.warn("Not replaying the transaction log {}: it has an unknown format '{}' version {}, and a new log "
                         + "replaces it",
                        logFile, header.getFormatName().trim(), header.getFileVersion());
                return List.of();
            }

            PersistedLogEntry logEntry;
            while ((logEntry = reader.readEntry()) != null) {
                count++;
                final ByteBuffer key = ByteBuffer.wrap(logEntry.getKey());
                // keep the keys in the order of their last entry
                lastEntries.remove(key);
                lastEntries.put(key, logEntry);
            }
        } catch (IOException | InvalidRecordException | IllegalArgumentException e) {
            LOG.warn("Stopped reading the transaction log {} at an incomplete or invalid record, the rest of the log is "
                     + "skipped: the {} entries before it are read. Reason: {}",
                    logFile, count, e.toString());
        }

        final List<LogEntry> unconfirmed = unconfirmed(lastEntries.values());
        if (count >= maxRecordCount && !unconfirmed.isEmpty()) {
            LOG.warn("Not replaying the {} unconfirmed entries of the transaction log {}: it holds {} entries, so it may "
                     + "have rolled over, and then the order of its entries is not the order of the offsets. A new log "
                     + "replaces it, so these entries are discarded",
                    unconfirmed.size(), logFile, count);
            return List.of();
        }
        return unconfirmed;
    }

    private static List<LogEntry> unconfirmed(Collection<LogEntry> entries) {
        final List<LogEntry> unconfirmed = new ArrayList<>();
        for (LogEntry entry : entries) {
            final LogEntry.EntryState entryState = entry.getEntryState();
            if (entryState == LogEntry.EntryState.NEW || entryState == LogEntry.EntryState.FAILED) {
                unconfirmed.add(new LogEntry(
                        entryState, entry.getKeyMetadata(), entry.getKey(), entry.getValueMetadata(), entry.getValue()));
            }
        }
        return unconfirmed;
    }

    @Override
    public void start() {
        try {
            this.logFile = resumeStrategyConfiguration.getLogFile();
            this.resumeStrategy = resumeStrategyConfiguration.getDelegateResumeStrategy();

            // the writer starts a new log, so read the entries of the previous run first
            final List<LogEntry> entries = readUnconfirmedEntries(logFile, maxRecordCount);

            final ScheduledExecutorService executorService = camelContext.getExecutorServiceManager()
                    .newScheduledThreadPool(this, "SingleNodeKafkaResumeStrategy", 1);

            DefaultLogSupervisor flushPolicy = new DefaultLogSupervisor(
                    resumeStrategyConfiguration.getSupervisorInterval(),
                    executorService);
            logWriter = new LogWriter(logFile, flushPolicy, maxRecordCount);

            // keep them in the new log until loadCache() replays them, in case the system crashes again before
            final List<UnconfirmedEntry> carried = new ArrayList<>(entries.size());
            for (LogEntry entry : entries) {
                carried.add(new UnconfirmedEntry(entry, logWriter.append(entry)));
            }
            unconfirmedEntries = carried;
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }

        resumeStrategy.start();
    }

    @Override
    public void stop() {
        LOG.trace("Stopping the delegate strategy");
        resumeStrategy.stop();
        LOG.trace("Done stopping the delegate strategy");

        LOG.trace("Closing the writer");
        logWriter.close();
        LOG.trace("Writer is closed");
    }

    @Override
    public void setResumeStrategyConfiguration(ResumeStrategyConfiguration resumeStrategyConfiguration) {
        this.resumeStrategyConfiguration = (WriteAheadResumeStrategyConfiguration) resumeStrategyConfiguration;
    }

    @Override
    public ResumeStrategyConfiguration getResumeStrategyConfiguration() {
        return resumeStrategyConfiguration;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}
