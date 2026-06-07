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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for process command tests. Handles file system setup, status file writing, and ProcessHandle mock creation
 * for use with Mockito's mockStatic.
 */
abstract class ProcessCommandTestSupport extends CamelCommandBaseTestSupport {

    static final long TEST_PID = 12345L;
    static final long CURRENT_PID = 99999L;

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        CommandLineHelper.useHomeDir("target/test-process");
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

    protected static void writeStatusFile(long pid, JsonObject root) throws Exception {
        Path f = CommandLineHelper.getCamelDir().resolve(pid + "-status.json");
        Files.writeString(f, root.toJson());
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
        // lenient: only called when table rows are populated (not for empty-result tests)
        lenient().when(info.startInstant()).thenReturn(Optional.of(Instant.now().minusSeconds(60)));
        return ph;
    }

    /**
     * Creates a mock for ProcessHandle.current() — a distinct process so the "skip current" filter in findPids does not
     * exclude the test handle.
     */
    protected static ProcessHandle mockCurrentHandle() {
        ProcessHandle current = mock(ProcessHandle.class);
        when(current.pid()).thenReturn(CURRENT_PID);
        return current;
    }

    /**
     * Builds a minimal context-level status JSON. Phase 5 = Running in CamelCommandHelper.extractState. Includes an
     * empty routes array so CamelContextStatus does not throw NPE.
     */
    protected static JsonObject buildContextStatus(String contextName, int phase) {
        JsonObject stats = new JsonObject();
        stats.put("exchangesTotal", "0");
        stats.put("exchangesFailed", "0");
        stats.put("exchangesInflight", "0");

        JsonObject context = new JsonObject();
        context.put("name", contextName);
        context.put("phase", phase);
        context.put("statistics", stats);

        JsonObject hc = new JsonObject();
        hc.put("ready", phase == 5);

        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("healthChecks", hc);
        root.put("routes", new JsonArray());
        return root;
    }

    /**
     * Builds a route-level status JSON with one route entry and basic statistics.
     */
    protected static JsonObject buildRouteStatus(String routeId, String state) {
        JsonObject routeStats = new JsonObject();
        routeStats.put("exchangesTotal", "0");
        routeStats.put("exchangesFailed", "0");
        routeStats.put("exchangesInflight", "0");
        routeStats.put("meanProcessingTime", "-1");
        routeStats.put("maxProcessingTime", "0");
        routeStats.put("minProcessingTime", "0");

        JsonObject route = new JsonObject();
        route.put("routeId", routeId);
        route.put("from", "timer:tick");
        route.put("state", state);
        route.put("uptime", "1m");
        route.put("statistics", routeStats);

        JsonArray routes = new JsonArray();
        routes.add(route);

        JsonObject context = new JsonObject();
        context.put("name", "myApp");
        context.put("phase", 5);

        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("routes", routes);
        return root;
    }
}
