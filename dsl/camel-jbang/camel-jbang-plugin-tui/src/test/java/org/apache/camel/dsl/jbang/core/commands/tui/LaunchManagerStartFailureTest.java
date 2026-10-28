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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Spring Boot app that finds its port in use prints APPLICATION FAILED TO START, while its JVM can stay up: the TUI
 * sees it in the output of the launch, so it does not stay "Starting".
 */
class LaunchManagerStartFailureTest {

    @TempDir
    Path dir;

    @Test
    void aFailedStartIsFoundInTheOutput() throws Exception {
        Path out = Files.writeString(dir.resolve("launch.log"), "[INFO] Building metrics 1.0\n");
        LaunchManager.PendingLaunch launch = new LaunchManager.PendingLaunch("metrics", null, out, 0);
        assertThat(launch.startFailed()).isFalse();

        // the marker is written in two parts, between two looks
        append(out, "***************************\nAPPLICATION FAIL");
        assertThat(launch.startFailed()).isFalse();
        append(out, "ED TO START\n***************************\n\nWeb server failed to start. Port 8080 was already in use.\n");
        assertThat(launch.startFailed()).isTrue();
    }

    @Test
    void aGoodStartIsNotAFailure() throws Exception {
        Path out = Files.writeString(dir.resolve("launch.log"),
                "Started MetricsApplication in 2.1 seconds\nApache Camel 4.23.0 (metrics) started in 120ms\n");
        assertThat(new LaunchManager.PendingLaunch("metrics", null, out, 0).startFailed()).isFalse();
        assertThat(new LaunchManager.PendingLaunch("metrics", null, dir.resolve("missing.log"), 0).startFailed()).isFalse();
    }

    @Test
    void onceCamelStartedTheLaunchIsNoLongerWatched() throws Exception {
        // stopping the app later makes Maven print BUILD FAILURE: that is not a failed start
        Path out = Files.writeString(dir.resolve("launch.log"), "Tomcat started on port 8080\n");
        LaunchManager.PendingLaunch launch = new LaunchManager.PendingLaunch("metrics", null, out, 0);
        assertThat(launch.startFailed()).isFalse();
        assertThat(launch.started).isFalse();
        append(out, "Apache Camel 4.23.0 (MyCamel) started in 120ms (build:0ms init:0ms start:120ms)\n");
        assertThat(launch.startFailed()).isFalse();
        assertThat(launch.started).isTrue();
    }

    @Test
    void aBuildFailureIsAFailedStart() throws Exception {
        Path out = Files.writeString(dir.resolve("launch.log"), "[ERROR] BUILD FAILURE\n");
        assertThat(new LaunchManager.PendingLaunch("metrics", null, out, 0).startFailed()).isTrue();
    }

    @Test
    void aFailedLaunchTellsWhyWithoutColorsAndStackFrames() throws Exception {
        Path out = Files.writeString(dir.resolve("launch.log"),
                "\u001B[32m INFO\u001B[m Starting\n"
                                                                + "ERROR Error starting Camel: Property with key [env:OPENAI_API_KEY] returned null\n"
                                                                + "\tat org.apache.camel.Foo.bar(Foo.java:1)\n"
                                                                + "Caused by: java.lang.IllegalArgumentException: no key\n"
                                                                + "\t... 12 more\n");

        LaunchManager.LaunchOutcome outcome = LaunchManager.LaunchOutcome.failed(out);

        assertThat(outcome.ok()).isFalse();
        assertThat(outcome.log()).isEqualTo(" INFO Starting\n"
                                            + "ERROR Error starting Camel: Property with key [env:OPENAI_API_KEY] returned null\n"
                                            + "Caused by: java.lang.IllegalArgumentException: no key");
    }

    private static void append(Path file, String text) throws Exception {
        Files.writeString(file, text, StandardOpenOption.APPEND);
    }
}
