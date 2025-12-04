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

import io.fabric8.kubernetes.api.model.IntOrString;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Jolokia;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;

public class JolokiaTrait extends BaseTrait {
    public static final int JolokiaTrait = 1800;

    public static final int DEFAULT_JOLOKIA_PORT = 8778;
    public static final String DEFAULT_JOLOKIA_PORT_NAME = "jolokia";
    public static final String DEFAULT_JOLOKIA_PORT_PROTOCOL = "TCP";

    public JolokiaTrait() {
        super("jolokia", JolokiaTrait);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        // must be explicitly enabled
        if (traitConfig.getJolokia() == null) {
            return false;
        } else {
            return Optional.ofNullable(traitConfig.getJolokia().getEnabled()).orElse(false);
        }
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Jolokia jolokiaTrait = Optional.ofNullable(traitConfig.getJolokia()).orElseGet(Jolokia::new);

        context.doWithDeployments(d -> d.editSpec()
                .editTemplate()
                .editSpec()
                .editFirstContainer()
                .addNewPort()
                .withName(
                        Optional.ofNullable(jolokiaTrait.getContainerPortName()).orElse(DEFAULT_JOLOKIA_PORT_NAME))
                .withContainerPort(Optional.ofNullable(jolokiaTrait.getContainerPort())
                        .map(Long::intValue)
                        .orElse(DEFAULT_JOLOKIA_PORT))
                .withProtocol(DEFAULT_JOLOKIA_PORT_PROTOCOL)
                .endPort()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec());
        context.doWithKnativeServices(s -> s.editSpec()
                .editTemplate()
                .editSpec()
                .editFirstContainer()
                .addNewPort()
                .withName(
                        Optional.ofNullable(jolokiaTrait.getContainerPortName()).orElse(DEFAULT_JOLOKIA_PORT_NAME))
                .withContainerPort(Optional.ofNullable(jolokiaTrait.getContainerPort())
                        .map(Long::intValue)
                        .orElse(DEFAULT_JOLOKIA_PORT))
                .withProtocol(DEFAULT_JOLOKIA_PORT_PROTOCOL)
                .endPort()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec());
        if (Boolean.TRUE.equals(jolokiaTrait.getExpose())) {
            context.doWithServices(s -> s.editSpec()
                    .addNewPort()
                    .withName(Optional.ofNullable(jolokiaTrait.getServicePortName())
                            .orElse(DEFAULT_JOLOKIA_PORT_NAME))
                    .withPort(Optional.ofNullable(jolokiaTrait.getServicePort())
                            .map(Long::intValue)
                            .orElse(DEFAULT_JOLOKIA_PORT))
                    .withTargetPort(new IntOrString(Optional.ofNullable(jolokiaTrait.getContainerPortName())
                            .orElse(DEFAULT_JOLOKIA_PORT_NAME)))
                    .endPort()
                    .endSpec());
        }
    }
}
