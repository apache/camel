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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import com.sun.tools.attach.VirtualMachine;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
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
        File agentJar = findJolokiaAgentJar();
        if (agentJar != null) {
            return agentJar;
        }
        throw new IllegalStateException("Jolokia agent jar not found in ~/.m2 repository");
    }

    private File findJolokiaAgentJar() {
        // Try to find the exact version via embedded pom.properties
        String version = null;
        ClassLoader loader = JolokiaAttacher.class.getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        try (InputStream is = loader.getResourceAsStream(
                "META-INF/maven/org.jolokia/jolokia-agent-jvm/pom.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                version = props.getProperty("version");
            }
        } catch (Exception ignored) {
        }
        Path m2Base = Path.of(System.getProperty("user.home"), ".m2", "repository", "org", "jolokia", "jolokia-agent-jvm");
        if (!Files.isDirectory(m2Base)) {
            return null;
        }
        if (version != null && !version.isBlank()) {
            Path candidate = m2Base.resolve(version).resolve("jolokia-agent-jvm-" + version + "-javaagent.jar");
            if (Files.exists(candidate)) {
                return candidate.toFile();
            }
        }
        // Scan all version directories and return the newest one
        try (Stream<Path> stream = Files.list(m2Base)) {
            return stream.filter(Files::isDirectory)
                    .map(dir -> {
                        String v = dir.getFileName().toString();
                        Path jar = dir.resolve("jolokia-agent-jvm-" + v + "-javaagent.jar");
                        return new JarCandidate(v, jar);
                    })
                    .filter(c -> Files.exists(c.path))
                    .max(Comparator.comparing(c -> c.version, this::compareVersions))
                    .map(c -> c.path.toFile())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private int compareVersions(String a, String b) {
        String[] pa = a.split("[.\\-]");
        String[] pb = b.split("[.\\-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            String sa = i < pa.length ? pa[i] : "0";
            String sb = i < pb.length ? pb[i] : "0";
            try {
                int cmp = Integer.compare(Integer.parseInt(sa), Integer.parseInt(sb));
                if (cmp != 0) {
                    return cmp;
                }
            } catch (NumberFormatException e) {
                int cmp = sa.compareTo(sb);
                if (cmp != 0) {
                    return cmp;
                }
            }
        }
        return 0;
    }

    // PID-finding logic is intentionally duplicated from ProcessBaseCommand.findPids().
    // ProcessBaseCommand is package-private in camel-jbang-core and cannot be accessed
    // from this plugin module. Extracting to ProcessHelper (a public utility) would add
    // camel-jbang-core changes beyond the scope of this plugin.
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

    private static final class JarCandidate {
        private final String version;
        private final Path path;

        private JarCandidate(String version, Path path) {
            this.version = version;
            this.path = path;
        }
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
}
