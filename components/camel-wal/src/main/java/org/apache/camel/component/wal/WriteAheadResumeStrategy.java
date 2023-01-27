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
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
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

    private static final Logger LOG = LoggerFactory.getLogger(WriteAheadResumeStrategy.class);
    private File logFile;
    private LogWriter logWriter;
    private ResumeStrategy resumeStrategy;
    private WriteAheadResumeStrategyConfiguration resumeStrategyConfiguration;
    private CamelContext camelContext;

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

    /**
     * Handles the result of an offset update for persisted entries (i.e.: those already saved to permanent storage)
     *
     * @param entry the information about the entry that was updated
     * @param t     a instance of any throwable class that was thrown by the delegate resume strategy during update, if
     *              none, then can be null
     */
    private void handleResult(PersistedLogEntry entry, Throwable t) {
        try {
            if (t == null) {
                logWriter.updateState(entry, LogEntry.EntryState.PROCESSED);
            } else {
                logWriter.updateState(entry, LogEntry.EntryState.FAILED);
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

    /**
     * Tries to update the offset in the delegate strategy, ensuring the entry on log reflects the success or failure of
     * the update request
     *
     * @param  offsetKey      the offset key to update
     * @param  offsetValue    the offset value to update
     * @param  entry          the entry being updated
     * @param  updateCallBack a callback to be executed after the updated has occurred (null if not available)
     * @throws Exception
     */
    private void tryUpdateDelegate(
            OffsetKey<?> offsetKey, Offset<?> offsetValue, PersistedLogEntry entry, UpdateCallBack updateCallBack)
            throws Exception {
        try {
            UpdateCallBack delegateCallback = resolveUpdateCallBack(entry, updateCallBack);

            resumeStrategy.updateLastOffset(offsetKey, offsetValue, delegateCallback);
        } catch (Throwable throwable) {
            logWriter.updateState(entry, LogEntry.EntryState.FAILED);

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

    private UpdateCallBack resolveUpdateCallBack(PersistedLogEntry entry, UpdateCallBack updateCallBack) {
        if (updateCallBack == null) {
            return t -> handleResult(entry, t);
        } else {
            return new DelegateCallback(updateCallBack, t -> handleResult(entry, t));
        }
    }

    @Override
    public void loadCache() throws Exception {
        LOG.debug("Loading cache for the delegate strategy");
        resumeStrategy.loadCache();
        LOG.debug("Done loading cache for the delegate strategy");

        try (LogReader reader = new LogReader(logFile)) {

            int updatedCount = 0;
            LOG.trace("Starting to read log entries");
            PersistedLogEntry logEntry;
            do {
                logEntry = reader.readEntry();
                if (logEntry != null) {
                    final LogEntry.EntryState entryState = logEntry.getEntryState();
                    if (entryState == LogEntry.EntryState.NEW || entryState == LogEntry.EntryState.FAILED) {
                        final ResumeAdapter adapter = resumeStrategy.getAdapter();

                        if (adapter instanceof Deserializable) {
                            Deserializable deserializable = (Deserializable) adapter;

                            Object oKey = deserializable.deserializeKey(ByteBuffer.wrap(logEntry.getKey()));
                            Object value = deserializable.deserializeValue(ByteBuffer.wrap(logEntry.getValue()));

                            tryUpdateDelegate(OffsetKeys.of(oKey), Offsets.of(value), logEntry, null);
                            updatedCount++;
                        }
                    }
                }

            } while (logEntry != null);
            LOG.trace("Finished reading log entries");

            if (updatedCount == 0) {
                logWriter.reset();
            }
        }
    }

    @Override
    public void start() {
        try {
            this.logFile = resumeStrategyConfiguration.getLogFile();
            this.resumeStrategy = resumeStrategyConfiguration.getDelegateResumeStrategy();

            final ScheduledExecutorService executorService = camelContext.getExecutorServiceManager()
                    .newScheduledThreadPool(this, "SingleNodeKafkaResumeStrategy", 1);

            DefaultLogSupervisor flushPolicy = new DefaultLogSupervisor(
                    resumeStrategyConfiguration.getSupervisorInterval(),
                    executorService);
            logWriter = new LogWriter(logFile, flushPolicy);
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
