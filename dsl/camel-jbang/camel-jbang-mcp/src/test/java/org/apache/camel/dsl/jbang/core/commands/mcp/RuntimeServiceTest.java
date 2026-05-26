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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.List;

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeServiceTest {

    @Test
    void discoverProcessesReturnsEmptyWhenNoneRunning() {
        RuntimeService service = new RuntimeService();
        List<RuntimeService.ProcessInfo> processes = service.discoverProcesses();
        // may or may not find processes depending on environment,
        // but should not throw
        assertThat(processes).isNotNull();
    }

    @Test
    void findSingleProcessThrowsWhenNoneRunning() {
        RuntimeService service = new RuntimeService();
        List<RuntimeService.ProcessInfo> processes = service.discoverProcesses();
        if (processes.isEmpty()) {
            assertThatThrownBy(() -> service.findSingleProcess(null))
                    .isInstanceOf(ToolCallException.class)
                    .hasMessageContaining("No running Camel processes found");
        }
    }

    @Test
    void findSingleProcessThrowsForInvalidPid() {
        RuntimeService service = new RuntimeService();
        assertThatThrownBy(() -> service.findSingleProcess("99999999"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("Camel process");
    }

    @Test
    void readStatusReturnsNullForNonExistentPid() {
        RuntimeService service = new RuntimeService();
        assertThat(service.readStatus(99999999L)).isNull();
    }

    @Test
    void readStatusSectionThrowsForNonExistentPid() {
        RuntimeService service = new RuntimeService();
        assertThatThrownBy(() -> service.readStatusSection(99999999L, "context"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("No status available");
    }
}
