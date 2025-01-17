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
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeUtil;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.test.infra.common.CamelLogConsumer;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.common.services.InfrastructureService;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.jetbrains.annotations.NotNull;
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
                .orElseThrow(() -> {
                    String message = ", use the list command for the available services";
                    if (testServiceImplementation != null) {
                        return new IllegalArgumentException(
                                "service " + testService + " with implementation " + testServiceImplementation + " not found"
                                                            + message);
                    }

                    return new IllegalArgumentException("service " + testService + " not found" + message);
                });

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

        if (actualService instanceof InfrastructureService is) {
            is.initialize();
        } else {
            System.err.println("Service " + serviceImpl + " is not an InfrastructureService");
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (actualService != null) {
                try {
                    ((InfrastructureService) actualService).shutdown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }));

        Method[] serviceMethods = cl.loadClass(serviceInterface).getDeclaredMethods();
        HashMap properties = new HashMap();
        for (Method method : serviceMethods) {
            if (method.getParameterCount() == 0 && !method.getName().contains("registerProperties")) {
                properties.put(method.getName(), method.invoke(actualService));
            }
        }

        printer().println(jsonMapper.writeValueAsString(properties));

        String name = getLogFileName(testService, RuntimeUtil.getPid());
        File logFile = new File(CommandLineHelper.getCamelDir(), name);
        logFile.createNewFile();
        logFile.deleteOnExit();

        if (actualService instanceof ContainerService<?> cs) {
            cs.followLog(new CamelLogConsumer(logFile.toPath(), logToStdout));
        }

        if (!jsonOutput) {
            printer().println("Press any key to stop the execution");
        }
        Scanner sc = new Scanner(System.in);

        while (!sc.hasNext()) {
        }

        ((InfrastructureService) actualService).shutdown();
        sc.close();
    }

    private static @NotNull DependencyDownloaderClassLoader getDependencyDownloaderClassLoader(
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
}
