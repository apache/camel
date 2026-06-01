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

import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "route-topology", description = "Display inter-route topology connections", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel cmd route-topology",
                 "  camel cmd route-topology --json" })
public class CamelRouteTopologyAction extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of a running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    public CamelRouteTopologyAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            printer().println("No running Camel integration found");
            return 1;
        }
        if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 1;
        }

        long pid = pids.get(0);
        Path outputFile = prepareAction(Long.toString(pid), "route-topology", root -> {
        });

        JsonObject jo = getJsonObject(outputFile);
        if (jo != null) {
            if (jsonOutput) {
                String dump = Jsoner.prettyPrint(jo.toJson(), 2);
                printer().println(dump);
            } else {
                printTopology(jo);
            }
        } else {
            printer().println("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        PathUtils.deleteFile(outputFile);
        return 0;
    }

    private void printTopology(JsonObject jo) {
        JsonArray nodes = (JsonArray) jo.get("nodes");
        JsonArray edges = (JsonArray) jo.get("edges");

        int nodeCount = nodes != null ? nodes.size() : 0;
        int edgeCount = edges != null ? edges.size() : 0;
        printer().printf("Route Topology (%d routes, %d connections)%n%n", nodeCount, edgeCount);

        if (nodes != null) {
            for (Object n : nodes) {
                JsonObject node = (JsonObject) n;
                printer().printf("  %s (%s) type=%s%n",
                        node.getString("routeId"),
                        node.getString("from"),
                        node.getString("nodeType"));

                if (edges != null) {
                    for (Object e : edges) {
                        JsonObject edge = (JsonObject) e;
                        if (node.getString("routeId").equals(edge.getString("fromRouteId"))) {
                            printer().printf("    --> %s via %s [%s]%n",
                                    edge.getString("toRouteId"),
                                    edge.getString("endpoint"),
                                    edge.getString("connectionType"));
                        }
                    }
                }
            }
        }
    }

}
