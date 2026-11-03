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
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "chaos-change",
                     description = "Change a chaos rule of a running integration (camel-chaos, for development only)",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nThe new rule replaces the rule with that id, and starts with new counts and a new end time;",
                             "it is the next version of the id. The report keeps the replaced version.",
                             "%nExamples:",
                             "  camel cmd chaos-change myApp --id=r1 --rule='endpoint=kafka:orders* delay duration=5s rate=0.5 until=5m'" })
public class CamelChaosChangeAction extends CamelChaosAction {

    @CommandLine.Parameters(description = "Name or pid of the running Camel integration (must match one)", arity = "1")
    String name;

    @CommandLine.Option(names = { "--id" }, required = true, description = "The id of the rule, such as r1")
    String id;

    @CommandLine.Option(names = { "--rule" }, required = true,
                        description = "The new rule, one rule in its one-line text form, as one entry of camel.chaos.rules")
    String rule;

    public CamelChaosChangeAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CamelProcess process = findOne(name);
        printer().println(describe(chaos(process, "change", root -> {
            root.put("id", id);
            root.put("rule", rule);
        })));
        return 0;
    }

    @Override
    protected String describe(JsonObject answer) {
        return "Changed chaos rule " + answer.getString("id") + " (version " + answer.get("version") + ")";
    }
}
