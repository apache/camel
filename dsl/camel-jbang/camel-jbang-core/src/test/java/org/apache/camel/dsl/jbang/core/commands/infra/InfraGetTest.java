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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InfraGet}.
 *
 * The command resolves running infra services from the pid files ({@code infra-<service>-<pid>.json}) stored in the
 * Camel directory, then prints a header plus the file contents for each match (or a JSON array with {@code --json}). A
 * name that matches no running service exits with code 3. The home directory is redirected to an isolated folder by
 * {@link InfraCommandTestSupport} so the tests are independent of any real local services.
 */
class InfraGetTest extends InfraCommandTestSupport {

    @Test
    void shouldPrintServiceHeaderAndContentByName() throws Exception {
        writePidFile("kafka", 1234, "{\"port\":9092}");

        InfraGet command = command("kafka");
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        // the service name and pid are parsed back out of the file name
        assertTrue(out.contains("Service kafka (PID: 1234)"), "should print the service header, was: " + out);
        assertTrue(out.contains("{\"port\":9092}"), "should print the pid file contents, was: " + out);
    }

    @Test
    void shouldMatchByExactPid() throws Exception {
        writePidFile("kafka", 1234, "{\"port\":9092}");
        writePidFile("ftp", 5678, "{\"port\":21}");

        InfraGet command = command("5678");
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        // a numeric argument matches the pid, not the service name
        assertTrue(out.contains("Service ftp (PID: 5678)"), "should match the ftp service by pid, was: " + out);
        assertFalse(out.contains("kafka"), "should not print the unmatched kafka service, was: " + out);
    }

    @Test
    void shouldParseHyphenatedServiceName() throws Exception {
        // service names may contain hyphens; the whole name between first and last hyphen must be recovered
        writePidFile("hive-mq", 1234, "{\"port\":1883}");

        InfraGet command = command("hive-mq");
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("Service hive-mq (PID: 1234)"),
                "should recover the full hyphenated service name, was: " + out);
    }

    @Test
    void shouldReportNotFoundWhenNoServiceMatches() throws Exception {
        writePidFile("kafka", 1234, "{\"port\":9092}");

        InfraGet command = command("redis");
        CliException e = assertThrows(CliException.class, command::doCall);

        assertEquals(CliExitCode.NOT_FOUND, e.getExitCode());
        assertEquals("No running infra service matches: redis", e.getMessage());
        assertEquals("", printer.getOutput(), "no matching service must produce no output");
    }

    @Test
    void shouldPrintNothingWhenNoServiceRuns() throws Exception {
        InfraGet command = command("*");
        int exit = command.doCall();

        assertEquals(0, exit);
        assertEquals("", printer.getOutput());
    }

    @Test
    void shouldSkipServiceWhoseProcessIsGone() throws Exception {
        writePidFile("kafka", 1234, "{\"port\":9092}");

        InfraGet command = command("*");
        command.aliveCheck = pid -> false;
        int exit = command.doCall();

        assertEquals(0, exit);
        assertEquals("", printer.getOutput());
        assertFalse(pidFile("kafka", 1234).toFile().exists(), "the pid file of a gone process is removed");
    }

    @Test
    void shouldPrintJson() throws Exception {
        writePidFile("kafka", 1234, "{\"port\":9092}");
        writePidFile("ftp", 5678, "not json");

        InfraGet command = command("*");
        command.jsonOutput = true;
        int exit = command.doCall();

        assertEquals(0, exit);
        JsonArray rows = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        assertEquals(2, rows.size(), printer.getStdout());
        // sorted by service name
        JsonObject ftp = rows.getMap(0);
        assertEquals("ftp", ftp.getString("alias"));
        assertEquals("5678", ftp.getString("pid"));
        assertFalse(ftp.containsKey("serviceData"), "service data that is not JSON is left out");
        JsonObject kafka = rows.getMap(1);
        assertEquals("kafka", kafka.getString("alias"));
        JsonObject data = kafka.getMap("serviceData");
        assertEquals(9092, data.getInteger("port"));
    }

    private InfraGet command(String name) {
        InfraGet command = new InfraGet(new CamelJBangMain().withPrinter(printer));
        command.name = name;
        // the pids in these tests are not real processes
        command.aliveCheck = pid -> true;
        return command;
    }
}
