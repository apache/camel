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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parameterized test for DevConsoles that require routes to be configured.
 */
public class RouteBasedDevConsoleTest extends AbstractDevConsoleTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute").routeGroup("myGroup")
                        .to("log:foo")
                        .to("mock:result");

                from("direct:bar").routeId("barRoute")
                        .to("mock:bar");
            }
        };
    }

    /**
     * Provides test parameters: console ID, expected JSON key (nullable).
     */
    static Stream<Arguments> routeConsoleParameters() {
        return Stream.of(
                // Console ID, JSON key to verify (nullable)
                Arguments.of("consumer", "consumers"),
                Arguments.of("endpoint", "endpoints"),
                Arguments.of("route", "routes"),
                Arguments.of("route-controller", "routes"),
                Arguments.of("route-dump", "routes"),
                Arguments.of("route-group", "routeGroups"),
                Arguments.of("route-structure", "routes"),
                Arguments.of("source", "routes"));
    }

    @ParameterizedTest(name = "{0} console - TEXT output")
    @MethodSource("routeConsoleParameters")
    void testConsoleText(String consoleId, String expectedJsonKey) {
        DevConsole console = assertConsoleExists(consoleId);
        callText(console);
    }

    @ParameterizedTest(name = "{0} console - JSON output")
    @MethodSource("routeConsoleParameters")
    void testConsoleJson(String consoleId, String expectedJsonKey) {
        DevConsole console = assertConsoleExists(consoleId);
        JsonObject out = callJson(console);

        if (expectedJsonKey != null) {
            assertTrue(out.containsKey(expectedJsonKey),
                    "JSON output should contain key '" + expectedJsonKey + "'");
        }
    }
}
