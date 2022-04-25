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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "deploy", description = "Deploy resources to Kubernetes, OpenShift, Minikube")
public class Deploy implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(Deploy.class);

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    @CommandLine.Option(names = { "--namespace" }, required = true, description = "Namespace", defaultValue = "default")
    private String namespace;
    @CommandLine.Option(names = { "--name" }, description = "Application name", required = true)
    private String name;
    @CommandLine.Option(names = { "--version" }, description = "Application version (label)", required = true)
    private String version;
    @CommandLine.Option(names = { "--image" }, description = "Deployment container image name", required = true)
    private String image;
    @CommandLine.Option(names = { "--container-port" }, description = "Container port", defaultValue = "8080")
    private int containerPort;
    @CommandLine.Option(names = { "--service-port" }, description = "Service port", defaultValue = "80")
    private int servicePort;
    @CommandLine.Option(names = { "--node-port" }, description = "Node port (minikube)", defaultValue = "30777")
    private int nodePort;
    @CommandLine.Option(names = { "--replicas" }, description = "Number of replicas of the application", defaultValue = "1")
    private int replicas;
    @CommandLine.Option(names = { "--minikube" }, description = "Target is minikube")
    private boolean minikube;

    @Override
    public Integer call() throws Exception {
        LOG.info("Generating Deployment...");
        Deployment deployment = KubernetesHelper.createDeployment(namespace, name, image, version, containerPort, replicas);
        LOG.info("Generating Service...");
        Service service
                = KubernetesHelper.createService(namespace, name, version, servicePort, containerPort, minikube, nodePort);

        try (KubernetesClient client = new DefaultKubernetesClient()) {
            LOG.info("Creating Deployment in " + (minikube ? "Minikube" : "Kubernetes"));
            client.apps().deployments().inNamespace(namespace).createOrReplace(deployment);
            client.services().inNamespace(namespace).delete(service);
            LOG.info("Creating Service in " + (minikube ? "Minikube" : "Kubernetes"));
            client.services().inNamespace(namespace).createOrReplace(service);
        } catch (Exception ex) {
            LOG.error("Error", ex.getMessage());
        }
        return 0;
    }

}
