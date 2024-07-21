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
import java.util.Arrays;
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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.CatalogHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.Source;

public class TraitContext {

    private final List<VisitableBuilder<?, ?>> resourceRegistry = new ArrayList<>();
    private TraitProfile profile = TraitProfile.KUBERNETES;

    private final Map<String, String> annotations = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();

    private final String name;
    private final String version;

    private CamelCatalog catalog;

    private final Printer printer;

    private final List<Source> sources;

    public TraitContext(String name, String version, Printer printer, Source... sources) {
        this(name, version, printer, null, sources);
    }

    public TraitContext(String name, String version, Printer printer, List<Source> sources) {
        this(name, version, printer, null, sources);
    }

    public TraitContext(String name, String version, Printer printer, CamelCatalog catalog, Source... sources) {
        this(name, version, printer, catalog, Arrays.asList(sources));
    }

    public TraitContext(String name, String version, Printer printer, CamelCatalog catalog, List<Source> sources) {
        this.name = name;
        this.version = version;
        this.printer = printer;
        this.catalog = catalog;
        this.sources = sources;
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

    public CamelCatalog getCatalog() {
        if (catalog == null) {
            try {
                catalog = CatalogHelper.loadCatalog(RuntimeType.quarkus, RuntimeType.QUARKUS_VERSION);
            } catch (Exception e) {
                throw new RuntimeCamelException("Failed to create default Quarkus Camel catalog", e);
            }
        }

        return catalog;
    }

    public Printer printer() {
        return printer;
    }

    public Source[] getSources() {
        return sources.toArray(Source[]::new);
    }
}
