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
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import org.apache.camel.v1.integrationspec.Traits;
import org.apache.camel.v1.integrationspec.traits.Container;

public class ContainerTrait extends BaseTrait {

    public static final int CONTAINER_TRAIT_ORDER = 1600;
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

        if (containerTrait.getPort() != null) {
            container.addToPorts(new ContainerPortBuilder()
                    .withName(Optional.ofNullable(containerTrait.getPortName()).orElse(DEFAULT_CONTAINER_PORT_NAME))
                    .withContainerPort(containerTrait.getPort().intValue())
                    .withProtocol("TCP")
                    .build());
        }

        Optional<ServiceBuilder> service = context.getService();
        if (service.isPresent()) {
            if (containerTrait.getPort() == null) {
                container.addToPorts(new ContainerPortBuilder()
                        .withName(Optional.ofNullable(containerTrait.getPortName()).orElse(DEFAULT_CONTAINER_PORT_NAME))
                        .withContainerPort(8080)
                        .withProtocol("TCP")
                        .build());
            }

            service.get().editOrNewSpec()
                    .addToPorts(new ServicePortBuilder()
                            .withName(Optional.ofNullable(containerTrait.getServicePortName())
                                    .orElse(DEFAULT_CONTAINER_PORT_NAME))
                            .withPort(Optional.ofNullable(containerTrait.getServicePort()).map(Long::intValue).orElse(80))
                            .withTargetPort(
                                    new IntOrString(
                                            Optional.ofNullable(containerTrait.getPortName())
                                                    .orElse(DEFAULT_CONTAINER_PORT_NAME)))
                            .withProtocol("TCP")
                            .build())
                    .endSpec();
        }

        context.doWithDeployments(d -> d.editOrNewSpec()
                .editOrNewTemplate()
                .editOrNewMetadata()
                .addToLabels(INTEGRATION_LABEL, context.getName())
                .endMetadata()
                .editOrNewSpec()
                .addToContainers(container.build())
                .endSpec()
                .endTemplate()
                .endSpec());

        context.doWithCronJobs(j -> j.editOrNewSpec()
                .editOrNewJobTemplate()
                .editOrNewMetadata()
                .addToLabels(INTEGRATION_LABEL, context.getName())
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
