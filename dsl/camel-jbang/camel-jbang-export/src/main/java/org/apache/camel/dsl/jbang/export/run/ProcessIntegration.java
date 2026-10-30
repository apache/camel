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

import org.apache.camel.dsl.jbang.export.common.ChildProcess;

/**
 * An integration that runs in a process of its own: the JVM of the application, or the Maven that runs it.
 */
final class ProcessIntegration implements RunningIntegration {

    private final Process process;
    private final ChildProcess.Cleanup cleanup;

    /**
     * @param process the process of the integration
     * @param cleanup what to remove once it has stopped (run once, also when this JVM exits)
     */
    ProcessIntegration(Process process, ChildProcess.Cleanup cleanup) {
        this.process = process;
        this.cleanup = cleanup;
    }

    @Override
    public long pid() {
        return process.pid();
    }

    @Override
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    @Override
    public void stop() {
        ChildProcess.stop(process, ChildProcess.STOP_SECONDS);
        try {
            cleanup.run();
        } catch (Exception e) {
            // best effort, as the files are also removed when this JVM exits
        }
    }
}
