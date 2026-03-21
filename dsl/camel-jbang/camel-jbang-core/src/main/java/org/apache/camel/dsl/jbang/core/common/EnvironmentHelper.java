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

/**
 * Helper for detecting environment characteristics such as CI environments, color support, and interactive terminals.
 *
 * Supports the following standard environment variables:
 * <ul>
 * <li>{@code NO_COLOR} - When set (any value), disables colored output. See
 * <a href="https://no-color.org/">no-color.org</a></li>
 * <li>{@code FORCE_COLOR} - When set (any value), forces colored output even when not a TTY</li>
 * <li>{@code CI} - When set (any value), indicates a CI environment (disables color and interactive prompts)</li>
 * <li>{@code GITHUB_ACTIONS} - GitHub Actions CI detection</li>
 * <li>{@code GITLAB_CI} - GitLab CI detection</li>
 * <li>{@code JENKINS_URL} - Jenkins CI detection</li>
 * </ul>
 */
public final class EnvironmentHelper {

    private EnvironmentHelper() {
    }

    /**
     * Determines whether colored output should be enabled based on environment variables and terminal capabilities.
     *
     * <p>
     * The precedence order is:
     * <ol>
     * <li>{@code NO_COLOR} set - returns false</li>
     * <li>{@code CI} set (without {@code FORCE_COLOR}) - returns false</li>
     * <li>{@code FORCE_COLOR} set - returns true</li>
     * <li>Otherwise, returns true if a console (TTY) is available</li>
     * </ol>
     *
     * @return true if colored output should be enabled
     */
    public static boolean isColorEnabled() {
        if (getEnv("NO_COLOR") != null) {
            return false;
        }
        if (getEnv("CI") != null && getEnv("FORCE_COLOR") == null) {
            return false;
        }
        if (getEnv("FORCE_COLOR") != null) {
            return true;
        }
        return System.console() != null;
    }

    /**
     * Detects whether the current process is running in a CI/CD environment.
     *
     * @return true if a known CI environment variable is set
     */
    public static boolean isCIEnvironment() {
        return getEnv("CI") != null
                || getEnv("GITHUB_ACTIONS") != null
                || getEnv("GITLAB_CI") != null
                || getEnv("JENKINS_URL") != null;
    }

    /**
     * Determines whether the current terminal is interactive (has a console and is not in CI).
     *
     * @return true if the terminal supports interactive prompts
     */
    public static boolean isInteractiveTerminal() {
        return System.console() != null && !isCIEnvironment();
    }

    // Visible for testing - allows overriding in tests
    static String getEnv(String name) {
        return System.getenv(name);
    }
}
