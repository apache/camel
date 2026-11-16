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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.jline.jansi.Ansi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Without a terminal, a command prints its colored text without the colors itself, as the console would, without
 * building the JLine terminal that the console needs to find that out (CAMEL-26937).
 */
class CamelCommandColorsTest {

    @Test
    void coloredTextWithoutTerminalIsPrintedWithoutColors() throws Exception {
        assumeFalse(EnvironmentHelper.hasTerminal(), "runs without a terminal");

        String out = printToConsole(new ColoredCommand(new CamelJBangMain()));

        assertThat(out).isEqualTo("red and bold" + System.lineSeparator() + "plain");
    }

    @Test
    void consoleStripsTheColorsWithoutTerminal() {
        assumeFalse(EnvironmentHelper.hasTerminal(), "runs without a terminal");

        assertThat(new ColoredCommand(new CamelJBangMain()).colorsStripped()).isTrue();
    }

    private static String printToConsole(CamelCommand command) throws Exception {
        PrintStream previous = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        try {
            command.doCall();
        } finally {
            System.setOut(previous);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static final class ColoredCommand extends CamelCommand {

        ColoredCommand(CamelJBangMain main) {
            super(main);
        }

        @Override
        public Integer doCall() {
            printlnColored(Ansi.ansi().fgRed().a("red").reset().a(" and ").bold().a("bold").reset());
            printColored(Ansi.ansi().a("plain"));
            return 0;
        }
    }
}
