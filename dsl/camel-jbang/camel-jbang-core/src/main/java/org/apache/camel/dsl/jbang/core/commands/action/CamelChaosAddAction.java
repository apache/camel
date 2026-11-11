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

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "chaos-add",
                   description = "Add chaos rules to a running integration (camel-chaos, for development only)",
                   generateHelp = true,
                   footer = {
                           "The integration must have camel-chaos switched on (camel run --chaos, profile",
                           "dev). The rule is checked as camel.chaos.rules is; a rule that is not valid is",
                           "refused with the reason. Its id (r1, r2, ...) is printed, for chaos-change and",
                           "chaos-remove. Several --rule options (or rules separated by ;) are added",
                           "together, all or none, so they are in place at the same time. A rule that",
                           "matches nothing in the integration now (often a typo) is added with a warning.",
                           "The rule is printed in plain words too, to check it does what was meant.",
                           "",
                           "Without a name or pid, the rule goes to the only integration that runs, or the",
                           "only one with chaos switched on; when that is not clear, the command fails with",
                           "the list of the running integrations (exit code 2).",
                           "",
                           "Examples:",
                           "  camel cmd chaos-add --rule='endpoint=kafka:orders* delay duration=2s rate=0.2 until=5m'",
                           "  camel cmd chaos-add myApp --rule='endpoint=file:inbox* drop at=poll rate=0.5 until=2m'",
                           "  camel cmd chaos-add myApp --rule='step=enrich* error exception=java.sql.SQLException rate=0.1'",
                           "  camel cmd chaos-add myApp --rule='endpoint=http:* in=orders error status=503 rate=0.1'",
                           "  camel cmd chaos-add myApp --rule='route=orders error rate=0.05'",
                           "  camel cmd chaos-add myApp --rule='endpoint=http:stock* error when=\"$${header.country} == \\\"ES\\\"\"'",
                           "  camel cmd chaos-add myApp --rule='endpoint=seda:dlq error at=dlq' --rule='endpoint=http:stock* error'",
                           "  camel cmd chaos-add myApp --dry-run --rule='endpoint=http:* in=orders error status=503'" })
public class CamelChaosAddAction extends CamelChaosAction {

    @Argument(description = "Name or pid of the running Camel integration (must match one); without it,"
                            + " the only one running, or the only one with chaos switched on")
    String name;

    @OptionList(name = "rule", valueSeparator = OptionList.NO_SEPARATOR,
                description = "A rule in its one-line text form, as an entry of camel.chaos.rules; can be given more"
                              + " than once",
                required = true)
    List<String> rules;

    @Option(name = "dry-run", hasValue = false,
            description = "Check the rules without adding them: each in plain words, and what it would hit in"
                          + " the integration now (the sends, polls, routes or steps, with their route)")
    boolean dryRun;

    public CamelChaosAddAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // a rule goes to one integration: its id is only known there
        CamelProcess process = findChaosTarget(name);
        // the rules go together, separated by ; as in camel.chaos.rules (a ; inside a rule is quoted)
        String text = String.join(";", rules);
        if (dryRun) {
            printer().println(describeCheck(chaos(process, "check", root -> root.put("rule", text))));
            return 0;
        }
        printer().println(describe(chaos(process, "add", root -> root.put("rule", text))));
        return 0;
    }

    /**
     * The rules of a dry run: each in plain words, then what it would hit now, by kind, and its warnings.
     */
    static String describeCheck(JsonObject answer) {
        StringBuilder sb = new StringBuilder();
        List<Object> checked = answer.getCollection("rules");
        if (checked == null) {
            return "The integration did not check the rules";
        }
        for (Object o : checked) {
            JsonObject rule = (JsonObject) o;
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("Would add: ").append(rule.getString("rule"));
            String words = rule.getString("description");
            if (words != null) {
                sb.append("\n  ").append(words);
            }
            JsonObject hits = (JsonObject) rule.get("hits");
            if (hits != null) {
                hits(sb, hits, "sends", "send", "endpoint");
                hits(sb, hits, "dynamicSends", "send (known when it sends)", "uri");
                hits(sb, hits, "polls", "poll", "endpoint");
                hits(sb, hits, "routes", "route", "from");
                hits(sb, hits, "steps", "step", "step");
                Integer more = hits.getInteger("more");
                if (more != null && more > 0) {
                    sb.append("\n  and ").append(more).append(" more");
                }
                if (hits.isEmpty()) {
                    sb.append("\n  hits nothing in the integration now");
                }
            }
            List<Object> warnings = rule.getCollection("warnings");
            if (warnings != null) {
                warnings.forEach(w -> sb.append("\n  Warning: ").append(w));
            }
        }
        return sb + "\n(dry run: nothing was added)";
    }

    private static void hits(StringBuilder sb, JsonObject hits, String key, String kind, String field) {
        List<Object> list = hits.getCollection(key);
        if (list == null) {
            return;
        }
        for (Object o : list) {
            JsonObject hit = (JsonObject) o;
            sb.append("\n  ").append(kind).append(' ');
            if ("route".equals(kind)) {
                sb.append(hit.getString("route")).append(" (from ").append(hit.getString(field)).append(')');
            } else {
                sb.append(hit.getString(field)).append(" in route ").append(hit.getString("route"));
                if (hit.getString("step") != null && !"step".equals(kind)) {
                    sb.append(" (step ").append(hit.getString("step")).append(')');
                }
            }
        }
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
