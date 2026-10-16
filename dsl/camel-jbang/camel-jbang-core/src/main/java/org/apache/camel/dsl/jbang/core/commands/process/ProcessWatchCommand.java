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
package org.apache.camel.dsl.jbang.core.commands.process;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.CommandHelper;
import org.apache.camel.dsl.jbang.core.commands.DurationMixin;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.util.StopWatch;
import org.jline.jansi.Ansi;
import org.jline.jansi.AnsiConsole;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Base class for commands that can run in watch mode.
 */
abstract class ProcessWatchCommand extends ProcessBaseCommand {

    @CommandLine.Option(names = { "--watch" },
                        description = "Execute periodically and showing output fullscreen")
    boolean watch;

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Mixin
    DurationMixin durationOption = new DurationMixin();

    protected ProcessWatchCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        int exit;
        long duration = durationOption.getDuration();
        if (watch && EnvironmentHelper.isEmbedded()) {
            printer().printTip("use the TUI tabs for live monitoring");
            exit = doProcessWatchCall();
        } else if (watch && duration <= 0 && !EnvironmentHelper.isInteractiveTerminal()) {
            // nobody can press Enter to stop watching
            printer().printWarn("--watch needs an interactive terminal or --duration, so the output is shown once");
            exit = doProcessWatchCall();
        } else if (watch) {
            CommandHelper.RunUntil until = CommandHelper.RunUntil.start(duration);
            do {
                autoClearScreen();
                exit = doProcessWatchCall();
                if (exit == 0) {
                    // use 2-sec delay in watch mode
                    try {
                        StopWatch watch = new StopWatch();
                        while (until.isRunning() && watch.taken() < 2000) {
                            Thread.sleep(100);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        until.stop();
                    }
                }
            } while (exit == 0 && until.isRunning());
        } else {
            exit = doProcessWatchCall();
        }
        return exit;
    }

    protected void autoClearScreen() {
        if (!jsonOutput) {
            // in JSON mode every refresh prints one more document, and the escape codes would break it
            clearScreen();
        }
    }

    protected void clearScreen() {
        Terminal t = EnvironmentHelper.getActiveTerminal();
        if (t != null) {
            t.writer().print("\033[2J\033[H");
            t.writer().flush();
        } else if (EnvironmentHelper.hasTerminal()) {
            // the escape codes would end up in a file or a pipe
            AnsiConsole.out().print(Ansi.ansi().eraseScreen().cursor(1, 1));
        }
    }

    protected abstract Integer doProcessWatchCall() throws Exception;

}
