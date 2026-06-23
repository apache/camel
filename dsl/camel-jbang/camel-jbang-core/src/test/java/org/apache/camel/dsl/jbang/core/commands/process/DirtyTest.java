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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class DirtyTest extends ProcessCommandTestSupport {

    @Test
    void testReturnsZeroWhenNoFiles() throws Exception {
        // camel dir is empty: Dirty returns before touching ProcessHandle
        Dirty command = new Dirty(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();
        assertEquals(0, exit);
        assertEquals("", printer.getOutput().trim());
    }

    @Test
    void testDetectsOrphanFile() throws Exception {
        // write a status file for a PID that has no running process
        Path orphan = CommandLineHelper.getCamelDir().resolve("77777-status.json");
        Files.writeString(orphan, "{}");

        Dirty command = new Dirty(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            // no running processes -> orphan file is not matched
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());

            int exit = command.doCall();

            assertEquals(0, exit);
            assertTrue(printer.getOutput().contains("orphan"), "Should report orphan file count");
        }
    }

    @Test
    void testRunningProcessFileIsNotDirty() throws Exception {
        // status file for TEST_PID is owned by a running process -> not dirty
        writeStatusFile(TEST_PID, buildContextStatus("liveApp", 5));

        Dirty command = new Dirty(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim(), "File belonging to a running PID is not dirty");
        }
    }

    @Test
    void testCleanFlagDeletesOrphanFile() throws Exception {
        Path orphan = CommandLineHelper.getCamelDir().resolve("88888-status.json");
        Files.writeString(orphan, "{}");
        assertTrue(Files.exists(orphan), "Orphan file must exist before cleaning");

        Dirty command = new Dirty(new CamelJBangMain().withPrinter(printer));
        command.clean = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());

            int exit = command.doCall();

            assertEquals(0, exit);
            assertFalse(Files.exists(orphan), "Orphan file should be deleted by --clean");
            assertTrue(printer.getOutput().contains("Cleaned"), "Should print cleaned confirmation");
        }
    }
}
