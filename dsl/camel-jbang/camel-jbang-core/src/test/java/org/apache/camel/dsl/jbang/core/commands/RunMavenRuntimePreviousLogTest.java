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
        // quarkus:run, quarkus:dev and spring-boot:run run the app in a process of its own; its log says no error here
        log("orders", "2026-10-20 09:56:19.842  INFO 16321 --- [           main] route1 : the app stopped");

        Run.keepRunLog("orders", 0, 1, false);

        PreviousRun.Info info = previous("orders");
        assertThat(info.pid()).isZero();
        assertThat(info.exitCode()).isNull();
        assertThat(info.failure()).isEqualTo("Maven ended with exit code 1");
        assertThat(info.describe()).contains("The previous run of orders ended at").endsWith(": Maven ended with exit code 1");
    }

    @Test
    void theFailureOfAMavenRunIsTheErrorItsLogEndsWith() throws Exception {
        // Spring Boot logs the failure, with the stack trace; the root cause says why
        log("orders",
                """
                        2026-10-20T09:56:19.838+02:00  INFO 16321 --- [           main] o.a.c.impl.engine.AbstractCamelContext   : Apache Camel 4.24.0 (orders) is starting
                        2026-10-20T09:56:19.842+02:00 ERROR 16321 --- [           main] o.s.boot.SpringApplication               : Application run failed
                        org.apache.camel.FailedToCreateRouteException: Failed to create route: boom
                        \tat org.apache.camel.reifier.RouteReifier.createRoute(RouteReifier.java:95)
                        Caused by: java.lang.IllegalArgumentException: Invalid time pattern: notanumber
                        \tat org.apache.camel.util.TimeUtils.toMilliSeconds(TimeUtils.java:214)
                        """);

        Run.keepRunLog("orders", 0, 1, false);

        PreviousRun.Info info = previous("orders");
        assertThat(info.exitCode()).isNull();
        assertThat(info.failure())
                .isEqualTo("Application run failed: java.lang.IllegalArgumentException: Invalid time pattern: notanumber");
    }

    @Test
    void theFirstOfTheErrorsAtTheEndIsWhyTheRunFailed() throws Exception {
        // Quarkus logs the failure of Camel, then that Quarkus failed to start (in the format camel run gives its log file)
        log("orders",
                """
                        2026-10-20 11:19:03.060 ERROR 24512 --- [           main] o.a.c.quarkus.main.CamelMainRuntime      : Failed to start application: org.apache.camel.FailedToCreateRouteException: Failed to create route: boom
                        org.apache.camel.FailedToCreateRouteException: Failed to create route: boom
                        \tat org.apache.camel.reifier.RouteReifier.createRoute(RouteReifier.java:95)
                        2026-10-20 11:19:03.072 ERROR 24512 --- [           main] io.quarkus.runtime.Application           : Failed to start application: java.lang.RuntimeException: Failed to start quarkus
                        java.lang.RuntimeException: Failed to start quarkus
                        \tat io.quarkus.runner.ApplicationImpl.doStart(Unknown Source)
                        """);

        Run.keepRunLog("orders", 0, 1, false);

        assertThat(previous("orders").failure()).isEqualTo(
                "Failed to start application: org.apache.camel.FailedToCreateRouteException: Failed to create route: boom");
    }

    @Test
    void theFailureOfAnAppIsTheErrorItsLogEndsWith() throws Exception {
        // Camel Main logs that it failed to start
        log("orders",
                """
                        2026-10-20 09:55:49.909  INFO 16017 --- [           main] he.camel.cli.connector.LocalCliConnector : Camel CLI connector enabled
                        2026-10-20 09:55:49.950 ERROR 16017 --- [           main] org.apache.camel.main.MainSupport        : Error starting Camel: org.apache.camel.FailedToCreateRouteException: Failed to create route: boom
                        org.apache.camel.FailedToCreateRouteException: Failed to create route: boom
                        \tat org.apache.camel.reifier.RouteReifier.createRoute(RouteReifier.java:95)
                        """);

        Run.keepRunLog("orders", 16017, 1, true);

        PreviousRun.Info info = previous("orders");
        assertThat(info.exitCode()).isEqualTo(1);
        assertThat(info.failure())
                .isEqualTo("Error starting Camel: org.apache.camel.FailedToCreateRouteException: Failed to create route: boom");
        assertThat(info.describe()).endsWith("with exit code 1: Error starting Camel:"
                                             + " org.apache.camel.FailedToCreateRouteException: Failed to create route: boom");
    }

    @Test
    void anErrorLongBeforeTheEndIsNotWhyTheRunFailed() throws Exception {
        StringBuilder sb = new StringBuilder(
                "2026-10-20 09:00:00.000 ERROR 1 --- [main] route1 : Failed delivery of an exchange\n");
        for (int i = 0; i < 30; i++) {
            sb.append("2026-10-20 09:00:01.000  INFO 1 --- [main] route1 : line ").append(i).append('\n');
        }
        log("orders", sb.toString());

        Run.keepRunLog("orders", 0, 1, false);

        assertThat(previous("orders").failure()).isEqualTo("Maven ended with exit code 1");
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
