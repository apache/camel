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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "chaos-explore-start",
                     description = "Start a chaos exploration in a running integration: a time to try chaos rules in,"
                                   + " that camel-chaos enforces",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nWhile the exploration runs, camel-chaos keeps its journal: the baseline (the counts",
                             "per route before its first rule), the rules tried, and the experiments",
                             "(camel cmd chaos-experiment). A rule ends at the deadline at the latest, and at the",
                             "deadline the rules are removed and the exploration is finished, whatever happens.",
                             "One exploration runs at a time. camel get chaos --report shows it.",
                             "%nExamples:",
                             "  camel cmd chaos-explore-start",
                             "  camel cmd chaos-explore-start myApp --budget=30m --focus=orders" })
public class CamelChaosExploreStartAction extends CamelChaosAction {

    @CommandLine.Parameters(description = "Name or pid of the running Camel integration (must match one); without it,"
                                          + " the only one running, or the only one with chaos switched on",
                            arity = "0..1")
    String name;

    @CommandLine.Option(names = { "--budget" }, defaultValue = "10m",
                        description = "How long the exploration runs, between 10s and 2h")
    String budget;

    @CommandLine.Option(names = { "--focus" },
                        description = "What the exploration is about, such as a route")
    String focus;

    public CamelChaosExploreStartAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CamelProcess process = findChaosTarget(name);
        printer().println(describe(chaos(process, "explore-start", root -> {
            root.put("budget", budget);
            if (focus != null && !focus.isBlank()) {
                root.put("focus", focus);
            }
        })));
        return 0;
    }

    @Override
    protected String describe(JsonObject answer) {
        String focus = answer.getString("focus");
        return "Chaos exploration started: " + TimeUtils.printDuration(answer.getLongOrDefault("budget", 0))
               + (focus != null ? " (focus: " + focus + ")" : "");
    }
}
