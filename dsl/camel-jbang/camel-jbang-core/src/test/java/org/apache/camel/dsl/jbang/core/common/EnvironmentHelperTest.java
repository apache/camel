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
package org.apache.camel.dsl.jbang.core.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentHelperTest {

    @Test
    void testIsColorEnabledReturnsBoolean() {
        // Should not throw and should return a boolean value
        // The actual result depends on the environment, but the method should work
        assertDoesNotThrow(() -> EnvironmentHelper.isColorEnabled());
    }

    @Test
    void testIsCIEnvironmentReturnsBoolean() {
        assertDoesNotThrow(() -> EnvironmentHelper.isCIEnvironment());
    }

    @Test
    void testIsInteractiveTerminalReturnsBoolean() {
        assertDoesNotThrow(() -> EnvironmentHelper.isInteractiveTerminal());
    }

    @Test
    void testNoColorEnvDisablesColor() {
        // When NO_COLOR is set in the actual environment, color should be disabled
        if (System.getenv("NO_COLOR") != null) {
            assertFalse(EnvironmentHelper.isColorEnabled());
        }
    }

    @Test
    void testCIEnvDetected() {
        // When CI is set in the actual environment, it should be detected
        if (System.getenv("CI") != null) {
            assertTrue(EnvironmentHelper.isCIEnvironment());
            assertFalse(EnvironmentHelper.isInteractiveTerminal());
        }
    }

    @Test
    void testGitHubActionsDetected() {
        if (System.getenv("GITHUB_ACTIONS") != null) {
            assertTrue(EnvironmentHelper.isCIEnvironment());
        }
    }

    @Test
    void testGitLabCIDetected() {
        if (System.getenv("GITLAB_CI") != null) {
            assertTrue(EnvironmentHelper.isCIEnvironment());
        }
    }

    @Test
    void testJenkinsCIDetected() {
        if (System.getenv("JENKINS_URL") != null) {
            assertTrue(EnvironmentHelper.isCIEnvironment());
        }
    }
}
