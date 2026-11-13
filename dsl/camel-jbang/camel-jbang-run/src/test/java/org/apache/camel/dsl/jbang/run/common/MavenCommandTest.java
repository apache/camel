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
package org.apache.camel.dsl.jbang.run.common;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Maven that builds a project: its wrapper, else mvn from the path.
 */
class MavenCommandTest {

    @TempDir
    Path dir;

    @Test
    void mvnFromThePathWithoutWrapper() {
        assertThat(MavenCommand.of(dir, false)).isEqualTo("mvn");
        // a process cannot be started as mvn on Windows, where it is mvn.cmd
        assertThat(MavenCommand.of(dir, true)).isEqualTo("mvn.cmd");
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a file is executable on Windows without the permission")
    void theWrapperOfTheProject() throws Exception {
        Path mvnw = Files.writeString(dir.resolve("mvnw"), "#!/bin/sh");
        assertThat(MavenCommand.of(dir, false)).isEqualTo("mvn");
        FileUtil.setPosixFilePermissions(mvnw, "rwxr-xr-x");
        assertThat(MavenCommand.of(dir, false)).isEqualTo(mvnw.toString());
    }
}
