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
 * Unit tests for {@link InfraPs}.
 *
 * Unlike {@code infra list}, which prints every known service as a single row per alias, {@code infra ps} prints one
 * row per <em>running instance</em> ({@code infra-<service>-<pid>.json}) and includes the PID column, so the same
 * service started twice shows up twice. The home directory is redirected to an isolated folder by
 * {@link InfraCommandTestSupport} so the tests do not depend on locally running services, and {@code aliveCheck} is
 * stubbed so the synthetic pids in the pid files are not pruned as stale.
 */
class InfraPsTest extends InfraCommandTestSupport {

    private InfraPs command() {
        InfraPs command = new InfraPs(new CamelJBangMain().withPrinter(printer));
        // the pid files carry synthetic pids, so treat every pid as a live process
        command.aliveCheck = pid -> true;
        return command;
    }

    @Test
    void shouldOnlyListRunningServicesWithPid() throws Exception {
        // kafka is running, minio is not
        writePidFile("kafka", 1234, "{}");

        int exit = command().doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("kafka"), "running kafka service should be listed, was: " + out);
        assertTrue(out.contains("1234"), "running kafka service should show its pid, was: " + out);
        assertFalse(out.contains("minio"), "non-running services must be filtered out, was: " + out);
    }

    @Test
    void shouldListRunningHyphenatedService() throws Exception {
        // regression: the running alias is parsed with the full hyphenated name (e.g. hive-mq), not just the
        // second hyphen-delimited segment ("hive"), otherwise the catalog row would be filtered out
        writePidFile("hive-mq", 1234, "{}");

        int exit = command().doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("hive-mq"), "running hyphenated service should be listed, was: " + out);
        assertTrue(out.contains("1234"), "running hyphenated service should show its pid, was: " + out);
    }

    @Test
    void shouldListNoServicesWhenNoneRunning() throws Exception {
        // no pid files written: the service table must be cleared
        int exit = command().doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertFalse(out.contains("kafka"), "no service rows should be printed when nothing is running, was: " + out);
        assertFalse(out.contains("minio"), "no service rows should be printed when nothing is running, was: " + out);
    }

    @Test
    void shouldListEveryInstanceOfTheSameService() throws Exception {
        // CAMEL-24678: the same service started twice used to collapse into a single row with an arbitrary pid
        writePidFile("ftp", 1234, "{\"port\":21}");
        writePidFile("ftp", 5678, "{\"port\":2121}");

        int exit = command().doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("1234"), "the first ftp instance should be listed, was: " + out);
        assertTrue(out.contains("5678"), "the second ftp instance should be listed, was: " + out);
        // both instances render their own connection details, not the details of whichever file was read first
        assertTrue(out.contains("21"), "the first instance should show its own service data, was: " + out);
        assertTrue(out.contains("2121"), "the second instance should show its own service data, was: " + out);
        assertEquals(2, out.lines().filter(l -> l.contains("ftp")).count(),
                "there should be exactly one row per running instance, was: " + out);
    }

    @Test
    void shouldOrderInstancesByAliasThenPid() throws Exception {
        // the pid used to come from File.list() order, which is filesystem dependent; the output must be stable
        writePidFile("ftp", 5678, "{}");
        writePidFile("ftp", 1234, "{}");
        writePidFile("kafka", 4321, "{}");

        int exit = command().doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.indexOf("1234") < out.indexOf("5678"),
                "instances of the same alias should be ordered numerically by pid, was: " + out);
        assertTrue(out.indexOf("5678") < out.indexOf("4321"),
                "rows should be grouped by alias, was: " + out);
    }

    @Test
    void shouldFilterByServiceName() throws Exception {
        writePidFile("ftp", 1234, "{}");
        writePidFile("kafka", 5678, "{}");

        InfraPs command = command();
        command.serviceName = List.of("ftp");
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("1234"), "the matching ftp instance should be listed, was: " + out);
        assertFalse(out.contains("5678"), "the unmatched kafka instance must not be listed, was: " + out);
    }

    @Test
    void shouldDropAndPruneInstancesWhoseProcessIsGone() throws Exception {
        // a service is stopped by deleting its pid file, and infra run removes the pid and log files on shutdown, so
        // a pid file with no process behind it means the process was killed hard: report it as gone and clean up
        writePidFile("ftp", 1234, "{}");
        writeLogFile("ftp", 1234, "starting\n");
        writePidFile("ftp", 5678, "{}");
        writeLogFile("ftp", 5678, "starting\n");

        InfraPs command = command();
        command.aliveCheck = pid -> pid == 1234;
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("1234"), "the live instance should still be listed, was: " + out);
        assertFalse(out.contains("5678"), "the dead instance must not be listed, was: " + out);

        assertTrue(Files.exists(pidFile("ftp", 1234)), "the live instance's pid file must be kept");
        assertTrue(Files.exists(logFile("ftp", 1234)), "the live instance's log file must be kept");
        assertFalse(Files.exists(pidFile("ftp", 5678)), "the dead instance's pid file should be pruned");
        assertFalse(Files.exists(logFile("ftp", 5678)), "the dead instance's log file should be pruned");
    }

    @Test
    void shouldTreatTheCurrentProcessAsAliveByDefault() throws Exception {
        // exercises the real ProcessHandle based aliveCheck rather than a stub: this JVM is certainly running
        long pid = ProcessHandle.current().pid();
        writePidFile("ftp", pid, "{}");

        InfraPs command = new InfraPs(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains(String.valueOf(pid)), "the running instance should be listed, was: " + out);
        assertTrue(Files.exists(pidFile("ftp", pid)), "a live instance's pid file must not be pruned");
    }

    @Test
    void shouldListRunningServiceMissingFromTheCatalog() throws Exception {
        // the rows come from the pid files, so an alias with no catalog metadata is still reported as running
        writePidFile("not-in-catalog", 1234, "{}");

        int exit = command().doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("not-in-catalog"), "an unknown running alias should still be listed, was: " + out);
        assertTrue(out.contains("1234"), "an unknown running alias should show its pid, was: " + out);
    }

    @Test
    void shouldIncludePidInJsonOutput() throws Exception {
        writePidFile("ftp", 1234, "{\"port\":21}");
        writePidFile("ftp", 5678, "{\"port\":2121}");

        InfraPs command = command();
        command.jsonOutput = true;
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        // without the pid, two instances of the same alias would be indistinguishable to a machine reader
        assertTrue(out.contains("\"pid\":\"1234\""), "json output should carry the pid, was: " + out);
        assertTrue(out.contains("\"pid\":\"5678\""), "json output should carry the pid, was: " + out);
        assertTrue(out.contains("\"port\":21"), "json output should carry the per-instance service data, was: " + out);
        assertTrue(out.contains("\"port\":2121"), "json output should carry the per-instance service data, was: " + out);
    }
}
