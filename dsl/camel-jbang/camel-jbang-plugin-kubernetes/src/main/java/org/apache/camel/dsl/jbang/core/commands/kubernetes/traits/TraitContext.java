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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.builder.Builder;
import io.fabric8.kubernetes.api.builder.VisitableBuilder;
import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;

public class TraitContext {

    private final List<VisitableBuilder<?, ?>> resourceRegistry = new ArrayList<>();
    private TraitProfile profile = TraitProfile.KUBERNETES;

    private final Map<String, String> annotations = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();

    private final String name;
    private final String version;

    public TraitContext(String name, String version) {
        this.name = name;
        this.version = version;
    }

    /**
     * Adds a resource that should be created as part of the Camel app.
     *
     * @param resource
     */
    public void add(VisitableBuilder<?, ?> resource) {
        resourceRegistry.add(resource);
    }

    public TraitProfile getProfile() {
        return profile;
    }

    public void setProfile(TraitProfile profile) {
        this.profile = profile;
    }

    /**
     * @param visitor
     */
    public void doWithServices(Visitor<ServiceBuilder> visitor) {
        resourceRegistry.forEach(r -> r.accept(ServiceBuilder.class, visitor));
    }

    public void doWithDeployments(Visitor<DeploymentBuilder> visitor) {
        resourceRegistry.forEach(r -> r.accept(DeploymentBuilder.class, visitor));
    }

    public void doWithCronJobs(Visitor<CronJobBuilder> visitor) {
        resourceRegistry.forEach(r -> r.accept(CronJobBuilder.class, visitor));
    }

    public Optional<DeploymentBuilder> getDeployment() {
        return resourceRegistry.stream()
                .filter(it -> it.getClass().isAssignableFrom(DeploymentBuilder.class))
                .map(it -> (DeploymentBuilder) it)
                .findFirst();
    }

    public Optional<ServiceBuilder> getService() {
        return resourceRegistry.stream()
                .filter(it -> it.getClass().isAssignableFrom(ServiceBuilder.class))
                .map(it -> (ServiceBuilder) it)
                .findFirst();
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public List<KubernetesResource> buildItems() {
        return this.resourceRegistry.stream().map(Builder::build).map(it -> (KubernetesResource) it)
                .collect(Collectors.toList());
    }

    public void addLabels(Map<String, String> labels) {
        this.labels.putAll(labels);
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void addAnnotations(Map<String, String> annotations) {
        this.annotations.putAll(annotations);
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }
}
