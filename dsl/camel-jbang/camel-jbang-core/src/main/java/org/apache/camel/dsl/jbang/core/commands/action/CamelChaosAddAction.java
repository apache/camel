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

@CommandLine.Command(name = "chaos-add",
                     description = "Add chaos rules to a running integration (camel-chaos, for development only)",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nThe integration must have camel-chaos switched on (camel run --chaos, profile",
                             "dev). The rule is checked as camel.chaos.rules is; a rule that is not valid is",
                             "refused with the reason. Its id (r1, r2, ...) is printed, for chaos-change and",
                             "chaos-remove. Several --rule options (or rules separated by ;) are added",
                             "together, all or none, so they are in place at the same time. A rule that",
                             "matches nothing in the integration now (often a typo) is added with a warning.",
                             "The rule is printed in plain words too, to check it does what was meant.",
                             "%nWithout a name or pid, the rule goes to the only integration that runs, or the",
                             "only one with chaos switched on; when that is not clear, the command fails with",
                             "the list of the running integrations (exit code 2).",
                             "%nExamples:",
                             "  camel cmd chaos-add --rule='endpoint=kafka:orders* delay duration=2s rate=0.2 until=5m'",
                             "  camel cmd chaos-add myApp --rule='endpoint=file:inbox* drop at=poll rate=0.5 until=2m'",
                             "  camel cmd chaos-add myApp --rule='step=enrich* error exception=java.sql.SQLException rate=0.1'",
                             "  camel cmd chaos-add myApp --rule='endpoint=http:* in=orders error status=503 rate=0.1'",
                             "  camel cmd chaos-add myApp --rule='route=orders error rate=0.05'",
                             "  camel cmd chaos-add myApp --rule='endpoint=http:stock* error when=\"$${header.country} == \\\"ES\\\"\"'",
                             "  camel cmd chaos-add myApp --rule='endpoint=seda:dlq error at=dlq' --rule='endpoint=http:stock* error'" })
public class CamelChaosAddAction extends CamelChaosAction {

    @CommandLine.Parameters(description = "Name or pid of the running Camel integration (must match one); without it,"
                                          + " the only one running, or the only one with chaos switched on",
                            arity = "0..1")
    String name;

    @CommandLine.Option(names = { "--rule" }, required = true,
                        description = "A rule in its one-line text form, as an entry of camel.chaos.rules; can be given more"
                                      + " than once")
    List<String> rules;

    public CamelChaosAddAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // a rule goes to one integration: its id is only known there
        CamelProcess process = findChaosTarget(name);
        // the rules go together, separated by ; as in camel.chaos.rules (a ; inside a rule is quoted)
        String text = String.join(";", rules);
        printer().println(describe(chaos(process, "add", root -> root.put("rule", text))));
        return 0;
    }

    @Override
    protected String describe(JsonObject answer) {
        List<Object> ids = answer.getCollection("ids");
        if (ids != null && ids.size() > 1) {
            StringBuilder sb = new StringBuilder("Added chaos rules ")
                    .append(ids.stream().map(String::valueOf).collect(Collectors.joining(", ")));
            // the rules in plain words, one per line (an integration before 4.24 does not send them)
            List<Object> words = answer.getCollection("descriptions");
            if (words != null && words.size() == ids.size()) {
                for (int i = 0; i < ids.size(); i++) {
                    sb.append("\n  ").append(ids.get(i)).append(": ").append(words.get(i));
                }
            }
            return sb + warnings(answer);
        }
        return "Added chaos rule " + answer.getString("id") + inWords(answer) + warnings(answer);
    }
}
