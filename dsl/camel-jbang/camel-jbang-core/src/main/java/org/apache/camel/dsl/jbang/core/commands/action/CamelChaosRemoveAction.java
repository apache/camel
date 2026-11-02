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

@CommandLine.Command(name = "chaos-remove",
                     description = "Remove a chaos rule of a running integration (camel-chaos, for development only)",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nThe rule stays in the report as removed. Use chaos-clear to remove all rules.",
                             "%nExamples:",
                             "  camel cmd chaos-remove myApp --id=r1" })
public class CamelChaosRemoveAction extends CamelChaosAction {

    @CommandLine.Parameters(description = "Name or pid of the running Camel integration (must match one)", arity = "1")
    String name;

    @CommandLine.Option(names = { "--id" }, required = true, description = "The id of the rule, such as r1")
    String id;

    public CamelChaosRemoveAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // ids are per integration: r1 of one integration is not r1 of another
        CamelProcess process = findOne(name);
        printer().println(describe(chaos(process, "remove", root -> root.put("id", id))));
        return 0;
    }

    @Override
    protected String describe(JsonObject answer) {
        return "Removed chaos rule " + answer.getString("id");
    }
}
