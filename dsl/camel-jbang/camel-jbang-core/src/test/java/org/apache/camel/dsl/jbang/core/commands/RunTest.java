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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class RunTest extends CamelCommandBaseTestSupport {

    @Test
    public void shouldParseJavaVersionOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--java-version=17", "route.yaml");

        Assertions.assertEquals("17", command.javaVersion);
    }

    @Test
    public void shouldUseDefaultJavaVersion() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "route.yaml");

        Assertions.assertEquals("21", command.javaVersion);
    }

    @Test
    public void shouldParseJavaVersion11() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--java-version=11", "route.yaml");

        Assertions.assertEquals("11", command.javaVersion);
    }

    @Test
    public void shouldParseJavaVersion21() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--java-version=21", "route.yaml");

        Assertions.assertEquals("21", command.javaVersion);
    }

    @Test
    public void shouldListExamples() throws Exception {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        command.exampleList = true;
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Available built-in examples:"));
        Assertions.assertTrue(output.contains("timer-log"));
        Assertions.assertTrue(output.contains("rest-api"));
        Assertions.assertTrue(output.contains("cron-log"));
    }

    @Test
    public void shouldListExamplesWithEmptyExampleFlag() throws Exception {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        command.example = "";
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        String output = printer.getOutput();
        Assertions.assertTrue(output.contains("Available built-in examples:"));
    }

    @Test
    public void shouldRejectUnknownExample() throws Exception {
        Run command = new Run(new CamelJBangMain().withPrinter(printer));
        command.example = "nonexistent";
        int exit = command.doCall();

        Assertions.assertEquals(1, exit);
    }

    @Test
    public void shouldParseExampleOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--example=timer-log");

        Assertions.assertEquals("timer-log", command.example);
    }

    @Test
    public void shouldParseExampleListOption() throws Exception {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, "--example-list");

        Assertions.assertTrue(command.exampleList);
    }
}
