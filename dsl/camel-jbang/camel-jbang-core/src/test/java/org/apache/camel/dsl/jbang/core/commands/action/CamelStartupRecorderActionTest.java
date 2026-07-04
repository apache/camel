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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelStartupRecorderActionTest extends CamelCommandBaseTestSupport {

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        CommandLineHelper.useHomeDir("target");
    }

    @Test
    void testDisplaysStepsFromOutput() throws Exception {
        JsonObject mockOutput = buildStartupOutput(
                step(1, 0, 0, "MyRoute", "Route", "Build route", 150),
                step(2, 1, 1, null, "From", "timer:tick", 50),
                step(3, 1, 1, null, "To", "log:info", 30));

        TestableStartupRecorder command = new TestableStartupRecorder(
                new CamelJBangMain().withPrinter(printer), mockOutput);

        int exit = command.doWatchCall();

        assertEquals(0, exit);
        String output = printer.getOutput();
        assertTrue(output.contains("Route"), "Should show step type Route");
        assertTrue(output.contains("From"), "Should show step type From");
        assertTrue(output.contains("Build route"), "Should show step description");
    }

    @Test
    void testStepWithNameShowsParentheses() throws Exception {
        JsonObject mockOutput = buildStartupOutput(
                step(1, 0, 0, "myRoute", "Route", "Configure route", 100));

        TestableStartupRecorder command = new TestableStartupRecorder(
                new CamelJBangMain().withPrinter(printer), mockOutput);

        int exit = command.doWatchCall();

        assertEquals(0, exit);
        // When a step has a name, it appears as "description(name)"
        assertTrue(printer.getOutput().contains("Configure route(myRoute)"),
                "Name should be appended in parentheses");
    }

    @Test
    void testSortByDuration() throws Exception {
        JsonObject mockOutput = buildStartupOutput(
                step(1, 0, 0, null, "Route", "FastStep", 10),
                step(2, 0, 0, null, "Route", "SlowStep", 500),
                step(3, 0, 0, null, "Route", "MidStep", 200));

        TestableStartupRecorder command = new TestableStartupRecorder(
                new CamelJBangMain().withPrinter(printer), mockOutput);
        command.sort = "duration";

        int exit = command.doWatchCall();

        assertEquals(0, exit);
        String output = printer.getOutput();
        int fastPos = output.indexOf("FastStep");
        int midPos = output.indexOf("MidStep");
        int slowPos = output.indexOf("SlowStep");
        // Ascending duration: FastStep (10ms) before MidStep (200ms) before SlowStep (500ms)
        assertTrue(fastPos < midPos && midPos < slowPos,
                "Sort by duration ascending: fastest step must appear first");
    }

    @Test
    void testSortByDurationDescending() throws Exception {
        JsonObject mockOutput = buildStartupOutput(
                step(1, 0, 0, null, "Route", "FastStep", 10),
                step(2, 0, 0, null, "Route", "SlowStep", 500));

        TestableStartupRecorder command = new TestableStartupRecorder(
                new CamelJBangMain().withPrinter(printer), mockOutput);
        command.sort = "-duration";

        int exit = command.doWatchCall();

        assertEquals(0, exit);
        String output = printer.getOutput();
        int slowPos = output.indexOf("SlowStep");
        int fastPos = output.indexOf("FastStep");
        assertTrue(slowPos < fastPos, "Descending duration: slowest step must appear first");
    }

    @Test
    void testZeroDurationDisplayedAsEmpty() throws Exception {
        JsonObject mockOutput = buildStartupOutput(
                step(1, 0, 0, null, "Route", "ZeroDurationStep", 0));

        TestableStartupRecorder command = new TestableStartupRecorder(
                new CamelJBangMain().withPrinter(printer), mockOutput);

        int exit = command.doWatchCall();

        assertEquals(0, exit);
        // Zero duration is rendered as empty string, not "0" — verify step name still appears
        assertTrue(printer.getOutput().contains("ZeroDurationStep"));
    }

    @Test
    void testEmptyOutputWhenNoPidFound() throws Exception {
        // No pids returned — command should return error code 1
        TestableStartupRecorder command = new TestableStartupRecorder(
                new CamelJBangMain().withPrinter(printer), null) {
            @Override
            List<Long> findPids(String name) {
                return List.of();
            }
        };

        int exit = command.doWatchCall();

        assertEquals(1, exit, "Should return error when no pids found");
    }

    private static JsonObject buildStartupOutput(JsonObject... steps) {
        JsonArray arr = new JsonArray();
        Collections.addAll(arr, steps);
        JsonObject jo = new JsonObject();
        jo.put("steps", arr);
        return jo;
    }

    private static JsonObject step(
            int id, int parentId, int level, String name, String type, String description,
            long duration) {
        JsonObject step = new JsonObject();
        step.put("id", id);
        step.put("parentId", parentId);
        step.put("level", level);
        step.put("name", name);
        step.put("type", type);
        step.put("description", description);
        step.put("duration", duration);
        return step;
    }

    /**
     * Test subclass that bypasses process discovery and output file waiting so the rendering logic can be tested
     * against a pre-built JSON response.
     */
    private static class TestableStartupRecorder extends CamelStartupRecorderAction {
        private final JsonObject mockOutput;

        TestableStartupRecorder(CamelJBangMain main, JsonObject mockOutput) {
            super(main);
            this.mockOutput = mockOutput;
        }

        @Override
        List<Long> findPids(String name) {
            return List.of(12345L);
        }

        @Override
        protected JsonObject waitForOutputFile(Path outputFile) {
            return mockOutput;
        }
    }
}
