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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.camel.AfterPropertiesConfigured;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.RouteAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.component.rest.postman.model.PostmanKeyValue;
import org.apache.camel.component.rest.postman.model.PostmanResponse;
import org.apache.camel.component.rest.postman.support.PostmanRequestBinding;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.support.processor.RestBindingAdvice;
import org.apache.camel.support.processor.RestBindingAdviceFactory;
import org.apache.camel.support.processor.RestBindingConfiguration;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.json.JsonObject;

/**
 * Routes incoming HTTP requests to the route that implements the matching request of a Postman collection.
 */
public class RestPostmanProcessor extends AsyncProcessorSupport implements CamelContextAware, AfterPropertiesConfigured {

    private static final List<String> METHODS = Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH");

    private CamelContext camelContext;
    private final List<PostmanRequestBinding> bindings;
    private final JsonObject redactedDocument;
    private final String collectionSource;
    private final String basePath;
    private final String apiContextPath;
    private final boolean clientRequestValidation;
    private final RestPostmanProcessorStrategy strategy;
    private final List<RestConsumerContextPathMatcher.ConsumerPath<PostmanRequestBinding>> paths = new ArrayList<>();
    private PlatformHttpConsumerAware platformHttpConsumer;
    private Consumer consumer;
    private RestRegistry restRegistry;

    public RestPostmanProcessor(List<PostmanRequestBinding> bindings, JsonObject redactedDocument,
                                String collectionSource, String basePath, String apiContextPath,
                                boolean clientRequestValidation, RestPostmanProcessorStrategy strategy) {
        this.bindings = List.copyOf(bindings);
        this.redactedDocument = redactedDocument;
        this.collectionSource = collectionSource;
        this.basePath = basePath;
        this.apiContextPath
                = apiContextPath != null && !apiContextPath.startsWith("/") ? "/" + apiContextPath : apiContextPath;
        this.clientRequestValidation = clientRequestValidation;
        this.strategy = strategy;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        String path = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        if (path != null && path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }
        String verb = exchange.getMessage().getHeader(Exchange.HTTP_METHOD, String.class);

        RestConsumerContextPathMatcher.ConsumerPath<PostmanRequestBinding> match
                = RestConsumerContextPathMatcher.matchBestPath(verb, path, paths);
        if (match instanceof RestPostmanConsumerPath rcp) {
            PostmanRequestBinding binding = rcp.getConsumer();
            String consumerPath = rcp.getConsumerPath();
            if (consumerPath.startsWith("/") && path != null && !path.startsWith("/")) {
                consumerPath = consumerPath.substring(1);
            }

            // turn the {name} markers of the matched template into message headers
            HttpHelper.evalPlaceholders(exchange.getMessage().getHeaders(), path, consumerPath);

            if (restRegistry != null) {
                restRegistry.hit(verb, basePath, consumerPath);
            }
            return strategy.process(binding, rcp.getDispatchId(), verb, path, rcp.getBinding(), exchange, callback);
        }

        if (path != null && path.equals(apiContextPath)) {
            return strategy.processCollectionDocument(redactedDocument, exchange, callback);
        }

        // neither a known request nor the api context path: distinguish "no such path" from "wrong method"
        final String contextPath = path;
        List<String> allow = METHODS.stream()
                .filter(v -> RestConsumerContextPathMatcher.matchBestPath(v, contextPath, paths) != null).toList();
        if (allow.isEmpty()) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
        } else {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 405);
            exchange.getMessage().setHeader("Allow", String.join(", ", allow));
        }
        exchange.setRouteStop(true);
        callback.done(true);
        return true;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        CamelContextAware.trySetCamelContext(strategy, getCamelContext());
    }

    @Override
    public void afterPropertiesConfigured(CamelContext camelContext) {
        this.restRegistry = PluginHelper.getRestRegistry(camelContext);

        String routeId = consumer instanceof RouteAware ra ? ra.getRoute().getRouteId() : null;

        for (PostmanRequestBinding binding : bindings) {
            RestBindingConfiguration bc = createRestBindingConfiguration(binding);

            String url = basePath + binding.uriTemplate();
            if (platformHttpConsumer != null) {
                url = platformHttpConsumer.getPlatformHttpConsumer().getEndpoint().getServiceUrl() + url;
            }
            restRegistry.addRestService(consumer, true, url, binding.uriTemplate(), basePath, null,
                    binding.method(), bc.getConsumes(), bc.getProduces(), null, null, routeId,
                    binding.id(), collectionSource, binding.item().getRequest().getDescription());

            try {
                RestBindingAdvice advice = RestBindingAdviceFactory.build(camelContext, bc);
                ServiceHelper.buildService(advice);
                paths.add(new RestPostmanConsumerPath(
                        binding.method(), binding.uriTemplate(), binding, advice,
                        strategy.resolveDispatchId(binding)));
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        if (apiContextPath != null) {
            restRegistry.addRestSpecification(consumer, true, basePath + apiContextPath, apiContextPath, basePath,
                    "GET", "application/json", null);
        }

        for (var p : paths) {
            if (p instanceof RestPostmanConsumerPath rcp) {
                ServiceHelper.startService(rcp.getBinding());
            }
        }

        ServiceHelper.initService(strategy);
        try {
            strategy.validateCollection(bindings, basePath, platformHttpConsumer);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        ServiceHelper.startService(strategy);
    }

    /**
     * Builds the binding configuration for one request.
     * <p>
     * A Postman collection carries no schemas, so unlike the OpenAPI equivalent there are no Java types to bind to and
     * {@code type}/{@code outType} are deliberately left unset. What remains is a best-effort notion of which headers,
     * query parameters and body are required, inferred from what the collection actually declares.
     */
    private RestBindingConfiguration createRestBindingConfiguration(PostmanRequestBinding binding) {
        RestConfiguration config = camelContext.getRestConfiguration();

        RestBindingConfiguration bc = new RestBindingConfiguration();
        bc.setBindingMode(config.getBindingMode().name());
        bc.setEnableCORS(config.isEnableCORS());
        bc.setCorsHeaders(config.getCorsHeaders());
        bc.setClientRequestValidation(config.isClientRequestValidation() || clientRequestValidation);
        bc.setEnableNoContentResponse(config.isEnableNoContentResponse());
        bc.setSkipBindingOnErrorCode(config.isSkipBindingOnErrorCode());
        bc.setConsumes(binding.produces());
        bc.setProduces(producesOf(binding));
        bc.setRequiredBody(binding.collectionBody() != null
                && DefaultRestPostmanProcessorStrategy.expectsBody(binding.method()));
        bc.setRequiredQueryParameters(requiredQueryParameters(binding));
        bc.setRequiredHeaders(requiredHeaders(binding));
        bc.setQueryDefaultValues(queryDefaultValues(binding));
        return bc;
    }

    /**
     * What the server would return, taken from the first saved example, since the collection describes no schemas.
     */
    private static String producesOf(PostmanRequestBinding binding) {
        return binding.item().getSavedResponses().stream()
                .map(PostmanResponse::getContentType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Set<String> requiredQueryParameters(PostmanRequestBinding binding) {
        // Postman has no "required" flag, so a parameter the author bothered to give a value to is the closest signal
        Set<String> answer = new LinkedHashSet<>();
        for (PostmanKeyValue param : binding.item().getRequest().getUrl().getQueryParams()) {
            if (param.hasValue()) {
                answer.add(param.key());
            }
        }
        return answer.isEmpty() ? null : answer;
    }

    private static Set<String> requiredHeaders(PostmanRequestBinding binding) {
        Set<String> answer = new LinkedHashSet<>();
        for (PostmanKeyValue header : binding.item().getRequest().getHeaders()) {
            if (header.disabled()) {
                continue;
            }
            String key = header.key();
            // Content-Type and Accept are negotiated rather than required, and an auth header is supplied by the
            // caller's own credentials rather than by the collection
            if ("Content-Type".equalsIgnoreCase(key) || "Accept".equalsIgnoreCase(key)
                    || "Authorization".equalsIgnoreCase(key) || "Host".equalsIgnoreCase(key)) {
                continue;
            }
            answer.add(key);
        }
        return answer.isEmpty() ? null : answer;
    }

    private static Map<String, String> queryDefaultValues(PostmanRequestBinding binding) {
        Map<String, String> answer = new LinkedHashMap<>();
        for (PostmanKeyValue param : binding.item().getRequest().getUrl().getQueryParams()) {
            if (param.hasValue()) {
                answer.put(param.key(), param.value());
            }
        }
        return answer.isEmpty() ? null : answer;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(strategy);
        for (var p : paths) {
            if (p instanceof RestPostmanConsumerPath rcp) {
                ServiceHelper.stopService(rcp.getBinding());
            }
        }
        paths.clear();
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public PlatformHttpConsumerAware getPlatformHttpConsumer() {
        return platformHttpConsumer;
    }

    public void setPlatformHttpConsumer(PlatformHttpConsumerAware platformHttpConsumer) {
        this.platformHttpConsumer = platformHttpConsumer;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }
}
