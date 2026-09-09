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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilesBrowserSourceDirectoryTest {

    private static ConfigurationTab.ConfigProperty prop(String key, String value) {
        ConfigurationTab.ConfigProperty cp = new ConfigurationTab.ConfigProperty();
        cp.key = key;
        cp.value = value;
        return cp;
    }

    @Test
    void reloadDirectoryWinsOverTheIncludePattern(@TempDir Path dir) throws IOException {
        Path original = Files.createDirectory(dir.resolve("original"));
        Path exported = Files.createDirectory(dir.resolve("exported"));
        IntegrationInfo info = new IntegrationInfo();
        info.configProperties.add(prop("camel.main.routesIncludePattern", "file:" + exported + "/foo.yaml"));
        info.configProperties.add(prop("camel.main.routesReloadDirectory", original.toString()));

        assertEquals(original, FilesBrowser.resolveSourceDirectory(info));
    }

    @Test
    void includePatternDirectoryIsUsedWithoutReload(@TempDir Path dir) {
        IntegrationInfo info = new IntegrationInfo();
        info.configProperties.add(prop("camel.main.routesIncludePattern", "file:" + dir + "/foo.yaml"));
        info.configProperties.add(prop("camel.main.routesReloadDirectory", dir.resolve("missing").toString()));

        assertEquals(dir, FilesBrowser.resolveSourceDirectory(info));
    }

    @Test
    void temporaryDirectories() throws IOException {
        Path tmp = Files.createTempDirectory("camel-tui-test");
        try {
            assertTrue(FilesBrowser.isTemporaryDirectory(tmp));
        } finally {
            Files.deleteIfExists(tmp);
        }
        assertTrue(FilesBrowser.isTemporaryDirectory(Path.of("/home/me/.camel/.camel-jbang-run/123")));
        assertFalse(FilesBrowser.isTemporaryDirectory(Path.of("/home/me/projects/demo")));
        assertFalse(FilesBrowser.isTemporaryDirectory(null));
    }
}
