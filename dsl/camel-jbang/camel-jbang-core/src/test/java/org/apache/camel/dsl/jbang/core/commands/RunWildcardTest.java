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

import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel run expands a wildcard that the shell passed on, such as a quoted '*' or the Windows command prompt, as a Unix
 * shell does (CAMEL-26930). It failed with "No routes found in: *".
 */
class RunWildcardTest {

    @TempDir
    Path dir;

    private static Run run(String... args) {
        Run command = new Run(new CamelJBangMain());
        CommandLineTestHelper.parse(command, args);
        return command;
    }

    @Test
    void aStarIsTheFilesOfTheFolder() throws Exception {
        Files.writeString(dir.resolve("b.camel.yaml"), "- route:\n");
        Files.writeString(dir.resolve("a.camel.yaml"), "- route:\n");
        Files.writeString(dir.resolve("application.properties"), "");
        Files.writeString(dir.resolve(".hidden.yaml"), "");

        Run run = run(dir + "/*");
        run.expandWildcards();

        // sorted as a shell does, without the hidden file
        assertThat(run.files).containsExactly(dir + "/a.camel.yaml", dir + "/application.properties",
                dir + "/b.camel.yaml");
    }

    @Test
    void aPatternIsTheFilesItMatches() throws Exception {
        Files.writeString(dir.resolve("a.camel.yaml"), "- route:\n");
        Files.writeString(dir.resolve("b.camel.xml"), "<routes/>");

        Run run = run(dir + "/*.yaml", dir + "/b.camel.xm?");
        run.expandWildcards();

        assertThat(run.files).containsExactly(dir + "/a.camel.yaml", dir + "/b.camel.xml");
    }

    @Test
    void otherFilesAreLeftAsGiven() throws Exception {
        Files.writeString(dir.resolve("a.camel.yaml"), "- route:\n");

        Run run = run(dir + "/a.camel.yaml", "github:apache:camel-kamelets-examples:jbang/*", "file:" + dir + "/*");
        run.expandWildcards();

        assertThat(run.files).containsExactly(dir + "/a.camel.yaml", "github:apache:camel-kamelets-examples:jbang/*",
                "file:" + dir + "/*");
    }

    @Test
    void aWildcardThatMatchesNothingIsAFileNotFound() {
        Run run = run(dir + "/*.yaml");

        assertThatThrownBy(run::expandWildcards)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.FILE_NOT_FOUND))
                .hasMessage("No file matches: " + dir + "/*.yaml");
    }
}
