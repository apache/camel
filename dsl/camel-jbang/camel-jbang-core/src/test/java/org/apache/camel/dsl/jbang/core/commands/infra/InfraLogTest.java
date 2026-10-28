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
import java.time.Duration;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Unit tests for {@link InfraLog}.
 *
 * {@code infra log} discovers what to tail from the pid files, so it only follows services that are actually running,
 * and it follows <em>every</em> instance of a service rather than the first log file that happens to match the alias.
 * Without an interactive terminal it prints the end of the logs and returns, unless it follows for a --duration.
 */
class InfraLogTest extends InfraCommandTestSupport {

    @Test
    void shouldReportNoRunningServices() throws Exception {
        InfraLog command = new InfraLog(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();

        assertEquals(3, exit);
        assertEquals("ERROR: There are no running services", printer.getStderr());
    }

    @Test
    void shouldReportMissingLogForNamedService() throws Exception {
        writePidFile("ftp", 1234, "{}");

        InfraLog command = new InfraLog(new CamelJBangMain().withPrinter(printer));
        command.aliveCheck = pid -> true;
        command.serviceName = List.of("kafka");
        int exit = command.doCall();

        assertEquals(3, exit);
        assertEquals("ERROR: Log not found for service kafka", printer.getStderr());
    }

    @Test
    void shouldNotTailAServiceWhoseProcessIsGone() throws Exception {
        // the log of a service that was killed hard is pruned along with its pid file, rather than tailed forever
        writePidFile("ftp", 1234, "{}");
        writeLogFile("ftp", 1234, "starting\n");

        InfraLog command = new InfraLog(new CamelJBangMain().withPrinter(printer));
        command.aliveCheck = pid -> false;
        int exit = command.doCall();

        assertEquals(3, exit);
        assertEquals("ERROR: There are no running services", printer.getStderr());
        assertFalse(Files.exists(pidFile("ftp", 1234)), "the dead instance's pid file should be pruned");
        assertFalse(Files.exists(logFile("ftp", 1234)), "the dead instance's log file should be pruned");
    }

    @Test
    void shouldPrintTheEndOfTheLogWithoutTerminal() throws Exception {
        assumeFalse(EnvironmentHelper.isInteractiveTerminal(), "runs without an interactive terminal");
        writePidFile("ftp", 1234, "{}");
        writeLogFile("ftp", 1234, "one\ntwo\nthree\n");

        InfraLog command = new InfraLog(new CamelJBangMain().withPrinter(printer));
        command.aliveCheck = pid -> true;
        new CommandLine(command).parseArgs("--lines=2");
        int exit = assertTimeoutPreemptively(Duration.ofSeconds(30), command::doCall);

        assertEquals(0, exit);
        assertEquals("[ftp] two" + System.lineSeparator() + "[ftp] three", printer.getOutput());
    }

    @Test
    void shouldFollowForTheDuration() throws Exception {
        writePidFile("ftp", 1234, "{}");
        writeLogFile("ftp", 1234, "starting\n");

        InfraLog command = new InfraLog(new CamelJBangMain().withPrinter(printer));
        command.aliveCheck = pid -> true;
        new CommandLine(command).parseArgs("--follow", "--duration=300");
        StopWatch watch = new StopWatch();
        int exit = assertTimeoutPreemptively(Duration.ofSeconds(30), command::doCall);

        assertEquals(0, exit);
        assertTrue(watch.taken() >= 300, "should follow for the duration");
        assertEquals("[ftp] starting", printer.getOutput());
    }
}
