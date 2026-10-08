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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The folders of examples left in the temporary directory are found for cleanup, but never the folder of an example
 * that still runs, nor one just made for an example that is starting (CAMEL-25425).
 */
class ExampleHelperStaleDirsTest {

    @TempDir
    Path tmp;

    @Test
    void anOldFolderNoIntegrationRunsInIsStale() throws Exception {
        Path left = exampleDir("camel-example-1", Duration.ofHours(2));
        Path running = exampleDir("camel-example-2", Duration.ofHours(2));
        Path starting = exampleDir("camel-example-3", Duration.ZERO);
        Path other = Files.createDirectory(tmp.resolve("something-else"));
        Files.setLastModifiedTime(other, FileTime.from(Instant.now().minus(Duration.ofHours(2))));

        List<Path> stale = ExampleHelper.staleExampleDirs(tmp, List.of(running), Duration.ofHours(1));

        assertThat(stale).containsExactly(left);
        assertThat(starting).exists();
    }

    @Test
    void anIntegrationRunningInASubfolderKeepsTheFolder() throws Exception {
        Path dir = exampleDir("camel-example-1", Duration.ofHours(2));
        Path sub = Files.createDirectories(dir.resolve("app"));

        assertThat(ExampleHelper.staleExampleDirs(tmp, List.of(sub), Duration.ZERO)).isEmpty();
    }

    private Path exampleDir(String name, Duration age) throws Exception {
        Path dir = Files.createDirectory(tmp.resolve(name));
        Files.writeString(dir.resolve("route.camel.yaml"), "- route: {}\n");
        Files.setLastModifiedTime(dir, FileTime.from(Instant.now().minus(age)));
        return dir;
    }
}
