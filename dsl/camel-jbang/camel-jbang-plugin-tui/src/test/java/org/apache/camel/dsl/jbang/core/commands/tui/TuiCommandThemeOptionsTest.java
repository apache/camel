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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code --theme} and {@code --panel-theme} options: {@code camel tui} forwards them to {@link CamelMonitor}, which
 * checks and applies them. As with the recording options, an option declared on only one of the two commands is either
 * rejected or silently dropped.
 */
@Isolated
class TuiCommandThemeOptionsTest {

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        Theme.resetForTesting();
    }

    @Test
    void forwardsBothThemeOptions() {
        assertThat(buildArgs("--theme=dark", "--panel-theme=light"))
                .containsExactly("--theme", "dark", "--panel-theme", "light");
        assertThat(buildArgs()).isEmpty();
    }

    @Test
    void panelThemeIsAppliedForTheSession() {
        CamelMonitor monitor = monitor("--panel-theme=tokyo-night");
        monitor.applyThemeOption();
        assertThat(Theme.panelTheme()).isEqualTo("tokyo-night");
    }

    @Test
    void sameDrawsThePanelsWithTheMainTheme() {
        Theme.setPanelTheme("light");
        CamelMonitor monitor = monitor("--panel-theme=same");
        monitor.applyThemeOption();
        assertThat(Theme.panelTheme()).isNull();
    }

    @Test
    void rejectsAnUnknownPanelTheme() {
        CamelMonitor monitor = monitor("--panel-theme=neon");
        assertThatThrownBy(monitor::applyThemeOption)
                .isInstanceOf(CommandLine.ParameterException.class)
                .hasMessageContaining("--panel-theme")
                .hasMessageContaining("'same'")
                .hasMessageContaining("neon");
        assertThat(Theme.panelTheme()).isNull();
    }

    private CamelMonitor monitor(String... args) {
        CamelMonitor monitor = new CamelMonitor(new CamelJBangMain(), getClass().getClassLoader());
        new CommandLine(monitor).parseArgs(args);
        return monitor;
    }

    private List<String> buildArgs(String... args) {
        TuiCommand command = new TuiCommand(new CamelJBangMain(), getClass().getClassLoader());
        new CommandLine(command).parseArgs(args);
        return command.buildArgs();
    }
}
