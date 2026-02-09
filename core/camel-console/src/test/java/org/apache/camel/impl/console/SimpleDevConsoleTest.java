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

import java.util.stream.Stream;

import org.apache.camel.console.DevConsole;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parameterized test for simple DevConsoles that don't require special setup. Each console is tested for basic TEXT and
 * JSON output functionality.
 */
public class SimpleDevConsoleTest extends AbstractDevConsoleTest {

    /**
     * Provides test parameters: console ID, expected JSON key.
     */
    static Stream<Arguments> consoleParameters() {
        return Stream.of(
                // Console ID, JSON key to verify (use keys that are always present in output)
                Arguments.of("blocked", "blocked"),
                Arguments.of("circuit-breaker", "circuitBreakers"),
                Arguments.of("context", "name"),
                Arguments.of("gc", "garbageCollectors"),
                Arguments.of("health", "checks"),
                Arguments.of("inflight", "inflight"),
                Arguments.of("internal-tasks", "tasks"),
                Arguments.of("java-security", "securityProviders"),
                Arguments.of("jvm", "vmName"),
                Arguments.of("memory", "heapMemoryUsed"),
                Arguments.of("system-properties", "systemProperties"),
                Arguments.of("thread", "threads"),
                Arguments.of("transformers", "size"),
                Arguments.of("type-converters", "statistics"));
    }

    @ParameterizedTest(name = "{0} console - TEXT output")
    @MethodSource("consoleParameters")
    void testConsoleText(String consoleId, String expectedJsonKey) {
        DevConsole console = assertConsoleExists(consoleId);
        // Just verify the console produces TEXT output without error
        callText(console);
    }

    @ParameterizedTest(name = "{0} console - JSON output")
    @MethodSource("consoleParameters")
    void testConsoleJson(String consoleId, String expectedJsonKey) {
        DevConsole console = assertConsoleExists(consoleId);
        JsonObject out = callJson(console);

        assertThat(out).containsKey(expectedJsonKey);
    }

    /**
     * Consoles with conditional output - just verify they produce output without errors.
     */
    static Stream<String> conditionalOutputConsoleIds() {
        return Stream.of(
                "debug",
                "log",
                "message-history",
                "reload",
                "rest",
                "service",
                "startup-recorder",
                "top",
                "trace");
    }

    @ParameterizedTest(name = "{0} console - basic output")
    @MethodSource("conditionalOutputConsoleIds")
    void testConditionalOutputConsole(String consoleId) {
        DevConsole console = assertConsoleExists(consoleId);
        // These consoles have conditional output - just verify they work without errors
        callText(console);
        callJson(console);
    }
}
