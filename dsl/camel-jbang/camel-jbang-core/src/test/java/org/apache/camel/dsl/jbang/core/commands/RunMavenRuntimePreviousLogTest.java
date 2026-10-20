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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PreviousRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25832: camel run on a Maven runtime (quarkus, spring-boot, main, or an existing project) keeps the log of the
 * app as its previous run when the run ends, as it does for the default runtime (CAMEL-25784).
 */
@Isolated
class RunMavenRuntimePreviousLogTest {

    @TempDir
    Path home;

    private String originalHome;
    private Path camelDir;

    @BeforeEach
    void useTempHome() throws Exception {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(home.toString());
        camelDir = CommandLineHelper.getCamelDir();
        Files.createDirectories(camelDir);
    }

    @AfterEach
    void restoreHome() {
        CommandLineHelper.useHomeDir(originalHome);
    }

    private Path log(String name, String content) throws Exception {
        Path log = camelDir.resolve(name + ".log");
        Files.writeString(log, content);
        return log;
    }

    private static PreviousRun.Info previous(String name) {
        List<PreviousRun.Info> found = PreviousRun.find(name + "!");
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    @Test
    void theLogOfAnAppThatFailedIsKeptWithItsPidAndExitCode() throws Exception {
        // java -jar of --runtime=main, or mvn camel:run where Camel runs in the Maven JVM
        Path log = log("orders", "ERROR Failed to start route orders");

        Path kept = Run.keepRunLog("orders", 4242, 1, true);

        assertThat(log).doesNotExist();
        assertThat(kept).hasContent("ERROR Failed to start route orders");
        PreviousRun.Info info = previous("orders");
        assertThat(info.pid()).isEqualTo(4242);
        assertThat(info.exitCode()).isEqualTo(1);
        assertThat(info.signal()).isNull();
        assertThat(info.failure()).isNull();
    }

    @Test
    void theExitCodeOfMavenIsNotGivenAsTheOneOfTheApp() throws Exception {
        // quarkus:run, quarkus:dev and spring-boot:run run the app in a process of its own
        log("orders", "ERROR Application run failed");

        Run.keepRunLog("orders", 0, 1, false);

        PreviousRun.Info info = previous("orders");
        assertThat(info.pid()).isZero();
        assertThat(info.exitCode()).isNull();
        assertThat(info.failure()).isEqualTo("Maven ended with exit code 1");
        assertThat(info.describe()).contains("The previous run of orders ended at").endsWith(": Maven ended with exit code 1");
    }

    @Test
    void aRunThatCtrlCStoppedEndsWithTheSignal() throws Exception {
        log("orders", "INFO Apache Camel 4.24.0 (orders) shutdown");

        Run.keepRunLog("orders", 0, 130, false);

        PreviousRun.Info info = previous("orders");
        assertThat(info.exitCode()).isEqualTo(130);
        assertThat(info.signal()).isEqualTo("SIGINT");
        assertThat(info.failure()).isNull();
        assertThat(info.describe()).endsWith("with exit code 130 (SIGINT)");
    }

    @Test
    void aRunThatEndedWellHasNoFailure() throws Exception {
        log("orders", "INFO Apache Camel 4.24.0 (orders) shutdown");

        Run.keepRunLog("orders", 0, 0, false);

        PreviousRun.Info info = previous("orders");
        assertThat(info.exitCode()).isNull();
        assertThat(info.failure()).isNull();
    }

    @Test
    void withoutALogOrANameNothingIsKept() throws Exception {
        // a build that failed before the app ran, or a run that stopped before it had a name
        assertThat(Run.keepRunLog("orders", 0, 1, false)).isNull();
        assertThat(Run.keepRunLog(null, 0, 1, false)).isNull();
        assertThat(PreviousRun.findAll()).isEmpty();
    }

    @Test
    void aLogARunLeftBehindIsKeptWhenTheNextRunStarts() throws Exception {
        // a run that was killed could not keep its log
        Path log = log("orders", "INFO the run before");
        // whole seconds, as some file systems keep no milliseconds
        long modified = (System.currentTimeMillis() / 1000 - 60) * 1000;
        Files.setLastModifiedTime(log, FileTime.fromMillis(modified));

        Run.keepLeftoverRunLog("orders");

        assertThat(log).doesNotExist();
        PreviousRun.Info info = previous("orders");
        assertThat(info.log()).hasContent("INFO the run before");
        // how it ended is not known: the last write of its log is the closest time
        assertThat(info.ended()).isEqualTo(modified);
        assertThat(info.exitCode()).isNull();
        assertThat(info.failure()).isNull();

        // nothing left behind: the previous run stays as it is
        Run.keepLeftoverRunLog("orders");
        assertThat(previous("orders").log()).hasContent("INFO the run before");
    }

    @Test
    void theSignalOfAnExitCode() {
        assertThat(Run.signalOf(130)).isEqualTo("SIGINT");
        assertThat(Run.signalOf(137)).isEqualTo("SIGKILL");
        assertThat(Run.signalOf(143)).isEqualTo("SIGTERM");
        assertThat(Run.signalOf(0)).isNull();
        assertThat(Run.signalOf(1)).isNull();
    }

    @Test
    void springBootLogsToTheLogOfItsName() throws Exception {
        Path logback = home.resolve("export/src/main/resources/logback.xml");

        Run.writeSpringBootLogbackConfig(logback, "orders");

        String logFile = camelDir.resolve("orders.log").toAbsolutePath().toString().replace("\\", "/");
        assertThat(logback).content().contains("<property name=\"LOG_FILE\" value=\"" + logFile + "\"/>")
                .doesNotContain("{{").doesNotContain("${PID}");
    }
}
