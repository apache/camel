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

package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.SourceScheme;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

/**
 * Base command supports Kubernetes client related options such as namespace or custom kube config option. Automatically
 * applies the options to the Kubernetes client instance that is being used to run commands.
 */
public abstract class KubernetesBaseCommand extends CamelCommand {

    static final String RUN_PLATFORM_DIR = ".camel-jbang-run";

    @CommandLine.Option(names = { "--kube-config" },
                        description = "Path to the kube config file to initialize Kubernetes client")
    String kubeConfig;

    @CommandLine.Option(names = { "--namespace", "-n" }, description = "Namespace to use for all operations")
    String namespace;

    @CommandLine.Option(names = { "--name" },
                        description = "The integration name. Use this when the name should not get derived otherwise.")
    String name;

    List<Supplier<String>> projectNameSuppliers = new ArrayList<>();

    private KubernetesClient kubernetesClient;

    public KubernetesBaseCommand(CamelJBangMain main) {
        super(main);
        projectNameSuppliers.add(() -> name);
    }

    protected String getProjectName() {
        return projectNameSuppliers.stream().map(Supplier::get).filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> new RuntimeCamelException("Failed to resolve project name"));
    }

    protected String projectNameFromImage(Supplier<String> imageSupplier) {
        return KubernetesHelper.sanitize(StringHelper.beforeLast(imageSupplier.get(), ":"));
    }

    protected String projectNameFromGav(Supplier<String> gavSupplier) {
        var gav = gavSupplier.get();
        if (gav != null) {
            String[] ids = gav.split(":");
            if (ids.length > 1) {
                return KubernetesHelper.sanitize(ids[1]); // artifactId
            }
        }
        return null;
    }

    protected String projectNameFromFilePath(Supplier<String> pathSupplier) {
        var filePath = pathSupplier.get();
        if (filePath != null) {
            return KubernetesHelper.sanitize(FileUtil.onlyName(SourceScheme.onlyName(filePath)));
        }
        return null;
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
        if (!ObjectHelper.isEmpty(namespace)) {
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
        if (!ObjectHelper.isEmpty(namespace)) {
            return client().pods().inNamespace(namespace);
        }

        return client().pods();
    }

    /**
     * Gets Kubernetes client. In case custom kubeConfig option is set initializes the client with the config otherwise
     * uses default client.
     */
    protected KubernetesClient client() {
        if (kubernetesClient == null) {
            if (kubeConfig != null) {
                kubernetesClient = KubernetesHelper.getKubernetesClient(kubeConfig);
            } else {
                kubernetesClient = KubernetesHelper.getKubernetesClient();
            }
        }

        return kubernetesClient;
    }

    /**
     * Sets the Kubernetes client.
     */
    public KubernetesBaseCommand withClient(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        return this;
    }
}
