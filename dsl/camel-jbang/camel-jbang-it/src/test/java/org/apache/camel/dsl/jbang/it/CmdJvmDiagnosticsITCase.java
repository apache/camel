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
package org.apache.camel.dsl.jbang.it;

import java.io.IOException;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

@DisabledOnOs(WINDOWS)
public class CmdJvmDiagnosticsITCase extends JBangTestSupport {

    @Test
    public void testCmdGc() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        execute("cmd gc");
        // route should still be running after GC
        checkCommandOutputs("get route", "Started", ASSERTION_WAIT_SECONDS);
    }

    @Test
    public void testCmdThreadDump() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        // default filter is "Camel", so output lists threads with "Camel" in their name
        checkCommandOutputs("cmd thread-dump", "Camel");
    }

    @Test
    public void testCmdHeapHistogram() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        checkCommandOutputs("cmd heap-histogram", "java.lang");
    }

    @Test
    public void testCmdReload() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        execute("cmd reload");
        // route should resume processing after reload
        checkCommandOutputs("get route", "Started", ASSERTION_WAIT_SECONDS);
    }

    @Test
    public void testCmdResetStats() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        execute("cmd reset-stats");
        // TOTAL and FAIL columns should both read 0 immediately after reset (before the next 1-second timer tick)
        Assertions.assertThat(execute("get count"))
                .as("exchange counters should be reset to zero after cmd reset-stats")
                .containsPattern("\\S+\\s+\\S+\\s+0\\s+0");
        checkCommandOutputs("get route", "Started", ASSERTION_WAIT_SECONDS);
    }
}
