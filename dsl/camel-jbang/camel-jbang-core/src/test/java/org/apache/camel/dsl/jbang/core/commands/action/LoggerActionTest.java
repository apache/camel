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
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoggerActionTest extends CamelCommandBaseTestSupport {

    private static final long TEST_PID = 12345L;
    private static final long CURRENT_PID = 99999L;

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        CommandLineHelper.useHomeDir("target/test-logger");
        Files.createDirectories(CommandLineHelper.getCamelDir());
    }

    @AfterEach
    void cleanup() throws IOException {
        Path camelDir = CommandLineHelper.getCamelDir();
        if (Files.exists(camelDir)) {
            try (var entries = Files.list(camelDir)) {
                for (Path p : entries.toList()) {
                    Files.deleteIfExists(p);
                }
            }
            Files.deleteIfExists(camelDir);
        }
    }

    @Test
    void testListShowsLoggers() throws Exception {
        JsonObject levels = new JsonObject();
        levels.put("org.apache.camel", "INFO");
        levels.put("com.example", "DEBUG");

        JsonObject logger = new JsonObject();
        logger.put("levels", levels);

        JsonObject context = new JsonObject();
        context.put("name", "myApp");

        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("logger", logger);
        writeStatusFile(TEST_PID, root);

        LoggerAction command = new LoggerAction(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockHandle(TEST_PID);
            ProcessHandle current = mockHandle(CURRENT_PID);
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("org.apache.camel"), "Should show logger name");
            assertTrue(output.contains("INFO"), "Should show logger level");
            assertTrue(output.contains("com.example"), "Should list all loggers");
            assertTrue(output.contains("DEBUG"), "Should show DEBUG level");
        }
    }

    @Test
    void testEmptyOutputWhenNoLoggers() throws Exception {
        JsonObject context = new JsonObject();
        context.put("name", "myApp");

        JsonObject root = new JsonObject();
        root.put("context", context);
        // No logger section
        writeStatusFile(TEST_PID, root);

        LoggerAction command = new LoggerAction(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockHandle(TEST_PID);
            ProcessHandle current = mockHandle(CURRENT_PID);
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim());
        }
    }

    @Test
    void testSortByName() throws Exception {
        // "sort=name" sorts rows by context/process name; use two processes with different names
        long pidZzz = TEST_PID;
        long pidAaa = TEST_PID + 1;

        JsonObject levels = new JsonObject();
        levels.put("root", "INFO");

        JsonObject logger = new JsonObject();
        logger.put("levels", levels);

        JsonObject rootZzz = new JsonObject();
        rootZzz.put("context", contextJson("zzzApp"));
        rootZzz.put("logger", logger);
        writeStatusFile(pidZzz, rootZzz);

        JsonObject rootAaa = new JsonObject();
        rootAaa.put("context", contextJson("aaaApp"));
        rootAaa.put("logger", logger);
        writeStatusFile(pidAaa, rootAaa);

        LoggerAction command = new LoggerAction(new CamelJBangMain().withPrinter(printer));
        command.sort = "name";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle phZzz = mockHandle(pidZzz);
            ProcessHandle phAaa = mockHandle(pidAaa);
            ProcessHandle current = mockHandle(CURRENT_PID);
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(phZzz, phAaa));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            int aaaPos = output.indexOf("aaaApp");
            int zzzPos = output.indexOf("zzzApp");
            assertTrue(aaaPos < zzzPos, "Sort by name ascending: aaaApp must appear before zzzApp");
        }
    }

    private static JsonObject contextJson(String name) {
        JsonObject ctx = new JsonObject();
        ctx.put("name", name);
        return ctx;
    }

    private static ProcessHandle mockHandle(long pid) {
        ProcessHandle ph = mock(ProcessHandle.class);
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(ph.pid()).thenReturn(pid);
        // info/commandLine/startInstant are only used for process handles, not for the current handle
        lenient().when(ph.info()).thenReturn(info);
        lenient().when(info.commandLine()).thenReturn(Optional.empty());
        lenient().when(info.startInstant()).thenReturn(Optional.of(Instant.now().minusSeconds(60)));
        return ph;
    }

    private static void writeStatusFile(long pid, JsonObject root) throws Exception {
        Path f = CommandLineHelper.getCamelDir().resolve(pid + "-status.json");
        Files.writeString(f, root.toJson());
    }
}
