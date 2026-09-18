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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationLauncherTest {

    @Test
    void noFilesRunsEverySourceFileInTheDirectory(@TempDir Path dir) throws Exception {
        // the process is started without a shell, so nothing expands *, and camel run with no files looks for an
        // application.properties with camel.main.routesIncludePattern: the launcher lists the sources itself
        Files.writeString(dir.resolve("b.camel.yaml"), "- from:\n    uri: timer:tick\n    steps: []\n");
        Files.writeString(dir.resolve("a.camel.yaml"), "- from:\n    uri: timer:tock\n    steps: []\n");
        Files.writeString(dir.resolve("application.properties"), "x=1\n");
        Files.writeString(dir.resolve("README.md"), "not a source\n");
        Files.writeString(dir.resolve(".hidden.yaml"), "- from:\n    uri: timer:hidden\n    steps: []\n");
        Files.createDirectory(dir.resolve("sub.yaml"));
        assertThat(IntegrationLauncher.sourceFiles(dir))
                .containsExactly("a.camel.yaml", "application.properties", "b.camel.yaml");
        assertThat(IntegrationLauncher.runArguments(IntegrationLauncher.sourceFiles(dir), null, true, null))
                .containsExactly("run", "a.camel.yaml", "application.properties", "b.camel.yaml", "--dev",
                        "--logging-color=false");
        assertThat(IntegrationLauncher.sourceFiles(dir.resolve("nope"))).isEmpty();
    }

    @Test
    void filesNameAndExtraArgumentsArePassedThrough() {
        assertThat(IntegrationLauncher.runArguments(List.of("a.camel.yaml", "application.properties"), "demo", true,
                List.of("--port=9000")))
                .containsExactly("run", "a.camel.yaml", "application.properties", "--dev", "--name=demo",
                        "--logging-color=false", "--port=9000");
    }
}
