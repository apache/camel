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
package org.apache.camel.dsl.jbang.core.commands;

import org.apache.camel.dsl.jbang.core.common.CamelCommandHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CamelCommandHelperTest {

    @Test
    void testStartingState() {
        // Phase <= 4 maps to Starting
        assertEquals("Starting", CamelCommandHelper.extractState(0));
        assertEquals("Starting", CamelCommandHelper.extractState(1));
        assertEquals("Starting", CamelCommandHelper.extractState(4));
    }

    @Test
    void testRunningState() {
        assertEquals("Running", CamelCommandHelper.extractState(5));
    }

    @Test
    void testSuspendingState() {
        assertEquals("Suspending", CamelCommandHelper.extractState(6));
    }

    @Test
    void testSuspendedState() {
        assertEquals("Suspended", CamelCommandHelper.extractState(7));
    }

    @Test
    void testTerminatingState() {
        assertEquals("Terminating", CamelCommandHelper.extractState(8));
    }

    @Test
    void testTerminatedState() {
        assertEquals("Terminated", CamelCommandHelper.extractState(9));
    }

    @Test
    void testUnknownPhaseFallsToTerminated() {
        // Any phase beyond 9 defaults to Terminated — prevents NPE during display
        assertEquals("Terminated", CamelCommandHelper.extractState(99));
        assertEquals("Terminated", CamelCommandHelper.extractState(Integer.MAX_VALUE));
    }
}
