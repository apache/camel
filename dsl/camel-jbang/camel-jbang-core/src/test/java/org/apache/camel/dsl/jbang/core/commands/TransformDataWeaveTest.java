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

import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class TransformDataWeaveTest {

    @TempDir
    Path dir;

    /**
     * A malformed script makes the parser throw DataWeaveConversionException. In directory mode the CLI must report the
     * failure for that file, continue with the remaining files, and exit non-zero.
     */
    @Test
    void directoryModeReportsParseErrorPerFileAndContinues() throws Exception {
        // valid DataWeave that converts fine
        Path good = Files.writeString(dir.resolve("good.dwl"), """
                %dw 2.0
                output application/json
                ---
                payload
                """);
        // malformed: the object is not closed
        Path bad = Files.writeString(dir.resolve("bad.dwl"), """
                %dw 2.0
                output application/json
                ---
                { a: payload.a
                """);

        StringPrinter printer = new StringPrinter();
        TransformDataWeave cmd = new TransformDataWeave(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(cmd, "--input=" + dir.toString());
        int exit = cmd.doCall();

        assertThat(exit).isEqualTo(1);
        // the bad file's name should be reported as an error (stderr)
        assertThat(printer.getStderr()).contains("ERROR: bad.dwl");
        // the good file should still have been processed — multi-file header proves it was converted
        assertThat(printer.getStdout()).contains("// === good.dwl ===").doesNotContain("bad.dwl");
    }

    /**
     * An inline expression that the parser cannot handle returns exit code 1 with an error message.
     */
    @Test
    void inlineExpressionParseErrorReturnsNonZero() throws Exception {
        StringPrinter printer = new StringPrinter();
        TransformDataWeave cmd = new TransformDataWeave(new CamelJBangMain().withPrinter(printer));
        // Unclosed parenthesis — expect() throws DataWeaveConversionException
        CommandLine.populateCommand(cmd, "--expression=(a + b");
        int exit = cmd.doCall();

        assertThat(exit).isEqualTo(1);
        assertThat(printer.getStderr()).startsWith("ERROR:");
        assertThat(printer.getStdout()).isEmpty();
    }

    /**
     * Without --input or --expression the command was called wrong: a usage error.
     */
    @Test
    void missingInputIsUsageError() throws Exception {
        StringPrinter printer = new StringPrinter();
        TransformDataWeave cmd = new TransformDataWeave(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(cmd);
        int exit = cmd.doCall();

        assertThat(exit).isEqualTo(2);
        assertThat(printer.getStderr()).isEqualTo("ERROR: Either --input or --expression must be specified");
    }

    /**
     * An input path that does not exist is not found.
     */
    @Test
    void missingInputPathIsNotFound() throws Exception {
        StringPrinter printer = new StringPrinter();
        TransformDataWeave cmd = new TransformDataWeave(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(cmd, "--input=" + dir.resolve("missing.dwl"));
        int exit = cmd.doCall();

        assertThat(exit).isEqualTo(3);
        assertThat(printer.getStderr()).startsWith("ERROR: Input path does not exist:");
    }

    /**
     * The summary is a report on stderr, so the converted expression on stdout can be redirected to a file.
     */
    @Test
    void summaryIsNotPartOfTheOutput() throws Exception {
        StringPrinter printer = new StringPrinter();
        TransformDataWeave cmd = new TransformDataWeave(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(cmd, "--expression=payload.name");
        int exit = cmd.doCall();

        assertThat(exit).isZero();
        assertThat(printer.getStdout()).doesNotContain("Converted:").doesNotContain("ERROR");
        assertThat(printer.getStderr()).matches("Converted: \\d+ expressions");
    }
}
