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

@CommandLine.Command(name = "chaos-add",
                     description = "Add a chaos rule to a running integration (camel-chaos, for development only)",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nThe integration must have camel-chaos switched on (camel run --chaos, profile dev). The rule is",
                             "checked as camel.chaos.rules is; a rule that is not valid is refused with the reason. Its id",
                             "(r1, r2, ...) is printed, for chaos-change and chaos-remove.",
                             "%nExamples:",
                             "  camel cmd chaos-add myApp --rule='endpoint=kafka:orders* delay duration=2s rate=0.2 until=5m'",
                             "  camel cmd chaos-add myApp --rule='endpoint=file:inbox* drop at=poll rate=0.5 until=2m'",
                             "  camel cmd chaos-add myApp --rule='step=enrich* error exception=java.sql.SQLException rate=0.1'" })
public class CamelChaosAddAction extends CamelChaosAction {

    @CommandLine.Parameters(description = "Name or pid of the running Camel integration (must match one)", arity = "1")
    String name;

    @CommandLine.Option(names = { "--rule" }, required = true,
                        description = "One rule in its one-line text form, as one entry of camel.chaos.rules")
    String rule;

    public CamelChaosAddAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // a rule goes to one integration: its id is only known there
        CamelProcess process = findOne(name);
        printer().println(describe(chaos(process, "add", root -> root.put("rule", rule))));
        return 0;
    }

    @Override
    protected String describe(JsonObject answer) {
        return "Added chaos rule " + answer.getString("id");
    }
}
