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
package org.apache.camel.dsl.jbang.it;

import org.apache.camel.dsl.jbang.it.support.JBangTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

@DisabledOnOs(WINDOWS)
public class DirtyITCase extends JBangTestSupport {

    @Test
    public void testDirtyClean() {
        // dirty output depends on the environment: blank when ~/.camel has no orphan files,
        // or "There are N orphan files" when stale files from dead processes are present.
        String output = execute("dirty").trim();
        Assertions.assertThat(output)
                .as("dirty output should be blank or report 'There are N orphan files'")
                .satisfiesAnyOf(
                        s -> Assertions.assertThat(s).isEmpty(),
                        s -> Assertions.assertThat(s).containsPattern("There are \\d+ orphan files"));
    }

    @Test
    public void testDirtyCleanFlag() {
        // --clean attempts to remove orphan files; output is blank when nothing to clean,
        // or "Cleaned N orphan files" when some were removed (non-deletable entries, e.g. dirs, may persist).
        String output = execute("dirty --clean").trim();
        Assertions.assertThat(output)
                .as("dirty --clean output should be blank or report 'Cleaned N orphan files'")
                .satisfiesAnyOf(
                        s -> Assertions.assertThat(s).isEmpty(),
                        s -> Assertions.assertThat(s).containsPattern("Cleaned \\d+ orphan files"));
    }
}
