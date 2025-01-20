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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifact;
import picocli.CommandLine;

@CommandLine.Command(name = "run",
                     description = "Run an external service")
public class InfraRun extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Service name", arity = "1")
    private List<String> serviceName;

    @CommandLine.Option(names = { "--log" },
                        description = "Log container's output to console")
    boolean logToStdout;

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

        run(service, serviceImplementation);

        return 0;
    }

    private void run(String testService, String testServiceImplementation) throws Exception {
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

                return;
            }

            printer().println("service " + testService + " not found" + message);
        }

        DependencyDownloaderClassLoader cl = getDependencyDownloaderClassLoader(testInfraService);

        // Update the class loader
        Thread.currentThread().setContextClassLoader(cl);

        String serviceInterface = testInfraService.service();
        String serviceImpl = testInfraService.implementation();

        if (!jsonOutput) {
            String prefix = "";
            if (testServiceImplementation != null && !testServiceImplementation.isEmpty()) {
                prefix = " with implementation " + testServiceImplementation;
            }
            printer().println("Starting service " + testService + prefix);
        }

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

            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (actualService != null) {
                try {
                    actualService.getClass().getMethod("shutdown").invoke(actualService);
                } catch (Exception e) {
                    printer().printErr(e);
                }
            }
        }));

        actualService.getClass().getMethod("initialize").invoke(actualService);

        Method[] serviceMethods = cl.loadClass(serviceInterface).getDeclaredMethods();
        HashMap properties = new HashMap();
        for (Method method : serviceMethods) {
            if (method.getParameterCount() == 0 && !method.getName().contains("registerProperties")) {
                properties.put(method.getName(), method.invoke(actualService));
            }
        }

        String jsonProperties = jsonMapper.writeValueAsString(properties);
        printer().println(jsonProperties);

        String name = getLogFileName(testService, RuntimeUtil.getPid());
        File logFile = createFile(name);

        String jsonName = getJsonFileName(testService, RuntimeUtil.getPid());
        File jsonFile = createFile(jsonName);
        Files.write(jsonFile.toPath(), jsonProperties.getBytes());

        if (Arrays.stream(actualService.getClass().getInterfaces()).filter(
                c -> c.getName().contains("ContainerService")).count()
            > 0) {
            Object containerLogConsumer = cl.loadClass("org.apache.camel.test.infra.common.CamelLogConsumer")
                    .getConstructor(Path.class, boolean.class).newInstance(logFile.toPath(), logToStdout);

            actualService.getClass()
                    .getMethod("followLog", cl.loadClass("org.testcontainers.containers.output.BaseConsumer"))
                    .invoke(actualService, containerLogConsumer);
        }

        if (!jsonOutput) {
            printer().println("Press any key to stop the execution");
        }
        Scanner sc = new Scanner(System.in);

        while (!sc.hasNext()) {
        }

        actualService.getClass().getMethod("shutdown").invoke(actualService);
        sc.close();
    }

    private static File createFile(String name) throws IOException {
        File logFile = new File(CommandLineHelper.getCamelDir(), name);
        logFile.createNewFile();
        logFile.deleteOnExit();
        return logFile;
    }

    private static DependencyDownloaderClassLoader getDependencyDownloaderClassLoader(
            TestInfraService testInfraService) {
        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(InfraRun.class.getClassLoader());

        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
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
