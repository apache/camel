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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.util.json.JsonObject;

class LaunchManager {

    private static volatile Path secureTempDir;

    private final Supplier<List<InfraInfo>> infraServices;
    private final List<PendingLaunch> pendingLaunches = new ArrayList<>();
    private final Map<Long, Path> activeTempPoms = new HashMap<>();
    private DeferredLaunch deferredLaunch;
    private volatile String pendingAutoSelect;
    private BiConsumer<String, Boolean> notificationCallback;
    private Runnable infraCatalogClearer;
    private BiConsumer<String, Path> failureLogCallback;

    LaunchManager(Supplier<List<InfraInfo>> infraServices) {
        this.infraServices = infraServices;
    }

    void setNotificationCallback(BiConsumer<String, Boolean> callback) {
        this.notificationCallback = callback;
    }

    void setInfraCatalogClearer(Runnable clearer) {
        this.infraCatalogClearer = clearer;
    }

    void setFailureLogCallback(BiConsumer<String, Path> callback) {
        this.failureLogCallback = callback;
    }

    String getPendingAutoSelect() {
        return pendingAutoSelect;
    }

    void clearPendingAutoSelect() {
        pendingAutoSelect = null;
    }

    /**
     * Launches {@code camel <extraArgs>} as a detached background process and registers it as a pending launch so it is
     * tracked and monitored like an example started from the F2 Actions menu. Output is redirected to a temporary log
     * file. Used by the AI panel's {@code /run} and {@code /infra run} slash commands.
     */
    void launchDetached(String displayName, List<String> extraArgs) throws IOException {
        List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
        cmd.addAll(extraArgs);
        Path outputFile = createSecureTempFile("camel-launch-", ".log");
        outputFile.toFile().deleteOnExit();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(outputFile.toFile());
        Process process = pb.start();
        addPendingLaunch(displayName, process, outputFile);
    }

    void addPendingLaunch(String name, Process process, Path outputFile) {
        pendingLaunches.add(new PendingLaunch(name, process, outputFile, System.currentTimeMillis()));
        pendingAutoSelect = name;
    }

    void addPendingLaunchNoAutoSelect(String name, Process process, Path outputFile) {
        pendingLaunches.add(new PendingLaunch(name, process, outputFile, System.currentTimeMillis()));
    }

    void tick(long now) {
        monitorPendingLaunches(now);
        checkDeferredLaunch(now);
    }

    List<String> findMissingInfraServices(JsonObject example) {
        List<String> required = ExampleHelper.getInfraServices(example);
        if (required.isEmpty()) {
            return List.of();
        }
        Set<String> runningAliases = infraServices.get().stream()
                .filter(i -> i.alive)
                .map(i -> i.alias)
                .collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (String alias : required) {
            if (!runningAliases.contains(alias)) {
                missing.add(alias);
            }
        }
        return missing;
    }

    boolean isJaegerRunning() {
        return infraServices.get().stream()
                .anyMatch(i -> i.alive && "jaeger".equals(i.alias));
    }

    void startMissingInfraAndDefer(List<String> missingInfra, String displayName, Runnable launchAction) {
        for (String alias : missingInfra) {
            try {
                List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
                cmd.add("infra");
                cmd.add("run");
                cmd.add(alias);
                cmd.add("--background");
                Path outputFile = createSecureTempFile("camel-infra-", ".log");
                outputFile.toFile().deleteOnExit();
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                pb.redirectOutput(outputFile.toFile());
                Process process = pb.start();
                pendingLaunches.add(new PendingLaunch(alias, process, outputFile, System.currentTimeMillis()));
            } catch (Exception e) {
                notify("Failed to start infra: " + alias + " - " + e.getMessage(), true);
                return;
            }
        }
        deferredLaunch = new DeferredLaunch(displayName, missingInfra, System.currentTimeMillis(), launchAction);
        if (infraCatalogClearer != null) {
            infraCatalogClearer.run();
        }
        String infraList = String.join(", ", missingInfra);
        notify("Starting infra: " + infraList + " → then: " + displayName, false);
    }

    /**
     * Creates a temporary file inside a secure (owner-only permissions) subdirectory rather than directly in the
     * publicly writable system temp directory. This avoids SonarCloud S5443 (use of publicly writable directories).
     */
    static Path createSecureTempFile(String prefix, String suffix) throws IOException {
        Path dir = secureTempDir;
        if (dir == null || !Files.isDirectory(dir)) {
            synchronized (LaunchManager.class) {
                dir = secureTempDir;
                if (dir == null || !Files.isDirectory(dir)) {
                    dir = Files.createTempDirectory("camel-tui-");
                    dir.toFile().deleteOnExit();
                    secureTempDir = dir;
                }
            }
        }
        return Files.createTempFile(dir, prefix, suffix);
    }

    static boolean isContainerRuntimeAvailable() {
        for (String cmd : new String[] { "docker", "podman" }) {
            try {
                Process p = new ProcessBuilder(cmd, "info")
                        .redirectErrorStream(true)
                        .start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                boolean done = p.waitFor(5, TimeUnit.SECONDS);
                if (!done) {
                    p.destroyForcibly();
                    continue;
                }
                if (p.exitValue() == 0) {
                    return true;
                }
            } catch (Exception e) {
                // not found, try next
            }
        }
        return false;
    }

    void launchMavenProject(String dir, String projectType, String displayName, List<String> extraArgs) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(resolveMvnCommand(dir));
            switch (projectType) {
                case "spring-boot" -> cmd.add("spring-boot:run");
                case "quarkus" -> cmd.add("quarkus:dev");
                default -> cmd.add("camel:run");
            }
            // Translate Camel JBang args to Maven-compatible args
            cmd.addAll(translateArgsForMaven(extraArgs, projectType));
            // Inject camel-cli-connector if not already in the project
            Path tempPom = null;
            if ("spring-boot".equals(projectType)) {
                tempPom = injectCliConnectorIfMissing(dir, cmd);
            }
            Path outputFile = createSecureTempFile("camel-maven-", ".log");
            outputFile.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new java.io.File(dir));
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();
            pendingLaunches.add(new PendingLaunch(displayName, process, outputFile, System.currentTimeMillis(), tempPom));
            if (pendingAutoSelect == null) {
                pendingAutoSelect = displayName;
            }
            notify("Starting: " + displayName + " (mvn " + cmd.get(1) + ")", false);
        } catch (Exception e) {
            notify("Failed to start Maven project: " + e.getMessage(), true);
        }
    }

    private Path injectCliConnectorIfMissing(String dir, List<String> cmd) {
        try {
            Path pomFile = Path.of(dir, "pom.xml");
            if (!Files.isRegularFile(pomFile)) {
                return null;
            }
            String pomContent = Files.readString(pomFile);
            if (pomContent.contains("camel-cli-connector")) {
                return null;
            }
            // Add cli-connector-starter dependency (version managed by BOM)
            String dep = "\n        <dependency>\n"
                         + "            <groupId>org.apache.camel.springboot</groupId>\n"
                         + "            <artifactId>camel-cli-connector-starter</artifactId>\n"
                         + "        </dependency>";
            // Find the project-level </dependencies> (not inside dependencyManagement or plugins)
            int insertIdx = findProjectDependenciesEnd(pomContent);
            if (insertIdx < 0) {
                return null;
            }
            String modified = pomContent.substring(0, insertIdx) + dep + "\n    " + pomContent.substring(insertIdx);
            // Write temp pom in the project dir so Maven can find sources
            Path tempPom = Path.of(dir, ".camel-tui-pom.xml");
            Files.writeString(tempPom, modified);
            cmd.add("-f");
            cmd.add(tempPom.getFileName().toString());
            return tempPom;
        } catch (Exception e) {
            // best effort — don't fail the launch
            return null;
        }
    }

    private static int findProjectDependenciesEnd(String pom) {
        // Find <dependencies> that is a direct child of <project>,
        // not nested inside <dependencyManagement>, <plugin>, or <profile>
        int dmStart = pom.indexOf("<dependencyManagement>");
        int dmEnd = dmStart >= 0 ? pom.indexOf("</dependencyManagement>", dmStart) : -1;
        int buildStart = pom.indexOf("<build>");

        int searchFrom = 0;
        while (true) {
            int depStart = pom.indexOf("<dependencies>", searchFrom);
            if (depStart < 0) {
                return -1;
            }
            // Skip if inside <dependencyManagement>
            if (dmStart >= 0 && depStart > dmStart && (dmEnd < 0 || depStart < dmEnd)) {
                searchFrom = dmEnd > 0 ? dmEnd : depStart + 14;
                continue;
            }
            // Skip if inside <build> (plugins can have dependencies)
            if (buildStart >= 0 && depStart > buildStart) {
                searchFrom = depStart + 14;
                continue;
            }
            int depEnd = pom.indexOf("</dependencies>", depStart);
            return depEnd >= 0 ? depEnd : -1;
        }
    }

    private static Path writeSpringBootLogbackConfig() {
        try {
            Path camelDir = Path.of(System.getProperty("user.home"), ".camel");
            Files.createDirectories(camelDir);
            Path logbackFile = camelDir.resolve(".tui-logback-spring-boot.xml");
            String config = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <configuration>
                        <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
                        <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>
                        <include resource="org/springframework/boot/logging/logback/file-appender.xml"/>
                        <property name="LOG_FILE" value="${user.home}${file.separator}.camel${file.separator}${PID}.log"/>
                        <property name="FILE_LOG_PATTERN" value="${CONSOLE_LOG_PATTERN}"/>
                        <root level="INFO">
                            <appender-ref ref="CONSOLE"/>
                            <appender-ref ref="FILE"/>
                        </root>
                    </configuration>
                    """;
            Files.writeString(logbackFile, config);
            return logbackFile;
        } catch (Exception e) {
            return null;
        }
    }

    void launchCamelRun(String sourceDir, String displayName, List<String> extraArgs) {
        try {
            List<String> cmd = new ArrayList<>(LauncherHelper.getCamelCommand());
            cmd.add("run");
            cmd.add("--source-dir=" + sourceDir);
            cmd.add("--logging-color=true");
            cmd.addAll(extraArgs);
            Path outputFile = createSecureTempFile("camel-folder-", ".log");
            outputFile.toFile().deleteOnExit();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();
            addPendingLaunch(displayName, process, outputFile);
            notify("Starting: " + displayName, false);
        } catch (Exception e) {
            notify("Failed to start: " + sourceDir + " - " + e.getMessage(), true);
        }
    }

    private static String resolveMvnCommand(String dir) {
        String wrapper = System.getProperty("os.name", "").toLowerCase().contains("win") ? "mvnw.cmd" : "./mvnw";
        Path wrapperPath = Path.of(dir).resolve(wrapper.startsWith("./") ? wrapper.substring(2) : wrapper);
        if (Files.isRegularFile(wrapperPath)) {
            return wrapperPath.toString();
        }
        return "mvn";
    }

    static List<String> translateArgsForMaven(List<String> extraArgs, String projectType) {
        List<String> mvnArgs = new ArrayList<>();
        StringBuilder jvmArgs = new StringBuilder();
        if ("spring-boot".equals(projectType)) {
            Path logbackFile = writeSpringBootLogbackConfig();
            if (logbackFile != null) {
                jvmArgs.append("-Dlogging.config=file:").append(logbackFile);
            }
        }
        for (String arg : extraArgs) {
            if (arg.startsWith("--prop=")) {
                String kv = arg.substring("--prop=".length());
                mvnArgs.add("-D" + kv);
            } else if (arg.startsWith("--port=")) {
                String port = arg.substring("--port=".length());
                if ("spring-boot".equals(projectType)) {
                    mvnArgs.add("-Dserver.port=" + port);
                } else if ("quarkus".equals(projectType)) {
                    mvnArgs.add("-Dquarkus.http.port=" + port);
                }
            } else if (arg.startsWith("--profile=")) {
                String profile = arg.substring("--profile=".length());
                if (!"prod".equals(profile)) {
                    if (!jvmArgs.isEmpty()) {
                        jvmArgs.append(" ");
                    }
                    jvmArgs.append("-Dcamel.main.profile=").append(profile);
                }
            } else if (arg.startsWith("--jvm-args=")) {
                String extra = arg.substring("--jvm-args=".length()).trim();
                if (!extra.isEmpty()) {
                    if (!jvmArgs.isEmpty()) {
                        jvmArgs.append(" ");
                    }
                    jvmArgs.append(extra);
                }
            }
            // other Camel JBang flags (--name, --runtime, --dev, --observe, etc.)
            // are not applicable to Maven and are silently dropped
        }
        if (!jvmArgs.isEmpty()) {
            if ("spring-boot".equals(projectType)) {
                mvnArgs.add("-Dspring-boot.run.jvmArguments=" + jvmArgs);
            } else if ("quarkus".equals(projectType)) {
                mvnArgs.add("-Djvm.args=" + jvmArgs);
            } else {
                mvnArgs.add("-Dcamel.jvmArgs=" + jvmArgs);
            }
        }
        return mvnArgs;
    }

    private void checkDeferredLaunch(long now) {
        if (deferredLaunch != null) {
            Set<String> runningAliases = infraServices.get().stream()
                    .filter(i -> i.alive)
                    .map(i -> i.alias)
                    .collect(Collectors.toSet());
            if (runningAliases.containsAll(deferredLaunch.requiredInfra)) {
                DeferredLaunch dl = deferredLaunch;
                deferredLaunch = null;
                dl.launchAction.run();
            } else if (now - deferredLaunch.startTime > 120_000) {
                deferredLaunch = null;
                notify("Timeout waiting for infra services to start", true);
            }
        }
    }

    private void monitorPendingLaunches(long now) {
        Iterator<PendingLaunch> it = pendingLaunches.iterator();
        while (it.hasNext()) {
            PendingLaunch pl = it.next();
            if (!pl.process().isAlive()) {
                int exitCode = pl.process().exitValue();
                if (exitCode == 0) {
                    notify("Started: " + pl.name(), false);
                } else {
                    if (failureLogCallback != null) {
                        failureLogCallback.accept(pl.name(), pl.outputFile());
                    }
                }
                cleanupTempPom(pl);
                it.remove();
            } else if (now - pl.startTime() > 8000) {
                notify("Started: " + pl.name(), false);
                // keep temp pom reference for cleanup when process stops
                if (pl.tempPom() != null) {
                    activeTempPoms.put(pl.process().pid(), pl.tempPom());
                }
                it.remove();
            }
        }
    }

    void cleanupTempPom(long pid) {
        Path tempPom = activeTempPoms.remove(pid);
        if (tempPom != null) {
            try {
                Files.deleteIfExists(tempPom);
            } catch (Exception e) {
                // best effort
            }
        }
    }

    private static void cleanupTempPom(PendingLaunch pl) {
        if (pl.tempPom() != null) {
            try {
                Files.deleteIfExists(pl.tempPom());
            } catch (Exception e) {
                // best effort
            }
        }
    }

    private void notify(String msg, boolean error) {
        if (notificationCallback != null) {
            notificationCallback.accept(msg, error);
        }
    }

    private record PendingLaunch(String name, Process process, Path outputFile, long startTime, Path tempPom) {
        PendingLaunch(String name, Process process, Path outputFile, long startTime) {
            this(name, process, outputFile, startTime, null);
        }
    }

    private record DeferredLaunch(
            String displayName, List<String> requiredInfra, long startTime,
            Runnable launchAction) {
    }
}
