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

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

/**
 * Base command for plugin subcommands. Provides the {@code --plugin-config} option that overrides the default plugin
 * configuration file location.
 */
abstract class PluginBaseCommand extends CamelCommand {

    @CommandLine.Option(names = { "--plugin-config" },
                        description = "Path to a custom plugin configuration file or directory. "
                                      + "Only applies to the plugin subcommands; "
                                      + "for plugin commands themselves (e.g. 'camel kubernetes'), "
                                      + "set CAMEL_CLI_PLUGIN_CONFIG instead.")
    String pluginConfig;

    protected PluginBaseCommand(CamelJBangMain main) {
        super(main);
    }

    private Path pluginConfigPath() {
        if (pluginConfig != null) {
            try {
                return Path.of(pluginConfig);
            } catch (InvalidPathException e) {
                throw new IllegalStateException(
                        "Invalid path in --plugin-config option: " + pluginConfig, e);
            }
        }
        return null;
    }

    JsonObject loadConfig() {
        Path path = pluginConfigPath();
        if (path != null) {
            return PluginHelper.getOrCreatePluginConfig(path);
        }
        return PluginHelper.getOrCreatePluginConfig();
    }

    void saveConfig(JsonObject plugins) {
        Path path = pluginConfigPath();
        if (path != null) {
            PluginHelper.savePluginConfig(plugins, path);
        } else {
            PluginHelper.savePluginConfig(plugins);
        }
    }
}
