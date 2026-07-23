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
 * Unit tests for {@link InfraPs}.
 *
 * Unlike {@code infra list}, which prints every known service, {@code infra ps} only shows the services that have a
 * running pid file ({@code infra-<service>-<pid>.json}) in the Camel directory, and includes the PID column. The home
 * directory is redirected to an isolated folder by {@link InfraCommandTestSupport} so the tests do not depend on
 * locally running services.
 */
class InfraPsTest extends InfraCommandTestSupport {

    @Test
    void shouldOnlyListRunningServicesWithPid() throws Exception {
        // kafka is running, minio is not
        writePidFile("kafka", 1234, "{}");

        InfraPs command = new InfraPs(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();

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

        InfraPs command = new InfraPs(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("hive-mq"), "running hyphenated service should be listed, was: " + out);
        assertTrue(out.contains("1234"), "running hyphenated service should show its pid, was: " + out);
    }

    @Test
    void shouldListNoServicesWhenNoneRunning() throws Exception {
        // no pid files written: the service table must be cleared
        InfraPs command = new InfraPs(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertFalse(out.contains("kafka"), "no service rows should be printed when nothing is running, was: " + out);
        assertFalse(out.contains("minio"), "no service rows should be printed when nothing is running, was: " + out);
    }
}
