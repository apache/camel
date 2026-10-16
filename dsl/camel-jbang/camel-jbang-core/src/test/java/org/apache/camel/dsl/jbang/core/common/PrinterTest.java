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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.StdErr;
import org.junitpioneer.jupiter.StdIo;
import org.junitpioneer.jupiter.StdOut;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The output of a command and its diagnostics (errors, warnings and tips) are two channels: stdout and stderr.
 */
class PrinterTest {

    @Test
    void stringPrinterKeepsTheChannelsApart() {
        StringPrinter printer = new StringPrinter();
        printer.println("row 1");
        printer.printWarn("slow");
        printer.printf("row %d%n", 2);
        printer.printErr("broken");
        printer.printTip("try again");

        assertThat(printer.getStdout()).isEqualTo("row 1" + System.lineSeparator() + "row 2");
        assertThat(printer.getStderr()).isEqualTo(
                String.join(System.lineSeparator(), "WARN: slow", "ERROR: broken", "Tip: try again"));
        // everything, in the order it was printed, as a terminal shows it
        assertThat(printer.getOutput()).isEqualTo(String.join(System.lineSeparator(),
                "row 1", "WARN: slow", "row 2", "ERROR: broken", "Tip: try again"));
    }

    @Test
    void customPrinterGetsTheDiagnosticsWithTheOutput() {
        // a printer of an in-process caller (such as the TUI) that only implements the output methods
        List<String> lines = new ArrayList<>();
        Printer printer = new Printer() {
            @Override
            public void println() {
                lines.add("");
            }

            @Override
            public void println(String line) {
                lines.add(line);
            }

            @Override
            public void print(String output) {
                lines.add(output);
            }

            @Override
            public void printf(String format, Object... args) {
                lines.add(String.format(format, args));
            }
        };
        printer.println("output");
        printer.printErr("broken");
        printer.printWarn("slow");

        assertThat(lines).containsExactly("output", "ERROR: broken", "WARN: slow");
    }

    @Test
    void quietPrinterOnlyPrintsErrors() {
        StringPrinter delegate = new StringPrinter();
        Printer printer = new Printer.QuietPrinter(delegate);
        printer.println("output");
        printer.printWarn("slow");
        printer.printTip("try again");
        printer.printErr("broken");

        assertThat(delegate.getStdout()).isEmpty();
        assertThat(delegate.getStderr()).isEqualTo("ERROR: broken");
    }

    @Test
    @StdIo
    void systemPrinterSendsDiagnosticsToStderr(StdOut out, StdErr err) {
        Printer printer = new Printer.SystemOutPrinter();
        printer.println("output");
        printer.printErr("broken");

        assertThat(out.capturedLines()).containsExactly("output");
        assertThat(err.capturedLines()).containsExactly("ERROR: broken");
    }
}
