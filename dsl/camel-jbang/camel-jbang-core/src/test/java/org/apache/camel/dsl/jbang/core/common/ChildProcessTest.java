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
package org.apache.camel.dsl.jbang.core.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A process the CLI started stops with the processes it started (as Maven starts the app in a JVM of its own), and one
 * that does not stop in time is killed.
 */
@DisabledOnOs(OS.WINDOWS)
class ChildProcessTest {

    @TempDir
    Path dir;

    @Test
    void stopsTheProcessAndTheProcessesItStarted() throws Exception {
        Process parent = new ProcessBuilder("sh", "-c", "sleep 60 & echo $! > child.pid; wait")
                .directory(dir.toFile()).start();
        Path childPid = dir.resolve("child.pid");
        await().atMost(10, TimeUnit.SECONDS).until(() -> Files.exists(childPid) && Files.size(childPid) > 0);
        long pid = Long.parseLong(Files.readString(childPid).trim());
        ProcessHandle child = ProcessHandle.of(pid).orElseThrow();

        ChildProcess.stop(parent, 10);

        assertThat(parent.isAlive()).isFalse();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !child.isAlive());
    }

    @Test
    void killsAProcessThatDoesNotStopInTime() throws Exception {
        // ignores SIGTERM
        Process process = new ProcessBuilder("sh", "-c", "trap '' TERM; while true; do sleep 1; done").start();
        StopWatch watch = new StopWatch();

        ChildProcess.stop(process, 1);

        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(watch.taken()).isLessThan(10000);
    }

    @Test
    void theWrapperOfAProjectOrMaven() throws Exception {
        assertThat(MavenCommand.of(dir)).isEqualTo("mvn");

        Path wrapper = Files.writeString(dir.resolve("mvnw"), "#!/bin/sh\n");
        assertThat(MavenCommand.wrapper(dir)).isEqualTo(wrapper);
        // a wrapper that cannot run is not used
        assertThat(MavenCommand.of(dir)).isEqualTo("mvn");
        assertThat(wrapper.toFile().setExecutable(true)).isTrue();
        assertThat(MavenCommand.of(dir)).isEqualTo(wrapper.toString());
    }
}
