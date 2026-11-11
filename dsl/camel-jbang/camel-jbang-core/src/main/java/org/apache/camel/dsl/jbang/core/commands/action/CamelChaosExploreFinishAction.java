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

import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "chaos-explore-finish",
                   description = "Finish the chaos exploration of a running integration before its deadline",
                   generateHelp = true,
                   footer = {
                           "The rules are removed (an open experiment is ended with what came of it so far), and",
                           "the journal stays in the report (camel get chaos --report) until the next",
                           "exploration starts or the report is reset.",
                           "",
                           "Examples:",
                           "  camel cmd chaos-explore-finish",
                           "  camel cmd chaos-explore-finish myApp" })
public class CamelChaosExploreFinishAction extends CamelChaosAction {

    @Argument(description = "Name or pid of the running Camel integration (must match one); without it,"
                            + " the only one running, or the only one with chaos switched on")
    String name;

    public CamelChaosExploreFinishAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CamelProcess process = findChaosTarget(name);
        printer().println(describe(chaos(process, "explore-finish", null)));
        return 0;
    }

    @Override
    protected String describe(JsonObject answer) {
        long ran = answer.getLongOrDefault("finishedAt", 0) - answer.getLongOrDefault("startedAt", 0);
        List<Object> rules = answer.getCollection("rules");
        List<Object> experiments = answer.getCollection("experiments");
        StringBuilder sb = new StringBuilder("Chaos exploration finished after ").append(TimeUtils.printDuration(ran))
                .append(": ").append(rules != null ? rules.size() : 0).append(" rules tried, ")
                .append(answer.getLongOrDefault("removed", 0)).append(" removed");
        if (experiments != null && !experiments.isEmpty()) {
            long failed = experiments.stream()
                    .filter(o -> o instanceof JsonObject jo && "FAIL".equals(jo.getString("verdict"))).count();
            sb.append(", ").append(experiments.size()).append(experiments.size() == 1 ? " experiment" : " experiments")
                    .append(" (").append(failed).append(" failed)");
        }
        return sb.toString();
    }
}
