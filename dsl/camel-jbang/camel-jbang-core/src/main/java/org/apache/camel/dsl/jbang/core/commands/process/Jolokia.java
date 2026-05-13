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
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.jolokia.jvmagent.client.command.CommandDispatcher;
import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.jvmagent.client.util.PlatformUtils;
import org.jolokia.jvmagent.client.util.VirtualMachineHandlerOperations;
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
        int exitCode;
        try {
            OptionsAndArgs options;
            if (stop) {
                options = new OptionsAndArgs(null, "stop", Long.toString(pid));
            } else {
                long p = port;
                if (p <= 0) {
                    p = AvailablePortFinder.getNextAvailable(8778, 10000);
                }
                options = new OptionsAndArgs(
                        null, "--port", Long.toString(p), "--discoveryEnabled", "true", "start", Long.toString(pid));
            }

            // When running from a Spring Boot nested JAR (e.g. camel-launcher),
            // OptionsAndArgs resolves the fat JAR instead of the Jolokia agent JAR.
            // The camel-launcher distribution ships the Jolokia agent JAR alongside
            // the launcher in the same directory, so look for it there.
            File resolvedJar = OptionsAndArgs.lookupJarFile();
            if (!isJolokiaAgentJar(resolvedJar)) {
                File agentJar = findJolokiaAgentJar(resolvedJar);
                if (agentJar != null) {
                    Field jarFileField = OptionsAndArgs.class.getDeclaredField("jarFile");
                    jarFileField.setAccessible(true);
                    jarFileField.set(options, agentJar);
                }
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
            printer().printErr("Cannot execute jolokia command due: " + e.getMessage());
            exitCode = 1;
        }

        return exitCode;
    }

    /**
     * Checks whether the given JAR file is a Jolokia agent JAR by inspecting its manifest for the Agent-Class
     * attribute.
     */
    private boolean isJolokiaAgentJar(File jar) {
        if (jar == null || !jar.isFile()) {
            return false;
        }
        try (JarFile jf = new JarFile(jar)) {
            Manifest manifest = jf.getManifest();
            if (manifest != null) {
                String agentClass = manifest.getMainAttributes().getValue("Agent-Class");
                return agentClass != null && agentClass.contains("jolokia");
            }
        } catch (IOException e) {
            // not a valid JAR or unreadable
        }
        return false;
    }

    /**
     * Finds the Jolokia agent JAR in the same directory as the given JAR file. The camel-launcher distribution ships
     * the Jolokia agent JAR alongside the launcher JAR in the bin/ directory.
     */
    private File findJolokiaAgentJar(File launcherJar) {
        if (launcherJar == null || !launcherJar.isFile()) {
            return null;
        }
        Path dir = launcherJar.toPath().getParent();
        if (dir == null) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "jolokia-*-javaagent.jar")) {
            for (Path entry : stream) {
                return entry.toFile();
            }
        } catch (IOException e) {
            // directory not readable
        }
        return null;
    }

    long getPid() {
        return pid;
    }
}
