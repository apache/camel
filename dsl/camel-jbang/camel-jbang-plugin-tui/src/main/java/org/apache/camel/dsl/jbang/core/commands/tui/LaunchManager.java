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

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.common.ExampleHelper;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.apache.camel.util.json.JsonObject;

class LaunchManager {

    private final Supplier<List<InfraInfo>> infraServices;
    private final List<PendingLaunch> pendingLaunches = new ArrayList<>();
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
                Path outputFile = Files.createTempFile("camel-infra-", ".log");
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

    static boolean isContainerRuntimeAvailable() {
        for (String cmd : new String[] { "docker", "podman" }) {
            try {
                Process p = new ProcessBuilder(cmd, "info")
                        .redirectErrorStream(true)
                        .start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                int exit = p.waitFor();
                if (exit == 0) {
                    return true;
                }
            } catch (Exception e) {
                // not found, try next
            }
        }
        return false;
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
                it.remove();
            } else if (now - pl.startTime() > 8000) {
                notify("Started: " + pl.name(), false);
                it.remove();
            }
        }
    }

    private void notify(String msg, boolean error) {
        if (notificationCallback != null) {
            notificationCallback.accept(msg, error);
        }
    }

    private record PendingLaunch(String name, Process process, Path outputFile, long startTime) {
    }

    private record DeferredLaunch(
            String displayName, List<String> requiredInfra, long startTime,
            Runnable launchAction) {
    }
}
