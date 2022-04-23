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

import java.util.Map;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import picocli.CommandLine;

public class Kubernetes {

    @CommandLine.Option(names = { "--name" }, description = "Application name", required = true)
    protected String name;

    @CommandLine.Option(names = { "--version" }, description = "Application version (label)", required = true)
    protected String version;

    @CommandLine.Option(names = { "--image" }, description = "Deployment container image name", required = true)
    protected String image;

    @CommandLine.Option(names = { "--container-port" }, description = "Container port", defaultValue = "8080")
    protected int containerPort;
    @CommandLine.Option(names = { "--service-port" }, description = "Service port", defaultValue = "80")
    protected int servicePort;
    @CommandLine.Option(names = { "--node-port" }, description = "Node port (minikube)", defaultValue = "30777")
    protected int nodePort;
    @CommandLine.Option(names = { "--replicas" }, description = "Number of replicas of the application", defaultValue = "1")
    protected int replicas;
    @CommandLine.Option(names = { "--minikube" }, description = "Target is minikube")
    protected boolean minikube;

    public Service createService(
            String namespace, String name, String version, int port, int targetPort, boolean minikube, int nodePort) {

        ObjectMetaBuilder metadata = new ObjectMetaBuilder()
                .withName(name)
                .withLabels(getLabels(name, version));
        if (namespace != null) {
            metadata.withNamespace(namespace);
        }

        ServicePortBuilder servicePort = new ServicePortBuilder()
                .withName("http")
                .withPort(port)
                .withNewTargetPort(targetPort);
        if (minikube) {
            servicePort.withNodePort(nodePort);
        }

        ServiceSpecBuilder spec = new ServiceSpecBuilder()
                .withPorts(servicePort.build());
        if (minikube) {
            spec.withType("NodePort");
        }

        return new ServiceBuilder()
                .withMetadata(metadata.build())
                .withSpec(spec.build())
                .build();
    }

    public Deployment createDeployment(
            String namespace, String name, String image, String version, int containerPort, int replica) {

        EnvVar envVar = new EnvVarBuilder()
                .withName("KUBERNETES_NAMESPACE")
                .withNewValueFrom()
                .withNewFieldRef()
                .withFieldPath("metadata.namespace")
                .endFieldRef()
                .endValueFrom()
                .build();

        ObjectMetaBuilder metadata = new ObjectMetaBuilder()
                .withName(name)
                .withLabels(getLabels(name, version));
        if (namespace != null) {
            metadata.withNamespace(namespace);
        }

        return new DeploymentBuilder()
                .withMetadata(metadata.build())
                .withNewSpec()
                .withReplicas(replica)
                .withNewSelector()
                .addToMatchLabels(getLabels(name, version))
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(getLabels(name, version))
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(name)
                .withImage(image)
                .withImagePullPolicy("IfNotPresent")
                .addNewPort()
                .withContainerPort(containerPort)
                .withName("http")
                .withProtocol("TCP")
                .endPort()
                .addNewEnv()
                .withName("KUBERNETES_NAMESPACE")
                .withValueFrom(envVar.getValueFrom())
                .endEnv()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    public Map<String, String> getLabels(String name, String version) {
        return Map.of(
                "app", name,
                "app.kubernetes.io/name", name,
                "app.kubernetes.io/version", version,
                "app.kubernetes.io/part-of", name,
                "runtime", "camel");
    }
}
