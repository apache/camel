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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.io.File;
import java.util.List;
import java.util.Properties;

import com.sun.tools.attach.VirtualMachine;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "jolokia", description = "Attach Jolokia JVM Agent to a running Camel integration", sortOptions = false,
         showDefaultValues = true)
public class Jolokia extends ProcessBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "1")
    String name;

    @CommandLine.Option(names = { "--stop" },
                        description = "Stops the Jolokia JVM Agent in the running Camel integration")
    boolean stop;

    @CommandLine.Option(names = { "--port" },
                        description = "To use a specific port number when attaching Jolokia JVM Agent (default a free port is found in range 8778-9999)")
    int port;

    private volatile long pid;

    public Jolokia(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 1;
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 1;
        }

        this.pid = pids.get(0);
        long effectivePort = port;
        if (!stop && effectivePort <= 0) {
            // find a new free port to use when starting a new connection
            effectivePort = AvailablePortFinder.getNextAvailable(8778, 10000);
        }
        return attachDirect(pid, effectivePort, stop);
    }

    /**
     * The pid of the running Camel integration that was discovered and used
     */
    long getPid() {
        return pid;
    }

    private int attachDirect(long pid, long port, boolean stop) {
        VirtualMachine vm = null;
        try {
            File agentJar = resolveAgentJar();
            vm = VirtualMachine.attach(Long.toString(pid));
            Properties props = vm.getSystemProperties();
            String agentUrl = props.getProperty("jolokia.agent");
            if (stop) {
                if (agentUrl == null) {
                    printer().printErr("Jolokia agent not running for PID: " + pid);
                    return 1;
                }
                vm.loadAgent(agentJar.getAbsolutePath(), "mode=stop");
                return 0;
            }
            if (agentUrl != null) {
                // Already attached — treat as success (idempotent)
                return 0;
            }
            String args = "port=" + port + ",discoveryEnabled=true";
            vm.loadAgent(agentJar.getAbsolutePath(), args);
            return 0;
        } catch (Exception e) {
            printer().printErr("Cannot execute jolokia command due: " + e.getMessage());
            return 1;
        } finally {
            if (vm != null) {
                try {
                    vm.detach();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private File resolveAgentJar() throws Exception {
        File m2Jar = LauncherHelper.findJolokiaAgentJar();
        if (m2Jar != null) {
            return m2Jar;
        }
        throw new IllegalStateException(
                "Jolokia agent jar not found in ~/.m2 repository. Ensure jolokia-agent-jvm is installed.");
    }

}
