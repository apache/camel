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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InfraGet}.
 *
 * The command resolves running infra services from the pid files ({@code infra-<service>-<pid>.json}) stored in the
 * Camel directory, then prints a header plus the file contents for each match. The home directory is redirected to an
 * isolated folder by {@link InfraCommandTestSupport} so the tests are independent of any real local services.
 */
class InfraGetTest extends InfraCommandTestSupport {

    @Test
    void shouldPrintServiceHeaderAndContentByName() throws Exception {
        writePidFile("kafka", 1234, "{\"port\":9092}");

        InfraGet command = new InfraGet(new CamelJBangMain().withPrinter(printer));
        command.name = "kafka";
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

        InfraGet command = new InfraGet(new CamelJBangMain().withPrinter(printer));
        command.name = "5678";
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

        InfraGet command = new InfraGet(new CamelJBangMain().withPrinter(printer));
        command.name = "hive-mq";
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("Service hive-mq (PID: 1234)"),
                "should recover the full hyphenated service name, was: " + out);
    }

    @Test
    void shouldPrintNothingWhenNoServiceMatches() throws Exception {
        writePidFile("kafka", 1234, "{\"port\":9092}");

        InfraGet command = new InfraGet(new CamelJBangMain().withPrinter(printer));
        command.name = "redis";
        int exit = command.doCall();

        assertEquals(0, exit);
        assertEquals("", printer.getOutput(), "no matching service must produce no output");
    }
}
