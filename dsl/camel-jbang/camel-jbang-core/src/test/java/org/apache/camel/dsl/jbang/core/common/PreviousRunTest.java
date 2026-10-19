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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Signal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25784: the log of a run that ended is kept by the name of the integration, as kubectl logs --previous shows the
 * log of the previous instance of a container.
 */
class PreviousRunTest {

    // pids no process has (above the highest pid of Linux and macOS)
    private static final long GONE_PID = 999_999_991L;
    private static final long GONE_PID_2 = 999_999_992L;

    @TempDir
    Path home;

    private Path previousHome;
    private Path camelDir;

    @BeforeEach
    void useHome() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        camelDir = CommandLineHelper.getCamelDir();
        Files.createDirectories(camelDir);
    }

    @AfterEach
    void restoreHome() {
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void theLogIsKeptByTheNameOfTheIntegration() throws Exception {
        Path log = writeLog("123.log", "Starting", "Failed to start route route1");

        Path kept = PreviousRun.keep(log, "orders", 123, 70, "Failed to start route route1 because: boom");

        assertThat(kept).isEqualTo(camelDir.resolve("orders-previous.log"));
        assertThat(log).doesNotExist();
        assertThat(Files.readAllLines(kept)).containsExactly("Starting", "Failed to start route route1");
        PreviousRun.Info info = only(PreviousRun.find("orders"));
        assertThat(info.pid()).isEqualTo(123);
        assertThat(info.exitCode()).isEqualTo(70);
        assertThat(info.failure()).isEqualTo("Failed to start route route1 because: boom");
        assertThat(info.directory()).isEqualTo(System.getProperty("user.dir"));
        assertThat(info.started()).isPositive();
        assertThat(info.ended()).isGreaterThanOrEqualTo(info.started());
        assertThat(info.describe()).startsWith("The previous run of orders (pid 123) ended at ")
                .endsWith(" ago) with exit code 70: Failed to start route route1 because: boom");
    }

    @Test
    void theNextRunThatEndsReplacesIt() throws Exception {
        PreviousRun.keep(writeLog("123.log", "first run"), "orders", 123, 70, "boom");
        PreviousRun.keep(writeLog("456.log", "second run"), "orders", 456, 0, null);

        PreviousRun.Info info = only(PreviousRun.find("orders"));
        assertThat(info.pid()).isEqualTo(456);
        assertThat(info.exitCode()).isZero();
        assertThat(info.failure()).isNull();
        assertThat(info.describe()).endsWith(" with exit code 0");
        assertThat(Files.readAllLines(info.log())).containsExactly("second run");
    }

    @Test
    void aRunIsFoundAsARunningIntegrationIs() throws Exception {
        PreviousRun.keep(writeLog("5.log", "orders"), "orders", 5, 0, null);
        PreviousRun.keep(writeLog("6.log", "orders-eu"), "orders-eu", 6, 0, null);
        PreviousRun.keep(writeLog("7.log", "payments"), "payments", 7, 0, null);

        // the whole name of a run is that run, a part of it a prefix
        assertThat(names(PreviousRun.find("orders"))).containsExactly("orders");
        assertThat(names(PreviousRun.find("ord"))).containsExactlyInAnyOrder("orders", "orders-eu");
        assertThat(names(PreviousRun.find("orders-eu!"))).containsExactly("orders-eu");
        assertThat(names(PreviousRun.find("ord!"))).isEmpty();
        assertThat(names(PreviousRun.find("pay*"))).containsExactly("payments");
        // a number is the pid it ran as
        assertThat(names(PreviousRun.find("7"))).containsExactly("payments");
        assertThat(PreviousRun.find("*")).hasSize(3);
        assertThat(PreviousRun.find("invoices")).isEmpty();
    }

    @Test
    void theNewestRunComesFirst() throws Exception {
        PreviousRun.keep(writeLog("5.log", "x"), new PreviousRun.Info("orders", 5, null, 0, 1000, 0, null, null, null));
        PreviousRun.keep(writeLog("6.log", "x"), new PreviousRun.Info("payments", 6, null, 0, 3000, 0, null, null, null));
        PreviousRun.keep(writeLog("7.log", "x"), new PreviousRun.Info("invoices", 7, null, 0, 2000, 0, null, null, null));

        assertThat(names(PreviousRun.findAll())).containsExactly("payments", "invoices", "orders");
    }

    @Test
    void aLargeLogKeepsItsEndFromTheStartOfALine() throws Exception {
        Path log = writeLog("123.log", "first line", "second line", "third");
        Path target = camelDir.resolve("copy.log");

        // 10 bytes from the end starts in the second line
        PreviousRun.copyEnd(log, target, 10);

        assertThat(Files.readString(target)).isEqualTo("third" + System.lineSeparator());
    }

    @Test
    void theLogOfAKilledProcessIsKeptByTheNameInItsStatus() throws Exception {
        writeLog(GONE_PID + ".log", "Started", "Processing");
        writeStatus(GONE_PID, "orders");

        PreviousRun.Info info = only(PreviousRun.find("orders"));

        assertThat(info.pid()).isEqualTo(GONE_PID);
        assertThat(info.exitCode()).isNull();
        assertThat(info.failure()).isEqualTo(PreviousRun.GONE);
        assertThat(info.directory()).isEqualTo("/work/orders");
        assertThat(Files.readAllLines(info.log())).containsExactly("Started", "Processing");
        assertThat(camelDir.resolve(GONE_PID + ".log")).doesNotExist();
    }

    @Test
    void theLogOfAKilledProcessOlderThanThePreviousRunIsRemoved() throws Exception {
        PreviousRun.keep(writeLog("123.log", "newer run"), "orders", 123, 0, null);
        Path old = writeLog(GONE_PID + ".log", "older run");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(1, ChronoUnit.HOURS)));
        writeStatus(GONE_PID, "orders");

        PreviousRun.Info info = only(PreviousRun.find("orders"));

        assertThat(Files.readAllLines(info.log())).containsExactly("newer run");
        assertThat(old).doesNotExist();
    }

    @Test
    void theLogOfAProcessWithoutNameIsRemovedAfterADay() throws Exception {
        Path old = writeLog(GONE_PID + ".log", "old");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));
        Path recent = writeLog(GONE_PID_2 + ".log", "recent");
        Files.setLastModifiedTime(recent, FileTime.from(Instant.now().minus(1, ChronoUnit.HOURS)));

        assertThat(PreviousRun.findAll()).isEmpty();

        assertThat(old).doesNotExist();
        assertThat(recent).exists();
    }

    @Test
    void theLogOfARunningProcessIsLeftAlone() throws Exception {
        long pid = ProcessHandle.current().pid();
        Path log = writeLog(pid + ".log", "running");
        Files.setLastModifiedTime(log, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));
        writeStatus(pid, "orders");

        assertThat(PreviousRun.findAll()).isEmpty();

        assertThat(log).exists();
    }

    @Test
    void aRunThatEndedKeepsItsLogOnce() throws Exception {
        Path log = writeLog("123.log", "the run");
        PreviousRun.Keeper keeper = new PreviousRun.Keeper(log, () -> "orders");

        keeper.ended(0, null);
        // a log of the same name afterwards (such as another run in this JVM) is not kept as the JVM exits
        writeLog("123.log", "after the run");
        keeper.exiting(0);

        PreviousRun.Info info = only(PreviousRun.find("orders"));
        assertThat(info.pid()).isEqualTo(ProcessHandle.current().pid());
        assertThat(info.exitCode()).isZero();
        assertThat(Files.readAllLines(info.log())).containsExactly("the run");
    }

    @Test
    void aRunThatASignalStoppedEndsWithTheExitCodeOfTheJvm() throws Exception {
        Path log = writeLog("123.log", "the run");
        PreviousRun.Keeper keeper = new PreviousRun.Keeper(log, () -> "orders");
        // SIGTERM, on which the JVM exits with 128 + the number of the signal
        keeper.stoppedBy(new Signal("TERM"));

        // Camel stopped as it should, so the run answers 0
        keeper.ended(0, null);

        PreviousRun.Info info = only(PreviousRun.find("orders"));
        assertThat(info.exitCode()).isEqualTo(128 + new Signal("TERM").getNumber());
        assertThat(info.signal()).isEqualTo("SIGTERM");
        assertThat(info.describe()).endsWith(" with exit code " + info.exitCode() + " (SIGTERM)");
    }

    @Test
    void aJvmThatExitsKeepsTheLogOfARunThatDidNotEnd() throws Exception {
        Path log = writeLog("123.log", "the run");
        PreviousRun.Keeper keeper = new PreviousRun.Keeper(log, () -> "orders");
        try {
            // the run gets a few seconds to end: not in a test
            keeper.exiting(0);

            PreviousRun.Info info = only(PreviousRun.find("orders"));
            assertThat(info.exitCode()).isNull();
            assertThat(Files.readAllLines(info.log())).containsExactly("the run");
        } finally {
            keeper.ended(null, null);
        }
    }

    @Test
    void aNameIsAFileName() {
        assertThat(PreviousRun.fileName("orders-eu.v2")).isEqualTo("orders-eu.v2");
        assertThat(PreviousRun.fileName("my app/1")).isEqualTo("my_app_1");
        assertThat(PreviousRun.fileName("..")).isEqualTo("_..");
    }

    @Test
    void theFilesOfAPreviousRun() {
        assertThat(PreviousRun.isPreviousRunFile("orders-previous.log")).isTrue();
        assertThat(PreviousRun.isPreviousRunFile("orders-previous.json")).isTrue();
        assertThat(PreviousRun.isPreviousRunFile("123.log")).isFalse();
        assertThat(PreviousRun.isPreviousRunFile("123-status.json")).isFalse();
    }

    private Path writeLog(String name, String... lines) throws Exception {
        return Files.write(camelDir.resolve(name), List.of(lines));
    }

    private void writeStatus(long pid, String name) throws Exception {
        JsonObject context = new JsonObject();
        context.put("name", name);
        JsonObject runtime = new JsonObject();
        runtime.put("directory", "/work/" + name);
        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("runtime", runtime);
        Files.writeString(camelDir.resolve(pid + "-status.json"), root.toJson());
    }

    private static PreviousRun.Info only(List<PreviousRun.Info> found) {
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    private static List<String> names(List<PreviousRun.Info> found) {
        return found.stream().map(PreviousRun.Info::name).toList();
    }
}
