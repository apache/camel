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

import java.util.Optional;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "add",
                     description = "Add new plugin.")
public class PluginAdd extends PluginBaseCommand {

    @CommandLine.Parameters(description = "The Camel plugin to add.",
                            paramLabel = "<name>")
    String name;

    @CommandLine.Option(names = { "--command", "-c" },
                        description = "The command that the plugin uses.")
    String command;

    @CommandLine.Option(names = { "--description", "-d" },
                        description = "A short description of the plugin.")
    String description;

    @CommandLine.Option(names = { "--artifactId", "-a" },
                        description = "Maven artifactId.")
    String artifactId;

    @CommandLine.Option(names = { "--groupId", "-g" },
                        defaultValue = "org.apache.camel",
                        description = "Maven groupId.")
    String groupId = "org.apache.camel";

    @CommandLine.Option(names = { "--version", "-v" },
                        defaultValue = "${camel-version}",
                        description = "Maven artifact version.")
    String version;

    @CommandLine.Option(names = { "--gav" },
                        description = "Maven group and artifact coordinates.")
    String gav;

    public PluginAdd(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        JsonObject pluginConfig = loadConfig();
        JsonObject plugins = pluginConfig.getMap("plugins");

        Optional<PluginType> camelPlugin = PluginType.findByName(name);
        if (camelPlugin.isPresent()) {
            if (command == null) {
                command = camelPlugin.get().getCommand();
            }

            if (description == null) {
                description = camelPlugin.get().getDescription();
            }
        }

        if (command == null) {
            // use plugin name as command
            command = name;
        }

        JsonObject plugin = new JsonObject();
        plugin.put("name", name);
        plugin.put("command", command);
        plugin.put("description",
                description != null ? description : "Plugin %s called with command %s".formatted(name, command));

        if (gav == null && (groupId != null && artifactId != null)) {
            if (version == null) {
                CamelCatalog catalog = new DefaultCamelCatalog();
                version = catalog.getCatalogVersion();
            }

            gav = "%s:%s:%s".formatted(groupId, artifactId, version);
        }

        if (gav != null) {
            plugin.put("dependency", gav);
        }

        plugins.put(name, plugin);

        saveConfig(pluginConfig);
        return 0;
    }

}
