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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel run . (or camel run dirName) runs the directory as --source-dir, so dev mode reloads changed and new files.
 */
class RunDirectoryTest {

    private static Run run(String... args) {
        Run command = new Run(new CamelJBangMain());
        CommandLine.populateCommand(command, args);
        return command;
    }

    @Test
    void directoryOfRouteFilesRunsAsSourceDir(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("orders.camel.yaml"), "- route:\n");

        assertThat(run(dir.toString()).runsDirectoryAsSourceDir(dir)).isTrue();
        assertThat(run(dir.toString(), "--runtime=main").runsDirectoryAsSourceDir(dir)).isTrue();
    }

    @Test
    void mavenProjectKeepsRunningItsFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");

        assertThat(run(dir.toString()).runsDirectoryAsSourceDir(dir)).isFalse();
    }

    @Test
    void exportedRuntimesKeepRunningTheFiles(@TempDir Path dir) {
        assertThat(run(dir.toString(), "--runtime=spring-boot").runsDirectoryAsSourceDir(dir)).isFalse();
        assertThat(run(dir.toString(), "--runtime=quarkus").runsDirectoryAsSourceDir(dir)).isFalse();
    }

    @Test
    void filesOfTheCurrentDirectoryHaveNoDotPrefix() {
        List<String> files = new ArrayList<>();
        RunHelper.dirToFiles(".", files);

        assertThat(files).isNotEmpty().contains("pom.xml").noneMatch(f -> f.startsWith("./"));
    }

    @Test
    void filesOfAnotherDirectoryKeepTheDirectory(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("orders.camel.yaml"), "- route:\n");
        List<String> files = new ArrayList<>();
        RunHelper.dirToFiles(dir + "/", files);

        assertThat(files).containsExactly(dir + "/orders.camel.yaml");
    }
}
