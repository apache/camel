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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel infra stop: a service is stopped by removing its pid file, which the infra run of the service watches.
 */
class InfraStopTest extends InfraCommandTestSupport {

    @Test
    void stopsTheMatchingService() throws Exception {
        writePidFile("kafka", 1234, "{}");
        writePidFile("ftp", 5678, "{}");

        assertThat(command("kafka").doCall()).isZero();

        assertThat(pidFile("kafka", 1234)).doesNotExist();
        assertThat(pidFile("ftp", 5678)).exists();
        assertThat(printer.getStdout()).isEqualTo("Shutting down external services (PID: 1234)");
    }

    @Test
    void stopsAllServices() throws Exception {
        writePidFile("kafka", 1234, "{}");
        writePidFile("ftp", 5678, "{}");

        assertThat(command("*").doCall()).isZero();

        assertThat(pidFile("kafka", 1234)).doesNotExist();
        assertThat(pidFile("ftp", 5678)).doesNotExist();
    }

    @Test
    void stopsByPid() throws Exception {
        writePidFile("kafka", 1234, "{}");

        assertThat(command("1234").doCall()).isZero();

        assertThat(pidFile("kafka", 1234)).doesNotExist();
    }

    @Test
    void stopsAsJson() throws Exception {
        writePidFile("kafka", 1234, "{}");

        InfraStop command = command("kafka");
        command.jsonOutput = true;
        assertThat(command.doCall()).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        JsonObject row = rows.getMap(0);
        assertThat(row.getString("alias")).isEqualTo("kafka");
        assertThat(row.getString("status")).isEqualTo("stopping");
    }

    @Test
    void serviceThatIsNotRunningIsNotFound() throws Exception {
        writePidFile("kafka", 1234, "{}");

        assertThatThrownBy(() -> command("ftp").doCall())
                .isInstanceOf(CliException.class)
                .hasMessage("No running infra service matches: ftp")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(pidFile("kafka", 1234)).exists();
    }

    @Test
    void serviceWhoseProcessIsGoneIsNotFound() throws Exception {
        writePidFile("kafka", 1234, "{}");
        writeLogFile("kafka", 1234, "started");

        InfraStop command = command("kafka");
        command.aliveCheck = pid -> false;

        assertThatThrownBy(command::doCall)
                .isInstanceOf(CliException.class)
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        // the files it left behind are removed
        assertThat(pidFile("kafka", 1234)).doesNotExist();
        assertThat(logFile("kafka", 1234)).doesNotExist();
    }

    @Test
    void nothingToStop() throws Exception {
        assertThat(command("*").doCall()).isZero();

        assertThat(printer.getStdout()).isEmpty();
    }

    private InfraStop command(String name) {
        InfraStop command = new InfraStop(new CamelJBangMain().withPrinter(printer));
        command.name = name;
        // the pid files have made-up pids
        command.aliveCheck = pid -> true;
        return command;
    }
}
