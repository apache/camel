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
package org.apache.camel.dsl.jbang.core.commands.jolokia;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.jolokia.jvmagent.client.command.CommandDispatcher;
import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.jvmagent.client.util.PlatformUtils;
import org.jolokia.jvmagent.client.util.VirtualMachineHandlerOperations;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "jolokia", description = "Attach Jolokia JVM Agent to a running Camel integration")
public class Jolokia extends CamelCommand {

    @CommandLine.Parameters(description = "PID of running Camel integration", arity = "1")
    private long pid;

    @CommandLine.Option(names = { "--stop" },
                        description = "Stops the Jolokia JVM Agent in the running Camel integration")
    private boolean stop;

    public Jolokia(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        int exitCode;

        try {
            OptionsAndArgs options;
            if (stop) {
                options = new OptionsAndArgs(null, "stop", "" + pid);
            } else {
                // find a new free port to use when starting a new connection
                long port = AvailablePortFinder.getNextAvailable(8778, 10000);
                options = new OptionsAndArgs(null, "--port", "" + port, "start", "" + pid);
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
            System.err.println("Cannot execute jolokia command due " + e.getMessage());
            exitCode = 1;
        }

        return exitCode;
    }

}
