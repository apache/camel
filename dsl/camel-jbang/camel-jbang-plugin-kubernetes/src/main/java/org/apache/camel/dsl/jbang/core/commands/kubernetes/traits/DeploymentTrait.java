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

import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.apache.camel.v1.integrationspec.Traits;

public class DeploymentTrait extends BaseTrait {

    public DeploymentTrait() {
        super("deployment", 900);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        return true;
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        DeploymentBuilder deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(context.getName())
                .endMetadata()
                .withNewSpec()
                .withSelector(new LabelSelectorBuilder()
                        .withMatchLabels(
                                Collections.singletonMap(INTEGRATION_LABEL, context.getName()))
                        .build())
                .endSpec();

        if (context.getServiceAccount() != null) {
            deployment.editSpec()
                    .editOrNewTemplate()
                    .editOrNewSpec()
                    .withServiceAccountName(context.getServiceAccount())
                    .endSpec()
                    .endTemplate()
                    .endSpec();
        }

        context.add(deployment);
    }
}
