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

import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers CamelRouteDiagramAction's dispatch between "running integration" and "source files" (the size() <= 1 check in
 * doWatchCall). Calls doWatchCall() directly (with ProcessHandle mocked, same as the ActionCommandTestSupport helpers
 * do) rather than doCall(), to avoid the Terminal setup CamelRouteDiagramAction.doCall() performs, which needs a real
 * terminal and is out of scope for a unit test.
 */
@ExtendWith(MockitoExtension.class)
class CamelRouteDiagramActionDispatchTest extends ActionCommandTestSupport {

    @Test
    void testSingleUnmatchedNameFallsBackToSourceFile() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain().withPrinter(printer));
        // "doesNotExist" matches no running process, so it is tried as a source file next
        command.files = List.of("doesNotExist");

        int exit = callWatchWithSingleProcess(command);

        assertEquals(1, exit);
        assertTrue(printer.getOutput().contains("File does not exist: doesNotExist"),
                "should report the missing source file, was: " + printer.getOutput());
    }

    @Test
    void testMultipleFilesSkipRunningIntegrationLookupEvenWhenFirstNameMatches() throws Exception {
        // a running integration whose name matches the first of the given file tokens: with 2+ tokens the
        // running-integration lookup (findPids) must not even be attempted, so no ProcessHandle mocking is needed
        // here at all -- that absence of interaction is exactly what this test proves
        writeStatusFile(TEST_PID, "route1");

        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain().withPrinter(printer));
        command.files = List.of("route1", "route2");

        int exit = command.doWatchCall();

        assertEquals(1, exit);
        assertTrue(printer.getOutput().contains("File does not exist: route1"),
                "with 2+ file args, dispatch must go straight to source-file handling and skip the "
                                                                                + "running-integration lookup entirely, was: "
                                                                                + printer.getOutput());
    }

    private static int callWatchWithSingleProcess(CamelRouteDiagramAction command) throws Exception {
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            return command.doWatchCall();
        }
    }
}
