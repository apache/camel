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
package org.apache.camel.component.knative;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.cloudevents.CloudEvent;
import org.apache.camel.component.cloudevents.CloudEvents;
import org.apache.camel.component.knative.ce.CloudEventProcessor;
import org.apache.camel.component.knative.ce.CloudEventProcessors;
import org.apache.camel.component.knative.spi.Knative;
import org.apache.camel.component.knative.spi.KnativeResource;
import org.apache.camel.component.knative.spi.KnativeTransportConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send and receive events from Knative.
 */
@UriEndpoint(firstVersion = "3.15.0",
             scheme = "knative",
             syntax = "knative:type/typeId",
             title = "Knative",
             category = Category.CLOUD)
public class KnativeEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(KnativeEndpoint.class);

    private final CloudEvent cloudEvent;
    private final CloudEventProcessor cloudEventProcessor;

    @UriPath(description = "The Knative resource type")
    private final Knative.Type type;

    @UriPath(description = "The identifier of the Knative resource")
    private final String typeId;

    @UriParam
    private KnativeConfiguration configuration;

    public KnativeEndpoint(String uri, KnativeComponent component, Knative.Type type, String name,
                           KnativeConfiguration configuration) {
        super(uri, component);
        this.type = type;
        this.typeId = name;
        this.configuration = configuration;
        this.cloudEvent = CloudEvents.fromSpecVersion(configuration.getCloudEventsSpecVersion());
        this.cloudEventProcessor = CloudEventProcessors.fromSpecVersion(configuration.getCloudEventsSpecVersion());
    }

    @Override
    public KnativeComponent getComponent() {
        return (KnativeComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        KnativeResource service = lookupServiceDefinition(Knative.EndpointKind.sink);

        final Processor ceProcessor = cloudEventProcessor.producer(this, service);
        final Producer producer
                = getComponent().getOrCreateProducerFactory().createProducer(this, createTransportConfiguration(service),
                        service);

        PropertyBindingSupport.build()
                .withCamelContext(getCamelContext())
                .withProperties(configuration.getTransportOptions())
                .withRemoveParameters(false)
                .withMandatory(false)
                .withTarget(producer)
                .bind();

        return new KnativeProducer(this, ceProcessor, e -> e.getMessage().removeHeader("Host"), producer);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        KnativeResource service = lookupServiceDefinition(Knative.EndpointKind.source);

        Processor ceProcessor = cloudEventProcessor.consumer(this, service);
        Processor replyProcessor
                = configuration.isReplyWithCloudEvent() ? cloudEventProcessor.producer(this, service) : null;

        List<Processor> list = new ArrayList<>();
        list.add(ceProcessor);
        list.add(processor);
        if (replyProcessor != null) {
            list.add(replyProcessor);
        }
        CamelContext camelContext = getCamelContext();
        Processor pipeline
                = PluginHelper.getProcessorFactory(camelContext).createProcessor(camelContext, "Pipeline",
                        new Object[] { list });

        Consumer consumer = getComponent().getOrCreateConsumerFactory().createConsumer(this,
                createTransportConfiguration(service), service, pipeline);

        // signal that this path is exposed for knative
        String path = service.getPath();

        PropertyBindingSupport.build()
                .withCamelContext(camelContext)
                .withProperties(configuration.getTransportOptions())
                .withRemoveParameters(false)
                .withMandatory(false)
                .withTarget(consumer)
                .bind();

        configureConsumer(consumer);
        return consumer;
    }

    public Knative.Type getType() {
        return type;
    }

    public String getTypeId() {
        return typeId;
    }

    public CloudEvent getCloudEvent() {
        return cloudEvent;
    }

    public KnativeConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(KnativeConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doInit() {
        if (ObjectHelper.isEmpty(this.configuration.getTypeId())) {
            this.configuration.setTypeId(this.typeId);
        }
    }

    KnativeResource lookupServiceDefinition(Knative.EndpointKind endpointKind) {
        final String resourceName;
        if (type == Knative.Type.event && configuration.getName() != null && endpointKind.equals(Knative.EndpointKind.sink)) {
            resourceName = configuration.getName();
        } else if (configuration.getTypeId() != null) {
            resourceName = configuration.getTypeId();
        } else {
            // in case there is no name in the configuration or type
            resourceName = "default";
        }

        KnativeResource resource = lookupServiceDefinition(resourceName, endpointKind)
                .or(() -> {
                    LOG.debug("Knative resource \"{}\" of type \"{}\" not found, trying the default named: \"default\"",
                            resourceName, type);
                    return lookupServiceDefinition("default", endpointKind);
                })
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Unable to find a resource definition for %s/%s/%s", type, endpointKind, resourceName)));

        //
        // We need to create a new resource as we need to inject additional data from the component
        // configuration.
        //
        KnativeResource answer = KnativeResource.from(resource);

        //
        // Set-up filters from config
        //
        for (Map.Entry<String, String> entry : configuration.getFilters().entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();

            if (key.startsWith(Knative.KNATIVE_FILTER_PREFIX)) {
                key = key.substring(Knative.KNATIVE_FILTER_PREFIX.length());
            }

            answer.addFilter(key, val);
        }

        //
        // Set-up overrides from config
        //
        for (Map.Entry<String, String> entry : configuration.getCeOverride().entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();

            if (key.startsWith(Knative.KNATIVE_CE_OVERRIDE_PREFIX)) {
                key = key.substring(Knative.KNATIVE_CE_OVERRIDE_PREFIX.length());
            }

            answer.addCeOverride(key, val);
        }

        //
        // For event type endpoints se need to add a filter to filter out events received
        // based on the given type.
        //
        if (resource.getType() == Knative.Type.event && ObjectHelper.isNotEmpty(configuration.getTypeId())) {
            answer.setCloudEventType(configuration.getTypeId());
            answer.addFilter(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, configuration.getTypeId());
        }

        return answer;
    }

    Optional<KnativeResource> lookupServiceDefinition(String name, Knative.EndpointKind endpointKind) {
        return servicesDefinitions()
                .filter(definition -> definition.matches(this.type, name))
                .filter(serviceFilter(this.configuration, endpointKind))
                .findFirst();
    }

    private KnativeTransportConfiguration createTransportConfiguration(KnativeResource definition) {
        return new KnativeTransportConfiguration(
                this.cloudEventProcessor.cloudEvent(),
                !this.configuration.isReplyWithCloudEvent(),
                ObjectHelper.supplyIfEmpty(this.configuration.getReply(), definition::getReply));
    }

    private Stream<KnativeResource> servicesDefinitions() {
        return Stream.concat(
                getCamelContext().getRegistry().findByType(KnativeResource.class).stream(),
                this.configuration.getEnvironment().stream());
    }

    private static Predicate<KnativeResource> serviceFilter(
            KnativeConfiguration configuration, Knative.EndpointKind endpointKind) {
        return resource -> {
            if (!Objects.equals(endpointKind, resource.getEndpointKind())) {
                return false;
            }
            if (configuration.getApiVersion() != null
                    && !Objects.equals(resource.getObjectApiVersion(), configuration.getApiVersion())) {
                return false;
            }
            if (configuration.getKind() != null && !Objects.equals(resource.getObjectKind(), configuration.getKind())) {
                return false;
            }
            return configuration.getName() == null || Objects.equals(resource.getObjectName(), configuration.getName());
        };
    }
}
