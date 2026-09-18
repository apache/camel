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
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InfraLog}.
 *
 * {@code infra log} discovers what to tail from the pid files, so it only follows services that are actually running,
 * and it follows <em>every</em> instance of a service rather than the first log file that happens to match the alias.
 * Only the paths that return without tailing are covered here, because tailing itself blocks until the services stop.
 */
class InfraLogTest extends InfraCommandTestSupport {

    @Test
    void shouldReportNoRunningServices() throws Exception {
        InfraLog command = new InfraLog(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();

        assertEquals(-1, exit);
        assertTrue(printer.getOutput().contains("There are no running services"),
                "was: " + printer.getOutput());
    }

    @Test
    void shouldReportMissingLogForNamedService() throws Exception {
        writePidFile("ftp", 1234, "{}");

        InfraLog command = new InfraLog(new CamelJBangMain().withPrinter(printer));
        command.aliveCheck = pid -> true;
        command.serviceName = List.of("kafka");
        int exit = command.doCall();

        assertEquals(-1, exit);
        assertTrue(printer.getOutput().contains("Log not found for service kafka"),
                "was: " + printer.getOutput());
    }

    @Test
    void shouldNotTailAServiceWhoseProcessIsGone() throws Exception {
        // the log of a service that was killed hard is pruned along with its pid file, rather than tailed forever
        writePidFile("ftp", 1234, "{}");
        writeLogFile("ftp", 1234, "starting\n");

        InfraLog command = new InfraLog(new CamelJBangMain().withPrinter(printer));
        command.aliveCheck = pid -> false;
        int exit = command.doCall();

        assertEquals(-1, exit);
        assertTrue(printer.getOutput().contains("There are no running services"),
                "was: " + printer.getOutput());
        assertFalse(Files.exists(pidFile("ftp", 1234)), "the dead instance's pid file should be pruned");
        assertFalse(Files.exists(logFile("ftp", 1234)), "the dead instance's log file should be pruned");
    }
}
