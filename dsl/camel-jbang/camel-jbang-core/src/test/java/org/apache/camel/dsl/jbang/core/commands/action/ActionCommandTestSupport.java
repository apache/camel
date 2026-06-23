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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Base class for action command tests. Handles file system setup, status file writing and ProcessHandle mock creation
 * for use with Mockito's mockStatic. Mirrors the proven {@code ProcessCommandTestSupport} (in the {@code process} test
 * package) and adds helpers for the action/output file round trip that action commands use.
 */
abstract class ActionCommandTestSupport extends CamelCommandBaseTestSupport {

    static final long TEST_PID = 12345L;
    static final long CURRENT_PID = 99999L;

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        CommandLineHelper.useHomeDir("target/test-action");
        Files.createDirectories(CommandLineHelper.getCamelDir());
    }

    @AfterEach
    public void cleanup() throws Exception {
        Path camelDir = CommandLineHelper.getCamelDir();
        if (Files.exists(camelDir)) {
            try (Stream<Path> walk = Files.walk(camelDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // best effort cleanup
                    }
                });
            }
        }
    }

    /**
     * Writes a minimal status file so {@code findPids} can resolve the given pid by its Camel context name.
     */
    protected static void writeStatusFile(long pid, String contextName) throws Exception {
        JsonObject context = new JsonObject();
        context.put("name", contextName);

        JsonObject root = new JsonObject();
        root.put("context", context);
        writeStatusFile(pid, root);
    }

    protected static void writeStatusFile(long pid, JsonObject root) throws Exception {
        Path f = CommandLineHelper.getCamelDir().resolve(pid + "-status.json");
        Files.writeString(f, root.toJson());
    }

    /**
     * Writes a message-history file for the given pid. Unlike the action/output round trip, {@code CamelHistoryAction}
     * reads this pre-existing {@code <pid>-history.json} file (one JSON object per line) directly, so no responder
     * thread is needed: write the fixture, then run with {@link #callWithSingleProcess}.
     */
    protected static void writeMessageHistoryFile(long pid, JsonObject line) throws Exception {
        Path f = CommandLineHelper.getCamelDir().resolve(pid + "-history.json");
        Files.writeString(f, line.toJson());
    }

    /**
     * Reads back the action file an action command wrote for the given pid.
     *
     * @return the deserialized action JSON, or {@code null} if no action file exists
     */
    protected static JsonObject readActionFile(long pid) throws Exception {
        Path f = CommandLineHelper.getCamelDir().resolve(pid + "-action.json");
        if (!Files.exists(f)) {
            return null;
        }
        return (JsonObject) Jsoner.deserialize(Files.readString(f));
    }

    /**
     * Runs the command with ProcessHandle mocked so a single test process (TEST_PID) is discoverable and the current
     * process is a distinct pid. Returns the exit code; any action/output files the command wrote persist on disk and
     * can be asserted after this returns.
     */
    protected static int callWithSingleProcess(CamelCommand command) throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            return command.doCall();
        }
    }

    /**
     * Runs a request/response reader command end to end. Mocks a single discoverable process (TEST_PID) and, in a
     * background thread, simulates the running Camel writing {@code response} to its output file once the command has
     * written its action request.
     * <p>
     * The reader commands delete any stale output file <em>before</em> writing the action file and only then poll for
     * the output, so the responder waits for the action file to appear: that guarantees the response lands after the
     * command's own delete and cannot be wiped out. No {@code Thread.sleep} is used; the wait is driven by Awaitility.
     *
     * @return the command exit code; rendered output can be asserted afterwards via {@code printer.getOutput()}
     */
    protected int callWithResponse(CamelCommand command, JsonObject response) throws Exception {
        Path actionFile = CommandLineHelper.getCamelDir().resolve(TEST_PID + "-action.json");
        Path outputFile = CommandLineHelper.getCamelDir().resolve(TEST_PID + "-output.json");
        Thread responder = new Thread(() -> {
            await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(actionFile));
            try {
                Files.writeString(outputFile, response.toJson());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "test-camel-responder");
        responder.setDaemon(true);
        responder.start();
        try {
            return callWithSingleProcess(command);
        } finally {
            responder.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    /**
     * Creates a mock ProcessHandle for the test process. Info is pre-configured with empty commandLine and a fixed
     * start instant so extractName falls through to context.name.
     */
    protected static ProcessHandle mockProcessHandle(long pid) {
        ProcessHandle ph = mock(ProcessHandle.class);
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(ph.pid()).thenReturn(pid);
        when(ph.info()).thenReturn(info);
        when(info.commandLine()).thenReturn(Optional.empty());
        // lenient: only read when a status file is matched
        lenient().when(info.startInstant()).thenReturn(Optional.of(Instant.now().minusSeconds(60)));
        return ph;
    }

    /**
     * Creates a mock for ProcessHandle.current(): a distinct process so the "skip current" filter in findPids does not
     * exclude the test handle.
     */
    protected static ProcessHandle mockCurrentHandle() {
        ProcessHandle current = mock(ProcessHandle.class);
        when(current.pid()).thenReturn(CURRENT_PID);
        return current;
    }
}
