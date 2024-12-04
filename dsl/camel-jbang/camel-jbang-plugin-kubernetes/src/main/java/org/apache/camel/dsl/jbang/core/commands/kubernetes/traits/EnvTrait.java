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
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Environment;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.util.ObjectHelper;

public class EnvTrait extends BaseTrait {

    public EnvTrait() {
        super("env", ContainerTrait.CONTAINER_TRAIT_ORDER + 5);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        Optional<DeploymentBuilder> deployment = context.getDeployment();
        if (deployment.isEmpty()) {
            return false;
        }

        return traitConfig.getEnvironment() != null &&
                Optional.ofNullable(traitConfig.getEnvironment().getEnabled()).orElse(true);
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Environment envTrait = traitConfig.getEnvironment();

        if (ObjectHelper.isNotEmpty(envTrait.getVars())) {
            context.doWithDeployments(
                    d -> d.editSpec()
                            .editTemplate()
                            .editSpec()
                            .editFirstContainer()
                            .addAllToEnv(envTrait.getVars().stream().map(envvar -> {
                                String[] parts = envvar.split("=", 2);
                                return new EnvVarBuilder()
                                        .withName(parts[0])
                                        .withValue(parts[1])
                                        .build();
                            }).collect(Collectors.toList()))
                            .endContainer()
                            .endSpec()
                            .endTemplate()
                            .endSpec());
        }
    }
}
