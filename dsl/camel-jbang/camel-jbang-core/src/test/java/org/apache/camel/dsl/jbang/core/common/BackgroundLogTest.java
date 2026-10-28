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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The output of an integration that runs in the background goes to ~/.camel/&lt;pid&gt;-run.log.
 */
class BackgroundLogTest {

    @TempDir
    Path home;

    private Path previousHome;

    @BeforeEach
    void setup() {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
    }

    @AfterEach
    void restore() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void outputOfTheProcessGoesToTheLogOfItsPid() throws Exception {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "-version");

        Path log = BackgroundLog.redirect(pb);
        assertThat(log.getParent()).isEqualTo(CommandLineHelper.getCamelDir());
        Process process = pb.start();
        // (renamed once it has ended, so the test does not depend on renaming a file in use, which Windows refuses)
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        Path named = BackgroundLog.started(log, process.pid());

        assertThat(named).isEqualTo(BackgroundLog.of(process.pid()));
        assertThat(named.getFileName()).hasToString(process.pid() + "-run.log");
        assertThat(log).doesNotExist();
        // java -version prints on stderr, which goes to the log too
        assertThat(Files.readString(named)).contains("version");

        BackgroundLog.remove(process.pid());
        assertThat(named).doesNotExist();
    }

    @Test
    void commandThatCannotRunLeavesNoLog() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(home.resolve("no-such-command").toString());

        assertThatThrownBy(() -> BackgroundLog.start(pb)).isInstanceOf(IOException.class);

        try (Stream<Path> files = Files.list(CommandLineHelper.getCamelDir())) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void tailOfALongLog() throws Exception {
        Path log = home.resolve("long.log");
        Files.write(log, IntStream.rangeClosed(1, 100_000).mapToObj(i -> "line " + i).toList());

        assertThat(BackgroundLog.tail(log, 2)).isEqualTo("line 99999" + System.lineSeparator() + "line 100000");
        assertThat(BackgroundLog.tail(log, 0)).isEmpty();
    }

    @Test
    void logThatIsNotUtf8() throws Exception {
        Path log = home.resolve("latin1.log");
        Files.write(log, new byte[] { 'c', 'a', 'f', (byte) 0xE9, '\n' });

        // as the output of a process may not be UTF-8, the bytes that are not are replaced
        assertThat(BackgroundLog.read(log)).isEqualTo("caf\uFFFD\n");
        assertThat(BackgroundLog.tail(log, Integer.MAX_VALUE)).isEqualTo("caf\uFFFD");
        assertThat(BackgroundLog.read(home.resolve("missing.log"))).isEmpty();
    }

    @Test
    void tailOfTheLog() throws Exception {
        Path log = home.resolve("some.log");
        Files.writeString(log, "one\ntwo\nthree\n");

        assertThat(BackgroundLog.tail(log, 2)).isEqualTo("two" + System.lineSeparator() + "three");
        assertThat(BackgroundLog.tail(log, 10)).isEqualTo(String.join(System.lineSeparator(), "one", "two", "three"));
        assertThat(BackgroundLog.tail(home.resolve("missing.log"), 2)).isEmpty();
    }
}
