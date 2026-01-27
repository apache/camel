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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parameterized test for simple DevConsoles that don't require special setup. Each console is tested for basic TEXT and
 * JSON output functionality.
 */
public class SimpleDevConsoleTest extends AbstractDevConsoleTest {

    /**
     * Provides test parameters: console ID, optional text assertion, optional JSON key to check.
     */
    static Stream<Arguments> consoleParameters() {
        return Stream.of(
                // Console ID, text contains (nullable), JSON key to verify (nullable)
                Arguments.of("blocked", "Blocked:", "blocked"),
                Arguments.of("circuit-breaker", null, "circuitBreakers"),
                Arguments.of("context", null, null),
                Arguments.of("debug", null, null),
                Arguments.of("gc", null, "garbageCollectors"),
                Arguments.of("health", null, "checks"),
                Arguments.of("inflight", "Inflight:", "inflight"),
                Arguments.of("internal-tasks", null, null),
                Arguments.of("java-security", null, "securityProviders"),
                Arguments.of("jvm", null, null),
                Arguments.of("log", null, null),
                Arguments.of("memory", null, null),
                Arguments.of("message-history", null, null),
                Arguments.of("reload", null, null),
                Arguments.of("rest", null, null),
                Arguments.of("service", null, null),
                Arguments.of("startup-recorder", null, null),
                Arguments.of("system-properties", null, null),
                Arguments.of("thread", null, null),
                Arguments.of("top", null, null),
                Arguments.of("trace", null, null),
                Arguments.of("transformers", null, null),
                Arguments.of("type-converters", null, null));
    }

    @ParameterizedTest(name = "{0} console - TEXT output")
    @MethodSource("consoleParameters")
    void testConsoleText(String consoleId, String expectedTextContent, String expectedJsonKey) {
        DevConsole console = assertConsoleExists(consoleId);
        String out = callText(console);

        if (expectedTextContent != null) {
            assertTrue(out.contains(expectedTextContent),
                    "TEXT output should contain '" + expectedTextContent + "'");
        }
    }

    @ParameterizedTest(name = "{0} console - JSON output")
    @MethodSource("consoleParameters")
    void testConsoleJson(String consoleId, String expectedTextContent, String expectedJsonKey) {
        DevConsole console = assertConsoleExists(consoleId);
        JsonObject out = callJson(console);

        if (expectedJsonKey != null) {
            assertNotNull(out.get(expectedJsonKey),
                    "JSON output should contain key '" + expectedJsonKey + "'");
        }
    }
}
