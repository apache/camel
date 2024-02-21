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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "delete",
                     description = "Removes a plugin.")
public class PluginDelete extends PluginBaseCommand {

    @CommandLine.Parameters(description = "The Camel plugin to remove.",
                            paramLabel = "<plugin>")
    String name;

    public PluginDelete(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        JsonObject pluginConfig = loadConfig();
        JsonObject plugins = pluginConfig.getMap("plugins");

        Object plugin = plugins.remove(name);
        if (plugin != null) {
            printer().printf("Plugin %s removed%n", name);
            saveConfig(pluginConfig);
        } else {
            printer().printf("Plugin %s not found in configuration%n", name);
        }

        return 0;
    }

}
