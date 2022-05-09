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
import java.util.concurrent.Callable;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import picocli.CommandLine;

@CommandLine.Command(name = "undeploy", description = "Undeploy resources from Kubernetes, OpenShift, Minikube")
public class Undeploy implements Callable<Integer> {

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    @CommandLine.Option(names = { "--namespace" }, required = true, description = "Namespace", defaultValue = "default")
    private String namespace;
    @CommandLine.Option(names = { "--name" }, description = "Application name", required = true)
    private String name;
    @CommandLine.Option(names = { "--version" }, description = "Application version", required = true)
    private String version;
    @CommandLine.Option(names = { "--openshift" }, description = "Target is openshift")
    private boolean openshift;
    @CommandLine.Option(names = { "--server" }, description = "Master URL")
    private String server;
    @CommandLine.Option(names = { "--token" }, description = "Token")
    private String token;

    @Override
    public Integer call() throws Exception {
        Map labels = KubernetesHelper.getLabels(name, version);
        if (openshift) {
            OpenShiftConfig config
                    = new OpenShiftConfigBuilder().withMasterUrl(server).withOauthToken(token).withTrustCerts(true).build();
            try (OpenShiftClient client = new DefaultOpenShiftClient(config)) {
                System.out.println("Deleting Routes...");
                client.routes().inNamespace(namespace).withLabels(labels).delete();
                System.out.println("Deleting Service...");
                client.services().inNamespace(namespace).withLabels(labels).delete();
                System.out.println("Deleting Deployment...");
                client.apps().deployments().inNamespace(namespace).withLabels(labels).delete();
                System.out.println("Deleting ImageStream...");
                client.imageStreams().inNamespace(namespace).withLabels(labels).delete();
                System.out.println("Deleting BuildConfig...");
                client.buildConfigs().inNamespace(namespace).withLabels(labels).delete();
            }
        } else {
            try (KubernetesClient client = new DefaultKubernetesClient()) {
                System.out.println("Deleting Service...");
                client.services().inNamespace(namespace).withLabels(labels).delete();
                System.out.println("Deleting Deployment...");
                client.apps().deployments().inNamespace(namespace).withLabels(labels).delete();
            } catch (Exception ex) {
                System.out.println("Error Undeploying " + ex.getMessage());
            }
        }
        return 0;
    }

}
