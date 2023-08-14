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

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.jolokia.jvmagent.client.command.CommandDispatcher;
import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.jvmagent.client.util.PlatformUtils;
import org.jolokia.jvmagent.client.util.VirtualMachineHandlerOperations;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "jolokia", description = "Attach Jolokia JVM Agent to a running Camel integration", sortOptions = false)
public class Jolokia extends ProcessBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "1")
    String name;

    @CommandLine.Option(names = { "--stop" },
                        description = "Stops the Jolokia JVM Agent in the running Camel integration")
    boolean stop;

    private volatile long pid;

    public Jolokia(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 0;
        } else if (pids.size() > 1) {
            System.out.println("Name or pid " + name + " matches " + pids.size()
                               + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 0;
        }

        this.pid = pids.get(0);
        int exitCode;
        try {
            OptionsAndArgs options;
            if (stop) {
                options = new OptionsAndArgs(null, "stop", Long.toString(pid));
            } else {
                // find a new free port to use when starting a new connection
                long port = AvailablePortFinder.getNextAvailable(8778, 10000);
                options = new OptionsAndArgs(null, "--port", Long.toString(port), "start", Long.toString(pid));
            }
            VirtualMachineHandlerOperations vmHandler = PlatformUtils.createVMAccess(options);
            CommandDispatcher dispatcher = new CommandDispatcher(options);

            Object vm = options.needsVm() ? vmHandler.attachVirtualMachine() : null;
            try {
                exitCode = dispatcher.dispatchCommand(vm, vmHandler);
            } finally {
                if (vm != null) {
                    vmHandler.detachAgent(vm);
                }
            }
        } catch (Exception e) {
            System.err.println("Cannot execute jolokia command due: " + e.getMessage());
            exitCode = 1;
        }

        return exitCode;
    }

    /**
     * The pid of the running Camel integration that was discovered and used
     */
    long getPid() {
        return pid;
    }
}
