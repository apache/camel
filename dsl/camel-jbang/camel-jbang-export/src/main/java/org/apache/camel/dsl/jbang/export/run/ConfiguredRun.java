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
package org.apache.camel.dsl.jbang.export.run;

import org.apache.camel.main.KameletMain;

/**
 * A run with the jbang runtime that {@link ProjectRunner#configure(RunRequest)} configured: the Camel Main of the run,
 * which {@link #start()} starts in this JVM. camel run decides only then whether it starts it, or runs the integration
 * in another JVM (in the background, or with another Camel version).
 *
 * @since 4.24
 */
public final class ConfiguredRun {

    private final JBangRunner runner;
    private final KameletMain main;
    private final int exitCode;

    ConfiguredRun(JBangRunner runner, KameletMain main, int exitCode) {
        this.runner = runner;
        this.main = main;
        this.exitCode = exitCode;
    }

    /**
     * The request, which the configuration completed (such as the name, the files and the application properties).
     */
    public RunRequest getRequest() {
        return runner.getRequest();
    }

    /**
     * The Camel Main of the run, or null when the configuration ended the run (see {@link #getExitCode()}).
     */
    public KameletMain getMain() {
        return main;
    }

    /**
     * 0 when the run is configured, else the exit code of why it cannot run (such as no files to run, or a file that
     * does not exist).
     */
    public int getExitCode() {
        return exitCode;
    }

    /**
     * Starts Camel in this JVM.
     *
     * @return the running integration, or a run that ended (the configuration ended the run, no routes, or a route that
     *         did not load with {@link RunRequest#ignoreLoadingError})
     */
    public RunningIntegration start() throws Exception {
        if (main == null) {
            return new EndedIntegration(exitCode);
        }
        return runner.start(main);
    }
}
