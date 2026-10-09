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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

@CommandLine.Command(name = "semantic", description = "List semantic definitions and expert contracts",
                     sortOptions = false, showDefaultValues = true)
public class CamelSemantic extends SemanticActionCommand {

    @CommandLine.Option(names = "--expert", description = "Show the operations and parameter contract of this expert")
    String expert;

    public CamelSemantic(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected JsonObject request() {
        if (expert != null && expert.isBlank()) {
            throw new IllegalArgumentException("--expert must not be blank");
        }
        JsonObject request = new JsonObject();
        request.put("action", "semantic-metadata");
        if (expert == null) {
            request.put("overview", true);
        } else {
            request.put("expert", expert);
        }
        return request;
    }

    @Override
    protected int printResponse(JsonObject response) {
        if (expert != null) {
            JsonArray operations = response.getCollection("operations");
            if (operations == null || operations.isEmpty()) {
                return error(3, "No operations available for semantic expert: " + expert, null);
            }
        }
        return super.printResponse(response);
    }

    @Override
    protected void render(JsonObject response) {
        if (expert != null) {
            printer().println("Expert: " + expert);
            renderOperations(response.getCollection("operations"));
            return;
        }
        printer().println("Definitions:");
        printer().println("NAME\tEXPERT\tOPERATION\tSTATE\tRESULT TYPE\tERROR");
        JsonArray evaluations = response.getCollection("evaluations");
        if (evaluations != null) {
            for (int i = 0; i < evaluations.size(); i++) {
                JsonObject value = evaluations.getMap(i);
                printer().println(String.join("\t", text(value, "name"), text(value, "expert"), text(value, "operation"),
                        text(value, "state"), text(value, "resultType"), text(value, "error")));
            }
        }
        printer().println("Experts:");
        JsonArray experts = response.getCollection("experts");
        if (experts != null) {
            for (int i = 0; i < experts.size(); i++) {
                JsonObject value = experts.getMap(i);
                printer().println(text(value, "reference") + "\t" + text(value, "name") + "\t" + text(value, "error"));
            }
        }
        printer().println("Default expert: " + text(response, "defaultExpert"));
        if (response.get("defaultError") != null) {
            printer().println("Default expert error: " + response.get("defaultError"));
        }
    }

    private void renderOperations(JsonArray operations) {
        for (int i = 0; i < operations.size(); i++) {
            JsonObject operation = operations.getMap(i);
            printer().println(text(operation, "name") + " (" + text(operation, "resultType") + "): "
                              + text(operation, "description"));
            printer().println(Jsoner.prettyPrint(Jsoner.serialize(operation.get("contract"))));
        }
    }

    private static String text(JsonObject value, String key) {
        return value.get(key) == null ? "" : value.get(key).toString();
    }
}
