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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "chaos-suggest",
                     description = "Suggest chaos rules to try in a running integration, per route (camel-chaos, for development only)",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nThe rules come from what the routes do: an HTTP call without a circuit breaker",
                             "(the service answers with an error, or does not answer in time), a circuit",
                             "breaker (the service is down for a while), a call that the input may repeat",
                             "(Kafka, JMS: the call is made twice), a dead letter channel (it fails too), a",
                             "hand-off to another route over seda (the next route fails, and no message may",
                             "be lost between the two), an input from outside (the route fails at its entry,",
                             "the polls fail, the route stalls). The most worth trying come first, each with",
                             "why and the command that adds it. Nothing is added.",
                             "%nWithout a name or pid, the only integration that runs, or the only one with",
                             "chaos switched on.",
                             "%nExamples:",
                             "  camel cmd chaos-suggest",
                             "  camel cmd chaos-suggest myApp" })
public class CamelChaosSuggestAction extends CamelChaosAction {

    @CommandLine.Parameters(description = "Name or pid of the running Camel integration (must match one); without it,"
                                          + " the only one running, or the only one with chaos switched on",
                            arity = "0..1")
    String name;

    public CamelChaosSuggestAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CamelProcess process = findChaosTarget(name);
        printer().println(describe(chaos(process, "suggest", null)));
        return 0;
    }

    @Override
    protected String describe(JsonObject answer) {
        List<Object> suggestions = answer.getCollection("suggestions");
        if (suggestions == null || suggestions.isEmpty()) {
            return "Nothing to suggest: the routes call nothing remote, and have no input from outside, dead letter"
                   + " channel or hand-off to another route";
        }
        StringBuilder sb = new StringBuilder("Chaos rules to try, the most worth trying first:");
        int n = 0;
        for (Object o : suggestions) {
            JsonObject s = (JsonObject) o;
            sb.append("\n\n").append(++n).append(". route ").append(s.getString("route")).append(": ")
                    .append(s.getString("title")).append(" (").append(s.getString("kind")).append(')');
            sb.append("\n   ").append(s.getString("why"));
            sb.append("\n   camel cmd chaos-add --rule='").append(s.getString("rule")).append('\'');
        }
        return sb.toString();
    }
}
