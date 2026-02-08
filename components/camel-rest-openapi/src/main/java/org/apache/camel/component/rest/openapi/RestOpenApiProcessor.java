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
package org.apache.camel.component.rest.openapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import org.apache.camel.*;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.support.processor.RestBindingAdvice;
import org.apache.camel.support.processor.RestBindingAdviceFactory;
import org.apache.camel.support.processor.RestBindingConfiguration;
import org.apache.camel.support.service.ServiceHelper;

public class RestOpenApiProcessor extends AsyncProcessorSupport implements CamelContextAware, AfterPropertiesConfigured {

    // just use the most common verbs
    private static final List<String> METHODS = Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH");

    private CamelContext camelContext;
    private final RestOpenApiEndpoint endpoint;
    private final OpenAPI openAPI;
    private final String basePath;
    private final String apiContextPath;
    private final List<RestConsumerContextPathMatcher.ConsumerPath<Operation>> paths = new ArrayList<>();
    private final RestOpenapiProcessorStrategy restOpenapiProcessorStrategy;
    private PlatformHttpConsumerAware platformHttpConsumer;
    private Consumer consumer;
    private OpenApiUtils openApiUtils;

    public RestOpenApiProcessor(RestOpenApiEndpoint endpoint, OpenAPI openAPI, String basePath, String apiContextPath,
                                RestOpenapiProcessorStrategy restOpenapiProcessorStrategy) {
        this.endpoint = endpoint;
        this.basePath = basePath;
        // ensure starts with leading slash
        this.apiContextPath = apiContextPath != null && !apiContextPath.startsWith("/") ? "/" + apiContextPath : apiContextPath;
        this.openAPI = openAPI;
        this.restOpenapiProcessorStrategy = restOpenapiProcessorStrategy;
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

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // use HTTP_URI as this works for all runtimes
        String path = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        if (path != null && path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }
        String verb = exchange.getMessage().getHeader(Exchange.HTTP_METHOD, String.class);

        RestConsumerContextPathMatcher.ConsumerPath<Operation> m
                = RestConsumerContextPathMatcher.matchBestPath(verb, path, paths);
        if (m instanceof RestOpenApiConsumerPath rcp) {
            Operation o = rcp.getConsumer();

            String consumerPath = rcp.getConsumerPath();

            //if uri is not starting with slash then remove the slash in the consumerPath from the openApi spec
            if (consumerPath.startsWith("/") && path != null && !path.startsWith("/")) {
                consumerPath = consumerPath.substring(1);
            }

            // map path-parameters from operation to camel headers
            HttpHelper.evalPlaceholders(exchange.getMessage().getHeaders(), path, consumerPath);

            // process the incoming request
            return restOpenapiProcessorStrategy.process(openAPI, o, verb, path, rcp.getBinding(), exchange, callback);
        }

        // is it the api-context path
        if (path != null && path.equals(apiContextPath)) {
            return restOpenapiProcessorStrategy.processApiSpecification(endpoint.getSpecificationUri(), exchange, callback);
        }

        // okay we cannot process this requires so return either 404 or 405.
        // to know if its 405 then we need to check if any other HTTP method would have a consumer for the "same" request
        final String contextPath = path;
        List<String> allow = METHODS.stream()
                .filter(v -> RestConsumerContextPathMatcher.matchBestPath(v, contextPath, paths) != null).toList();
        if (allow.isEmpty()) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
        } else {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 405);
            // include list of allowed VERBs
            exchange.getMessage().setHeader("Allow", String.join(", ", allow));
        }
        exchange.setRouteStop(true);
        callback.done(true);
        return true;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        CamelContextAware.trySetCamelContext(restOpenapiProcessorStrategy, getCamelContext());
    }

    @Override
    public void afterPropertiesConfigured(CamelContext camelContext) {
        // this method is triggered by platformHttpConsumer when it has been initialized and would be possible
        // to know the actual url of the http server that would service the incoming requests
        // this is required to build the paths with all the details

        this.openApiUtils = new OpenApiUtils(camelContext, endpoint.getBindingPackageScan(), openAPI.getComponents());
        // register all openapi paths
        for (var e : openAPI.getPaths().entrySet()) {
            String path = e.getKey(); // path
            for (var o : e.getValue().readOperationsMap().entrySet()) {
                String v = o.getKey().name(); // verb
                // create per operation binding
                RestBindingConfiguration bc = createRestBindingConfiguration(o.getValue());

                String url = basePath + path;
                if (platformHttpConsumer != null) {
                    url = platformHttpConsumer.getPlatformHttpConsumer().getEndpoint().getServiceUrl() + url;
                }

                String desc = o.getValue().getSummary();
                if (desc != null && desc.isBlank()) {
                    desc = null;
                }
                String routeId = null;
                if (consumer instanceof RouteAware ra) {
                    routeId = ra.getRoute().getRouteId();
                }
                camelContext.getRestRegistry().addRestService(consumer, true, url, path, basePath, null, v, bc.getConsumes(),
                        bc.getProduces(), bc.getType(), bc.getOutType(), routeId, desc);

                try {
                    RestBindingAdvice binding = RestBindingAdviceFactory.build(camelContext, bc);
                    ServiceHelper.buildService(binding);
                    paths.add(new RestOpenApiConsumerPath(v, path, o.getValue(), binding));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        openApiUtils.clear(); // no longer needed

        // register api-doc in rest registry
        if (endpoint.getSpecificationUri() != null && apiContextPath != null) {
            String url = basePath + apiContextPath;
            String produces = null;
            if (endpoint.getSpecificationUri().endsWith("json")) {
                produces = "application/json";
            } else if (endpoint.getSpecificationUri().endsWith("yaml") || endpoint.getSpecificationUri().endsWith("yml")) {
                produces = "text/yaml";
            }
            // register api-doc
            camelContext.getRestRegistry().addRestSpecification(consumer, true, url, apiContextPath, basePath, "GET", produces,
                    null);
        }

        for (var p : paths) {
            if (p instanceof RestOpenApiConsumerPath rcp) {
                ServiceHelper.startService(rcp.getBinding());
            }
        }

        restOpenapiProcessorStrategy.setMissingOperation(endpoint.getMissingOperation());
        restOpenapiProcessorStrategy.setMockIncludePattern(endpoint.getMockIncludePattern());
        ServiceHelper.initService(restOpenapiProcessorStrategy);

        try {
            // validate openapi contract
            restOpenapiProcessorStrategy.validateOpenApi(openAPI, basePath, platformHttpConsumer);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        ServiceHelper.startService(restOpenapiProcessorStrategy);
    }

    private RestBindingConfiguration createRestBindingConfiguration(Operation o) {
        RestConfiguration config = camelContext.getRestConfiguration();
        RestConfiguration.RestBindingMode mode = config.getBindingMode();

        RestBindingConfiguration bc = new RestBindingConfiguration();
        bc.setBindingMode(mode.name());
        bc.setEnableCORS(config.isEnableCORS());
        bc.setCorsHeaders(config.getCorsHeaders());
        bc.setClientRequestValidation(config.isClientRequestValidation() || endpoint.isClientRequestValidation());
        bc.setClientResponseValidation(config.isClientResponseValidation() || endpoint.isClientResponseValidation());
        bc.setEnableNoContentResponse(config.isEnableNoContentResponse());
        bc.setSkipBindingOnErrorCode(config.isSkipBindingOnErrorCode());
        bc.setConsumes(openApiUtils.getConsumes(o));
        bc.setProduces(openApiUtils.getProduces(o));
        bc.setRequiredBody(openApiUtils.isRequiredBody(o));
        bc.setRequiredQueryParameters(openApiUtils.getRequiredQueryParameters(o));
        bc.setRequiredHeaders(openApiUtils.getRequiredHeaders(o));
        bc.setQueryDefaultValues(openApiUtils.getQueryParametersDefaultValue(o));

        // input and output types binding to java classes
        bc.setType(openApiUtils.manageRequestBody(o));
        bc.setOutType(openApiUtils.manageResponseBody(o));

        return bc;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(restOpenapiProcessorStrategy);
        for (var p : paths) {
            if (p instanceof RestOpenApiConsumerPath rcp) {
                ServiceHelper.stopService(rcp.getBinding());
            }
        }
        paths.clear();
    }
}
