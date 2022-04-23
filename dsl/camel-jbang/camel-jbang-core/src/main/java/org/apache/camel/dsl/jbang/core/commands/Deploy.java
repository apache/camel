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

import java.util.concurrent.Callable;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import picocli.CommandLine;

@CommandLine.Command(name = "deploy", description = "Deploy resources to Kubernetes, OpenShift, Minikube")
public class Deploy extends Kubernetes implements Callable<Integer> {

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    @CommandLine.Option(names = { "--namespace" }, required = true, description = "Namespace", defaultValue = "default")
    private String namespace;

    @Override
    public Integer call() throws Exception {
        Deployment deployment = createDeployment(namespace, name, image, version, containerPort, replicas);
        Service service = createService(namespace, name, version, servicePort, containerPort, minikube, nodePort);

        try (KubernetesClient client = new DefaultKubernetesClient()) {
            client.apps().deployments().inNamespace(namespace).createOrReplace(deployment);
            client.services().inNamespace(namespace).delete(service);
            client.services().inNamespace(namespace).createOrReplace(service);
        }
        return 0;
    }

}
