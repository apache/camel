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
import java.io.RandomAccessFile;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import org.apache.camel.CamelContext;
import org.apache.camel.resume.Deserializable;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.resume.ResumeStrategy.UpdateCallBack;
import org.apache.camel.resume.ResumeStrategyConfiguration;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.support.resume.OffsetKeys;
import org.apache.camel.support.resume.Offsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The entries that the delegate strategy did not confirm before the system stopped are replayed on the next start, in
 * the order Camel calls the strategy: start() (from ResumableProcessor.doStart) and then loadCache() (from the
 * consumer).
 */
class WriteAheadResumeStrategyRestartTest {

    /**
     * The size of a record with a key "key-N" (one digit) and a long value
     */
    private static final int RECORD_SIZE = 41;

    @TempDir
    File testDir;

    private final List<WriteAheadResumeStrategy> running = new ArrayList<>();

    static class Adapter implements ResumeAdapter, Deserializable {
        @Override
        public void resume() {
        }

        @Override
        public boolean deserialize(ByteBuffer keyBuffer, ByteBuffer valueBuffer) {
            return true;
        }
    }

    /**
     * A delegate strategy that records the replayed offsets and confirms them, except for the keys it fails
     */
    static class Delegate implements ResumeStrategy {
        final List<String> replayed = new CopyOnWriteArrayList<>();
        final Set<String> failingKeys;
        final Adapter adapter = new Adapter();

        Delegate(String... failingKeys) {
            this.failingKeys = Set.of(failingKeys);
        }

        @Override
        public void setAdapter(ResumeAdapter adapter) {
        }

        @Override
        public ResumeAdapter getAdapter() {
            return adapter;
        }

        @Override
        public <T extends Resumable> void updateLastOffset(T offset) {
        }

        @Override
        public <T extends Resumable> void updateLastOffset(T offset, UpdateCallBack updateCallBack) {
        }

        @Override
        public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offsetValue) {
            updateLastOffset(offsetKey, offsetValue, null);
        }

        @Override
        public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset, UpdateCallBack updateCallBack) {
            replayed.add(offsetKey.getValue() + "=" + offset.getValue());
            if (updateCallBack != null) {
                updateCallBack.onUpdate(failingKeys.contains(offsetKey.getValue())
                        ? new IllegalStateException("the delegate failed") : null);
            }
        }

        @Override
        public void setResumeStrategyConfiguration(ResumeStrategyConfiguration resumeStrategyConfiguration) {
        }

        @Override
        public ResumeStrategyConfiguration getResumeStrategyConfiguration() {
            return null;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    }

    /**
     * A delegate strategy that never calls back, so the entries stay unconfirmed (as with the MongoDB strategy)
     */
    static class NeverConfirmingDelegate extends Delegate {
        @Override
        public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset, UpdateCallBack updateCallBack) {
            replayed.add(offsetKey.getValue() + "=" + offset.getValue());
        }
    }

    /**
     * A delegate strategy that keeps the callbacks of some keys without calling them (as a delegate that confirms
     * later, on another thread), and confirms the others
     */
    static class LateConfirmingDelegate extends Delegate {
        final Set<String> lateKeys;
        final List<UpdateCallBack> callbacks = new CopyOnWriteArrayList<>();

        LateConfirmingDelegate(String... lateKeys) {
            this.lateKeys = Set.of(lateKeys);
        }

        @Override
        public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset, UpdateCallBack updateCallBack) {
            if (lateKeys.contains(offsetKey.getValue())) {
                replayed.add(offsetKey.getValue() + "=" + offset.getValue());
                callbacks.add(updateCallBack);
            } else {
                super.updateLastOffset(offsetKey, offset, updateCallBack);
            }
        }
    }

    /**
     * A delegate strategy that throws for one key (as when its storage is not available)
     */
    static class ThrowingDelegate extends Delegate {
        private final String throwingKey;

        ThrowingDelegate(String throwingKey) {
            this.throwingKey = throwingKey;
        }

        @Override
        public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset, UpdateCallBack updateCallBack) {
            if (throwingKey.equals(offsetKey.getValue())) {
                replayed.add(offsetKey.getValue() + "=" + offset.getValue());
                throw new IllegalStateException("the delegate is not available");
            }
            super.updateLastOffset(offsetKey, offset, updateCallBack);
        }
    }

    @AfterEach
    void stopRunningStrategies() {
        for (WriteAheadResumeStrategy strategy : List.copyOf(running)) {
            stop(strategy);
        }
    }

    private static LogEntry entry(LogEntry.EntryState state, String key, long value) {
        return new LogEntry(
                state, 0, OffsetKeys.of(key).serialize().array(), 0, Offsets.of(value).serialize().array());
    }

    /**
     * Writes a log as a previous run would leave it
     */
    private File writeLog(LogEntry... entries) throws IOException {
        return writeLog(LogWriter.DEFAULT_CAPACITY, entries);
    }

    /**
     * Writes a log as a previous run would leave it, with a log that keeps the given number of entries before it rolls
     * over
     */
    private File writeLog(int maxRecordCount, LogEntry... entries) throws IOException {
        File logFile = new File(testDir, "wal.data");
        try (LogWriter writer = new LogWriter(logFile, new DefaultLogSupervisor(100_000), maxRecordCount)) {
            for (LogEntry entry : entries) {
                writer.append(entry);
            }
        }
        return logFile;
    }

    private File writeNewEntries(int count) throws IOException {
        LogEntry[] entries = new LogEntry[count];
        for (int i = 0; i < count; i++) {
            entries[i] = entry(LogEntry.EntryState.NEW, "key-" + i, i);
        }
        return writeLog(entries);
    }

    private WriteAheadResumeStrategy start(File logFile, ResumeStrategy delegate) {
        return start(logFile, delegate, LogWriter.DEFAULT_CAPACITY);
    }

    private WriteAheadResumeStrategy start(File logFile, ResumeStrategy delegate, int maxRecordCount) {
        WriteAheadResumeStrategy strategy = new WriteAheadResumeStrategy(
                WriteAheadResumeStrategyConfigurationBuilder.newBuilder()
                        .withLogFile(logFile).withDelegateResumeStrategy(delegate).build());
        strategy.maxRecordCount = maxRecordCount;

        ExecutorServiceManager executorServiceManager = (ExecutorServiceManager) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { ExecutorServiceManager.class },
                (proxy, method, args) -> "newScheduledThreadPool".equals(method.getName())
                        ? Executors.newSingleThreadScheduledExecutor() : null);
        CamelContext camelContext = (CamelContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { CamelContext.class },
                (proxy, method, args) -> "getExecutorServiceManager".equals(method.getName())
                        ? executorServiceManager : null);
        strategy.setCamelContext(camelContext);

        strategy.start();
        running.add(strategy);
        return strategy;
    }

    private WriteAheadResumeStrategy restart(File logFile, ResumeStrategy delegate) throws Exception {
        WriteAheadResumeStrategy strategy = start(logFile, delegate);
        strategy.loadCache();
        return strategy;
    }

    private void stop(WriteAheadResumeStrategy strategy) {
        running.remove(strategy);
        strategy.stop();
    }

    /**
     * Reads the log, as "key=STATE" for each entry
     */
    private static List<String> readLog(File logFile) throws IOException {
        Adapter adapter = new Adapter();
        List<String> entries = new ArrayList<>();
        try (LogReader reader = new LogReader(logFile)) {
            assertNotNull(reader.getHeader(), "the log has a header");
            assertEquals(Header.FORMAT_NAME, reader.getHeader().getFormatName().trim());
            assertEquals(Header.CURRENT_FILE_VERSION, reader.getHeader().getFileVersion());

            PersistedLogEntry entry;
            while ((entry = reader.readEntry()) != null) {
                entries.add(adapter.deserializeKey(ByteBuffer.wrap(entry.getKey())) + "=" + entry.getEntryState());
            }
        }
        return entries;
    }

    private static List<LogEntry.EntryState> readStates(File logFile) throws IOException {
        List<LogEntry.EntryState> states = new ArrayList<>();
        try (LogReader reader = new LogReader(logFile)) {
            PersistedLogEntry entry;
            while ((entry = reader.readEntry()) != null) {
                states.add(entry.getEntryState());
            }
        }
        return states;
    }

    private static void writeIntAt(File file, long position, int value) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(position);
            raf.writeInt(value);
        }
    }

    private static void truncate(File file, long length) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(length);
        }
    }

    @Test
    void restartReplaysTheNewAndFailedEntriesButNotTheProcessedOnes() throws Exception {
        File logFile = writeLog(
                entry(LogEntry.EntryState.NEW, "key-0", 0),
                entry(LogEntry.EntryState.PROCESSED, "key-1", 1),
                entry(LogEntry.EntryState.FAILED, "key-2", 2),
                entry(LogEntry.EntryState.NEW, "key-3", 3));

        Delegate delegate = new Delegate();
        stop(restart(logFile, delegate));

        assertEquals(List.of("key-0=0", "key-2=2", "key-3=3"), delegate.replayed);
        assertEquals(List.of("key-0=PROCESSED", "key-2=PROCESSED", "key-3=PROCESSED"), readLog(logFile));

        Delegate next = new Delegate();
        stop(restart(logFile, next));
        assertEquals(List.of(), next.replayed, "the confirmed entries are not replayed again");
    }

    @Test
    void onlyTheLastOffsetOfEachKeyIsReplayed() throws Exception {
        File logFile = writeLog(
                entry(LogEntry.EntryState.FAILED, "key-0", 0),
                entry(LogEntry.EntryState.PROCESSED, "key-0", 1),
                entry(LogEntry.EntryState.NEW, "key-1", 2),
                entry(LogEntry.EntryState.NEW, "key-1", 3),
                entry(LogEntry.EntryState.PROCESSED, "key-2", 4),
                entry(LogEntry.EntryState.FAILED, "key-2", 5));

        Delegate delegate = new Delegate();
        stop(restart(logFile, delegate));

        // a failed offset does not replace a later offset of its key that the delegate confirmed
        assertEquals(List.of("key-1=3", "key-2=5"), delegate.replayed);
        assertEquals(List.of("key-1=PROCESSED", "key-2=PROCESSED"), readLog(logFile));
    }

    @Test
    void entriesOfADelegateThatNeverConfirmsDoNotPileUpAcrossRestarts() throws Exception {
        File logFile = new File(testDir, "wal.data");
        WriteAheadResumeStrategy strategy = restart(logFile, new NeverConfirmingDelegate());
        strategy.updateLastOffset(OffsetKeys.of("key-0"), Offsets.of(0L));
        strategy.updateLastOffset(OffsetKeys.of("key-0"), Offsets.of(1L));
        strategy.updateLastOffset(OffsetKeys.of("key-1"), Offsets.of(2L));
        stop(strategy);

        for (int i = 0; i < 2; i++) {
            Delegate delegate = new NeverConfirmingDelegate();
            stop(restart(logFile, delegate));

            assertEquals(List.of("key-0=1", "key-1=2"), delegate.replayed);
            assertEquals(List.of("key-0=NEW", "key-1=NEW"), readLog(logFile));
        }
    }

    @Test
    void entryThatCannotBeReplayedDoesNotStopTheOthersNorTheStart() throws Exception {
        byte[] value = Offsets.of(7L).serialize().array();
        File logFile = writeLog(
                // a key too short for its type, and a key of an unknown type
                new LogEntry(LogEntry.EntryState.NEW, 0, new byte[] { 1, 2 }, 0, value),
                new LogEntry(LogEntry.EntryState.NEW, 0, ByteBuffer.allocate(8).putInt(99).array(), 0, value),
                entry(LogEntry.EntryState.NEW, "key-0", 0),
                entry(LogEntry.EntryState.NEW, "key-1", 1));

        Delegate delegate = new ThrowingDelegate("key-0");
        stop(restart(logFile, delegate));

        assertEquals(List.of("key-0=0", "key-1=1"), delegate.replayed);
        assertEquals(List.of(LogEntry.EntryState.NEW, LogEntry.EntryState.NEW, LogEntry.EntryState.FAILED,
                LogEntry.EntryState.PROCESSED), readStates(logFile));

        Delegate next = new Delegate();
        stop(restart(logFile, next));
        assertEquals(List.of("key-0=0"), next.replayed);
        assertEquals(List.of(LogEntry.EntryState.NEW, LogEntry.EntryState.NEW, LogEntry.EntryState.PROCESSED),
                readStates(logFile));
    }

    @Test
    void entryWhoseReplayFailsIsReplayedOnTheNextStart() throws Exception {
        File logFile = writeNewEntries(2);

        Delegate delegate = new Delegate("key-0");
        stop(restart(logFile, delegate));

        assertEquals(List.of("key-0=0", "key-1=1"), delegate.replayed);
        assertEquals(List.of("key-0=FAILED", "key-1=PROCESSED"), readLog(logFile));

        Delegate next = new Delegate();
        stop(restart(logFile, next));
        assertEquals(List.of("key-0=0"), next.replayed);
    }

    @Test
    void unconfirmedEntriesStayInTheLogUntilTheyAreReplayed() throws Exception {
        File logFile = writeNewEntries(2);

        // stopped (or crashed) after start() but before loadCache() replayed the entries
        Delegate notReplayed = new Delegate();
        stop(start(logFile, notReplayed));
        assertEquals(List.of(), notReplayed.replayed);
        assertEquals(List.of("key-0=NEW", "key-1=NEW"), readLog(logFile));

        Delegate delegate = new Delegate();
        stop(restart(logFile, delegate));
        assertEquals(List.of("key-0=0", "key-1=1"), delegate.replayed);
    }

    @Test
    void callbackOfThePreviousStartDoesNotUpdateAnEntryOfTheNewLog() throws Exception {
        File logFile = new File(testDir, "wal.data");
        LateConfirmingDelegate delegate = new LateConfirmingDelegate("key-1");
        WriteAheadResumeStrategy strategy = restart(logFile, delegate);
        strategy.updateLastOffset(OffsetKeys.of("key-0"), Offsets.of(0L));
        strategy.updateLastOffset(OffsetKeys.of("key-1"), Offsets.of(1L));
        UpdateCallBack late = delegate.callbacks.get(0);

        // the route is stopped and started again (the same strategy) while the callback of key-1 is pending: key-1 is
        // carried into the new log, and a new offset of key-2 is confirmed there, where key-1 was in the old log
        strategy.stop();
        strategy.start();
        strategy.loadCache();
        strategy.updateLastOffset(OffsetKeys.of("key-2"), Offsets.of(2L));

        // the callback of the previous start reports late
        late.onUpdate(new IllegalStateException("the delegate failed late"));
        stop(strategy);

        assertEquals(List.of("key-1=NEW", "key-2=PROCESSED"), readLog(logFile));
    }

    @Test
    void logThatMayHaveRolledOverIsNotReplayed() throws Exception {
        // the log keeps 3 entries: the fourth offset rolled it over, so the file holds 4, then the older 2 and 3
        File logFile = writeLog(3,
                entry(LogEntry.EntryState.NEW, "key-0", 1),
                entry(LogEntry.EntryState.NEW, "key-0", 2),
                entry(LogEntry.EntryState.NEW, "key-0", 3),
                entry(LogEntry.EntryState.NEW, "key-0", 4));

        Delegate delegate = new NeverConfirmingDelegate();
        WriteAheadResumeStrategy strategy = start(logFile, delegate, 3);
        strategy.loadCache();
        stop(strategy);

        // the last entry of the key in the file is the older offset 3: replaying it would resume before 4
        assertEquals(List.of(), delegate.replayed);
        assertEquals(List.of(), readLog(logFile), "a new log replaces the previous one");
    }

    @Test
    void logThatHasNotRolledOverIsReplayed() throws Exception {
        File logFile = writeLog(3,
                entry(LogEntry.EntryState.NEW, "key-0", 1),
                entry(LogEntry.EntryState.NEW, "key-0", 2));

        Delegate delegate = new NeverConfirmingDelegate();
        WriteAheadResumeStrategy strategy = start(logFile, delegate, 3);
        strategy.loadCache();
        stop(strategy);

        assertEquals(List.of("key-0=2"), delegate.replayed);
        assertEquals(List.of("key-0=NEW"), readLog(logFile));
    }

    @ParameterizedTest
    @ValueSource(ints = { 3, 6, 16, 27, 40 })
    void truncatedLastRecordIsSkippedAndThePreviousOnesAreReplayed(int bytesLeftOfTheLastRecord) throws Exception {
        File logFile = writeNewEntries(3);
        truncate(logFile, Header.BYTES + 2 * RECORD_SIZE + bytesLeftOfTheLastRecord);

        Delegate delegate = new Delegate();
        stop(restart(logFile, delegate));

        assertEquals(List.of("key-0=0", "key-1=1"), delegate.replayed);
        assertEquals(List.of("key-0=PROCESSED", "key-1=PROCESSED"), readLog(logFile), "the new log is complete");
    }

    @Test
    void truncatedLastRecordAcrossTheReadBufferIsSkipped() throws Exception {
        // the last record starts in the first read of the reader's buffer, and the file ends 2 bytes after it
        File logFile = new File(testDir, "wal.data");
        int count = 0;
        try (LogWriter writer = new LogWriter(logFile, new DefaultLogSupervisor(100_000))) {
            long end;
            do {
                LogEntry entry = entry(LogEntry.EntryState.NEW, "key-" + count, count);
                end = writer.append(entry).getPosition() + entry.size();
                count++;
            } while (end <= LogReader.DEFAULT_CAPACITY + 2);
        }
        truncate(logFile, LogReader.DEFAULT_CAPACITY + 2);

        Delegate delegate = new Delegate();
        stop(restart(logFile, delegate));

        assertEquals(count - 1, delegate.replayed.size());
        assertEquals("key-" + (count - 2) + "=" + (count - 2), delegate.replayed.get(count - 2));
    }

    @ParameterizedTest
    @CsvSource({
            // the position in the last record, the value written there
            "8, -5", // key length
            "25, -1", // value length
            "8, 2147483647", // key length, larger than the reader's buffer
            "8, 1000", // key length, beyond the end of the file
            "0, 99" // state
    })
    void invalidLastRecordDoesNotFailTheStart(int positionInRecord, int value) throws Exception {
        File logFile = writeNewEntries(3);
        writeIntAt(logFile, Header.BYTES + 2 * RECORD_SIZE + positionInRecord, value);

        Delegate delegate = new Delegate();
        stop(restart(logFile, delegate));

        assertEquals(List.of("key-0=0", "key-1=1"), delegate.replayed);
        assertEquals(List.of("key-0=PROCESSED", "key-1=PROCESSED"), readLog(logFile));
    }

    @ParameterizedTest
    @ValueSource(strings = { "no file", "empty", "header only", "shorter than a header", "other format", "other version" })
    void startsWithNothingToReplay(String content) throws Exception {
        File logFile = new File(testDir, "wal.data");
        switch (content) {
            case "no file":
                break;
            case "empty":
                assertTrue(logFile.createNewFile());
                break;
            case "header only":
                writeLog();
                break;
            case "shorter than a header":
                Files.write(logFile.toPath(), new byte[] { 'c', 'a', 'm', 'e', 'l' });
                break;
            case "other format":
                writeNewEntries(1);
                Files.write(logFile.toPath(), "other-fm".getBytes(), StandardOpenOption.WRITE);
                break;
            default:
                writeNewEntries(1);
                writeIntAt(logFile, Header.FORMAT_NAME_SIZE, Header.CURRENT_FILE_VERSION + 1);
                break;
        }

        Delegate delegate = new Delegate();
        WriteAheadResumeStrategy strategy = restart(logFile, delegate);
        strategy.updateLastOffset(OffsetKeys.of("key-9"), Offsets.of(9L));
        stop(strategy);

        assertEquals(List.of("key-9=9"), delegate.replayed, "only the offset of this run is updated");
        assertEquals(List.of("key-9=PROCESSED"), readLog(logFile), "a new log replaces the previous one");
    }
}
