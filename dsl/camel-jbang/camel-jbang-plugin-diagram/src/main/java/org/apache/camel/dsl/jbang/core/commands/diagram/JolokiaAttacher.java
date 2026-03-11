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
package org.apache.camel.dsl.jbang.core.commands.diagram;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.sun.tools.attach.VirtualMachine;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

final class JolokiaAttacher {

    private final Printer printer;

    JolokiaAttacher(Printer printer) {
        this.printer = printer;
    }

    long resolvePid(String name) {
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            printer.printErr("No running Camel integration matches: " + name);
            return 0;
        }
        if (pids.size() > 1) {
            printer.printErr("Name or pid " + name + " matches " + pids.size()
                             + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 0;
        }
        return pids.get(0);
    }

    int attach(long pid, int port) {
        int effectivePort = port;
        if (effectivePort <= 0) {
            effectivePort = findAvailablePort(8778, 10000);
        }
        return loadAgent(pid, effectivePort, false);
    }

    int detach(long pid) {
        return loadAgent(pid, 0, true);
    }

    private int loadAgent(long pid, int port, boolean stop) {
        VirtualMachine vm = null;
        try {
            File agentJar = resolveAgentJar();
            vm = VirtualMachine.attach(Long.toString(pid));
            Properties props = vm.getSystemProperties();
            String agentUrl = props.getProperty("jolokia.agent");
            if (stop) {
                if (agentUrl == null) {
                    return 1;
                }
                vm.loadAgent(agentJar.getAbsolutePath(), "mode=stop");
                return 0;
            }
            if (agentUrl != null) {
                return 0;
            }
            String args = "port=" + port + ",discoveryEnabled=true";
            vm.loadAgent(agentJar.getAbsolutePath(), args);
            return 0;
        } catch (Exception e) {
            printer.printErr("Cannot execute jolokia command due: " + e.getMessage());
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

    private File resolveAgentJar() {
        File agentJar = LauncherHelper.findJolokiaAgentJar();
        if (agentJar != null) {
            return agentJar;
        }
        throw new IllegalStateException("Jolokia agent jar not found in ~/.m2 repository");
    }

    private int findAvailablePort(int fromPort, int toPort) {
        for (int port = fromPort; port <= toPort; port++) {
            if (isPortFree(port)) {
                return port;
            }
        }
        throw new IllegalStateException("Cannot find free port");
    }

    private boolean isPortFree(int port) {
        try (var socket = new java.net.ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress((java.net.InetAddress) null, port), 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<Long> findPids(String name) {
        List<Long> pids = new ArrayList<>();
        if (name == null || name.isBlank()) {
            return pids;
        }
        if (name.matches("\\d+")) {
            return List.of(Long.parseLong(name));
        }
        String pattern = name;
        if (pattern.endsWith("!")) {
            pattern = pattern.substring(0, pattern.length() - 1);
        } else if (!pattern.endsWith("*")) {
            pattern = pattern + "*";
        }
        long current = ProcessHandle.current().pid();
        String matchPattern = pattern;
        ProcessHandle.allProcesses()
                .filter(ph -> ph.pid() != current)
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        String pName = ProcessHelper.extractName(root, ph);
                        pName = FileUtil.onlyName(pName);
                        if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, matchPattern)) {
                            pids.add(ph.pid());
                        } else {
                            JsonObject context = (JsonObject) root.get("context");
                            if (context != null) {
                                pName = context.getString("name");
                                if ("CamelJBang".equals(pName)) {
                                    pName = null;
                                }
                                if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, matchPattern)) {
                                    pids.add(ph.pid());
                                }
                            }
                        }
                    }
                });
        return pids;
    }

    private JsonObject loadStatus(long pid) {
        try {
            Path file = CommandLineHelper.getCamelDir().resolve(pid + "-status.json");
            if (Files.exists(file)) {
                String text = Files.readString(file);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
