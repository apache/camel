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

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "stop",
                     description = "Shuts down running external services", sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel infra stop kafka" })
public class InfraStop extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running service(s)", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--kill" },
                        description = "To force killing the process (SIGKILL)")
    boolean kill;

    public InfraStop(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<RunningService> services = findRunningServices(name);
        if (services.isEmpty() && !"*".equals(name)) {
            throw CliException.notFound("No running infra service matches: " + name);
        }

        List<JsonObject> rows = new ArrayList<>();
        // stop by deleting the pid file
        for (RunningService service : services) {
            if (Files.exists(service.pidFile())) {
                if (!jsonOutput) {
                    printer().println("Shutting down external services (PID: " + service.pid() + ")");
                }
                PathUtils.deleteFile(service.pidFile());
            }
            JsonObject jo = new JsonObject();
            jo.put("pid", service.pid());
            jo.put("alias", service.alias());
            jo.put("status", kill ? "killed" : "stopping");
            rows.add(jo);
        }
        if (kill) {
            for (RunningService service : services) {
                ProcessHandle.of(service.pidNumber()).ifPresent(ph -> {
                    if (!jsonOutput) {
                        printer().println("Killing external service (PID: " + service.pid() + ")");
                    }
                    ph.destroyForcibly();
                });
            }
        }
        if (jsonOutput) {
            JsonOutput.printList(printer(), rows);
        }

        return 0;
    }

}
