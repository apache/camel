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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Mount;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Openapi;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.util.ObjectHelper;

public class OpenApiTrait extends BaseTrait {

    public OpenApiTrait() {
        super("openapi", MountTrait.MOUNT_TRAIT_ORDER + 1);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        Openapi openApiTrait = Optional.ofNullable(traitConfig.getOpenapi()).orElseGet(Openapi::new);

        if (openApiTrait.getConfigmaps() != null) {
            for (String resource : openApiTrait.getConfigmaps()) {
                if (!resource.startsWith("configmap:")) {
                    throw new RuntimeCamelException(
                            "Unsupported resource %s, must be a configmap".formatted(resource));
                }
            }
        }

        return true;
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Openapi openApiTrait = Optional.ofNullable(traitConfig.getOpenapi()).orElseGet(Openapi::new);

        if (ObjectHelper.isNotEmpty(openApiTrait.getConfigmaps())) {
            MountTrait delegate = new MountTrait();
            // ugly code
            // TODO : builder or fluent
            Traits traits = new Traits();
            Mount mount = new Mount();
            mount.setResources(openApiTrait.getConfigmaps());
            traits.setMount(mount);
            delegate.apply(traits, context);
        }
    }
}
