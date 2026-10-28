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

import java.util.List;
import java.util.stream.LongStream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * camel jolokia attaches to exactly one running integration.
 */
@ExtendWith(MockitoExtension.class)
class JolokiaTest extends ProcessCommandTestSupport {

    @Test
    void nameThatMatchesNoIntegrationIsNotFound() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("orders", 5));

        assertThatThrownBy(() -> call("billing", TEST_PID))
                .isInstanceOf(CliException.class)
                .hasMessage("No running Camel integration matches: billing")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
    }

    @Test
    void nameThatMatchesSeveralIntegrationsIsNotFound() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("orders", 5));
        writeStatusFile(TEST_PID + 1, buildContextStatus("orders", 5));

        assertThatThrownBy(() -> call("orders", TEST_PID, TEST_PID + 1))
                .isInstanceOf(CliException.class)
                .hasMessageStartingWith("Name or pid orders matches 2 running Camel integrations")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
    }

    private int call(String name, long... pids) throws Exception {
        Jolokia command = new Jolokia(new CamelJBangMain().withPrinter(printer));
        command.name = name;
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            List<ProcessHandle> handles = LongStream.of(pids).mapToObj(ProcessCommandTestSupport::mockProcessHandle).toList();
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> handles.stream());
            return command.doCall();
        }
    }
}
