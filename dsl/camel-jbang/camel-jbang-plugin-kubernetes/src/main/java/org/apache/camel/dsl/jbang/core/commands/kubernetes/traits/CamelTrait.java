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

import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Camel;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.util.ObjectHelper;

public class CamelTrait extends BaseTrait {

    public CamelTrait() {
        super("camel", 12000);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        return true;
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Camel camelTrait = Optional.ofNullable(traitConfig.getCamel()).orElseGet(Camel::new);

        if (ObjectHelper.isNotEmpty(camelTrait.getProperties())) {
            // TODO: use ConfigMap resource
            context.addConfigurationResource("application.properties",
                    camelTrait.getProperties().stream().collect(Collectors.joining(System.lineSeparator())));
        }
    }
}
