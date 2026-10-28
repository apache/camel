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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFoldersTest {

    @TempDir
    Path dir;

    @Test
    void aWorkspaceOfCheckoutsIsNotOneProject() throws IOException {
        // a repository, a Maven build, a Camel JBang project, and two folders that are none
        Files.createDirectories(dir.resolve("camel/.git"));
        Files.writeString(Files.createDirectories(dir.resolve("camel-spring-boot")).resolve("pom.xml"), "<project/>");
        Files.writeString(Files.createDirectories(dir.resolve("orders")).resolve("application.properties"), "");
        Files.createDirectories(dir.resolve("inbox"));
        Files.createDirectories(dir.resolve(".idea"));
        Files.writeString(dir.resolve("notes.md"), "# notes");

        assertThat(ProjectFolders.notOneProject(dir))
                .endsWith(" holds 3 projects (camel, camel-spring-boot, orders)");
    }

    @Test
    void manyProjectsAreCountedAndTheFirstNamed() throws IOException {
        for (String name : new String[] { "a", "b", "c", "d", "e" }) {
            Files.createDirectories(dir.resolve(name + "/.git"));
        }
        assertThat(ProjectFolders.notOneProject(dir)).endsWith(" holds 5 projects (a, b, c and 2 more)");
    }

    @Test
    void aFolderWithItsOwnBuildOrRoutesIsOneProject() throws IOException {
        Files.createDirectories(dir.resolve("module-a/.git"));
        Files.createDirectories(dir.resolve("module-b/.git"));
        // a Camel JBang project keeps its routes at the top
        Files.writeString(dir.resolve("orders.camel.yaml"), "- route:\n");
        assertThat(ProjectFolders.notOneProject(dir)).isNull();

        // and a repository with its own sub-repositories is still one project
        Files.delete(dir.resolve("orders.camel.yaml"));
        Files.createDirectories(dir.resolve(".git"));
        assertThat(ProjectFolders.notOneProject(dir)).isNull();
    }

    @Test
    void aFolderWithOneProjectOrNoneIsLeftToTheOverview() throws IOException {
        // what it holds may be the routes of the project, kept in folders of their own
        Files.createDirectories(dir.resolve("routes"));
        Files.writeString(dir.resolve("routes/orders.yaml"), "- route:\n");
        Files.createDirectories(dir.resolve("library/.git"));
        assertThat(ProjectFolders.notOneProject(dir)).isNull();
    }

    @Test
    void theHomeFolderAndTheRootAreNeverOneProject() throws IOException {
        // whatever the home folder holds, even routes at its top
        Files.writeString(dir.resolve("orders.camel.yaml"), "- route:\n");
        assertThat(ProjectFolders.notOneProject(dir, dir)).endsWith(" is your home folder");
        assertThat(ProjectFolders.notOneProject(dir.getRoot(), dir)).endsWith(" is the root of the file system");
        assertThat(ProjectFolders.notOneProject(null, dir)).isNull();
    }
}
