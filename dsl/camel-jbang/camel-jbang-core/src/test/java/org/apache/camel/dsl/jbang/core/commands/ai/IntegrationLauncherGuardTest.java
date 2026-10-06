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

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25364: camel_run of a directory an integration already runs from returns that one instead of starting a second
 * copy. The running integration is this test's own JVM, made visible by a status file that names the directory.
 */
@Isolated
class IntegrationLauncherGuardTest {

    @TempDir
    Path home;

    @TempDir
    Path project;

    private String originalHome;

    @BeforeEach
    void runningFromTheProject() throws Exception {
        originalHome = CommandLineHelper.getHomeDir().toString();
        CommandLineHelper.useHomeDir(home.toString());
        Files.writeString(project.resolve("route.camel.yaml"), "- from:\n    uri: timer:tick\n    steps: []\n");
        Path camelDir = CommandLineHelper.getCamelDir();
        Files.createDirectories(camelDir);
        JsonObject runtime = new JsonObject();
        // the process reports its working directory resolved, as new File(".").getAbsolutePath() does
        runtime.put("directory", project.toRealPath().toString());
        JsonObject context = new JsonObject();
        context.put("name", "demo");
        JsonObject status = new JsonObject();
        status.put("runtime", runtime);
        status.put("context", context);
        Files.writeString(camelDir.resolve(ProcessHandle.current().pid() + "-status.json"), status.toJson());
    }

    @AfterEach
    void restoreHome() {
        CommandLineHelper.useHomeDir(originalHome);
    }

    @Test
    void theRunningIntegrationIsReturned() {
        JsonObject result = IntegrationLauncher.run(project, List.of(), null, true, List.of());
        assertThat(result.getString("status")).isEqualTo("running");
        assertThat(result.getLong("pid")).isEqualTo(ProcessHandle.current().pid());
        assertThat(result.getString("name")).isEqualTo("demo");
        assertThat(result.getString("log")).isNotBlank();
        assertThat(result.containsKey("command")).isFalse();
        // this JVM was not started with --source-dir, so it is not said to reload an added file
        assertThat(result.getString("message")).contains("a second copy was not started")
                .contains("started with its own files").doesNotContain("is reloaded");
    }

    @Test
    void theRunningIntegrationIsFoundThroughASymlink() throws Exception {
        Path link = Files.createSymbolicLink(home.resolve("project-link"), project);
        JsonObject result = IntegrationLauncher.run(link, List.of(), null, true, List.of());
        assertThat(result.getString("status")).isEqualTo("running");
        assertThat(result.getLong("pid")).isEqualTo(ProcessHandle.current().pid());
    }
}
