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

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code camel tui} forwards the recording options to {@link CamelMonitor}.
 * <p>
 * {@link TuiCommand} is the entry point registered by the TUI plugin, but the options are implemented on
 * {@link CamelMonitor}, which it delegates to by rebuilding a command line. An option declared on only one of the two
 * fails in a way no compiler catches: declared only on {@code CamelMonitor} it is rejected as an unknown option,
 * declared only on {@code TuiCommand} it is accepted and then silently dropped. This test pins the forwarding so the
 * documented {@code camel tui --record=demo.tape --record-size=160x44 --record-fps=15} keeps working.
 */
class TuiCommandRecordOptionsTest {

    @Test
    void forwardsAllRecordingOptions() {
        assertThat(buildArgs("--record=demo.tape", "--record-size=160x44", "--record-fps=15", "--record-duration=30000"))
                .containsExactly(
                        "--record", "demo.tape",
                        "--record-size", "160x44",
                        "--record-fps", "15",
                        "--record-duration", "30000");
    }

    @Test
    void omitsRecordingOptionsLeftAtTheirDefault() {
        // Passing the defaults through would be harmless but noisy; more importantly the delegate must keep
        // owning the default values, so they are only declared in one place.
        assertThat(buildArgs("--record=demo.tape")).containsExactly("--record", "demo.tape");
    }

    @Test
    void everyRecordingOptionIsAcceptedByTheDelegate() {
        // The forwarded command line is only useful if CamelMonitor understands it — the bug this guards against
        // was TuiCommand accepting --record-size and CamelMonitor never seeing it.
        CamelMonitor monitor = new CamelMonitor(new CamelJBangMain(), getClass().getClassLoader());
        String[] args = buildArgs("--record=demo.tape", "--record-size=160x44", "--record-fps=15",
                "--record-duration=30000").toArray(String[]::new);

        new CommandLine(monitor).parseArgs(args);

        assertThat(monitor.record).isEqualTo("demo.tape");
        assertThat(monitor.recordSize).isEqualTo("160x44");
        assertThat(monitor.recordFps).isEqualTo(15);
        assertThat(monitor.recordDuration).isEqualTo(30000);
    }

    private List<String> buildArgs(String... args) {
        TuiCommand command = new TuiCommand(new CamelJBangMain(), getClass().getClassLoader());
        new CommandLine(command).parseArgs(args);
        return command.buildArgs();
    }
}
