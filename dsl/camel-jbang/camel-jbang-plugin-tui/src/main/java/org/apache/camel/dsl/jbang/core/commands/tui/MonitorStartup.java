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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;

/**
 * What the monitor works out from its options before it starts.
 */
final class MonitorStartup {

    private MonitorStartup() {
    }

    /**
     * The project to open, when the name argument is a directory rather than the name or pid of an integration.
     *
     * @return the directory, or <tt>null</tt>
     */
    static String openDirectory(String name) {
        if (name != null && !"*".equals(name)) {
            Path p = Path.of(name).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                return p.toString();
            }
        }
        return null;
    }

    /**
     * Fails the monitor without an interactive terminal (a script, a CI job, an AI agent), instead of drawing in none:
     * a browser session (--web) has a terminal of its own, and a recording (--record) plays a tape headless.
     *
     * @throws CliException with exit code 2
     */
    static void requireTerminal(boolean browserSession, boolean recording) {
        if (!browserSession && !recording && !EnvironmentHelper.isInteractiveTerminal()) {
            throw CliException.usage("camel tui requires an interactive terminal");
        }
    }
}
