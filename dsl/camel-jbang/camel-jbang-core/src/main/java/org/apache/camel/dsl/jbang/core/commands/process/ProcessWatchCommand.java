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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.CommandHelper;
import org.apache.camel.util.StopWatch;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

/**
 * Base class for commands that can run in watch mode.
 */
abstract class ProcessWatchCommand extends ProcessBaseCommand {

    @CommandLine.Option(names = { "--watch" },
                        description = "Execute periodically and showing output fullscreen")
    boolean watch;

    private CommandHelper.ReadConsoleTask waitUserTask;

    public ProcessWatchCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        int exit;
        final AtomicBoolean running = new AtomicBoolean(true);
        if (watch) {
            Thread t = new Thread(() -> {
                waitUserTask = new CommandHelper.ReadConsoleTask(() -> running.set(false));
                waitUserTask.run();
            }, "WaitForUser");
            t.start();
            do {
                autoClearScreen();
                exit = doProcessWatchCall();
                if (exit == 0) {
                    // use 2-sec delay in watch mode
                    try {
                        StopWatch watch = new StopWatch();
                        while (running.get() && watch.taken() < 2000) {
                            Thread.sleep(100);
                        }
                    } catch (Exception e) {
                        running.set(false);
                    }
                }
            } while (exit == 0 && running.get());
        } else {
            exit = doProcessWatchCall();
        }
        return exit;
    }

    protected void autoClearScreen() {
        clearScreen();
    }

    protected void clearScreen() {
        AnsiConsole.out().print(Ansi.ansi().eraseScreen().cursor(1, 1));
    }

    protected abstract Integer doProcessWatchCall() throws Exception;

}
