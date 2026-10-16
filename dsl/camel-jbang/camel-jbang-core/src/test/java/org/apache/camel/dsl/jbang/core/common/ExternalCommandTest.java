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
package org.apache.camel.dsl.jbang.core.common;

import java.util.List;

import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An external command that hangs, such as a docker info of a stopped Docker Desktop, is destroyed when it does not
 * finish in time, also when it prints more than a pipe holds.
 */
@DisabledOnOs(OS.WINDOWS)
class ExternalCommandTest {

    @Test
    void exitCode() {
        assertThat(ExternalCommand.succeeds(10000, "true")).isTrue();
        assertThat(ExternalCommand.succeeds(10000, "false")).isFalse();
        assertThat(ExternalCommand.succeeds(10000, "no-such-command-for-camel")).isFalse();
    }

    @Test
    void commandThatHangsIsDestroyed() {
        StopWatch watch = new StopWatch();

        assertThat(ExternalCommand.succeeds(300, "sleep", "30")).isFalse();
        assertThat(ExternalCommand.output(300, List.of("sleep", "30"))).isNull();

        assertThat(watch.taken()).isLessThan(10000);
    }

    @Test
    void output() {
        assertThat(ExternalCommand.output(10000, List.of("echo", "hello"))).isEqualTo("hello\n");
        assertThat(ExternalCommand.output(10000, List.of("sh", "-c", "echo oops; exit 1"))).isNull();
        // more than the 64 KB a pipe holds, which a command cannot write before it is read
        String big = ExternalCommand.output(10000, List.of("sh", "-c", "head -c 200000 /dev/zero | tr '\\0' x"));
        assertThat(big).hasSize(200000);
    }
}
