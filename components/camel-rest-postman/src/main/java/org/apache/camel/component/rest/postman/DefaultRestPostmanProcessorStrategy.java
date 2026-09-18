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
package org.apache.camel.component.rest.postman;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.component.rest.postman.model.PostmanKeyValue;
import org.apache.camel.component.rest.postman.model.PostmanResponse;
import org.apache.camel.component.rest.postman.support.PostmanRequestBinding;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.cache.DefaultProducerCache;
import org.apache.camel.support.processor.RestBindingAdvice;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link RestPostmanProcessorStrategy}, which links each request of the collection to a route consuming from
 * {@code direct:<requestId>}.
 */
public class DefaultRestPostmanProcessorStrategy extends ServiceSupport
        implements RestPostmanProcessorStrategy, CamelContextAware, NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRestPostmanProcessorStrategy.class);

    private static final String BODY_VERBS = "DELETE,PUT,POST,PATCH";

    private CamelContext camelContext;
    private ProducerCache producerCache;
    private String component = "direct";
    private String missingRequest;
    private String mockIncludePattern;
    private final List<String> uris = new ArrayList<>();

    @Override
    public String resolveDispatchId(PostmanRequestBinding binding) {
        Set<String> existing = existingDirectEndpoints();
        // more than one spelling is accepted so that both an exported collection, routed by slug, and a
        // cloud-fetched one, routed by request id, work without the author having to know which they have
        for (String candidate : candidateIds(binding)) {
            if (existing.contains(component + "://" + candidate)) {
                return candidate;
            }
        }
        return binding.id();
    }

    private static List<String> candidateIds(PostmanRequestBinding binding) {
        List<String> candidates = new ArrayList<>(3);
        candidates.add(binding.id());
        String qualified = binding.item().getQualifiedSlug();
        if (!candidates.contains(qualified)) {
            candidates.add(qualified);
        }
        String id = binding.item().getId();
        if (id != null && !candidates.contains(id)) {
            candidates.add(id);
        }
        return candidates;
    }

    /**
     * The base URIs of every route currently in the context.
     * <p>
     * Comparing base URIs is used rather than {@code hasEndpoint} because looking an endpoint up would create it.
     */
    private Set<String> existingDirectEndpoints() {
        Set<String> answer = new LinkedHashSet<>();
        for (Route route : camelContext.getRoutes()) {
            answer.add(route.getEndpoint().getEndpointBaseUri());
        }
        return answer;
    }

    @Override
    public void validateCollection(
            List<PostmanRequestBinding> bindings, String basePath, PlatformHttpConsumerAware platformHttpConsumer)
            throws Exception {

        failOnShadowedRequests(bindings);

        Set<String> existing = existingDirectEndpoints();
        List<String> missing = new ArrayList<>();
        for (PostmanRequestBinding binding : bindings) {
            boolean found = candidateIds(binding).stream()
                    .anyMatch(candidate -> existing.contains(component + "://" + candidate));
            if (!found) {
                missing.add(component + ":" + binding.id());
            }
        }

        if (!missing.isEmpty()) {
            String message = String.format(
                    "Postman collection has %d request(s) not mapped to a corresponding route:%n\t%s",
                    missing.size(), String.join("\n\t", missing.stream().sorted().toList()));
            if ("fail".equalsIgnoreCase(missingRequest)) {
                throw new IllegalArgumentException(message);
            } else if ("ignore".equalsIgnoreCase(missingRequest)) {
                LOG.warn("{}\nThis validation error is ignored.", message);
            } else {
                LOG.debug("{}\nThis validation error is ignored (a mocked response will be returned).", message);
            }
        }

        registerHttpEndpoints(bindings, basePath, platformHttpConsumer);
    }

    /**
     * Rejects a collection where two requests share a verb and path.
     * <p>
     * This is common in real collections, which often keep a success and an error variant of the same call. The matcher
     * would pick one of them non-deterministically, so failing loudly at startup is better than silently shadowing a
     * route.
     */
    private void failOnShadowedRequests(List<PostmanRequestBinding> bindings) {
        Map<String, List<PostmanRequestBinding>> byRoute = new LinkedHashMap<>();
        for (PostmanRequestBinding binding : bindings) {
            byRoute.computeIfAbsent(binding.method() + " " + binding.fullPath(), k -> new ArrayList<>()).add(binding);
        }
        List<String> clashes = byRoute.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " is declared by "
                          + e.getValue().stream().map(PostmanRequestBinding::id).collect(Collectors.joining(", ")))
                .toList();
        if (!clashes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Postman collection has requests that would shadow each other because they share an HTTP method"
                                               + " and path:\n\t" + String.join("\n\t", clashes)
                                               + "\nUse the requestFilter option to select which of them to serve.");
        }
    }

    private void registerHttpEndpoints(
            List<PostmanRequestBinding> bindings, String basePath, PlatformHttpConsumerAware platformHttpConsumer) {
        if (platformHttpConsumer == null) {
            return;
        }
        // hasComponent rather than getComponent: looking it up would auto-create the component, which fails
        // outside a runtime that provides an HTTP engine
        if (!(camelContext.hasComponent("platform-http") instanceof PlatformHttpComponent phc)) {
            return;
        }
        String prefix = basePath == null || basePath.isEmpty() || "/".equals(basePath) ? "" : basePath;

        Map<String, Set<String>> verbsByUri = new LinkedHashMap<>();
        Map<String, String> consumesByUri = new LinkedHashMap<>();
        Map<String, String> producesByUri = new LinkedHashMap<>();
        for (PostmanRequestBinding binding : bindings) {
            String uri = prefix + binding.uriTemplate();
            verbsByUri.computeIfAbsent(uri, k -> new LinkedHashSet<>()).add(binding.method());
            if (binding.produces() != null) {
                consumesByUri.putIfAbsent(uri, binding.produces());
            }
            if (binding.consumes() != null) {
                producesByUri.putIfAbsent(uri, binding.consumes());
            }
        }
        verbsByUri.forEach((uri, verbs) -> {
            phc.addHttpEndpoint(uri, String.join(",", verbs.stream().sorted().toList()),
                    consumesByUri.get(uri), producesByUri.get(uri), platformHttpConsumer.getPlatformHttpConsumer());
            uris.add(uri);
        });
    }

    @Override
    public boolean process(
            PostmanRequestBinding binding, String dispatchId, String verb, String path,
            RestBindingAdvice advice, Exchange exchange, AsyncCallback callback) {

        exchange.getMessage().setHeader(RestPostmanConstants.REQUEST_ID, binding.id());
        exchange.getMessage().setHeader(RestPostmanConstants.REQUEST_NAME, binding.item().getName());

        if ("mock".equalsIgnoreCase(missingRequest) || "ignore".equalsIgnoreCase(missingRequest)) {
            Endpoint existing = camelContext.hasEndpoint(component + ":" + dispatchId);
            if (existing == null) {
                try {
                    var requestError = advice.doClientRequestValidation(exchange);
                    if (requestError != null) {
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, requestError.statusCode());
                        exchange.getMessage().setBody(requestError.body());
                        exchange.setRouteStop(true);
                    } else if ("mock".equalsIgnoreCase(missingRequest)) {
                        loadMockData(binding, path, exchange);
                    }
                } catch (Exception e) {
                    exchange.setException(e);
                }
                callback.done(true);
                return true;
            }
        }

        Map<String, Object> state;
        try {
            state = advice.before(exchange);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        final Endpoint endpoint = camelContext.getEndpoint(component + ":" + dispatchId);
        final AsyncProducer producer = producerCache.acquireProducer(endpoint);
        return producer.process(exchange, doneSync -> {
            try {
                producerCache.releaseProducer(endpoint, producer);
                advice.after(exchange, state);
            } catch (Exception e) {
                exchange.setException(e);
            } finally {
                callback.done(doneSync);
            }
        });
    }

    /**
     * Produces a mock response.
     * <p>
     * A saved example in the collection is preferred over a file on disk, because it is a real recorded response for
     * exactly this request and needs no naming convention to find.
     */
    private void loadMockData(PostmanRequestBinding binding, String path, Exchange exchange) {
        PostmanResponse example = pickSavedResponse(binding, exchange);
        if (example != null) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, example.getCode());
            for (PostmanKeyValue header : example.getHeaders()) {
                if (!header.disabled()) {
                    exchange.getMessage().setHeader(header.key(), header.value());
                }
            }
            exchange.getMessage().setBody(example.getBody());
            return;
        }
        loadMockDataFromFiles(path, exchange);
    }

    /**
     * Chooses the saved example that best matches what the caller asked for: a successful one, preferring a content
     * type the caller said it accepts.
     */
    private static PostmanResponse pickSavedResponse(PostmanRequestBinding binding, Exchange exchange) {
        List<PostmanResponse> saved = binding.item().getSavedResponses().stream()
                .filter(PostmanResponse::isSuccess)
                .toList();
        if (saved.isEmpty()) {
            return null;
        }
        String accept = exchange.getMessage().getHeader("Accept", String.class);
        if (accept != null) {
            for (PostmanResponse candidate : saved) {
                String contentType = candidate.getContentType();
                if (contentType != null && accept.contains(stripParameters(contentType))) {
                    return candidate;
                }
            }
        }
        return saved.get(0);
    }

    private static String stripParameters(String contentType) {
        int semicolon = contentType.indexOf(';');
        return semicolon > 0 ? contentType.substring(0, semicolon).trim() : contentType.trim();
    }

    private void loadMockDataFromFiles(String path, Exchange exchange) {
        final PackageScanResourceResolver resolver = PluginHelper.getPackageScanResourceResolver(camelContext);
        final String[] includes = mockIncludePattern != null ? mockIncludePattern.split(",") : null;
        if (includes == null) {
            return;
        }

        Collection<Resource> accepted = new ArrayList<>();
        for (String include : includes) {
            try {
                accepted.addAll(resolver.findResources(include));
            } catch (Exception e) {
                LOG.trace("Mock data directory {} cannot be scanned", include, e);
            }
        }

        String contentType = ExchangeHelper.getContentType(exchange);
        boolean json = contentType != null && contentType.contains("json");
        boolean xml = contentType != null && contentType.contains("xml");

        String target = FileUtil.stripFirstLeadingSeparator(path);
        for (Resource resource : accepted) {
            String location = FileUtil.stripExt(FileUtil.compactPath(resource.getLocation(), '/'));
            String extension = FileUtil.onlyExt(resource.getLocation());
            boolean matchExt = !json && !xml
                    || json && "json".equals(extension)
                    || xml && "xml".equals(extension);
            if (location.endsWith(target) && matchExt) {
                try (InputStream is = resource.getInputStream()) {
                    exchange.getMessage().setBody(IOHelper.loadText(is));
                    if ("json".equals(extension)) {
                        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                    } else if ("xml".equals(extension)) {
                        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/xml");
                    }
                    return;
                } catch (Exception e) {
                    exchange.setException(e);
                    return;
                }
            }
        }
    }

    @Override
    public boolean processCollectionDocument(JsonObject redactedDocument, Exchange exchange, AsyncCallback callback) {
        try {
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
            exchange.getMessage().setBody(redactedDocument.toJson());
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    /**
     * Whether the verb normally carries a request body, used to decide if one should be required.
     */
    static boolean expectsBody(String verb) {
        return BODY_VERBS.contains(verb.toUpperCase(Locale.ROOT));
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getMissingRequest() {
        return missingRequest;
    }

    @Override
    public void setMissingRequest(String missingRequest) {
        this.missingRequest = missingRequest;
    }

    @Override
    public String getMockIncludePattern() {
        return mockIncludePattern;
    }

    @Override
    public void setMockIncludePattern(String mockIncludePattern) {
        this.mockIncludePattern = mockIncludePattern;
    }

    @Override
    protected void doInit() throws Exception {
        producerCache = new DefaultProducerCache(this, getCamelContext(), 1000);
        ServiceHelper.initService(producerCache);

        if (missingRequest == null) {
            // in developer mode an unmapped request is far more likely to be work in progress than a mistake
            boolean dev = "dev".equalsIgnoreCase(camelContext.getCamelContextExtension().getProfile());
            missingRequest = dev ? "mock" : "fail";
        }
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(producerCache);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);

        if (camelContext != null) {
            PlatformHttpComponent phc = (PlatformHttpComponent) camelContext.hasComponent("platform-http");
            if (phc != null) {
                uris.forEach(phc::removeHttpEndpoint);
                uris.clear();
            }
        }
    }
}
