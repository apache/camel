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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import com.google.common.base.Strings;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.test.infra.common.services.InfrastructureService;
import picocli.CommandLine;

@CommandLine.Command(name = "run",
                     description = "Run an external service")
public class InfraRun extends InfraBaseCommand {

    @CommandLine.Parameters(description = "Service name", arity = "1")
    private List<String> serviceName;

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

        String serviceInterface = testInfraService.service();
        String serviceImpl = testInfraService.implementation();

        if (!jsonOutput) {
            String prefix = "";
            if (!Strings.isNullOrEmpty(testServiceImplementation)) {
                prefix = " with implementation " + testServiceImplementation;
            }
            printer().println("Starting service " + testService + prefix);
        }

        InfrastructureService actualService = (InfrastructureService) Class.forName(serviceImpl)
                .getDeclaredConstructor(null)
                .newInstance(null);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (actualService != null) {
                try {
                    actualService.shutdown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }));

        actualService.initialize();

        Method[] serviceMethods = Class.forName(serviceInterface).getDeclaredMethods();
        HashMap properties = new HashMap();
        for (Method method : serviceMethods) {
            if (method.getParameterCount() == 0 && !method.getName().contains("registerProperties")) {
                Object value = method.invoke(actualService);
                properties.put(method.getName(), method.invoke(actualService));
            }
        }

        printer().println(jsonMapper.writeValueAsString(properties));

        if (!jsonOutput) {
            printer().println("To stop the execution press q");
        }
        Scanner sc = new Scanner(System.in).useDelimiter("\n");

        if (sc.nextLine().equals("q")) {
            actualService.shutdown();
            sc.close();
        }
    }
}
