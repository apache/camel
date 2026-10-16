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
package org.apache.camel.dsl.jbang.core.commands.process;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.EnvironmentHelper;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * camel hawtio opens no browser window without an interactive terminal, unless asked to.
 */
class HawtioTest {

    @BeforeEach
    void noTerminal() {
        assumeFalse(EnvironmentHelper.isInteractiveTerminal(), "runs without an interactive terminal");
    }

    @Test
    void noBrowserWithoutTerminal() {
        assertThat(openUrl()).isFalse();
    }

    @Test
    void browserWhenAsked() {
        assertThat(openUrl("--open-url")).isTrue();
        assertThat(openUrl("--open-url=false")).isFalse();
        // the former name of the option
        assertThat(openUrl("--openUrl=true")).isTrue();
    }

    private static boolean openUrl(String... args) {
        TestHawtio hawtio = new TestHawtio();
        assertThat(new CommandLine(hawtio).execute(args)).isZero();
        return hawtio.opened;
    }

    private static class TestHawtio extends Hawtio {
        boolean opened;

        TestHawtio() {
            super(new CamelJBangMain().withPrinter(new StringPrinter()));
        }

        @Override
        protected Integer callHawtio() {
            // instead of downloading and running Hawtio
            opened = openUrl;
            return 0;
        }
    }
}
