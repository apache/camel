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
import java.util.Arrays;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "get",
                     description = "Display available plugins", sortOptions = false, showDefaultValues = true)
public class PluginGet extends PluginBaseCommand {

    @CommandLine.Option(names = { "--all" }, defaultValue = "false", description = "Display all available plugins")
    public boolean all;

    @CommandLine.Option(names = { "--repos" }, defaultValue = "false", description = "Display maven repository column")
    public boolean repos;

    public PluginGet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        JsonObject plugins = loadConfig().getMap("plugins");
        plugins.forEach((key, value) -> {
            JsonObject details = (JsonObject) value;

            String name = details.getStringOrDefault("name", key);
            String command = details.getStringOrDefault("command", name);
            String dependency = details.getStringOrDefault("dependency",
                    "org.apache.camel:camel-jbang-plugin-%s".formatted(command));
            String description
                    = details.getStringOrDefault("description", "Plugin %s called with command %s".formatted(name, command));
            String repos = details.getString("repos");

            rows.add(new Row(name, command, dependency, description, repos));
        });

        printRows(rows);

        if (all) {
            rows.clear();
            for (PluginType camelPlugin : PluginType.values()) {
                if (plugins.get(camelPlugin.getName()) == null) {
                    String dependency = "org.apache.camel:camel-jbang-plugin-%s".formatted(camelPlugin.getCommand());
                    rows.add(new Row(
                            camelPlugin.getName(), camelPlugin.getCommand(), dependency,
                            camelPlugin.getDescription(), camelPlugin.getRepos()));
                }
            }

            if (!rows.isEmpty()) {
                printer().println();
                printer().println("Supported plugins:");
                printer().println();

                printRows(rows);
            }
        }

        return 0;
    }

    private void printRows(List<Row> rows) {
        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("NAME").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.name),
                    new Column().header("COMMAND").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.command),
                    new Column().header("DEPENDENCY").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.dependency),
                    new Column().visible(repos).header("REPOSITORY").headerAlign(HorizontalAlign.LEFT)
                            .dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.repos),
                    new Column().header("DESCRIPTION").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(50, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.description))));
        }
    }

    private record Row(String name, String command, String dependency, String description, String repos) {
    }
}
