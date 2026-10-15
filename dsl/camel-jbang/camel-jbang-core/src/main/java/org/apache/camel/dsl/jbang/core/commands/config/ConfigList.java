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
package org.apache.camel.dsl.jbang.core.commands.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "Displays user configuration", sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel config list",
                             "  camel config list --global",
                             "  camel config list --json" })
public class ConfigList extends CamelCommand {

    @CommandLine.Option(names = { "--global" }, description = "Use global or local configuration")
    boolean global = true;

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format: one entry per key, with its scope (local or global)")
    boolean jsonOutput;

    public ConfigList(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<JsonObject> rows = new ArrayList<>();
        listConfigurations(true, rows);
        if (global) {
            listConfigurations(false, rows);
        }
        if (jsonOutput) {
            JsonOutput.printList(printer(), rows);
        }
        return 0;
    }

    private void listConfigurations(boolean local, List<JsonObject> rows) {
        CommandLineHelper
                .loadProperties(p -> {
                    String configurationType = local ? "Local" : "Global";
                    if (!jsonOutput && !p.stringPropertyNames().isEmpty()) {
                        printer().println("----- " + configurationType + " -----");
                    }
                    for (String k : p.stringPropertyNames()) {
                        String v = p.getProperty(k);
                        if (jsonOutput) {
                            JsonObject jo = new JsonObject();
                            jo.put("key", k);
                            jo.put("value", v);
                            jo.put("scope", local ? "local" : "global");
                            rows.add(jo);
                        } else {
                            printer().printf("%s = %s%n", k, v);
                        }
                    }
                }, local);
    }
}
