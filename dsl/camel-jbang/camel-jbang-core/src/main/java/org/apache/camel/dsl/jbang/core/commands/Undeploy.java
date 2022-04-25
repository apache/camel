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

@CommandLine.Command(name = "undeploy", description = "Undeploy resources from Kubernetes, OpenShift, Minikube")
public class Undeploy implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(Undeploy.class);

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested;
    @CommandLine.Option(names = { "--namespace" }, required = true, description = "Namespace", defaultValue = "default")
    private String namespace;
    @CommandLine.Option(names = { "--name" }, description = "Application name", required = true)
    private String name;

    @Override
    public Integer call() throws Exception {
        Deployment deployment = KubernetesHelper.createDeployment(namespace, name, "", "", 0, 0);
        Service service = KubernetesHelper.createService(namespace, name, "", 0, 0, false, 0);

        try (KubernetesClient client = new DefaultKubernetesClient()) {
            LOG.info("Deleting Service...");
            client.services().inNamespace(namespace).delete(service);
            LOG.info("Deleting Deployment...");
            client.apps().deployments().inNamespace(namespace).delete(deployment);
        } catch (Exception ex) {
            LOG.error("Error", ex.getMessage());
        }
        return 0;
    }

}
