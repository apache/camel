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
package org.apache.camel.dsl.jbang.core.commands.action;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "chaos-clear",
                   description = "Remove all chaos rules of running integrations (camel-chaos, for development only)",
                   generateHelp = true,
                   footer = {
                           "The rules stay in the report as removed; with --reset the report starts",
                           "again (no rules, counts or verdict). Without a name it acts on every running",
                           "integration that has chaos switched on.",
                           "",
                           "Examples:",
                           "  camel cmd chaos-clear",
                           "  camel cmd chaos-clear myApp",
                           "  camel cmd chaos-clear myApp --reset" })
public class CamelChaosClearAction extends CamelChaosAction {

    @Argument(description = "Name or pid of running Camel integration")
    String name = "*";

    @Option(name = "reset", hasValue = false,
            description = "Also start a new report: the removed rules, the counts per route and the verdict are gone")
    boolean reset;

    public CamelChaosClearAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        return chaosOnEach(findChaosProcesses(name), "clear", reset ? root -> root.put("reset", true) : null);
    }

    @Override
    protected String describe(JsonObject answer) {
        String removed = "Removed chaos rules: " + answer.get("removed");
        return answer.getBooleanOrDefault("reset", false) ? removed + " (report reset)" : removed;
    }
}
