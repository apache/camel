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
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.service.ServiceHelper;

public class RestOpenApiProcessor extends DelegateAsyncProcessor implements CamelContextAware {

    private CamelContext camelContext;
    private final OpenAPI openAPI;
    private final String basePath;
    private final List<RestConsumerContextPathMatcher.ConsumerPath<Operation>> paths = new ArrayList<>();
    private RestOpenapiProcessorStrategy restOpenapiProcessorStrategy;

    public RestOpenApiProcessor(OpenAPI openAPI, String basePath, Processor processor) {
        super(processor);
        this.basePath = basePath;
        this.openAPI = openAPI;
        this.restOpenapiProcessorStrategy = new DefaultRestOpenapiProcessorStrategy();
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public RestOpenapiProcessorStrategy getRestOpenapiProcessorStrategy() {
        return restOpenapiProcessorStrategy;
    }

    public void setRestOpenapiProcessorStrategy(RestOpenapiProcessorStrategy restOpenapiProcessorStrategy) {
        this.restOpenapiProcessorStrategy = restOpenapiProcessorStrategy;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // TODO: check if valid operation according to OpenApi
        // TODO: validate GET/POST etc
        // TODO: RequestValidator
        // TODO: 404 and so on
        // TODO: binding

        String path = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);
        if (path != null && path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }
        String verb = exchange.getMessage().getHeader(Exchange.HTTP_METHOD, String.class);

        RestConsumerContextPathMatcher.ConsumerPath<Operation> m
                = RestConsumerContextPathMatcher.matchBestPath(verb, path, paths);
        if (m != null) {
            Operation o = m.getConsumer();
            return restOpenapiProcessorStrategy.process(o, path, exchange, callback);
        }

        // no operation found so it's a 404
        exchange.setException(new RejectedExecutionException());
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
        callback.done(true);
        return true;
    }

    protected Operation asOperation(PathItem item, String verb) {
        return switch (verb) {
            case "GET" -> item.getGet();
            case "DELETE" -> item.getDelete();
            case "HEAD" -> item.getHead();
            case "PATCH" -> item.getPatch();
            case "OPTIONS" -> item.getOptions();
            case "PUT" -> item.getPut();
            case "POST" -> item.getPost();
            default -> null;
        };
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        // register all openapi paths
        for (var e : openAPI.getPaths().entrySet()) {
            String path = e.getKey(); // path
            for (var o : e.getValue().readOperationsMap().entrySet()) {
                String v = o.getKey().name(); // verb
                paths.add(new RestOpenApiConsumerPath(v, path, o.getValue()));
            }
        }

        CamelContextAware.trySetCamelContext(restOpenapiProcessorStrategy, getCamelContext());
        ServiceHelper.buildService(restOpenapiProcessorStrategy);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        ServiceHelper.initService(restOpenapiProcessorStrategy);
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
