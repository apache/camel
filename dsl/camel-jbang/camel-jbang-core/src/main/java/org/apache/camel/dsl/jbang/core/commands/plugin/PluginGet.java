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
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.core.common.PluginType;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "get",
                     description = "Get installed plugins", sortOptions = false, showDefaultValues = true)
public class PluginGet extends PluginBaseCommand {

    @Deprecated
    @CommandLine.Option(names = { "--all" }, hidden = true, defaultValue = "false",
                        description = "DEPRECATED: use 'camel plugin list' instead")
    public boolean all;

    @CommandLine.Option(names = { "--repos" }, defaultValue = "false", description = "Display maven repository column")
    public boolean repos;

    public PluginGet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (all) {
            PluginList delegate = new PluginList(getMain());
            delegate.repos = this.repos;
            return delegate.doCall();
        }

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

            String vendor = resolveVendor(name);
            rows.add(new Row(name, command, dependency, description, repos, vendor));
        });

        if (!rows.isEmpty()) {
            printer().println("Installed plugins:");
            printer().println();
        }
        printRows(rows);

        return 0;
    }

    private String resolveVendor(String name) {
        return PluginType.findByName(name)
                .map(PluginType::getVendor)
                .or(() -> PluginHelper.findKnownPlugin(name).map(kp -> kp.getStringOrDefault("vendor", "Community")))
                .orElse("");
    }

    protected void printRows(List<Row> rows) {
        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("NAME").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.name),
                    new Column().header("COMMAND").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.command),
                    new Column().header("VENDOR").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.vendor != null ? r.vendor : ""),
                    new Column().header("DEPENDENCY").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.dependency),
                    new Column().visible(repos).header("REPOSITORY").headerAlign(HorizontalAlign.LEFT)
                            .dataAlign(HorizontalAlign.LEFT)
                            .with(r -> r.repos),
                    new Column().header("DESCRIPTION").headerAlign(HorizontalAlign.LEFT).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(80, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.description))));
        }
    }

    record Row(String name, String command, String dependency, String description, String repos, String vendor) {
    }
}
