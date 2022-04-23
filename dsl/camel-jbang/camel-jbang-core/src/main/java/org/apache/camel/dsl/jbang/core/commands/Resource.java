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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.utils.Serialization;
import picocli.CommandLine;

@CommandLine.Command(name = "resources", description = "Generate Kubernetes resources")
public class Resource extends Kubernetes implements Callable<Integer> {

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    @CommandLine.Option(names = { "--path" }, description = "Output folder path")
    private String path;
    @CommandLine.Option(names = { "--namespace" }, description = "Namespace")
    private String namespace;

    @Override
    public Integer call() throws Exception {
        Deployment deployment = createDeployment(namespace, name, image, version, containerPort, replicas);
        Service service = createService(namespace, name, version, servicePort, containerPort, minikube, nodePort);
        Path output = Paths.get(path != null ? path : System.getProperty("user.dir"));
        if (!Files.exists(output)) {
            Files.createDirectories(output);
        }
        Files.write(Paths.get(output.toString(), "deployment.yaml"),
                Serialization.asYaml(deployment).getBytes(StandardCharsets.UTF_8));
        Files.write(Paths.get(output.toString(), "service.yaml"),
                Serialization.asYaml(service).getBytes(StandardCharsets.UTF_8));
        return 0;
    }

}
