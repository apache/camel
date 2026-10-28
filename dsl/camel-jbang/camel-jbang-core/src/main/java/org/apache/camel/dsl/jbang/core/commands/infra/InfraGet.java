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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "get", description = "Displays running service(s) information", sortOptions = false,
                     showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel infra get kafka",
                             "  camel infra get kafka --json" })
public class InfraGet extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running service(s)", arity = "0..1")
    String name = "*";

    public InfraGet(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<RunningService> services = findRunningServices(name);
        if (services.isEmpty() && !"*".equals(name)) {
            throw CliException.notFound("No running infra service matches: " + name);
        }

        List<JsonObject> rows = new ArrayList<>();
        int printed = 0;
        for (RunningService service : services) {
            String data = readServiceData(service.pidFile());
            if (data == null) {
                // stopped meanwhile
                continue;
            }
            if (jsonOutput) {
                JsonObject jo = new JsonObject();
                jo.put("pid", service.pid());
                jo.put("alias", service.alias());
                Object serviceData = parseServiceData(data);
                if (serviceData != null) {
                    jo.put("serviceData", serviceData);
                }
                rows.add(jo);
            } else {
                if (printed++ > 0) {
                    printer().println("");
                }
                printer().println("Service " + service.alias() + " (PID: " + service.pid() + ")");
                printer().println(data);
            }
        }
        if (jsonOutput) {
            JsonOutput.printList(printer(), rows);
        }

        return 0;
    }
}
