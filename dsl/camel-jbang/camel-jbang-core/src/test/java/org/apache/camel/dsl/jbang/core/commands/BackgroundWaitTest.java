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

import java.util.List;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The --background-wait option of camel run: a time to wait, where true is 60 seconds and false does not wait, and a
 * file after the option is not its value.
 */
class BackgroundWaitTest {

    @Test
    void timeToWait() {
        assertThat(BackgroundWait.parse("true")).isEqualTo(60000);
        assertThat(BackgroundWait.parse("false")).isZero();
        assertThat(BackgroundWait.parse("2m")).isEqualTo(120000);
        assertThat(BackgroundWait.parse("1m30s")).isEqualTo(90000);
        assertThat(BackgroundWait.parse("1500")).isEqualTo(1500);
        assertThatThrownBy(() -> BackgroundWait.parse("hello.yaml"))
                .isInstanceOf(CommandLine.TypeConversionException.class)
                .hasMessage("'hello.yaml' is not a time to wait: use true, false, millis or a duration such as 2m");
        assertThat(BackgroundWait.isValue("10s.yaml")).isFalse();
        assertThat(BackgroundWait.isValue("orders")).isFalse();
    }

    @Test
    void optionValueIsOptional() {
        assertThat(parse("hello.yaml").backgroundWait).isEqualTo(60000);
        assertThat(parse("hello.yaml", "--background-wait").backgroundWait).isEqualTo(60000);
        assertThat(parse("hello.yaml", "--background-wait=false").backgroundWait).isZero();
        assertThat(parse("hello.yaml", "--background-wait=true").backgroundWait).isEqualTo(60000);
        assertThat(parse("--background-wait", "30s", "hello.yaml").backgroundWait).isEqualTo(30000);

        // as when the option was a boolean
        Run run = parse("--background-wait", "hello.yaml");
        assertThat(run.backgroundWait).isEqualTo(60000);
        assertThat(run.files).containsExactly("hello.yaml");
    }

    @Test
    void integrationInTheBackgroundGetsNoBackgroundOptions() {
        assertThat(BackgroundWait.withoutBackgroundOptions(
                List.of("run", "hello.yaml", "--background", "--background-wait", "30s", "--dev")))
                .containsExactly("run", "hello.yaml", "--dev");
        assertThat(BackgroundWait.withoutBackgroundOptions(
                List.of("run", "--background-wait", "hello.yaml", "--background=true")))
                .containsExactly("run", "hello.yaml");
        assertThat(BackgroundWait.withoutBackgroundOptions(
                List.of("run", "--background-wait=false", "hello.yaml", "--background")))
                .containsExactly("run", "hello.yaml");
    }

    private static Run parse(String... args) {
        Run run = new Run(new CamelJBangMain());
        CommandLine.populateCommand(run, args);
        return run;
    }
}
