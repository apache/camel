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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "list",
                     description = "List all available plugins", sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel plugin list",
                             "  camel plugin list --json" })
public class PluginList extends PluginGet {

    public PluginList(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Row> installed = installedRows();
        if (!jsonOutput) {
            if (!installed.isEmpty()) {
                printer().println("Installed plugins:");
                printer().println();
            }
            printRows(installed);
        }

        JsonObject plugins = loadConfig().getMap("plugins");

        List<Row> bundled = new ArrayList<>();
        for (PluginType camelPlugin : PluginType.values()) {
            if (plugins.get(camelPlugin.getName()) == null) {
                String dependency = "org.apache.camel:camel-jbang-plugin-%s".formatted(camelPlugin.getCommand());
                bundled.add(new Row(
                        camelPlugin.getName(), camelPlugin.getCommand(), dependency,
                        camelPlugin.getDescription(), camelPlugin.getRepos(), camelPlugin.getVendor()));
            }
        }

        if (!jsonOutput && !bundled.isEmpty()) {
            printer().println();
            printer().println("Bundled plugins:");
            printer().println();
            printRows(bundled);
        }

        List<Row> known = new ArrayList<>();
        List<JsonObject> knownPlugins = PluginHelper.loadKnownPlugins();
        for (JsonObject kp : knownPlugins) {
            String kpName = kp.getString("name");
            if (plugins.get(kpName) == null && PluginType.findByName(kpName).isEmpty()) {
                String dep = kp.getString("groupId") != null && kp.getString("artifactId") != null
                        ? "%s:%s".formatted(kp.getString("groupId"), kp.getString("artifactId"))
                        : kp.getStringOrDefault("dependency", "");
                known.add(new Row(
                        kpName, kp.getString("command"), dep,
                        kp.getString("description"), kp.getString("repos"),
                        kp.getStringOrDefault("vendor", "Community")));
            }
        }

        if (jsonOutput) {
            // one document: the installed plugins, then the ones that can be installed
            List<JsonObject> rows = new ArrayList<>();
            installed.forEach(r -> rows.add(toJson(r, "installed")));
            bundled.forEach(r -> rows.add(toJson(r, "available")));
            known.forEach(r -> rows.add(toJson(r, "available")));
            JsonOutput.printList(printer(), rows);
        } else if (!known.isEmpty()) {
            printer().println();
            printer().println("Known 3rd party plugins:");
            printer().println();
            printRows(known);
        }

        return 0;
    }
}
