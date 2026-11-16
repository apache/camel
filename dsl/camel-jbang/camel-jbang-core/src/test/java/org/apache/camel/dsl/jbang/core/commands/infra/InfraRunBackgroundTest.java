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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.BackgroundLog;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel infra run --background has the output of the service in its log, and waits until the service has written its
 * pid file, which it does once it has started (CAMEL-27004: the output went to a pipe nobody read, so a service that
 * could not start was gone without a word, and the command exited with 0).
 * <p>
 * A shell stands in for the service: it writes the pid file <tt>infra-&lt;service&gt;-$$.json</tt> as infra run does.
 */
@DisabledOnOs(OS.WINDOWS)
class InfraRunBackgroundTest extends InfraCommandTestSupport {

    private static final Pattern PID = Pattern.compile("(?:PID: |\"pid\"\\s*:\\s*\")(\\d+)");

    @AfterEach
    void stopService() {
        // the shell that stands in for the service, which the command left running
        Matcher m = PID.matcher(printer.getStdout() + "\n" + printer.getStderr());
        if (m.find()) {
            ProcessHandle.of(Long.parseLong(m.group(1))).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    void waitsForTheServiceToStart() throws Exception {
        InfraRun infraRun = infraRun();

        int exit = infraRun.startInBackground(
                service("echo Starting service kafka; sleep 0.5; printf '{\"getBootstrapServers\":\"localhost:9092\"}' > "
                        + pidFileOfShell("kafka") + "; sleep 60"),
                "kafka");

        assertThat(exit).isZero();
        long pid = pid();
        assertThat(printer.getStdout())
                .contains("Running kafka in background with PID: " + pid + " (waiting for it to start)")
                .contains("\"getBootstrapServers\" : \"localhost:9092\"")
                .contains("Log: " + BackgroundLog.of(pid));
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive)).contains(true);
        // the log has the output of the service
        assertThat(BackgroundLog.read(BackgroundLog.of(pid))).contains("Starting service kafka");
    }

    @Test
    void theServiceDataAsJson() throws Exception {
        InfraRun infraRun = infraRun();
        infraRun.jsonOutput = true;

        int exit = infraRun.startInBackground(
                service("printf '{\"getBootstrapServers\":\"localhost:9092\"}' > " + pidFileOfShell("kafka")
                        + "; sleep 60"),
                "kafka");

        assertThat(exit).isZero();
        long pid = pid();
        JsonObject json = (JsonObject) Jsoner.deserialize(printer.getStdout());
        assertThat(json.getString("pid")).isEqualTo(String.valueOf(pid));
        assertThat(json.getString("alias")).isEqualTo("kafka");
        assertThat(json.getString("log")).isEqualTo(BackgroundLog.of(pid).toString());
        assertThat(((Map<?, ?>) json.get("serviceData")).get("getBootstrapServers")).isEqualTo("localhost:9092");
    }

    @Test
    void aServiceThatCannotStartTellsWhy() throws Exception {
        InfraRun infraRun = infraRun();

        int exit = infraRun.startInBackground(
                service("echo 'Could not find a valid Docker environment'; exit 1"), "kafka");

        assertThat(exit).isEqualTo(1);
        long pid = pid();
        assertThat(printer.getStderr()).contains("Service kafka (PID: " + pid + ") did not start (exit code 1)");
        assertThat(printer.getStdout()).contains("Could not find a valid Docker environment");
        // it was shown, and the service is gone
        assertThat(BackgroundLog.of(pid)).doesNotExist();
    }

    @Test
    void whyAServiceCannotStartIsOnStderrWithJson() throws Exception {
        InfraRun infraRun = infraRun();
        infraRun.jsonOutput = true;

        int exit = infraRun.startInBackground(
                service("echo 'Could not find a valid Docker environment'; exit 3"), "kafka");

        assertThat(exit).isEqualTo(3);
        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).contains("did not start (exit code 3)")
                .contains("Could not find a valid Docker environment");
    }

    @Test
    void aServiceThatEndsWithZeroBeforeItStartedIsAFailure() throws Exception {
        int exit = infraRun().startInBackground(service("exit 0"), "kafka");

        assertThat(exit).isEqualTo(CliExitCode.FAILURE);
        assertThat(printer.getStderr()).contains("did not start (exit code 0)");
    }

    @Test
    void aServiceStillStartingIsLeftRunning() throws Exception {
        InfraRun infraRun = infraRun();
        infraRun.backgroundWait = 500;

        // such as pulling its container image the first time
        assertThatThrownBy(() -> infraRun.startInBackground(service("echo Pulling image; sleep 60"), "kafka"))
                .isInstanceOf(CliException.class)
                .hasMessageContaining("is still starting after 500ms")
                .hasMessageContaining("the log is in " + BackgroundLog.of(pid()))
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.TIMEOUT);
        assertThat(ProcessHandle.of(pid()).map(ProcessHandle::isAlive)).contains(true);
    }

    @Test
    void noWait() throws Exception {
        InfraRun infraRun = infraRun();
        infraRun.backgroundWait = 0;

        int exit = infraRun.startInBackground(service("sleep 60"), "kafka");

        assertThat(exit).isZero();
        long pid = pid();
        assertThat(printer.getStdout())
                .contains("Running kafka in background with PID: " + pid)
                .doesNotContain("waiting")
                .contains("Log: " + BackgroundLog.of(pid));
    }

    @Test
    void theOutputOfAServiceThatIsGoneIsRemoved() throws Exception {
        writePidFile("kafka", 1234, "{}");
        Files.writeString(BackgroundLog.of(1234), "Starting service kafka");

        InfraStop command = new InfraStop(new CamelJBangMain().withPrinter(printer));
        command.aliveCheck = pid -> false;
        command.findRunningServices("kafka");

        assertThat(BackgroundLog.of(1234)).doesNotExist();
    }

    private InfraRun infraRun() {
        return new InfraRun(new CamelJBangMain().withPrinter(printer));
    }

    // the pid file of the shell that stands in for the service, named after its pid ($$)
    private static String pidFileOfShell(String service) {
        Path dir = CommandLineHelper.getCamelDir();
        return "'" + dir + "/infra-" + service + "-'$$'.json'";
    }

    private static ProcessBuilder service(String script) {
        return new ProcessBuilder("sh", "-c", script);
    }

    // the pid of the service, as the command printed it
    private long pid() {
        Matcher m = PID.matcher(printer.getStdout() + "\n" + printer.getStderr());
        assertThat(m.find()).as("the command printed the pid").isTrue();
        return Long.parseLong(m.group(1));
    }
}
