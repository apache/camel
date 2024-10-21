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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Container;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;

public class ContainerTrait extends BaseTrait {

    public static final int CONTAINER_TRAIT_ORDER = 1600;
    public static final int DEFAULT_CONTAINER_PORT = 8080;
    public static final String DEFAULT_CONTAINER_PORT_NAME = "http";

    public ContainerTrait() {
        super("container", CONTAINER_TRAIT_ORDER);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        return traitConfig.getContainer() == null ||
                Optional.ofNullable(traitConfig.getContainer().getEnabled()).orElse(true);
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Container containerTrait = Optional.ofNullable(traitConfig.getContainer()).orElseGet(Container::new);

        ContainerBuilder container = new ContainerBuilder()
                .withName(Optional.ofNullable(containerTrait.getName()).orElse(context.getName()))
                .withImage(containerTrait.getImage());

        if (containerTrait.getImagePullPolicy() != null) {
            container.withImagePullPolicy(containerTrait.getImagePullPolicy().getValue());
        }

        if (containerTrait.getPort() != null || context.getService().isPresent() || context.getKnativeService().isPresent()) {
            container.addToPorts(new ContainerPortBuilder()
                    .withName(Optional.ofNullable(containerTrait.getPortName()).orElse(DEFAULT_CONTAINER_PORT_NAME))
                    .withContainerPort(
                            Optional.ofNullable(containerTrait.getPort()).map(Long::intValue).orElse(DEFAULT_CONTAINER_PORT))
                    .withProtocol("TCP")
                    .build());
        }

        ResourceRequirementsBuilder resourceRequirementsBuilder = new ResourceRequirementsBuilder();
        if (containerTrait.getRequestMemory() != null) {
            resourceRequirementsBuilder.addToRequests("memory", new Quantity(containerTrait.getRequestMemory()));
        }
        if (containerTrait.getRequestCPU() != null) {
            resourceRequirementsBuilder.addToRequests("cpu", new Quantity(containerTrait.getRequestCPU()));
        }
        if (containerTrait.getLimitMemory() != null) {
            resourceRequirementsBuilder.addToLimits("memory", new Quantity(containerTrait.getLimitMemory()));
        }
        if (containerTrait.getLimitCPU() != null) {
            resourceRequirementsBuilder.addToLimits("cpu", new Quantity(containerTrait.getLimitCPU()));
        }
        container.withResources(resourceRequirementsBuilder.build());

        context.doWithDeployments(d -> d.editOrNewSpec()
                .editOrNewTemplate()
                .editOrNewMetadata()
                .addToLabels(KUBERNETES_NAME_LABEL, context.getName())
                .endMetadata()
                .editOrNewSpec()
                .addToContainers(container.build())
                .endSpec()
                .endTemplate()
                .endSpec());

        context.doWithCronJobs(j -> j.editOrNewSpec()
                .editOrNewJobTemplate()
                .editOrNewMetadata()
                .addToLabels(KUBERNETES_NAME_LABEL, context.getName())
                .endMetadata()
                .editOrNewSpec()
                .editOrNewTemplate()
                .editOrNewSpec()
                .addToContainers(container.build())
                .endSpec()
                .endTemplate()
                .endSpec()
                .endJobTemplate()
                .endSpec());
    }
}
