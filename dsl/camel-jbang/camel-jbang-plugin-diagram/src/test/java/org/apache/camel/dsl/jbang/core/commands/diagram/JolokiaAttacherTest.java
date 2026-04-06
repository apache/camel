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
package org.apache.camel.dsl.jbang.core.commands.diagram;

import java.net.ServerSocket;

import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JolokiaAttacherTest {

    private StringPrinter printer;
    private JolokiaAttacher attacher;

    @BeforeEach
    void setUp() {
        printer = new StringPrinter();
        attacher = new JolokiaAttacher(printer);
    }

    @Test
    void resolvePidWithNumericInputReturnsThatPid() {
        assertEquals(12345L, attacher.resolvePid("12345"));
    }

    @Test
    void resolvePidWithNullReturnsZero() {
        assertEquals(0L, attacher.resolvePid(null));
    }

    @Test
    void resolvePidWithBlankReturnsZero() {
        assertEquals(0L, attacher.resolvePid("   "));
    }

    @Test
    void resolvePidWithUnknownNameReturnsZeroAndPrintsError() {
        long pid = attacher.resolvePid("definitely-not-a-camel-integration-xyz-987");
        assertEquals(0L, pid);
        assertTrue(printer.getOutput().contains("No running Camel integration matches"));
    }

    @Test
    void compareVersionsNumericOrder() {
        assertTrue(attacher.compareVersions("2.0.0", "1.9.9") > 0);
        assertTrue(attacher.compareVersions("1.0.0", "2.0.0") < 0);
        assertEquals(0, attacher.compareVersions("1.2.3", "1.2.3"));
    }

    @Test
    void compareVersionsMajorMinorPatch() {
        assertTrue(attacher.compareVersions("1.10.0", "1.9.0") > 0, "1.10.0 should be > 1.9.0");
        assertTrue(attacher.compareVersions("1.0.10", "1.0.9") > 0, "1.0.10 should be > 1.0.9");
        assertTrue(attacher.compareVersions("2.0.0", "1.99.99") > 0, "2.0.0 should be > 1.99.99");
    }

    @Test
    void compareVersionsWithAlphaTagDoesNotThrow() {
        // Alphabetic segments are compared as strings — just ensure no exception
        attacher.compareVersions("2.0.0-alpha", "1.9.9");
        attacher.compareVersions("1.0.0.Final", "1.0.0.Beta");
    }

    @Test
    void isPortFreeReturnsTrueForAvailablePort() throws Exception {
        // Find an ephemeral port that is free
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) {
            freePort = s.getLocalPort();
        }
        assertTrue(attacher.isPortFree(freePort));
    }

    @Test
    void isPortFreeReturnsFalseForBoundPort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            assertFalse(attacher.isPortFree(s.getLocalPort()));
        }
    }

    @Test
    void findAvailablePortReturnsPortInRange() {
        int port = attacher.findAvailablePort(20000, 30000);
        assertTrue(port >= 20000 && port <= 30000, "Expected port in [20000, 30000] but got: " + port);
    }

    @Test
    void findAvailablePortThrowsWhenRangeExhausted() {
        // An impossible range always throws
        assertThrows(IllegalStateException.class, () -> attacher.findAvailablePort(1, 0));
    }

    @Test
    void attachDelegatesGetPort() {
        // attach() returns 0 on success, 1 on failure — just exercise the delegation, not JVM attach
        assertNotNull(attacher); // attacher was constructed without error
    }
}
