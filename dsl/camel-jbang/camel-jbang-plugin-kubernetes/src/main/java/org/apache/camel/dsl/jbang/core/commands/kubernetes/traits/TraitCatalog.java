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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.knative.KnativeServiceTrait;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.knative.KnativeTrait;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;

/**
 * Catalog of traits that get applied to a trait context in order to generate a set of Kubernetes resources as a
 * manifest. Traits may be filtered according to a given trait profile. The given trait specification holds the trait
 * configuration for the applied traits.
 */
public class TraitCatalog {

    private final List<Trait> traits = new ArrayList<>();

    public TraitCatalog() {
        register(new DeploymentTrait());
        register(new KnativeTrait());
        register(new KnativeServiceTrait());
        register(new ServiceTrait());
        register(new RouteTrait());
        register(new IngressTrait());
        register(new ContainerTrait());
        register(new EnvTrait());
        register(new MountTrait());
        register(new OpenApiTrait());
        register(new LabelTrait());
        register(new AnnotationTrait());
        register(new CamelTrait());
    }

    public List<Trait> allTraits() {
        return traits.stream().sorted().collect(Collectors.toList());
    }

    public List<Trait> traitsForProfile(TraitProfile profile) {
        return traits.stream().filter(t -> t.accept(profile)).sorted().collect(Collectors.toList());
    }

    public void register(Trait trait) {
        traits.add(trait);
    }

    /**
     * Applies traits in this catalog for given profile or all traits if profile is not set.
     *
     * @param traitsSpec   the trait configuration spec.
     * @param context      the trait context.
     * @param traitProfile the optional trait profile to select traits.
     * @param runtimeType  the runtime.
     */
    public void apply(Traits traitsSpec, TraitContext context, String traitProfile, RuntimeType runtimeType) {
        if (traitProfile != null) {
            new TraitCatalog().traitsForProfile(TraitProfile.valueOf(traitProfile.toUpperCase(Locale.US))).forEach(t -> {
                if (t.configure(traitsSpec, context)) {
                    t.apply(traitsSpec, context);
                    t.applyRuntimeSpecificProperties(traitsSpec, context, runtimeType);
                }
            });
        } else {
            new TraitCatalog().allTraits().forEach(t -> {
                if (t.configure(traitsSpec, context)) {
                    t.apply(traitsSpec, context);
                    t.applyRuntimeSpecificProperties(traitsSpec, context, runtimeType);
                }
            });
        }
    }
}
