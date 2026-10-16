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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.camel.dsl.jbang.core.commands.ai.ToolConfirms;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * CAMEL-25676: camel ask asks on the terminal before a call that is confirmed first; without a terminal the call does
 * not run unless --yes allows it. A local model gets the core tools, and a small one a note.
 */
class AskTest {

    private static final ToolConfirms.Call STOP = ToolConfirms.stopApplication("orders (PID 42)");

    private final StringBuilder out = new StringBuilder();
    private final StringBuilder err = new StringBuilder();

    private final Printer printer = new Printer() {
        @Override
        public void println() {
            out.append('\n');
        }

        @Override
        public void println(String line) {
            out.append(line).append('\n');
        }

        @Override
        public void print(String output) {
            out.append(output);
        }

        @Override
        public void printf(String format, Object... args) {
            out.append(String.format(format, args));
        }

        @Override
        public void printErr(String message) {
            err.append(message).append('\n');
        }
    };

    @Test
    void theUserIsAskedOnTheTerminal() {
        List<String> prompts = new ArrayList<>();
        assertThat(Ask.confirm(STOP, false, prompt -> {
            prompts.add(prompt);
            return "y";
        }, printer)).isTrue();
        assertThat(prompts).containsExactly("Allow? [y/N] ");
        assertThat(out).contains("The AI wants to stop the integration:\n  orders (PID 42)\n")
                .contains("It stops the integration; it does not start again by itself.");

        assertThat(Ask.confirm(STOP, false, prompt -> " YES ", printer)).isTrue();
        assertThat(Ask.confirm(STOP, false, prompt -> "", printer)).isFalse();
        assertThat(Ask.confirm(STOP, false, prompt -> "no", printer)).isFalse();
        // the input ended
        assertThat(Ask.confirm(STOP, false, prompt -> null, printer)).isFalse();
    }

    @Test
    void withoutATerminalTheCallDoesNotRun() {
        assertThat(Ask.confirm(STOP, false, null, printer)).isFalse();
        assertThat(err).contains("Not run, as there is no terminal to ask on: stop the integration").contains("--yes");
    }

    @Test
    void yesRunsTheCallWithoutAsking() {
        assertThat(Ask.confirm(STOP, true, null, printer)).isTrue();
        assertThat(out).contains("(--yes) stop the integration");
    }

    @Test
    void aLocalModelGetsTheCoreToolsByDefault() {
        assertThat(Ask.useCoreTools(Ask.ToolSet.auto, true)).isTrue();
        assertThat(Ask.useCoreTools(Ask.ToolSet.auto, false)).isFalse();
        assertThat(Ask.useCoreTools(Ask.ToolSet.full, true)).isFalse();
        assertThat(Ask.useCoreTools(Ask.ToolSet.core, false)).isTrue();
    }

    @Test
    void aSmallModelGetsANote() {
        Locale before = Locale.getDefault();
        try {
            // no decimal comma
            Locale.setDefault(Locale.GERMANY);
            assertThat(Ask.smallModelNote("llama3.2:latest", 3.2))
                    .startsWith("Note: llama3.2:latest is a small model (3.2B parameters)");
        } finally {
            Locale.setDefault(before);
        }
        assertThat(Ask.smallModelNote("granite4:3b", 3.0)).contains("(3B parameters)");
        assertThat(Ask.smallModelNote("qwen3.6:35b-mlx", 35.1)).isNull();
        assertThat(Ask.smallModelNote("unknown", -1)).isNull();
    }

    @Test
    void theModelSizeIsReadAsOllamaWritesIt() {
        assertThat(LlmClient.parseParameterSize("3.2B")).isEqualTo(3.2);
        assertThat(LlmClient.parseParameterSize("494.03M")).isCloseTo(0.494, within(0.001));
        assertThat(LlmClient.parseParameterSize("35.1B")).isEqualTo(35.1);
        assertThat(LlmClient.parseParameterSize("big")).isEqualTo(-1);
        assertThat(LlmClient.parseParameterSize(null)).isEqualTo(-1);
    }
}
