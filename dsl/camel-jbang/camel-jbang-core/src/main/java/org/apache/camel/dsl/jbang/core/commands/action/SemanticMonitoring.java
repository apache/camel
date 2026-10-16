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
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "monitoring", description = "Read current semantic measurements",
                     sortOptions = false, showDefaultValues = true)
public class SemanticMonitoring extends SemanticActionCommand {
    @CommandLine.Option(names = "--source", description = "Producer or configured metric source name")
    String source;

    public SemanticMonitoring(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected JsonObject request() {
        JsonObject request = new JsonObject();
        request.put("action", "semantic-monitoring");
        if (source != null) {
            if (source.isBlank() || source.length() > 256) {
                throw new IllegalArgumentException("Invalid --source");
            }
            request.put("source", source);
        }
        return request;
    }

    @Override
    protected int printResponse(JsonObject response) {
        if (response.containsKey("error") || !response.containsKey("snapshots")) {
            return error(CliExitCode.FAILURE, "Semantic monitoring is unavailable", response);
        }
        return super.printResponse(response);
    }

    @Override
    protected void render(JsonObject response) {
        printer().println("Scope: " + displayText(response.get("scope")));
        JsonArray snapshots = response.getCollection("snapshots");
        if (snapshots == null || snapshots.isEmpty()) {
            printer().println("No current measurements are available.");
        } else {
            for (int i = 0; i < snapshots.size(); i++) {
                JsonObject snapshot = snapshots.getMap(i);
                printer().println();
                snapshot.forEach((field, value) -> renderField(fieldLabel(field), value, "  "));
            }
        }
        for (String field : List.of("sources", "sourceErrors", "evicted", "omittedSources", "managementErrors",
                "lastSample", "sampleIntervalSeconds", "capacity")) {
            if (response.containsKey(field)) {
                renderField(fieldLabel(field), response.get(field), "");
            }
        }
    }
}
