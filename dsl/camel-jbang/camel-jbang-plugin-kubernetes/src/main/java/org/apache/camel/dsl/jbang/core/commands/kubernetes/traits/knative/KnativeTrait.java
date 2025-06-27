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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.knative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.knative.duck.v1.DestinationBuilder;
import io.fabric8.knative.duck.v1.KReference;
import io.fabric8.knative.duck.v1.KReferenceBuilder;
import io.fabric8.knative.eventing.v1.TriggerBuilder;
import io.fabric8.knative.eventing.v1.TriggerFilterBuilder;
import io.fabric8.knative.messaging.v1.SubscriptionBuilder;
import io.fabric8.knative.pkg.tracker.ReferenceBuilder;
import io.fabric8.knative.sources.v1.SinkBindingBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesHelper;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.support.SourceMetadata;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.ServiceTrait;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.TraitContext;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Camel;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Knative;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model.Traits;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

public class KnativeTrait extends KnativeBaseTrait {

    private static final Pattern knativeUriPattern
            = Pattern.compile("^knative:/*(channel|endpoint|event)(?:[?].*|$|/([A-Za-z0-9.-]+)(?:[/?].*|$))");
    private static final Pattern plainNamePattern = Pattern.compile("^[A-Za-z0-9.-]+$");
    private static final String K_SINK_URL = "{{k.sink:http://localhost:8080}}";

    private final List<Map<String, Object>> knativeResourcesConfig = new ArrayList<>();

    public KnativeTrait() {
        super("knative", ServiceTrait.SERVICE_TRAIT_ORDER + 100);
    }

    @Override
    public boolean configure(Traits traitConfig, TraitContext context) {
        // Knative trait needs to be explicitly enabled
        boolean enabled = false;
        if (traitConfig.getKnative() != null) {
            enabled = Optional.ofNullable(traitConfig.getKnative().getEnabled()).orElse(false);
        }
        if (!enabled) {
            return false;
        }
        Knative knativeTrait = Optional.ofNullable(traitConfig.getKnative()).orElseGet(Knative::new);
        List<SourceMetadata> allSourcesMetadata = context.getSourceMetadata();

        if (knativeTrait.getChannelSources() == null) {
            knativeTrait.setChannelSources(extractKnativeEndpointUris(allSourcesMetadata, KnativeResourceType.CHANNEL, "from"));
        }

        if (knativeTrait.getChannelSinks() == null) {
            knativeTrait.setChannelSinks(extractKnativeEndpointUris(allSourcesMetadata, KnativeResourceType.CHANNEL, "to"));
        }

        if (knativeTrait.getEndpointSources() == null) {
            knativeTrait
                    .setEndpointSources(extractKnativeEndpointUris(allSourcesMetadata, KnativeResourceType.ENDPOINT, "from"));
        }

        if (knativeTrait.getEndpointSinks() == null) {
            knativeTrait.setEndpointSinks(extractKnativeEndpointUris(allSourcesMetadata, KnativeResourceType.ENDPOINT, "to"));
        }

        if (knativeTrait.getEventSources() == null) {
            knativeTrait.setEventSources(extractKnativeEndpointUris(allSourcesMetadata, KnativeResourceType.EVENT, "from"));
        }

        if (knativeTrait.getEventSinks() == null) {
            knativeTrait.setEventSinks(extractKnativeEndpointUris(allSourcesMetadata, KnativeResourceType.EVENT, "to"));
        }

        boolean hasKnativeEndpoint = !knativeTrait.getChannelSources().isEmpty() ||
                !knativeTrait.getChannelSinks().isEmpty() ||
                !knativeTrait.getEndpointSources().isEmpty() ||
                !knativeTrait.getEndpointSinks().isEmpty() ||
                !knativeTrait.getEventSources().isEmpty() ||
                !knativeTrait.getEventSinks().isEmpty();

        if (knativeTrait.getSinkBinding() == null) {
            knativeTrait.setSinkBinding(shouldCreateSinkBinding(knativeTrait));
        }

        knativeTrait.setEnabled(hasKnativeEndpoint);
        traitConfig.setKnative(knativeTrait);

        return hasKnativeEndpoint;
    }

    @Override
    public void apply(Traits traitConfig, TraitContext context) {
        Knative knativeTrait = Optional.ofNullable(traitConfig.getKnative()).orElseGet(Knative::new);

        configureChannels(knativeTrait, context);
        configureEndpoints(knativeTrait);
        configureEvents(knativeTrait, context);

        if (knativeTrait.getSinkBinding() != null && knativeTrait.getSinkBinding()) {
            createSinkBinding(knativeTrait, context);
        }

        if (!knativeResourcesConfig.isEmpty()) {
            try {
                context.addConfigurationResource("knative.json", KubernetesHelper.json()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(Collections.singletonMap("resources", knativeResourcesConfig)));
                Camel camelTrait = Optional.ofNullable(traitConfig.getCamel()).orElseGet(Camel::new);
                if (camelTrait.getProperties() == null) {
                    camelTrait.setProperties(new ArrayList<>());
                }
                camelTrait.getProperties().add("camel.component.knative.environmentPath=classpath:knative.json");
                traitConfig.setCamel(camelTrait);
            } catch (JsonProcessingException e) {
                context.printer().printf("Failed to write knative.json environment configuration - %s%n", e.getMessage());
            }
        }
    }

    private void configureChannels(Knative knativeTrait, TraitContext context) {
        for (String uri : knativeTrait.getChannelSources()) {
            createSubscription(toKnativeUri(KnativeResourceType.CHANNEL, uri), context);
        }

        for (String uri : knativeTrait.getChannelSinks()) {
            String channelName = extractKnativeResource(uri);
            addKnativeResourceConfiguration(new KnativeResourceConfiguration(
                    channelName,
                    KnativeResourceType.CHANNEL,
                    "sink",
                    K_SINK_URL,
                    null,
                    channelName,
                    null));
        }
    }

    private void configureEndpoints(Knative knativeTrait) {
        for (String uri : knativeTrait.getEndpointSources()) {
            String endpointName = extractKnativeResource(uri);
            addKnativeResourceConfiguration(new KnativeResourceConfiguration(
                    endpointName,
                    KnativeResourceType.ENDPOINT,
                    "source",
                    null,
                    "/",
                    endpointName,
                    null));
        }

        for (String uri : knativeTrait.getEndpointSinks()) {
            String endpointName = extractKnativeResource(uri);
            addKnativeResourceConfiguration(new KnativeResourceConfiguration(
                    endpointName,
                    KnativeResourceType.ENDPOINT,
                    "sink",
                    K_SINK_URL,
                    null,
                    endpointName,
                    null));
        }
    }

    private void configureEvents(Knative knativeTrait, TraitContext context) {
        for (String uri : knativeTrait.getEventSources()) {
            createTrigger(toKnativeUri(KnativeResourceType.EVENT, uri), knativeTrait, context);
        }

        for (String uri : knativeTrait.getEventSinks()) {
            String eventType = extractKnativeResource(uri);
            String brokerName = extractBrokerName(uri);
            String serviceName = ObjectHelper.isNotEmpty(eventType) ? brokerName : "default";
            addKnativeResourceConfiguration(new KnativeResourceConfiguration(
                    serviceName,
                    KnativeResourceType.EVENT,
                    "sink",
                    K_SINK_URL,
                    null,
                    brokerName,
                    null));
        }
    }

    private void createSubscription(String uri, TraitContext context) {
        String channelName = extractKnativeResource(uri);

        String subscriptionName = createSubscriptionName(context.getName(), channelName);
        if (ObjectHelper.isEmpty(channelName) || context.getKnativeSubscription(subscriptionName).isPresent()) {
            // no channel name given or same subscription already exists
            return;
        }

        String servicePath = "/channels/%s".formatted(channelName);

        SubscriptionBuilder subscription = new SubscriptionBuilder()
                .withNewMetadata()
                .withName(subscriptionName)
                .endMetadata()
                .withNewSpec()
                .withChannel(new KReferenceBuilder()
                        .withApiVersion(KnativeResourceType.CHANNEL.getApiVersion())
                        .withKind(KnativeResourceType.CHANNEL.getKind())
                        .withName(channelName)
                        .build())
                .withSubscriber(new DestinationBuilder()
                        .withRef(getSubscriberRef(context))
                        .withUri(servicePath)
                        .build())
                .endSpec();

        context.add(subscription);

        addKnativeResourceConfiguration(new KnativeResourceConfiguration(
                channelName,
                KnativeResourceType.CHANNEL,
                "source",
                null,
                servicePath,
                channelName,
                null));
    }

    private void createTrigger(String uri, Knative knativeTrait, TraitContext context) {
        String eventType = extractKnativeResource(uri);
        String brokerName = extractBrokerName(uri);

        String triggerName = createTriggerName(context.getName(), brokerName, eventType);
        if (context.getKnativeTrigger(triggerName, brokerName).isPresent()) {
            // same trigger already exists
            return;
        }

        String servicePath = "/events/%s".formatted(eventType);

        TriggerBuilder trigger = new TriggerBuilder()
                .withNewMetadata()
                .withName(triggerName)
                .endMetadata()
                .withNewSpec()
                .withBroker(brokerName)
                .withSubscriber(new DestinationBuilder()
                        .withRef(getSubscriberRef(context))
                        .withUri(servicePath)
                        .build())
                .withFilter(new TriggerFilterBuilder().addToAttributes(getFilterAttributes(knativeTrait, eventType)).build())
                .endSpec();

        context.add(trigger);

        String serviceName = ObjectHelper.isNotEmpty(eventType) ? eventType : "default";
        addKnativeResourceConfiguration(new KnativeResourceConfiguration(
                serviceName,
                KnativeResourceType.EVENT,
                "source",
                null,
                servicePath,
                brokerName,
                null));
    }

    private Map<String, String> getFilterAttributes(Knative knativeTrait, String eventType) {
        Map<String, String> filterAttributes = new HashMap<>();

        if (knativeTrait.getFilters() != null) {
            for (String filterExpression : knativeTrait.getFilters()) {
                String[] keyValue = filterExpression.split("=", 2);
                if (keyValue.length != 2) {
                    throw new RuntimeCamelException(
                            "Invalid Knative trigger filter expression: %s".formatted(filterExpression));
                }
                filterAttributes.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }

        if (!filterAttributes.containsKey("type") && Optional.ofNullable(knativeTrait.getFilterEventType()).orElse(true)
                && ObjectHelper.isNotEmpty(eventType)) {
            // Apply default trigger filter attribute for the event type
            filterAttributes.put("type", eventType);
        }

        return filterAttributes;
    }

    /**
     * Adds service endpoint to the Knative component environment configuration.
     */
    private void addKnativeResourceConfiguration(KnativeResourceConfiguration configuration) {
        knativeResourcesConfig.add(configuration.toJsonMap());
    }

    /**
     * Create subscriber reference based on which service type has been set on the give context. Assumes that the either
     * Knative service or arbitrary service has already been set on the context due to trait execution ordering. If
     * Knative service is present use this service subscriber kind otherwise use arbitrary service subscriber reference.
     */
    private static KReference getSubscriberRef(TraitContext context) {
        if (context.getKnativeService().isPresent()) {
            return new KReferenceBuilder()
                    .withApiVersion(KnativeResourceType.ENDPOINT.getApiVersion())
                    .withKind(KnativeResourceType.ENDPOINT.getKind())
                    .withName(context.getName())
                    .build();
        } else {
            return new KReferenceBuilder()
                    .withApiVersion("v1")
                    .withKind("Service")
                    .withName(context.getName())
                    .build();
        }
    }

    private static String createTriggerName(String subscriberName, String brokerName, String eventType) {
        String nameSuffix = "";
        if (ObjectHelper.isNotEmpty(eventType)) {
            nameSuffix = "-%s".formatted(KubernetesHelper.sanitize(eventType));
        }

        return KubernetesHelper.sanitize(brokerName) + "-" + subscriberName + nameSuffix;
    }

    private void createSinkBinding(Knative knativeTrait, TraitContext context) {
        final KnativeResourceType resourceType;
        final String uri;
        final String resourceName;
        if (!knativeTrait.getChannelSinks().isEmpty()) {
            uri = knativeTrait.getChannelSinks().get(0);
            resourceType = KnativeResourceType.CHANNEL;
            resourceName = extractKnativeResource(uri);
        } else if (!knativeTrait.getEndpointSinks().isEmpty()) {
            uri = knativeTrait.getEndpointSinks().get(0);
            resourceType = KnativeResourceType.ENDPOINT;
            resourceName = extractKnativeResource(uri);
        } else if (!knativeTrait.getEventSinks().isEmpty()) {
            uri = knativeTrait.getEventSinks().get(0);
            resourceType = KnativeResourceType.EVENT;
            resourceName = extractBrokerName(uri);
        } else {
            context.printer().println("Failed to create sink binding!");
            return;
        }

        context.add(new SinkBindingBuilder()
                .withNewMetadata()
                .withName(context.getName())
                .withFinalizers("sinkbindings.sources.knative.dev")
                .endMetadata()
                .withNewSpec()
                .withSubject(new ReferenceBuilder()
                        .withApiVersion("apps/v1")
                        .withKind("Deployment")
                        .withName(context.getName())
                        .build())
                .withNewSink()
                .withRef(resourceType.getReference(resourceName))
                .endSink()
                .endSpec());
    }

    private static String createSubscriptionName(String subscriberName, String channelName) {
        return "%s-%s".formatted(channelName, subscriberName);
    }

    private static String extractBrokerName(String uri) {
        try {
            return URISupport.parseQuery(URISupport.extractQuery(uri)).getOrDefault("name", "default").toString();
        } catch (Exception e) {
            return "default";
        }
    }

    /**
     * Sink binding should be created only when a single Knative sink is being used.
     *
     * @param  knativeTrait holding the sinks for channels, endpoints and events.
     * @return              true when single Knative sink is used, otherwise false.
     */
    private static boolean shouldCreateSinkBinding(Knative knativeTrait) {
        return knativeTrait.getChannelSinks().size() + knativeTrait.getEndpointSinks().size()
               + knativeTrait.getEventSinks().size() == 1;
    }

    private static List<String> extractKnativeEndpointUris(
            List<SourceMetadata> metadata, KnativeResourceType resourceType, String endpointType) {
        return metadata.stream()
                .flatMap(m -> endpointType.equals("from") ? m.endpoints.from.stream() : m.endpoints.to.stream())
                .filter(uri -> isKnativeUri(resourceType, uri))
                .collect(Collectors.toList());
    }

    private static String extractKnativeResource(String uri) {
        Matcher uriMatch = knativeUriPattern.matcher(uri);
        if (uriMatch.matches()) {
            return Optional.ofNullable(uriMatch.group(2)).orElse("");
        }

        return "";
    }

    private static boolean isKnativeUri(KnativeResourceType resourceType, String uri) {
        Matcher uriMatcher = knativeUriPattern.matcher(uri);
        return uriMatcher.matches() && uriMatcher.group(1).equals(resourceType.getType());
    }

    /**
     * If applicable, converts plain service, channel or broker name to Knative component endpoint URI.
     */
    private static String toKnativeUri(KnativeResourceType resourceType, String uriOrName) {
        if (plainNamePattern.matcher(uriOrName).matches()) {
            return "knative://%s/%s".formatted(resourceType.getType(), uriOrName);
        }

        return uriOrName;
    }

    enum KnativeResourceType {
        CHANNEL("Channel", "messaging.knative.dev/v1"),
        ENDPOINT("Service", "serving.knative.dev/v1"),
        EVENT("Broker", "eventing.knative.dev/v1");

        private final String kind;
        private final String apiVersion;

        KnativeResourceType(String kind, String apiVersion) {
            this.kind = kind;
            this.apiVersion = apiVersion;
        }

        public String getKind() {
            return kind;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public String getType() {
            return this.name().toLowerCase(Locale.US);
        }

        public KReference getReference(String resourceName) {
            return new KReferenceBuilder()
                    .withApiVersion(apiVersion)
                    .withKind(kind)
                    .withName(resourceName)
                    .build();
        }
    }

    private record KnativeResourceConfiguration(String name, KnativeResourceType resourceType, String endpointKind, String url,
            String path, String objectName, Map<String, String> ceOverrides) {

        public Map<String, Object> toJsonMap() {
            Map<String, Object> json = new LinkedHashMap<>();

            json.put("name", name);
            json.put("type", resourceType.getType());
            json.put("endpointKind", endpointKind);

            if (ObjectHelper.isNotEmpty(url)) {
                json.put("url", url);
            }

            if (ObjectHelper.isNotEmpty(path)) {
                json.put("path", path);
            }

            json.put("objectApiVersion", resourceType.getApiVersion());
            json.put("objectKind", resourceType.getKind());
            json.put("objectName", objectName);

            if (ObjectHelper.isNotEmpty(ceOverrides)) {
                json.put("ceOverrides", ceOverrides);
            }

            // knative.reply is set to true in case of endpoint service as a source
            if (resourceType == KnativeResourceType.ENDPOINT && endpointKind.equals("source")) {
                json.put("reply", true);
            } else {
                json.put("reply", false);
            }

            return json;
        }
    }
}
