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
 * Unit tests for {@link InfraList}.
 *
 * {@code infra list} describes the catalog of services that <em>can</em> be run, so unlike {@code infra ps} it keeps
 * one row per alias, has no PID column, and lists services that are not running. These tests pin that shape, because
 * both commands share the table rendering in {@link InfraBaseCommand}.
 */
class InfraListTest extends InfraCommandTestSupport {

    @Test
    void shouldKeepOneRowPerAliasWhenSeveralInstancesAreRunning() throws Exception {
        // CAMEL-24678: infra ps became one row per instance, but infra list must stay one row per alias
        writePidFile("ftp", 1234, "{\"port\":21}");
        writePidFile("ftp", 5678, "{\"port\":2121}");

        InfraList command = new InfraList(new CamelJBangMain().withPrinter(printer));
        command.aliveCheck = pid -> true;
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertEquals(1, out.lines().filter(l -> l.stripLeading().startsWith("ftp ")).count(),
                "the ftp alias must appear exactly once, was: " + out);
        assertFalse(out.contains("PID"), "infra list must not render the PID column, was: " + out);
        // the catalog view lists what can be run, not only what is running
        assertTrue(out.contains("minio"), "a service that is not running should still be listed, was: " + out);
    }

    @Test
    void shouldListTheCatalogWhenNothingIsRunning() throws Exception {
        InfraList command = new InfraList(new CamelJBangMain().withPrinter(printer));
        int exit = command.doCall();

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("ALIAS"), "the table header should be printed, was: " + out);
        assertTrue(out.contains("ftp"), "every known service should be listed, was: " + out);
        assertTrue(out.contains("minio"), "every known service should be listed, was: " + out);
    }
}
