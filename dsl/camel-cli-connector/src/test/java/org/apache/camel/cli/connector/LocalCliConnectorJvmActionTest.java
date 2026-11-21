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
package org.apache.camel.cli.connector;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27340: the jvm action has the JARs of the application class loader too, which camel run adds the downloaded
 * dependencies to, so a CVE audit of the classpath sees them.
 */
class LocalCliConnectorJvmActionTest {

    @TempDir
    Path dir;

    @Test
    void theJarsOfAUrlClassLoader() throws Exception {
        Path jar = Files.createFile(dir.resolve("commons-text-1.9.jar"));
        try (URLClassLoader cl = new URLClassLoader(new URL[] { jar.toUri().toURL() }, getClass().getClassLoader())) {
            assertThat(LocalCliConnector.applicationClasspath(cl)).containsExactly(jar.toString());
        }
    }

    @Test
    void noneForAnotherClassLoaderOrNone() {
        assertThat(LocalCliConnector.applicationClasspath(new ClassLoader(getClass().getClassLoader()) {
        })).isEmpty();
        assertThat(LocalCliConnector.applicationClasspath(null)).isEmpty();
    }
}
