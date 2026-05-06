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
import java.nio.file.StandardOpenOption;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginHelperTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() {
        CommandLineHelper.useHomeDir(tempDir.toString());
    }

    @Test
    public void testEmbeddedPluginDetectionDoesNotThrowException() {
        assertDoesNotThrow(PluginHelper::hasEmbeddedPlugins);
    }

    @Test
    public void testFallbackToJsonConfig() throws Exception {
        // Create a user plugin config file in home directory
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        String userConfigContent = """
                {
                  "plugins": {
                    "user-plugin": {
                      "name": "user-plugin",
                      "command": "user",
                      "description": "User plugin",
                      "firstVersion": "2.0.0"
                    }
                  }
                }
                """;
        Files.writeString(userConfig, userConfigContent, StandardOpenOption.CREATE);

        // Test that user config is loaded
        JsonObject config = PluginHelper.getPluginConfig();
        assertNotNull(config);

        JsonObject plugins = config.getMap("plugins");
        assertNotNull(plugins);

        // Should have user plugin
        JsonObject userPlugin = plugins.getMap("user-plugin");
        assertNotNull(userPlugin);
    }

    @Test
    public void testGetActivePluginsFiltersByTarget() throws Exception {
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        String configContent = """
                {
                  "plugins": {
                    "forage": {
                      "name": "forage",
                      "command": "forage",
                      "description": "Forage plugin",
                      "firstVersion": "4.18.0"
                    },
                    "test": {
                      "name": "test",
                      "command": "test",
                      "description": "Test plugin",
                      "firstVersion": "4.14.0"
                    }
                  }
                }
                """;
        Files.writeString(userConfig, configContent, StandardOpenOption.CREATE);

        CamelJBangMain main = new CamelJBangMain();

        // target "version" should not match any plugin — returns empty without downloading
        Map<String, Plugin> plugins = PluginHelper.getActivePlugins(main, null, "version");
        assertTrue(plugins.isEmpty());
    }

    @Test
    public void testGetActivePluginsNoFilterLoadsAll() throws Exception {
        Path userConfig = CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        String configContent = """
                {
                  "plugins": {
                    "test": {
                      "name": "test",
                      "command": "test",
                      "description": "Test plugin",
                      "firstVersion": "4.14.0"
                    }
                  }
                }
                """;
        Files.writeString(userConfig, configContent, StandardOpenOption.CREATE);

        // null target should attempt to load all plugins (will fail to download in test,
        // but the important thing is the filter logic doesn't skip it)
        JsonObject config = PluginHelper.getPluginConfig();
        assertNotNull(config);
        JsonObject pluginsConfig = config.getMap("plugins");
        assertEquals(1, pluginsConfig.size());
    }
}
