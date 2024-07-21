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

import java.util.Collections;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.MetadataHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.SourceMetadata;
import org.apache.camel.dsl.jbang.core.common.Source;
import org.apache.camel.v1.integrationspec.Traits;
import org.apache.camel.v1.integrationspec.traits.Container;
import org.apache.camel.v1.integrationspec.traits.Service;

public class ServiceTrait extends BaseTrait {

    public ServiceTrait() {
        super("service", 1500);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        if (context.getService().isPresent()) {
            return false;
        }

        if (traitConfig.getService() != null && traitConfig.getService().getEnabled() != null) {
            // either explicitly enabled or disabled
            return traitConfig.getService().getEnabled();
        }

        try {
            boolean exposesHttpServices = false;
            CamelCatalog catalog = context.getCatalog();
            if (context.getSources() != null) {
                for (Source source : context.getSources()) {
                    SourceMetadata metadata = MetadataHelper.readFromSource(catalog, source);
                    if (MetadataHelper.exposesHttpServices(catalog, metadata)) {
                        exposesHttpServices = true;
                        break;
                    }
                }
            }

            return exposesHttpServices;
        } catch (Exception e) {
            context.printer().printf("Failed to apply service trait %s%n", e.getMessage());
            return false;
        }
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Service serviceTrait = Optional.ofNullable(traitConfig.getService()).orElseGet(Service::new);
        String serviceType = Optional.ofNullable(serviceTrait.getType()).map(Service.Type::getValue)
                .orElse(Service.Type.NODEPORT.getValue());

        Container containerTrait = Optional.ofNullable(traitConfig.getContainer()).orElseGet(Container::new);

        ServiceBuilder service = new ServiceBuilder()
                .withNewMetadata()
                .withName(context.getName())
                .endMetadata()
                .withNewSpec()
                .withType(serviceType)
                .withSelector(Collections.singletonMap(BaseTrait.INTEGRATION_LABEL, context.getName()))
                .addToPorts(new ServicePortBuilder()
                        .withName(Optional.ofNullable(containerTrait.getServicePortName())
                                .orElse(ContainerTrait.DEFAULT_CONTAINER_PORT_NAME))
                        .withPort(Optional.ofNullable(containerTrait.getServicePort()).map(Long::intValue).orElse(80))
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
