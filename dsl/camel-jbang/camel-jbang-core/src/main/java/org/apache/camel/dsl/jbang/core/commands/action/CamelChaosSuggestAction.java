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
import java.util.stream.Collectors;

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
                             "the polls fail, the route stalls), and then two faults at once (an outage while",
                             "the dead letter channel fails, a slow service that also fails). The most worth",
                             "trying come first, each with why, the command that adds it, and how to feed the",
                             "route with messages (curl, a file, or camel cmd send), unless it feeds itself.",
                             "Nothing is added.",
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
            // a dead letter channel that routes share is suggested once, for all of them
            List<Object> routes = s.getCollection("routes");
            String where = routes != null && routes.size() > 1
                    ? "routes " + routes.stream().map(String::valueOf).collect(Collectors.joining(", "))
                    : "route " + s.getString("route");
            sb.append("\n\n").append(++n).append(". ").append(where).append(": ")
                    .append(s.getString("title")).append(" (").append(s.getString("kind")).append(')');
            if (s.getBooleanOrDefault("tried", false)) {
                // a rule of this kind faulted the route already (camel get chaos --report shows how it went)
                sb.append(" - tried");
            }
            sb.append("\n   ").append(s.getString("why"));
            // two faults at once are added together
            List<Object> rules = s.getCollection("rules");
            sb.append("\n   camel cmd chaos-add");
            for (Object rule : rules != null && !rules.isEmpty() ? rules : List.of(s.getString("rule"))) {
                sb.append(" --rule='").append(rule).append('\'');
            }
            JsonObject feed = s.getMap("feed");
            if (feed != null && !"self".equals(feed.getString("kind"))) {
                // how to send messages into the route, so the rule has something to act on
                sb.append("\n   feed: ").append(feed.getString("hint"));
                if (feed.getBooleanOrDefault("real", false)) {
                    sb.append("\n   (this sends to the real system: run with --stub=remote to feed a stub instead)");
                }
            }
        }
        return sb.toString();
    }
}
