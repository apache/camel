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
import java.util.Optional;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import org.apache.camel.*;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.processor.RestBindingAdvice;
import org.apache.camel.support.processor.RestBindingAdviceFactory;
import org.apache.camel.support.processor.RestBindingConfiguration;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestOpenApiProcessor extends DelegateAsyncProcessor implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(RestOpenApiProcessor.class);

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
                                Processor processor, RestOpenapiProcessorStrategy restOpenapiProcessorStrategy) {
        super(processor);
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
        String uri = exchange.getMessage().getHeader(Exchange.HTTP_URI, String.class);
        if (uri != null) {
            uri = URISupport.stripQuery(uri);
        }
        if (uri != null && uri.startsWith(basePath)) {
            uri = uri.substring(basePath.length());
        }
        String verb = exchange.getMessage().getHeader(Exchange.HTTP_METHOD, String.class);

        RestConsumerContextPathMatcher.ConsumerPath<Operation> m
                = RestConsumerContextPathMatcher.matchBestPath(verb, uri, paths);
        if (m instanceof RestOpenApiConsumerPath rcp) {
            Operation o = rcp.getConsumer();

            String consumerPath = rcp.getConsumerPath();

            //if uri is not starting with slash then remove the slash in the consumerPath from the openApi spec
            if(consumerPath.startsWith("/") && !uri.startsWith("/"))
            {
                consumerPath = consumerPath.substring(1);
            }
            
            // map path-parameters from operation to camel headers
            HttpHelper.evalPlaceholders(exchange.getMessage().getHeaders(), uri, rcp.getConsumerPath());

            // process the incoming request
            return restOpenapiProcessorStrategy.process(openAPI, o, verb, uri, rcp.getBinding(), exchange, callback);
        }

        // is it the api-context path
        if (uri != null && uri.equals(apiContextPath)) {
            return restOpenapiProcessorStrategy.processApiSpecification(endpoint.getSpecificationUri(), exchange, callback);
        }

        // okay we cannot process this requires so return either 404 or 405.
        // to know if its 405 then we need to check if any other HTTP method would have a consumer for the "same" request
        final String contextPath = uri;
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
        this.openApiUtils = new OpenApiUtils(camelContext, endpoint.getBindingPackageScan(), openAPI.getComponents());
        CamelContextAware.trySetCamelContext(restOpenapiProcessorStrategy, getCamelContext());

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

                RestBindingAdvice binding = RestBindingAdviceFactory.build(camelContext, bc);
                RestBindingAdviceFactory.build(camelContext, bc);

                ServiceHelper.buildService(binding);
                paths.add(new RestOpenApiConsumerPath(v, path, o.getValue(), binding));
            }
        }
        openApiUtils.clear(); // no longer needed

        restOpenapiProcessorStrategy.setMissingOperation(endpoint.getMissingOperation());
        restOpenapiProcessorStrategy.setMockIncludePattern(endpoint.getMockIncludePattern());
        ServiceHelper.initService(restOpenapiProcessorStrategy);

        // validate openapi contract
        restOpenapiProcessorStrategy.validateOpenApi(openAPI, platformHttpConsumer);
    }

    private RestBindingConfiguration createRestBindingConfiguration(Operation o) throws Exception {
        RestConfiguration config = camelContext.getRestConfiguration();
        RestConfiguration.RestBindingMode mode = config.getBindingMode();

        RestBindingConfiguration bc = new RestBindingConfiguration();
        bc.setBindingMode(mode.name());
        bc.setEnableCORS(config.isEnableCORS());
        bc.setCorsHeaders(config.getCorsHeaders());
        bc.setClientRequestValidation(config.isClientRequestValidation() || endpoint.isClientRequestValidation());
        bc.setEnableNoContentResponse(config.isEnableNoContentResponse());
        bc.setSkipBindingOnErrorCode(config.isSkipBindingOnErrorCode());

        String consumes = Optional.ofNullable(openApiUtils.getConsumes(o)).orElse(endpoint.getConsumes());
        String produces = Optional.ofNullable(openApiUtils.getProduces(o)).orElse(endpoint.getProduces());

        bc.setConsumes(consumes);
        bc.setProduces(produces);

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
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(restOpenapiProcessorStrategy);
        for (var p : paths) {
            if (p instanceof RestOpenApiConsumerPath rcp) {
                ServiceHelper.startService(rcp.getBinding());
            }
        }
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
