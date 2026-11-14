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

import org.apache.camel.dsl.jbang.core.commands.action.CamelSendAction;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The colors of camel cmd send --reply, camel trace and camel cmd browse: ANSI codes in a file or a pipe, which an AI
 * agent or a script reads, were noise (CAMEL-26929).
 */
class ColorOutputTest {

    @Test
    void noColorsWhenTheOutputIsNoTerminal() {
        // the forked JVM of the test has no terminal, and the command prints to the console
        CamelSendAction command = new CamelSendAction(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--endpoint=direct:foo");

        assertThat(command.colorOutput(true)).isFalse();
        assertThat(command.colorOutput(false)).isFalse();
    }

    @Test
    void colorsWhenTheOptionIsGiven() {
        CamelSendAction command = new CamelSendAction(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--endpoint=direct:foo", "--logging-color");

        assertThat(command.colorOutput(true)).isTrue();
    }

    @Test
    void anotherPrinterKeepsTheOption() {
        // such as the TUI or the MCP server, which take the output of the command
        CamelSendAction command = new CamelSendAction(new CamelJBangMain().withPrinter(new StringPrinter()));
        CommandLineTestHelper.parse(command, "--endpoint=direct:foo");

        assertThat(command.colorOutput(true)).isTrue();
    }
}
