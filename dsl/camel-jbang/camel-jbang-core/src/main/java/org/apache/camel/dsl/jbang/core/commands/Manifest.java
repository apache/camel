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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "manifests", description = "Create Kubernetes resources")
public class Manifest implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(Manifest.class);

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    @CommandLine.Option(names = { "--path" }, description = "Output folder path", defaultValue = "manifests")
    private String path;
    @CommandLine.Option(names = { "--namespace" }, description = "Namespace")
    private String namespace;
    @CommandLine.Option(names = { "--name" }, description = "Application name", required = true)
    private String name;
    @CommandLine.Option(names = { "--version" }, description = "Application version (label)", required = true)
    private String version;
    @CommandLine.Option(names = { "--image" }, description = "Deployment container image name", required = true)
    private String image;
    @CommandLine.Option(names = { "--source-image" }, description = "Source image name (for OpenShift buildConfig)",
                        defaultValue = "java:openjdk-11-ubi8")
    private String sourceImage;
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
    @CommandLine.Option(names = { "-j", "--jar" }, description = "Jar filename")
    private String jar;

    @Override
    public Integer call() throws Exception {
        try {
            LOG.info("Generating resources...");
            if (minikube) {
                Deployment deployment
                        = KubernetesHelper.createDeployment(namespace, name, image, version, containerPort, replicas);
                Service service = KubernetesHelper.createService(namespace, name, version, servicePort, containerPort, minikube,
                        nodePort);
                write(deployment, "deployment.yaml");
                write(service, "service.yaml");
            } else if (openshift) {
                Deployment deployment
                        = KubernetesHelper.createDeployment(namespace, name, image, version, containerPort, replicas);
                Service service = KubernetesHelper.createService(namespace, name, version, servicePort, containerPort, minikube,
                        nodePort);
                Route route = KubernetesHelper.createRoute(namespace, name, version, containerPort);
                ImageStream imageStream = KubernetesHelper.createImageStream(namespace, name, version);
                File jarFile = Paths.get(jar).toFile();
                BuildConfig buildConfig
                        = KubernetesHelper.createBuildConfig(namespace, name, version, jarFile.getName(), sourceImage);
                write(deployment, "deployment.yaml");
                write(service, "service.yaml");
                write(route, "route.yaml");
                write(imageStream, "image-stream.yaml");
                write(buildConfig, "build-config.yaml");
            }
        } catch (Exception ex) {
            LOG.error("Error", ex.getMessage());
        }
        return 0;
    }

    private void write(Object object, String filename) throws IOException {
        Path output = Paths.get(path != null ? path : System.getProperty("user.dir"));
        if (!Files.exists(output)) {
            LOG.info("Creating output folder " + output);
            Files.createDirectories(output);
        }
        LOG.info("Writing {}...", filename);
        Files.write(Paths.get(output.toString(), filename),
                Serialization.asYaml(object).getBytes(StandardCharsets.UTF_8));
    }

}
