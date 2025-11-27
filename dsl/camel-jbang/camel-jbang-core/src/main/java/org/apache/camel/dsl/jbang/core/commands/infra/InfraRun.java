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

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;
import picocli.CommandLine;

import static org.apache.camel.dsl.jbang.core.commands.RunHelper.addCamelJBangCommand;

@CommandLine.Command(name = "run", description = "Run an external service", sortOptions = false, showDefaultValues = true)
public class InfraRun extends InfraBaseCommand {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Parameters(description = "Service name", arity = "1")
    private List<String> serviceName;

    @CommandLine.Option(names = { "--log" },
                        description = "Log container output to console")
    boolean logToStdout;

    @CommandLine.Option(names = { "--background" }, defaultValue = "false", description = "Run in the background")
    boolean background;

    public InfraRun(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (serviceName == null || serviceName.isEmpty()) {
            return 0;
        }

        String service = serviceName.get(0);
        String serviceImplementation = serviceName.size() > 1 ? serviceName.get(1) : null;

        return run(service, serviceImplementation);
    }

    private Integer run(String testService, String testServiceImplementation) throws Exception {
        List<TestInfraService> services = getMetadata();

        TestInfraService testInfraService = services
                .stream()
                .filter(service -> {
                    if (testServiceImplementation != null && !testServiceImplementation.isEmpty()
                            && service.aliasImplementation() != null) {
                        return service.alias().contains(testService)
                                && service.aliasImplementation().contains(testServiceImplementation);
                    } else if (testServiceImplementation == null) {
                        return service.alias().contains(testService)
                                && (service.aliasImplementation() == null || service.aliasImplementation().isEmpty());
                    }

                    return false;
                })
                .findFirst()
                .orElse(null);

        if (testInfraService == null) {
            String message = ", use the list command for the available services";
            if (testServiceImplementation != null) {
                printer().println("service " + testService + " with implementation " + testServiceImplementation + " not found"
                                  + message);
            }
            printer().println("service " + testService + " not found" + message);
            return 1;
        }

        if (background) {
            return runBackground(testService);
        } else {
            return doRun(testService, testServiceImplementation, testInfraService);
        }
    }

    protected int runBackground(String testService) throws Exception {
        List<String> cmds;
        if (spec != null) {
            cmds = new ArrayList<>(spec.commandLine().getParseResult().originalArgs());
        } else {
            cmds = new ArrayList<>();
            cmds.add("run");
        }

        cmds.remove("--background=true");
        cmds.remove("--background");

        addCamelJBangCommand(cmds);

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmds);

        Process p = pb.start();
        printer().println(
                "Running " + testService + " in background with PID: " + p.pid());
        return 0;
    }

    protected Integer doRun(String testService, String testServiceImplementation, TestInfraService testInfraService)
            throws Exception {
        DependencyDownloaderClassLoader cl = getDependencyDownloaderClassLoader(testInfraService, printer());

        // Update the class loader
        Thread.currentThread().setContextClassLoader(cl);

        String serviceInterface = testInfraService.service();
        String serviceImpl = testInfraService.implementation();

        Object actualService = cl.loadClass(serviceImpl).newInstance();

        // Make sure the actualService can be run with initialize method
        boolean actualServiceIsAnInfrastructureService = false;

        for (Method method : actualService.getClass().getMethods()) {
            if (method.getName().contains("initialize")) {
                actualServiceIsAnInfrastructureService = true;
                break;
            }
        }

        if (!actualServiceIsAnInfrastructureService) {
            printer().println("Service " + serviceImpl + " is not an InfrastructureService");
            return 1;
        }

        if (!jsonOutput) {
            String prefix = "";
            if (testServiceImplementation != null) {
                prefix = " with implementation " + testServiceImplementation;
            }
            printer().println("Starting service " + testService + prefix + " (PID: " + RuntimeUtil.getPid() + ")");
        }
        actualService.getClass().getMethod("initialize").invoke(actualService);

        Method[] serviceMethods = cl.loadClass(serviceInterface).getDeclaredMethods();
        Map<String, Object> properties = new TreeMap<>();
        for (Method method : serviceMethods) {
            if (method.getParameterCount() == 0 && !method.getName().contains("registerProperties")) {
                Object value = null;
                try {
                    value = method.invoke(actualService);
                } catch (Exception e) {
                    // ignore
                }
                if (value != null) {
                    properties.put(method.getName(), value);
                }
            }
        }

        String jsonProperties = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(properties);
        printer().println(jsonProperties);

        String name = getLogFileName(testService, RuntimeUtil.getPid());
        Path logFile = createFile(name);

        String jsonName = getJsonFileName(testService, RuntimeUtil.getPid());
        Path jsonFile = createFile(jsonName);
        Files.write(jsonFile, jsonMapper.writeValueAsString(properties).getBytes());

        if (Arrays.stream(actualService.getClass().getInterfaces()).anyMatch(c -> c.getName().contains("ContainerService"))) {
            Object containerLogConsumer = cl.loadClass("org.apache.camel.test.infra.common.CamelLogConsumer")
                    .getConstructor(Path.class, boolean.class).newInstance(logFile, logToStdout);
            actualService.getClass()
                    .getMethod("followLog", cl.loadClass("org.testcontainers.containers.output.BaseConsumer"))
                    .invoke(actualService, containerLogConsumer);
        }

        AtomicBoolean closed = new AtomicBoolean();
        // use shutdown hook as fallback to shut-down and delete files
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownInfra(closed, logFile, jsonFile, actualService)));

        final CountDownLatch latch = new CountDownLatch(1);

        // running in foreground then wait for user to exit
        final Console c = System.console();
        if (c != null) {
            if (!jsonOutput) {
                printer().println("Press ENTER to stop the execution");
            }
            Thread t = new Thread(() -> {
                boolean quit = false;
                do {
                    String line = c.readLine();
                    if (line != null) {
                        quit = true;
                        latch.countDown();
                    }
                } while (!quit);
            }, "WaitEnter");
            t.start();
        } else {
            // wait for this process to be stopped
            printer().println("Running (use camel infra stop "
                              + testService + (testServiceImplementation != null ? " " + testServiceImplementation : "")
                              + " to stop the execution)");
        }

        // always wait for external signal to stop if the json-file is deleted
        Thread t = new Thread(() -> {
            while (latch.getCount() > 0) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    // ignore
                }
                File f = jsonFile.toFile();
                if (!f.exists()) {
                    latch.countDown();
                }
            }
        }, "WaitShutdownSignal");
        t.start();

        try {
            latch.await();
        } catch (Exception e) {
            // ignore
        }

        shutdownInfra(closed, logFile, jsonFile, actualService);

        return 0;
    }

    private static void shutdownInfra(AtomicBoolean closed, Path logFile, Path jsonFile, Object actualService) {
        if (closed.compareAndSet(false, true)) {
            try {
                actualService.getClass().getMethod("shutdown").invoke(actualService);
            } catch (Exception e) {
                // ignore
            }
            try {
                Files.deleteIfExists(logFile);
            } catch (Exception e) {
                // ignore
            }
            try {
                Files.deleteIfExists(jsonFile);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private static Path createFile(String name) throws IOException {
        Path logDir = CommandLineHelper.getCamelDir();
        Files.createDirectories(logDir); //make sure the parent dir exists
        Path logFile = logDir.resolve(name);
        Files.createFile(logFile);
        return logFile;
    }

    private static DependencyDownloaderClassLoader getDependencyDownloaderClassLoader(
            TestInfraService testInfraService, Printer printer) {
        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(InfraRun.class.getClassLoader());

        try (MavenDependencyDownloader downloader = new MavenDependencyDownloader()) {
            downloader.setClassLoader(cl);
            downloader.start();
            // download required camel-test-infra-* dependency
            downloader.downloadDependency(testInfraService.groupId(),
                    testInfraService.artifactId(),
                    testInfraService.version(), true);

            MavenArtifact ma = downloader.downloadArtifact(testInfraService.groupId(),
                    testInfraService.artifactId(),
                    testInfraService.version());
            cl.addFile(ma.getFile());
        } catch (Exception e) {
            printer.printErr(e);
        }
        return cl;
    }

    public List<String> getServiceName() {
        return serviceName;
    }

    public void setServiceName(List<String> serviceName) {
        this.serviceName = serviceName;
    }

    public boolean isLogToStdout() {
        return logToStdout;
    }

    public void setLogToStdout(boolean logToStdout) {
        this.logToStdout = logToStdout;
    }
}
