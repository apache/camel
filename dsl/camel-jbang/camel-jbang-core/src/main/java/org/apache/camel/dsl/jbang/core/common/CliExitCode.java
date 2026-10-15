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
 * The exit codes of the Camel CLI.
 * <p/>
 * Scripts and AI agents branch on these codes, so they are a contract: never renumber them. Each code is the one most
 * tools already use for that meaning, so a caller can read it without the Camel documentation. Codes from 126 up are
 * left to the shell and to signals (130 is Ctrl+C, 143 is SIGTERM, 137 is SIGKILL).
 */
public final class CliExitCode {

    /**
     * The command succeeded.
     */
    public static final int OK = 0;

    /**
     * The command ran, but the outcome is negative: validation found errors, a check failed, or the integration
     * rejected the action.
     */
    public static final int FAILURE = 1;

    /**
     * Usage error: an unknown option, an invalid or missing argument, options that cannot be used together, or a
     * command that needs an interactive terminal. The same code as picocli and most argument parsers use.
     */
    public static final int USAGE = 2;

    /**
     * Not found: a file, integration, route, component or service named on the command line does not exist, or a name
     * matches more than one integration.
     */
    public static final int NOT_FOUND = 3;

    /**
     * Internal error: an unexpected exception, which is most often a bug in Camel. The code is EX_SOFTWARE from
     * sysexits.h.
     */
    public static final int INTERNAL = 70;

    /**
     * Timed out: the integration did not reply in time, is still starting, or did not stop in time. The same code as
     * the GNU {@code timeout} command uses.
     */
    public static final int TIMEOUT = 124;

    private CliExitCode() {
    }
}
