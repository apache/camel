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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.rest.openapi.validator.RequestValidator;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.service.ServiceHelper;

public class RestOpenApiProcessor extends DelegateAsyncProcessor implements CamelContextAware {

    private static final List<String> METHODS
            = Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "TRACE", "OPTIONS", "CONNECT", "PATCH");

    private CamelContext camelContext;
    private final RestOpenApiEndpoint endpoint;
    private final OpenAPI openAPI;
    private final String basePath;
    private final String apiContextPath;
    private final List<RestConsumerContextPathMatcher.ConsumerPath<Operation>> paths = new ArrayList<>();
    private final RestOpenapiProcessorStrategy restOpenapiProcessorStrategy;

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

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        String path = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        if (path != null && path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }
        String verb = exchange.getMessage().getHeader(Exchange.HTTP_METHOD, String.class);

        RestConsumerContextPathMatcher.ConsumerPath<Operation> m
                = RestConsumerContextPathMatcher.matchBestPath(verb, path, paths);
        if (m != null) {
            Operation operation = m.getConsumer();

            // we have found the operation to call, but if validation is enabled then we need
            // to validate the incoming request first
            if (endpoint.isRequestValidationEnabled()) {
                Map<String, Parameter> pathParameters;
                if (operation.getParameters() != null) {
                    pathParameters = operation.getParameters().stream()
                            .filter(p -> "path".equals(p.getIn()))
                            .collect(Collectors.toMap(Parameter::getName, Function.identity()));
                } else {
                    pathParameters = new HashMap<>();
                }
                try {
                    final String uriTemplate = endpoint.resolveUri(path, pathParameters);
                    RequestValidator validator = endpoint.configureRequestValidator(openAPI, operation, verb, uriTemplate);
                    Set<String> errors = validator.validate(exchange);
                    if (!errors.isEmpty()) {
                        RestOpenApiValidationException exception = new RestOpenApiValidationException(errors);
                        exchange.setException(exception);
                        // validation error should be 405
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 405);
                        callback.done(true);
                        return true;
                    }
                } catch (Exception e) {
                    exchange.setException(e);
                    callback.done(true);
                    return true;
                }
            }

            return restOpenapiProcessorStrategy.process(operation, path, exchange, callback);
        }

        // is it the api-context path
        if (path != null && path.equals(apiContextPath)) {
            return restOpenapiProcessorStrategy.processApiSpecification(endpoint.getSpecificationUri(), exchange, callback);
        }

        // okay we cannot process this requires so return either 404 or 405.
        // to know if its 405 then we need to check if any other HTTP method would have a consumer for the "same" request
        final String contextPath = path;
        boolean hasAnyMethod
                = METHODS.stream().anyMatch(v -> RestConsumerContextPathMatcher.matchBestPath(v, contextPath, paths) != null);
        if (hasAnyMethod) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 405);
        } else {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
        }
        exchange.setRouteStop(true);
        callback.done(true);
        return true;
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        CamelContextAware.trySetCamelContext(restOpenapiProcessorStrategy, getCamelContext());
        // register all openapi paths
        for (var e : openAPI.getPaths().entrySet()) {
            String path = e.getKey(); // path
            for (var o : e.getValue().readOperationsMap().entrySet()) {
                String v = o.getKey().name(); // verb
                paths.add(new RestOpenApiConsumerPath(v, path, o.getValue()));
            }
        }
        ServiceHelper.buildService(restOpenapiProcessorStrategy);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        restOpenapiProcessorStrategy.setMissingOperation(endpoint.getMissingOperation());
        restOpenapiProcessorStrategy.setMockIncludePattern(endpoint.getMockIncludePattern());
        ServiceHelper.initService(restOpenapiProcessorStrategy);

        // validate openapi contract
        restOpenapiProcessorStrategy.validateOpenApi(openAPI);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(restOpenapiProcessorStrategy);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        paths.clear();
        ServiceHelper.stopService(restOpenapiProcessorStrategy);
    }
}
