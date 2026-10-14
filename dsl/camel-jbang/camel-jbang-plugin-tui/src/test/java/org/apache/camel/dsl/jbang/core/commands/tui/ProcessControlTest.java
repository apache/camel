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
package org.apache.camel.dsl.jbang.core.commands.tui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The command line a restart runs again, when the process does not give its arguments one by one.
 */
class ProcessControlTest {

    @Test
    void splitsAtSpaces() {
        assertThat(ProcessControl.parseCommandLine("java  -jar app.jar")).containsExactly("java", "-jar", "app.jar");
    }

    @Test
    void keepsTheSpacesInDoubleQuotes() {
        assertThat(ProcessControl.parseCommandLine("java \"-Dgreeting=Hello World\" -jar app.jar"))
                .containsExactly("java", "-Dgreeting=Hello World", "-jar", "app.jar");
    }

    @Test
    void aBackslashEscapesTheNextChar() {
        assertThat(ProcessControl.parseCommandLine("/opt/my\\ tools/java -Dq=\\\"x\\\""))
                .containsExactly("/opt/my tools/java", "-Dq=\"x\"");
    }

    @Test
    void anEmptyCommandLineHasNoArguments() {
        assertThat(ProcessControl.parseCommandLine("")).isEmpty();
        assertThat(ProcessControl.parseCommandLine("   ")).isEmpty();
    }
}
