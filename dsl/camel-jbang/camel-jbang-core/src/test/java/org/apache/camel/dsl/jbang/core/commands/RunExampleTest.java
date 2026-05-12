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

import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunExampleTest {

    @Test
    void shouldListExamples() throws Exception {
        StringPrinter printer = new StringPrinter();
        Run run = new Run(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(run, "--example-list");

        int exit = run.doCall();

        assertEquals(0, exit);
        String output = printer.getOutput();
        assertTrue(output.contains("Available built-in examples"), "Should contain header");
        assertTrue(output.contains("timer-log"), "Should list timer-log example");
        assertTrue(output.contains("rest-api"), "Should list rest-api example");
        assertTrue(output.contains("cron-log"), "Should list cron-log example");
    }

    @Test
    void shouldListExamplesWithEmptyExampleFlag() throws Exception {
        StringPrinter printer = new StringPrinter();
        Run run = new Run(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(run, "--example");

        int exit = run.doCall();

        assertEquals(0, exit);
        String output = printer.getOutput();
        assertTrue(output.contains("Available built-in examples"),
                "Should list examples when --example is used without a name");
    }

    @Test
    void shouldRejectUnknownExample() throws Exception {
        StringPrinter printer = new StringPrinter();
        Run run = new Run(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(run, "--example=nonexistent");

        int exit = run.doCall();

        assertEquals(1, exit);
        String output = printer.getOutput();
        assertTrue(output.contains("Unknown example: nonexistent"), "Should report unknown example");
    }

    @Test
    void shouldSuggestSimilarExampleName() throws Exception {
        StringPrinter printer = new StringPrinter();
        Run run = new Run(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(run, "--example=timer");

        int exit = run.doCall();

        assertEquals(1, exit);
        String output = printer.getOutput();
        assertTrue(output.contains("Did you mean"), "Should suggest similar example name");
        assertTrue(output.contains("timer-log"), "Should suggest timer-log");
    }

    @Test
    void shouldParseExampleOption() throws Exception {
        Run run = new Run(new CamelJBangMain());
        CommandLine.populateCommand(run, "--example=timer-log");

        assertEquals("timer-log", run.example);
    }

    @Test
    void shouldParseExampleListOption() throws Exception {
        Run run = new Run(new CamelJBangMain());
        CommandLine.populateCommand(run, "--example-list");

        assertTrue(run.exampleList);
    }
}
