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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The line that says the integration in the background has started: not for the Camel that camel eval and camel
 * transform start for themselves, as X=$(camel eval ...) got that line too (CAMEL-26931).
 */
@DisabledOnOs(OS.WINDOWS)
class RunBackgroundStateLineTest extends CamelCommandBaseTestSupport {

    @Test
    void anIntegrationInTheBackgroundSaysItHasStarted() throws Exception {
        assertThat(runInTheBackground(false)).contains("Camel Main: orders (state: Running)");
    }

    @Test
    void theCamelOfEvalAndTransformSaysNothing() throws Exception {
        assertThat(runInTheBackground(true)).doesNotContain("(state:");
    }

    private String runInTheBackground(boolean transform) throws Exception {
        useTestHomeDir();
        Path camelDir = CommandLineHelper.getCamelDir();
        Files.createDirectories(camelDir);
        Run run = new Run(new CamelJBangMain().withPrinter(printer));
        run.name = "orders";
        run.transformRun = transform;
        // a process that writes the status of a started Camel (phase 5) for its own pid, as an integration does
        ProcessBuilder pb = new ProcessBuilder(
                "sh", "-c",
                "echo '{\"context\":{\"name\":\"orders\",\"phase\":5}}' > '" + camelDir + "'/$$-status.json; exec sleep 10");
        try {
            assertThat(run.runBackgroundProcess(pb, "Camel Main")).isZero();
        } finally {
            if (run.spawnPid > 0) {
                ProcessHandle.of(run.spawnPid).ifPresent(ProcessHandle::destroy);
            }
        }
        return printer.getOutput();
    }
}
