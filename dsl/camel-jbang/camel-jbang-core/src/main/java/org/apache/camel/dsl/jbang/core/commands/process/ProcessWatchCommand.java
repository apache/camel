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

    public ProcessWatchCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        int exit;
        if (watch) {
            do {
                // clear screen first
                AnsiConsole.out().print(Ansi.ansi().eraseScreen());
                AnsiConsole.out().print(Ansi.ansi().cursor(0, 0));
                // output command
                exit = doCall();
                // use 2-sec delay in watch mode
                Thread.sleep(2000);
            } while (exit == 0);
        } else {
            exit = doCall();
        }
        return exit;
    }

    protected abstract Integer doCall() throws Exception;

}
