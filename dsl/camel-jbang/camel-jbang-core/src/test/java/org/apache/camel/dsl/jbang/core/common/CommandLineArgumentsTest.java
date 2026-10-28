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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arguments of a command line typed as one text, such as --jvm-args: a quoted value with spaces is one argument.
 */
class CommandLineArgumentsTest {

    @Test
    void splitAtWhitespace() {
        assertThat(CommandLineHelper.splitArguments(" -Xmx1g\t-Dfoo=bar  ")).containsExactly("-Xmx1g", "-Dfoo=bar");
        assertThat(CommandLineHelper.splitArguments("")).isEmpty();
    }

    @Test
    void quotedValueIsOneArgument() {
        assertThat(CommandLineHelper.splitArguments("-Xmx1g -Dgreeting=\"hello world\""))
                .containsExactly("-Xmx1g", "-Dgreeting=hello world");
        assertThat(CommandLineHelper.splitArguments("-Dgreeting='hello world' -Dempty=''"))
                .containsExactly("-Dgreeting=hello world", "-Dempty=");
        // a quote of the other kind inside quotes is kept
        assertThat(CommandLineHelper.splitArguments("-Dname=\"O'Brien\"")).containsExactly("-Dname=O'Brien");
    }

    @Test
    void backslashEscapes() {
        assertThat(CommandLineHelper.splitArguments("-Dpath=a\\ b")).containsExactly("-Dpath=a b");
        // not inside single quotes
        assertThat(CommandLineHelper.splitArguments("'a\\b'")).containsExactly("a\\b");
    }
}
