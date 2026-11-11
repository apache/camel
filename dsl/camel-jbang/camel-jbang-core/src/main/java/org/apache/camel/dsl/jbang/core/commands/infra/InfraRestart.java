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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.run.common.PathUtils;

@CommandDefinition(name = "restart", description = "Restarts a running external service", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel infra restart kafka",
                           "  camel infra restart kafka --background" })
public class InfraRestart extends InfraBaseCommand {

    @Arguments(required = true, description = "Service name (and optional implementation)")
    private List<String> serviceName;

    @Option(name = "port", description = "Override the default port for the service")
    Integer port;

    @Option(name = "log", hasValue = false, description = "Log container output to console")
    boolean logToStdout;

    @Option(name = "background", hasValue = false, description = "Run in the background", defaultValue = "false")
    boolean background;

    @OptionList(name = "prop", aliases = { "property" }, valueSeparator = OptionList.NO_SEPARATOR,
                description = "Service properties, ex. --property=ollama.model=qwen2.5:0.5b")
    List<String> serviceProperties = new ArrayList<>();

    @Option(name = "kill", hasValue = false, description = "To force killing the process (SIGKILL) when stopping")
    boolean kill;

    public InfraRestart(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        String service = serviceName.get(0);

        // stop any running instance(s) of the service by deleting the pid file
        Map<Long, Path> pids = findPids(service);
        for (var entry : pids.entrySet()) {
            Path pidFile = entry.getValue();
            if (Files.exists(pidFile)) {
                printer().println("Stopping external service " + service + " (PID: " + entry.getKey() + ")");
                PathUtils.deleteFile(pidFile);
            }
            if (kill) {
                ProcessHandle.of(entry.getKey()).ifPresent(ProcessHandle::destroyForcibly);
            }
        }

        // wait for the previous instance(s) to shut down before starting again to avoid port clashes
        for (Long pid : pids.keySet()) {
            ProcessHandle.of(pid).ifPresent(ph -> {
                try {
                    ph.onExit().get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // ignore and proceed to start the service again
                }
            });
        }

        return startService(service);
    }

    private Integer startService(String service) throws Exception {
        InfraRun infraRun = new InfraRun(getMain());
        infraRun.setServiceName(serviceName);
        infraRun.port = port;
        infraRun.setLogToStdout(logToStdout);
        infraRun.background = background;
        // a restart starts a new service, so it takes its properties the way it takes its port
        infraRun.serviceProperties = serviceProperties;
        return infraRun.doCall();
    }

    public void setServiceName(List<String> serviceName) {
        this.serviceName = serviceName;
    }

}
