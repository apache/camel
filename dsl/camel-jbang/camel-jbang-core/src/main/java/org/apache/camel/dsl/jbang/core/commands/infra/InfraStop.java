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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "stop",
                     description = "Stop an external service")
public class InfraStop extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Service name", arity = "1")
    private List<String> serviceName;

    public InfraStop(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        String serviceToStop = serviceName.get(0);

        boolean serviceStopped = false;
        String pid = null;
        try {
            List<Path> pidFiles = Files.list(CommandLineHelper.getCamelDir())
                    .filter(p -> p.getFileName().toString().startsWith("infra-" + serviceToStop + "-"))
                    .collect(java.util.stream.Collectors.toList());

            for (Path pidFile : pidFiles) {
                String name = pidFile.getFileName().toString();
                pid = name.substring(name.lastIndexOf("-") + 1, name.lastIndexOf('.'));

                Files.deleteIfExists(pidFile);
                serviceStopped = true;
                break;
            }
        } catch (IOException e) {
            // ignore
        }

        if (!serviceStopped) {
            printer().println("No Camel Infrastructure found with name " + serviceToStop + " found.");

            return -1;
        }

        printer().println("Shutting down service " + serviceToStop + " (PID: " + pid + ")");
        ProcessHandle.of(Long.valueOf(pid)).ifPresent(ProcessHandle::destroy);

        return 0;
    }
}
