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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

/**
 * Base class for action command tests. Handles file system setup, status file writing and ProcessHandle mock creation
 * for use with Mockito's mockStatic. Mirrors the proven {@code ProcessCommandTestSupport} (in the {@code process} test
 * package) and adds a fake integration that answers the actions of the commands, like the camel-cli-connector does.
 */
abstract class ActionCommandTestSupport extends CamelCommandBaseTestSupport {

    static final long TEST_PID = 12345L;
    static final long CURRENT_PID = 99999L;

    /**
     * How the camel-cli-connector of Camel 4.24 confirms an action without a result.
     */
    static final JsonObject CONFIRMED = new JsonObject(Map.of("status", "success"));

    private static volatile JsonObject lastAction;

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        useTestHomeDir();
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
     * The action the fake integration took last (see {@link #callWithResponse}).
     *
     * @return the action JSON, or {@code null} if the command sent no action
     */
    protected static JsonObject readActionFile(long pid) {
        return lastAction;
    }

    /**
     * Runs the command with ProcessHandle mocked so a single test process (TEST_PID) is discoverable and the current
     * process is a distinct pid. The test process confirms an action like the camel-cli-connector of Camel 4.24 does,
     * and {@link #readActionFile(long)} returns the action. Returns the exit code.
     */
    protected static int callWithSingleProcess(CamelCommand command) throws Exception {
        return call(command, CONFIRMED);
    }

    /**
     * Runs a request/response reader command end to end. Mocks a single discoverable process (TEST_PID) that answers
     * the action of the command with {@code response}, like the camel-cli-connector does: it writes the answer next to
     * the request, then deletes the request.
     *
     * @return the command exit code; rendered output can be asserted afterwards via {@code printer.getOutput()}
     */
    protected int callWithResponse(CamelCommand command, JsonObject response) throws Exception {
        return call(command, response);
    }

    /**
     * Runs a step of a command, such as the one that sends the action of a command that first starts an integration of
     * its own, against the fake integration (TEST_PID) that answers with {@code response}.
     */
    protected int callWithResponse(Callable<Integer> step, JsonObject response) throws Exception {
        return call(step, response);
    }

    private static int call(CamelCommand command, JsonObject response) throws Exception {
        return call(command::doCall, response);
    }

    private static int call(Callable<Integer> step, JsonObject response) throws Exception {
        lastAction = null;
        AtomicBoolean running = new AtomicBoolean(true);
        Thread responder = new Thread(() -> {
            Path dir = CommandLineHelper.getCamelDir();
            while (running.get()) {
                // the integration polls for actions
                await().pollInterval(Duration.ofMillis(10)).atMost(30, TimeUnit.SECONDS)
                        .until(() -> !running.get() || nextRequest(dir).isPresent());
                Optional<Path> request = nextRequest(dir);
                if (request.isPresent()) {
                    answer(dir, request.get(), response);
                }
            }
        }, "test-camel-responder");
        responder.setDaemon(true);
        responder.start();
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            return step.call();
        } finally {
            running.set(false);
            responder.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    private static Optional<Path> nextRequest(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().startsWith(TEST_PID + "-action")).findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static void answer(Path dir, Path request, JsonObject response) {
        try {
            String text = Files.readString(request);
            if (text.isBlank()) {
                return;
            }
            lastAction = Jsoner.deserialize(text, (JsonObject) null);
            // the answer of a request with an id has the same id, and the shared action file (before 4.21) has one
            String name = request.getFileName().toString();
            Path output = dir.resolve(name.equals(TEST_PID + "-action.json")
                    ? TEST_PID + "-output.json" : name.replace("-action-", "-output-"));
            if (response != null) {
                Files.writeString(output, response.toJson());
            }
            Files.deleteIfExists(request);
        } catch (IOException e) {
            // the command withdrew the request
        }
    }

    /**
     * Creates a mock ProcessHandle for the test process. Info is pre-configured with empty commandLine and a fixed
     * start instant so extractName falls through to context.name.
     */
    protected static ProcessHandle mockProcessHandle(long pid) {
        ProcessHandle ph = mock(ProcessHandle.class);
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        // lenient: a step of a command that knows the pid (see callWithResponse(Callable, JsonObject)) does not look
        // for the process
        lenient().when(ph.pid()).thenReturn(pid);
        // lenient: info()/commandLine()/startInstant() are only read when a status file is matched; a test that
        // exercises the "no status file for this pid" path never reaches that code, leaving these unused
        lenient().when(ph.info()).thenReturn(info);
        lenient().when(info.commandLine()).thenReturn(Optional.empty());
        lenient().when(info.startInstant()).thenReturn(Optional.of(Instant.now().minusSeconds(60)));
        return ph;
    }

    /**
     * Creates a mock for ProcessHandle.current(): a distinct process so the "skip current" filter in findPids does not
     * exclude the test handle.
     */
    protected static ProcessHandle mockCurrentHandle() {
        ProcessHandle current = mock(ProcessHandle.class);
        lenient().when(current.pid()).thenReturn(CURRENT_PID);
        return current;
    }
}
