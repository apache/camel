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
package org.apache.camel.impl.console;

import java.util.Map;

import org.apache.camel.console.DevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JfrMemoryLeakDevConsole. The console captures real JFR old-object-sample events, which are inherently
 * timing/GC-dependent and not reliable to trigger deterministically in a unit test, so these tests focus on the command
 * lifecycle (start/stop/status/query/compare) and error paths rather than on actual leak data.
 */
public class JfrMemoryLeakDevConsoleTest extends AbstractDevConsoleTest {

    @Test
    public void testConsoleNotReadOnly() {
        DevConsole console = assertConsoleExists("jfr-memory-leak", "jvm");
        assertFalse(console.isReadOnly());
    }

    @Test
    public void testStatusIdleByDefault() {
        DevConsole console = assertConsoleExists("jfr-memory-leak");

        JsonObject json = callJson(console, Map.of("command", "status"));
        assertEquals("idle", json.getString("status"));
        assertNull(json.get("startTime"));
        assertNull(json.get("sampleCount"));
    }

    @Test
    public void testQueryWithNoRecordingYet() {
        DevConsole console = assertConsoleExists("jfr-memory-leak");

        JsonObject json = callJson(console, Map.of("command", "query"));
        assertEquals("idle", json.getString("status"));
        assertEquals(0, json.getInteger("sampleCount"));
        assertNotNull(json.getCollection("samples"));
        assertTrue(json.getCollection("samples").isEmpty());
        assertNotNull(json.getString("note"));
    }

    @Test
    public void testCompareWithoutTwoRecordingsReturnsError() {
        DevConsole console = assertConsoleExists("jfr-memory-leak");

        JsonObject json = callJson(console, Map.of("command", "compare"));
        assertEquals("error", json.getString("status"));
        assertNotNull(json.getString("error"));
    }

    @Test
    public void testStopWithoutActiveRecordingReturnsError() {
        DevConsole console = assertConsoleExists("jfr-memory-leak");

        JsonObject json = callJson(console, Map.of("command", "stop"));
        assertEquals("error", json.getString("status"));
        assertNotNull(json.getString("error"));
    }

    @Test
    public void testUnknownCommandReturnsError() {
        DevConsole console = assertConsoleExists("jfr-memory-leak");

        JsonObject json = callJson(console, Map.of("command", "bogus"));
        assertEquals("error", json.getString("status"));
        assertTrue(json.getString("error").contains("bogus"));
    }

    @Test
    public void testStartStatusStopLifecycle() {
        DevConsole console = assertConsoleExists("jfr-memory-leak");
        try {
            JsonObject started = callJson(console, Map.of("command", "start"));
            assertEquals("recording", started.getString("status"));
            assertNotNull(started.getLong("startTime"));
            assertNull(started.get("durationSeconds"));

            JsonObject startedAgain = callJson(console, Map.of("command", "start"));
            assertEquals("error", startedAgain.getString("status"));

            JsonObject status = callJson(console, Map.of("command", "status"));
            assertEquals("recording", status.getString("status"));
            assertNotNull(status.get("elapsedMs"));

            String text = callText(console, Map.of("command", "status"));
            assertNotNull(text);

            JsonObject stopped = callJson(console, Map.of("command", "stop"));
            assertEquals("completed", stopped.getString("status"));
            assertNotNull(stopped.get("sampleCount"));
            assertNotNull(stopped.get("gcCount"));
            assertNotNull(stopped.get("recordingDurationMs"));
            assertNotNull(stopped.get("recordingEndTime"));
            JsonArray samples = stopped.getCollection("samples");
            assertNotNull(samples);

            JsonObject afterStopStatus = callJson(console, Map.of("command", "status"));
            assertEquals("completed", afterStopStatus.getString("status"));
            assertTrue(afterStopStatus.getBoolean("hasCachedResults"));
            assertFalse(afterStopStatus.getBoolean("hasComparisonData"));

            // stop again without an active recording should replay the cached (filtered) result
            JsonObject stoppedAgain = callJson(console, Map.of("command", "stop"));
            assertEquals("completed", stoppedAgain.getString("status"));

            JsonObject queried = callJson(console, Map.of("command", "query"));
            assertEquals("completed", queried.getString("status"));

            // a second recording makes comparison data available
            callJson(console, Map.of("command", "start"));
            JsonObject secondStop = callJson(console, Map.of("command", "stop"));
            assertEquals("completed", secondStop.getString("status"));

            JsonObject statusWithComparison = callJson(console, Map.of("command", "status"));
            assertTrue(statusWithComparison.getBoolean("hasComparisonData"));

            JsonObject compared = callJson(console, Map.of("command", "compare"));
            assertEquals("compared", compared.getString("status"));
            assertNotNull(compared.getJsonObject("baseline"));
            assertNotNull(compared.getJsonObject("current"));
            assertNotNull(compared.getCollection("comparisons"));
            assertNotNull(compared.getDouble("durationRatio"));
        } finally {
            // ensure no recording is left running across tests
            callJson(console, Map.of("command", "stop"));
        }
    }
}
