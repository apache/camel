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

package org.apache.camel.dsl.jbang.launcher;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.junit.jupiter.api.Test;

/**
 * Test for the CamelLauncher.
 */
public class CamelLauncherTest {

    /**
     * A simple PrintStream-based Printer implementation for testing.
     */
    static class PrintStreamPrinter implements Printer {
        private final PrintStream printStream;

        public PrintStreamPrinter(PrintStream printStream) {
            this.printStream = printStream;
        }

        @Override
        public void println() {
            printStream.println();
        }

        @Override
        public void println(String line) {
            printStream.println(line);
        }

        @Override
        public void print(String output) {
            printStream.print(output);
        }

        @Override
        public void printf(String format, Object... args) {
            printStream.printf(format, args);
        }
    }

    @Test
    public void testLauncherVersion() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);

        // Create a custom main that doesn't exit
        CamelJBangMain main = new CamelJBangMain() {
            @Override
            public void quit(int exitCode) {
                // Do nothing to prevent System.exit
            }
        };

        // Set a custom printer to capture output
        main.setOut(new PrintStreamPrinter(ps));

        // Run the version command
        CamelJBangMain.run(main, "version");

        String output = baos.toString();
        assertTrue(output.contains("Camel JBang version:"), "Output should contain version information");
    }
}
