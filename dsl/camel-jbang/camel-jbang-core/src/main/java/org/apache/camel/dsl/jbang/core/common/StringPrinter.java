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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * A printer that keeps what a command prints, such as for tests and for tools that run commands in-process.
 * <p/>
 * {@link #getOutput()} has everything in the order it was printed, as a terminal shows it. {@link #getStdout()} and
 * {@link #getStderr()} have the output of the command and the diagnostics channel (errors, warnings and tips) apart.
 */
public class StringPrinter implements Printer {

    private final StringWriter writer = new StringWriter();
    private final StringWriter stdout = new StringWriter();
    private final StringWriter stderr = new StringWriter();

    @Override
    public void println() {
        print(System.lineSeparator());
    }

    @Override
    public void println(String line) {
        printf("%s%n", line);
    }

    @Override
    public void print(String output) {
        writer.write(output);
        stdout.write(output);
    }

    @Override
    public void printf(String format, Object... args) {
        print(format.formatted(args));
    }

    @Override
    public void printDiagnostic(String line) {
        String text = line + System.lineSeparator();
        writer.write(text);
        stderr.write(text);
    }

    /**
     * Provides access to the cached output: the output of the command and the diagnostics, in the order they were
     * printed.
     *
     * @return
     */
    public String getOutput() {
        return writer.toString().trim();
    }

    /**
     * The output of the command only (what the CLI prints on stdout).
     */
    public String getStdout() {
        return stdout.toString().trim();
    }

    /**
     * The errors, warnings and tips only (what the CLI prints on stderr).
     */
    public String getStderr() {
        return stderr.toString().trim();
    }

    /**
     * Provides access to all lines of the cached output.
     *
     * @return
     * @throws IOException
     */
    public List<String> getLines() throws IOException {
        BufferedReader buf = new BufferedReader(new StringReader(getOutput()));
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = buf.readLine()) != null) {
            lines.add(line.trim());
        }

        return lines;
    }
}
