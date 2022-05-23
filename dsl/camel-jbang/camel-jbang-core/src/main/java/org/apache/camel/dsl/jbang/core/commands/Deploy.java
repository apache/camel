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

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import picocli.CommandLine;

@CommandLine.Command(name = "deploy", description = "Deploy resources to Kubernetes, OpenShift, Minikube")
public class Deploy extends CamelCommand {

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    @CommandLine.Option(names = { "--namespace" }, required = true, description = "Namespace", defaultValue = "default")
    private String namespace;
    @CommandLine.Option(names = { "--name" }, description = "Application name", required = true)
    private String name;
    @CommandLine.Option(names = { "--version" }, description = "Application version (label)")
    private String version = "latest";
    @CommandLine.Option(names = { "--image" }, description = "Deployment container image name")
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
    @CommandLine.Option(names = { "--openshift" }, description = "Target is openshift")
    private boolean openshift;
    @CommandLine.Option(names = { "--server" }, description = "Master URL")
    private String server;
    @CommandLine.Option(names = { "--token" }, description = "Token")
    private String token;
    @CommandLine.Option(names = { "-u", "--username" }, description = "Username")
    private String username;
    @CommandLine.Option(names = { "-p", "--password" }, description = "Password")
    private String password;

    public Deploy(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        if (openshift) {
            image = image == null ? namespace + "/" + name + ":" + version : image;
            if (!image.startsWith("image-registry.openshift-image-registry.svc:5000") && image.split("/").length != 3) {
                image = "image-registry.openshift-image-registry.svc:5000/" + image;
            }
            System.out.println("Generating Deployment...");
            Deployment deployment = KubernetesHelper.createDeployment(namespace, name, image, version, containerPort, replicas);
            System.out.println("Generating Service...");
            Service service
                    = KubernetesHelper.createService(namespace, name, version, servicePort, containerPort, minikube, nodePort);
            System.out.println("Generating Route...");
            Route route = KubernetesHelper.createRoute(namespace, name, version, containerPort);

            try (OpenShiftClient client
                    = new DefaultOpenShiftClient(KubernetesHelper.getOpenShiftConfig(server, username, password, token))) {
                System.out.println("Creating Deployment in Openshift");
                client.apps().deployments().inNamespace(namespace).createOrReplace(deployment);
                client.services().inNamespace(namespace).delete(service);
                System.out.println("Creating Service in Openshift");
                client.services().inNamespace(namespace).createOrReplace(service);
                System.out.println("Creating Route in Openshift");
                client.routes().inNamespace(namespace).createOrReplace(route);
            } catch (KubernetesClientException ex) {
                Status status = ex.getStatus();
                if (status != null) {
                    System.out.println("ERROR: " + status.getCode() + " " + status.getStatus() + " " + status.getReason() + " "
                                       + ex.getMessage());
                } else {
                    System.out.println("ERROR " + ex.getMessage());
                }
            }
        } else {
            System.out.println("Generating Deployment for " + (minikube ? "Minikube" : "Kubernetes"));
            Deployment deployment = KubernetesHelper.createDeployment(namespace, name, image, version, containerPort, replicas);
            System.out.println("Generating Service " + (minikube ? "Minikube" : "Kubernetes"));
            Service service
                    = KubernetesHelper.createService(namespace, name, version, servicePort, containerPort, minikube, nodePort);

            try (KubernetesClient client
                    = new DefaultKubernetesClient(KubernetesHelper.getConfig(server, username, password, token))) {
                System.out.println("Creating Deployment in " + (minikube ? "Minikube" : "Kubernetes"));
                client.apps().deployments().inNamespace(namespace).createOrReplace(deployment);
                client.services().inNamespace(namespace).delete(service);
                System.out.println("Creating Service in " + (minikube ? "Minikube" : "Kubernetes"));
                client.services().inNamespace(namespace).createOrReplace(service);
            } catch (Exception ex) {
                System.out.println("ERROR: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        return 0;
    }
}
