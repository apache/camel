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
package org.apache.camel.dsl.jbang.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application properties of a run, and the ones of its profile.
 */
class RunProfilePropertiesTest {

    @TempDir
    Path dir;

    @Test
    void theProfileIsReadNextToApplicationProperties() throws Exception {
        // the profile was read from the current directory, not from the folder of the files (CAMEL-26930)
        Files.writeString(dir.resolve("application.properties"), "greeting=hello\nport=8080\n");
        Files.writeString(dir.resolve("application-dev.properties"), "greeting=dev\n");
        Set<Path> loaded = new LinkedHashSet<>();

        Properties properties = RunProfileProperties.load(dir, null, "dev", loaded, p -> {
        });

        assertThat(properties).containsEntry("greeting", "dev").containsEntry("port", "8080");
        assertThat(loaded).containsExactly(dir.resolve("application.properties"),
                dir.resolve("application-dev.properties"));
    }

    @Test
    void theProfileOfASourceDirectory() throws Exception {
        Files.writeString(dir.resolve("application-dev.properties"), "greeting=dev\n");

        Properties properties = RunProfileProperties.load(Path.of("."), dir.toString(), "dev", new LinkedHashSet<>(),
                p -> {
                });

        assertThat(properties).containsEntry("greeting", "dev");
    }
}
