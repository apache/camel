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
     * A conditional object element ({ (a: 1) if cond }) is valid DataWeave that the parser does not model yet; it
     * causes expect() to throw DataWeaveConversionException. In directory mode the CLI must report the failure for that
     * file, continue with the remaining files, and exit non-zero.
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
        // conditional object element — not yet modelled by the parser, expect() throws
        Path bad = Files.writeString(dir.resolve("bad.dwl"), """
                %dw 2.0
                output application/json
                ---
                { (a: 1) if payload.flag }
                """);

        StringPrinter printer = new StringPrinter();
        TransformDataWeave cmd = new TransformDataWeave(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(cmd, "--input=" + dir.toString());
        int exit = cmd.doCall();

        assertThat(exit).isEqualTo(1);
        String out = printer.getOutput();
        // the bad file's name should be reported
        assertThat(out).contains("bad.dwl");
        // the good file should still have been processed (its output lands in the summary)
        assertThat(out).contains("Converted:");
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
        assertThat(printer.getOutput()).contains("Error:");
    }
}
