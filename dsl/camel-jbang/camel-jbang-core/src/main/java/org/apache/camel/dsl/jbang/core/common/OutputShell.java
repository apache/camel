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

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.aesh.command.shell.Shell;
import org.aesh.readline.prompt.Prompt;
import org.aesh.terminal.Key;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.Parser;

/**
 * The shell of the commands that aesh runs: what aesh prints itself, such as the help of {@code --help}, goes to the
 * printer of the CLI, as what the commands print, so that a caller that captures the output (the tests, camel shell,
 * the AI tools) gets it too. The commands do not read from it.
 */
public final class OutputShell implements Shell {

    private final Consumer<String> output;

    public OutputShell(Consumer<String> output) {
        this.output = output;
    }

    @Override
    public void write(String out, boolean paging) {
        output.accept(out);
    }

    @Override
    public void writeln(String out, boolean paging) {
        output.accept(out + System.lineSeparator());
    }

    @Override
    public void write(int[] out) {
        output.accept(Parser.fromCodePoints(out));
    }

    @Override
    public void write(char out) {
        output.accept(String.valueOf(out));
    }

    @Override
    public String readLine() {
        return null;
    }

    @Override
    public String readLine(Prompt prompt) {
        return null;
    }

    @Override
    public Key read() {
        return null;
    }

    @Override
    public Key read(long timeout, TimeUnit unit) {
        return null;
    }

    @Override
    public Key read(Prompt prompt) {
        return null;
    }

    @Override
    public boolean enableAlternateBuffer() {
        return false;
    }

    @Override
    public boolean enableMainBuffer() {
        return false;
    }

    @Override
    public Size size() {
        return new Size(TerminalWidthHelper.getTerminalWidth(), -1);
    }

    @Override
    public void clear() {
        // no screen to clear
    }
}
