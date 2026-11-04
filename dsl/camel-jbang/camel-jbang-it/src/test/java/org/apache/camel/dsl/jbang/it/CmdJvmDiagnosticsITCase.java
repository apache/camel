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
        // gc triggers JVM garbage collection; produces no visible output on success
        execute("cmd gc");
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
        // reload triggers route reloading; produces no visible output on success
        execute("cmd reload");
    }

    @Test
    public void testCmdResetStats() throws IOException {
        copyResourceInDataFolder(TestResources.ROUTE2);
        executeBackground(String.format("run %s/route2.yaml", mountPoint()));
        checkLogContains("Hello Camel from custom integration");
        // reset-stats clears exchange counters; produces no visible output on success
        execute("cmd reset-stats");
    }
}
