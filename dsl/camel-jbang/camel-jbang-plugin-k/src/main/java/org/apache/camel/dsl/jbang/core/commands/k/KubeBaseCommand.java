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

package org.apache.camel.dsl.jbang.core.commands.k;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import picocli.CommandLine;

/**
 * Bas command supports Kubernetes client related options such as namespace or custom kube config option. Automatically
 * applies the options to the Kubernetes client instance that is being used to run commands.
 */
abstract class KubeBaseCommand extends CamelCommand {

    @CommandLine.Option(names = { "--kube-config" },
                        description = "Path to the kube config file to initialize Kubernetes client")
    String kubeConfig;

    @CommandLine.Option(names = { "--namespace", "-n" }, description = "Namespace to use for all operations")
    String namespace;

    private KubernetesClient kubernetesClient;

    public KubeBaseCommand(CamelJBangMain main) {
        super(main);
    }

    /**
     * Provides access to the Kubernetes client and automatically sets current namespace if option is given.
     *
     * @param  resourceType the Kubernetes resource this client will operate with.
     * @return              namespaced client if applicable.
     * @param  <T>          resource type parameter.
     */
    protected <T extends HasMetadata> NonNamespaceOperation<T, KubernetesResourceList<T>, Resource<T>> client(
            Class<T> resourceType) {
        if (namespace != null) {
            return client().resources(resourceType).inNamespace(namespace);
        }

        return client().resources(resourceType);
    }

    /**
     * Provides access to Pod resources using the Kubernetes client with current namespace automatically set if option
     * is given.
     *
     * @return namespaced client if applicable.
     */
    protected NonNamespaceOperation<Pod, PodList, PodResource> pods() {
        if (namespace != null) {
            return client().pods().inNamespace(namespace);
        }

        return client().pods();
    }

    /**
     * Gets Kubernetes client. In case custom kubeConfig option is set initializes the client with the config otherwise
     * uses default client.
     *
     * @return
     */
    protected KubernetesClient client() {
        if (kubernetesClient == null) {
            if (kubeConfig != null) {
                kubernetesClient = KubernetesHelper.getKubernetesClient(kubeConfig);
            }

            kubernetesClient = KubernetesHelper.getKubernetesClient();
        }

        return kubernetesClient;
    }

    /**
     * Sets the Kubernetes client.
     *
     * @param kubernetesClient
     */
    KubeBaseCommand withClient(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        return this;
    }
}
