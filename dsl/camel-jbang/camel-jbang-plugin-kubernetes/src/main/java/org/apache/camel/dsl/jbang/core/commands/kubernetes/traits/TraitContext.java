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
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import io.fabric8.knative.eventing.v1.TriggerBuilder;
import io.fabric8.knative.messaging.v1.SubscriptionBuilder;
import io.fabric8.kubernetes.api.builder.Builder;
import io.fabric8.kubernetes.api.builder.VisitableBuilder;
import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.openshift.api.model.RouteBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.CatalogHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.MetadataHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.SourceMetadata;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.Source;

public class TraitContext {

    private final List<VisitableBuilder<?, ?>> resourceRegistry = new ArrayList<>();
    private TraitProfile profile = TraitProfile.KUBERNETES;

    private final Map<String, String> configurationResources = new HashMap<>();

    private final Map<String, SourceMetadata> sourceMetadata = new HashMap<>();

    private final Map<String, String> annotations = new HashMap<>();
    private final Map<String, String> labels = new HashMap<>();

    private final String name;
    private final String version;

    private String serviceAccount;

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

    public void doWithServices(Visitor<ServiceBuilder> visitor) {
        resourceRegistry.forEach(r -> r.accept(ServiceBuilder.class, visitor));
    }

    public void doWithKnativeServices(Visitor<io.fabric8.knative.serving.v1.ServiceBuilder> visitor) {
        resourceRegistry.forEach(r -> r.accept(io.fabric8.knative.serving.v1.ServiceBuilder.class, visitor));
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

    public Optional<IngressBuilder> getIngress() {
        return resourceRegistry.stream()
                .filter(it -> it.getClass().isAssignableFrom(IngressBuilder.class))
                .map(it -> (IngressBuilder) it)
                .findFirst();
    }

    public Optional<RouteBuilder> getRoute() {
        return resourceRegistry.stream()
                .filter(it -> it.getClass().isAssignableFrom(RouteBuilder.class))
                .map(it -> (RouteBuilder) it)
                .findFirst();
    }

    public Optional<io.fabric8.knative.serving.v1.ServiceBuilder> getKnativeService() {
        return resourceRegistry.stream()
                .filter(it -> it.getClass().isAssignableFrom(io.fabric8.knative.serving.v1.ServiceBuilder.class))
                .map(it -> (io.fabric8.knative.serving.v1.ServiceBuilder) it)
                .findFirst();
    }

    public Optional<TriggerBuilder> getKnativeTrigger(String triggerName, String brokerName) {
        return resourceRegistry.stream()
                .filter(it -> it.getClass().isAssignableFrom(TriggerBuilder.class))
                .map(it -> (TriggerBuilder) it)
                .filter(trigger -> triggerName.equals(trigger.buildMetadata().getName()))
                .filter(trigger -> brokerName.equals(trigger.buildSpec().getBroker()))
                .findFirst();
    }

    public Optional<SubscriptionBuilder> getKnativeSubscription(String name) {
        return resourceRegistry.stream()
                .filter(it -> it.getClass().isAssignableFrom(SubscriptionBuilder.class))
                .map(it -> (SubscriptionBuilder) it)
                .filter(subscription -> name.equals(subscription.buildMetadata().getName()))
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

    public void setServiceAccount(String serviceAccount) {
        this.serviceAccount = serviceAccount;
    }

    public String getServiceAccount() {
        return serviceAccount;
    }

    /**
     * Performs source metadata inspection and uses local cache to not inspect the same source over and over again.
     *
     * @param  source    the source to inspect and create the metadata from.
     * @return           the metadata holding information such as components, endpoints, languages used with the source.
     * @throws Exception
     */
    public SourceMetadata inspectMetaData(Source source) throws Exception {
        if (sourceMetadata.containsKey(source.name())) {
            return sourceMetadata.get(source.name());
        }

        SourceMetadata metadata = MetadataHelper.readFromSource(catalog, source);
        sourceMetadata.put(source.name(), metadata);
        return metadata;
    }

    /**
     * Inspect all sources in this context and retrieve the source metadata. Uses internal cache for sources that have
     * already been inspected.
     *
     * @return
     */
    public List<SourceMetadata> getSourceMetadata() {
        List<SourceMetadata> answer = new ArrayList<>();
        if (sources != null) {
            for (Source source : sources) {
                answer.add(sourceMetadata.computeIfAbsent(source.name(), name -> {
                    try {
                        return MetadataHelper.readFromSource(catalog, source);
                    } catch (Exception e) {
                        throw new RuntimeCamelException(e);
                    }
                }));
            }
        }

        return answer;
    }

    public void addConfigurationResource(String name, String content) {
        this.configurationResources.put(name, content);
    }

    public void addOrAppendConfigurationResource(String name, String content) {
        this.configurationResources.merge(name, content, (content1, content2) -> content1 + System.lineSeparator() + content2);
    }

    public void doWithConfigurationResources(BiConsumer<String, String> consumer) {
        configurationResources.forEach(consumer);
    }
}
