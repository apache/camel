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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.support.resume.OffsetKeys;
import org.apache.camel.support.resume.Offsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * start() replaces the log of the previous run by a new log that holds the unconfirmed entries of the previous run. A
 * start that fails (or a system that crashes) while it does so must not lose these entries: the next start replays
 * them.
 */
class WriteAheadResumeStrategyStartFailureTest {

    @TempDir
    File testDir;

    private final List<ScheduledExecutorService> executors = new ArrayList<>();

    @AfterEach
    void shutdownExecutors() {
        executors.forEach(ScheduledExecutorService::shutdownNow);
    }

    /**
     * A strategy whose flush task cannot be scheduled when rejectFlushTask is true (as when the executor is shut down):
     * then start() fails after it read the log of the previous run
     */
    private WriteAheadResumeStrategy strategy(File logFile, ResumeStrategy delegate, boolean rejectFlushTask) {
        WriteAheadResumeStrategy strategy = new WriteAheadResumeStrategy(
                WriteAheadResumeStrategyConfigurationBuilder.newBuilder()
                        .withLogFile(logFile).withDelegateResumeStrategy(delegate).build(),
                LogWriter.DEFAULT_CAPACITY);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executors.add(executor);
        ScheduledExecutorService pool = (ScheduledExecutorService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { ScheduledExecutorService.class },
                (proxy, method, args) -> {
                    if (rejectFlushTask && "scheduleAtFixedRate".equals(method.getName())) {
                        throw new RejectedExecutionException("the executor is shut down");
                    }
                    try {
                        return method.invoke(executor, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        ExecutorServiceManager executorServiceManager = (ExecutorServiceManager) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { ExecutorServiceManager.class },
                (proxy, method, args) -> "newScheduledThreadPool".equals(method.getName()) ? pool : null);
        CamelContext camelContext = (CamelContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { CamelContext.class },
                (proxy, method, args) -> "getExecutorServiceManager".equals(method.getName())
                        ? executorServiceManager : null);
        strategy.setCamelContext(camelContext);
        return strategy;
    }

    /**
     * A run that appends the offset 1 of the key A, which the delegate never confirms, and stops
     */
    private File runWithAnUnconfirmedEntry() throws Exception {
        return runWithAnUnconfirmedEntry(new File(testDir, "wal.data"));
    }

    private File runWithAnUnconfirmedEntry(File logFile) throws Exception {
        WriteAheadResumeStrategy strategy
                = strategy(logFile, new WriteAheadResumeStrategyRestartTest.NeverConfirmingDelegate(), false);
        strategy.start();
        strategy.loadCache();
        strategy.updateLastOffset(OffsetKeys.of("A"), Offsets.of(1L));
        strategy.stop();
        assertEquals(List.of("A=1 NEW"), readLog(logFile));
        return logFile;
    }

    private List<String> restart(File logFile, WriteAheadResumeStrategyRestartTest.Delegate delegate) throws Exception {
        WriteAheadResumeStrategy strategy = strategy(logFile, delegate, false);
        strategy.start();
        strategy.loadCache();
        // the new log takes new entries after the replayed one
        strategy.updateLastOffset(OffsetKeys.of("B"), Offsets.of(1L));
        strategy.stop();
        return delegate.replayed;
    }

    /**
     * Reads the log, as "key=value STATE" for each entry
     */
    private static List<String> readLog(File logFile) throws IOException {
        WriteAheadResumeStrategyRestartTest.Adapter adapter = new WriteAheadResumeStrategyRestartTest.Adapter();
        List<String> entries = new ArrayList<>();
        try (LogReader reader = new LogReader(logFile)) {
            PersistedLogEntry entry;
            while ((entry = reader.readEntry()) != null) {
                entries.add(adapter.deserializeKey(ByteBuffer.wrap(entry.getKey())) + "="
                            + adapter.deserializeValue(ByteBuffer.wrap(entry.getValue())) + " "
                            + entry.getEntryState());
            }
        }
        return entries;
    }

    @Test
    void unconfirmedEntryIsReplayedAfterTheNextStart() throws Exception {
        File logFile = runWithAnUnconfirmedEntry();

        WriteAheadResumeStrategyRestartTest.Delegate delegate = new WriteAheadResumeStrategyRestartTest.Delegate();
        assertEquals(List.of("A=1", "B=1"), restart(logFile, delegate));

        // the delegate confirmed both in the log that replaced the previous one
        assertEquals(List.of("A=1 PROCESSED", "B=1 PROCESSED"), readLog(logFile));
        assertFalse(new File(testDir, "wal.data.tmp").exists(), "no temporary file is left");
    }

    @Test
    void unconfirmedEntryIsReplayedAfterAStartThatFailedAfterReadingIt() throws Exception {
        File logFile = runWithAnUnconfirmedEntry();

        // start() reads A=1 as unconfirmed, then fails before the new log is supervised
        WriteAheadResumeStrategy failing
                = strategy(logFile, new WriteAheadResumeStrategyRestartTest.NeverConfirmingDelegate(), true);
        assertThrows(RuntimeCamelException.class, failing::start);
        assertEquals(List.of("A=1 NEW"), readLog(logFile), "the log still holds the unconfirmed entry");

        WriteAheadResumeStrategyRestartTest.Delegate delegate = new WriteAheadResumeStrategyRestartTest.Delegate();
        assertEquals(List.of("A=1", "B=1"), restart(logFile, delegate));
        assertEquals(List.of("A=1 PROCESSED", "B=1 PROCESSED"), readLog(logFile));
    }

    @Test
    void logIsRewrittenInPlaceWhenTheNewLogCannotReplaceIt() throws Exception {
        File logFile = runWithAnUnconfirmedEntry();

        // the temporary file of the new log cannot be created, so the log cannot be replaced atomically: start()
        // rewrites it in place, as before, instead of failing
        File blocked = new File(testDir, "wal.data.tmp");
        Files.createDirectory(blocked.toPath());
        WriteAheadResumeStrategy strategy
                = strategy(logFile, new WriteAheadResumeStrategyRestartTest.NeverConfirmingDelegate(), false);
        strategy.start();
        assertEquals(List.of("A=1 NEW"), readLog(logFile), "the rewritten log holds the unconfirmed entry");
        strategy.stop();
        assertTrue(blocked.isDirectory(), "what has the name of the temporary file is not deleted");

        WriteAheadResumeStrategyRestartTest.Delegate delegate = new WriteAheadResumeStrategyRestartTest.Delegate();
        assertEquals(List.of("A=1", "B=1"), restart(logFile, delegate));
        assertEquals(List.of("A=1 PROCESSED", "B=1 PROCESSED"), readLog(logFile));
    }

    @Test
    void previousLogIsKeptWhenNeitherTheNewLogNorTheLogCanBeWritten() throws Exception {
        File logFile = runWithAnUnconfirmedEntry();

        // the temporary file cannot be created, and the log cannot be rewritten in place either: start() fails
        File blocked = new File(testDir, "wal.data.tmp");
        Files.createDirectory(blocked.toPath());
        assertTrue(logFile.setWritable(false));
        assumeFalse(Files.isWritable(logFile.toPath()), "the process can write to a read-only file (root)");
        WriteAheadResumeStrategy failing
                = strategy(logFile, new WriteAheadResumeStrategyRestartTest.NeverConfirmingDelegate(), false);
        assertThrows(RuntimeCamelException.class, failing::start);
        assertEquals(List.of("A=1 NEW"), readLog(logFile), "the log still holds the unconfirmed entry");

        assertTrue(logFile.setWritable(true));
        Files.delete(blocked.toPath());
        WriteAheadResumeStrategyRestartTest.Delegate delegate = new WriteAheadResumeStrategyRestartTest.Delegate();
        assertEquals(List.of("A=1", "B=1"), restart(logFile, delegate));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void newLogKeepsThePermissionsOfThePreviousOne() throws Exception {
        File logFile = runWithAnUnconfirmedEntry();
        Files.setPosixFilePermissions(logFile.toPath(), PosixFilePermissions.fromString("rw-------"));

        WriteAheadResumeStrategyRestartTest.Delegate delegate = new WriteAheadResumeStrategyRestartTest.Delegate();
        assertEquals(List.of("A=1", "B=1"), restart(logFile, delegate));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(logFile.toPath())));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void newLogReplacesTheFileASymbolicLinkPointsTo() throws Exception {
        Path data = Files.createDirectory(testDir.toPath().resolve("data"));
        Path link = Files.createSymbolicLink(testDir.toPath().resolve("wal.data"), data.resolve("wal.data"));
        File logFile = runWithAnUnconfirmedEntry(link.toFile());

        WriteAheadResumeStrategyRestartTest.Delegate delegate = new WriteAheadResumeStrategyRestartTest.Delegate();
        assertEquals(List.of("A=1", "B=1"), restart(logFile, delegate));
        assertTrue(Files.isSymbolicLink(link), "the link is kept");
        assertEquals(List.of("A=1 PROCESSED", "B=1 PROCESSED"), readLog(data.resolve("wal.data").toFile()));
        assertFalse(Files.exists(data.resolve("wal.data.tmp")), "no temporary file is left");
    }
}
