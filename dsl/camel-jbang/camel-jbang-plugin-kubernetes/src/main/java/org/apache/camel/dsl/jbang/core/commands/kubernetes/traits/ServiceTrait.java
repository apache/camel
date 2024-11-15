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

import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Container;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Service;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;

public class ServiceTrait extends BaseTrait {

    public static final int SERVICE_TRAIT_ORDER = 1500;
    public static final int DEFAULT_SERVICE_PORT = 80;

    public ServiceTrait() {
        super("service", SERVICE_TRAIT_ORDER);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        if (context.getService().isPresent() || context.getKnativeService().isPresent()) {
            return false;
        }

        if (traitConfig.getService() != null && traitConfig.getService().getEnabled() != null) {
            // either explicitly enabled or disabled
            return traitConfig.getService().getEnabled();
        }

        if (traitConfig.getContainer().getPort() != null) {
            return true;
        }

        return TraitHelper.exposesHttpService(context);
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Service serviceTrait = Optional.ofNullable(traitConfig.getService()).orElseGet(Service::new);
        String serviceType = Optional.ofNullable(serviceTrait.getType()).map(Service.Type::getValue)
                .orElse(Service.Type.CLUSTERIP.getValue());

        Container containerTrait = Optional.ofNullable(traitConfig.getContainer()).orElseGet(Container::new);

        ServiceBuilder service = new ServiceBuilder()
                .withNewMetadata()
                .withName(context.getName())
                .endMetadata()
                .withNewSpec()
                .withType(serviceType)
                .withSelector(Map.of(BaseTrait.KUBERNETES_LABEL_NAME, context.getName()))
                .addToPorts(new ServicePortBuilder()
                        .withName(Optional.ofNullable(containerTrait.getServicePortName())
                                .orElse(ContainerTrait.DEFAULT_CONTAINER_PORT_NAME))
                        .withPort(Optional.ofNullable(containerTrait.getServicePort()).map(Long::intValue)
                                .orElse(DEFAULT_SERVICE_PORT))
                        .withTargetPort(
                                new IntOrString(
                                        Optional.ofNullable(containerTrait.getPortName())
                                                .orElse(ContainerTrait.DEFAULT_CONTAINER_PORT_NAME)))
                        .withProtocol("TCP")
                        .build())
                .endSpec();

        context.add(service);
    }

    @Override
    public boolean accept(TraitProfile profile) {
        return TraitProfile.KNATIVE != profile;
    }
}
