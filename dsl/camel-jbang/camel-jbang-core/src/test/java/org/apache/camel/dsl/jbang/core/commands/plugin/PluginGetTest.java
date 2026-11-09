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
package org.apache.camel.dsl.jbang.core.commands.plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginGetTest extends CamelCommandBaseTestSupport {

    @TempDir
    Path customConfigDir;

    @BeforeEach
    public void setup() throws Exception {
        super.setup();

        useTestHomeDir();
        PluginHelper.createPluginConfig();
    }

    @Test
    public void shouldGetEmptyPlugins() throws Exception {
        PluginGet command = new PluginGet(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        Assertions.assertEquals("", printer.getOutput());
    }

    @Test
    public void shouldGetPlugin() throws Exception {
        PluginHelper.enable(PluginType.KUBERNETES);

        PluginGet command = new PluginGet(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        List<String> output = printer.getLines();
        Assertions.assertEquals(4, output.size());
        Assertions.assertEquals("Installed plugins:", output.get(0));
        Assertions.assertEquals("NAME        COMMAND     VENDOR  DEPENDENCY                                      DESCRIPTION",
                output.get(2));
        Assertions.assertEquals(
                "kubernetes  kubernetes  ASF     org.apache.camel:camel-jbang-plugin-kubernetes  %s"
                        .formatted(PluginType.KUBERNETES.getDescription()),
                output.get(3));
    }

    @Test
    public void shouldUseCustomPluginConfigFile() throws Exception {
        Path customConfig = customConfigDir.resolve("custom-plugins.json");
        String configContent = """
                {
                  "plugins": {
                    "custom-plugin": {
                      "name": "custom-plugin",
                      "command": "custom",
                      "description": "Custom plugin from custom config"
                    }
                  }
                }
                """;
        Files.writeString(customConfig, configContent, StandardOpenOption.CREATE);

        PluginGet command = new PluginGet(new CamelJBangMain().withPrinter(printer));
        command.pluginConfig = customConfig.toString();
        command.doCall();

        List<String> output = printer.getLines();
        Assertions.assertEquals(4, output.size());
        Assertions.assertTrue(output.get(3).contains("custom-plugin"));
        Assertions.assertTrue(output.get(3).contains("Custom plugin from custom config"));
    }

    @Test
    public void shouldUseCustomPluginConfigDirectory() throws Exception {
        Path configDir = customConfigDir.resolve("config-dir");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve(PluginHelper.PLUGIN_CONFIG_FILE);
        String configContent = """
                {
                  "plugins": {
                    "dir-plugin": {
                      "name": "dir-plugin",
                      "command": "dir",
                      "description": "Plugin from directory config"
                    }
                  }
                }
                """;
        Files.writeString(configFile, configContent, StandardOpenOption.CREATE);

        PluginGet command = new PluginGet(new CamelJBangMain().withPrinter(printer));
        command.pluginConfig = configDir.toString();
        command.doCall();

        List<String> output = printer.getLines();
        Assertions.assertEquals(4, output.size());
        Assertions.assertTrue(output.get(3).contains("dir-plugin"));
        Assertions.assertTrue(output.get(3).contains("Plugin from directory config"));
    }

    @Test
    public void shouldGenerateDependencyAndDescription() throws Exception {
        JsonObject pluginConfig = PluginHelper.getOrCreatePluginConfig();
        JsonObject plugins = pluginConfig.getMap("plugins");

        JsonObject fooPlugin = new JsonObject();
        fooPlugin.put("name", "foo");
        fooPlugin.put("command", "foo");
        plugins.put("foo-plugin", fooPlugin);

        PluginHelper.savePluginConfig(pluginConfig);

        PluginGet command = new PluginGet(new CamelJBangMain().withPrinter(printer));
        command.doCall();

        List<String> output = printer.getLines();
        Assertions.assertEquals(4, output.size());
        Assertions.assertEquals("Installed plugins:", output.get(0));
        Assertions.assertEquals("NAME  COMMAND  VENDOR  DEPENDENCY                               DESCRIPTION", output.get(2));
        Assertions.assertEquals(
                "foo   foo              org.apache.camel:camel-jbang-plugin-foo  Plugin foo called with command foo",
                output.get(3));
    }

}
