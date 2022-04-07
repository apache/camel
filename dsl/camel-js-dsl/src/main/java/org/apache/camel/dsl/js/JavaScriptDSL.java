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
package org.apache.camel.dsl.js;

import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.BuilderSupport;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.endpoint.EndpointBuilderFactory;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.spi.Registry;

public class JavaScriptDSL extends BuilderSupport implements EndpointBuilderFactory {
    public final Registry registry;
    public final EndpointRouteBuilder builder;
    public final CamelContext context;

    public JavaScriptDSL(EndpointRouteBuilder builder) {
        super(builder.getContext());

        this.registry = builder.getContext().getRegistry();
        this.builder = builder;
        this.context = builder.getContext();
    }

    public RouteDefinition from(String endpoint) {
        return builder.from(endpoint);
    }

    public RouteDefinition from(EndpointConsumerBuilder endpoint) {
        return builder.from(endpoint);
    }

    public RestDefinition rest(String path) {
        return builder.rest(path);
    }

    public RestConfigurationDefinition restConfiguration() {
        return builder.restConfiguration();
    }

    public OnExceptionDefinition onException(Class<? extends Throwable> exception) {
        return builder.onException(exception);
    }

    public OnCompletionDefinition onCompletion() {
        return builder.onCompletion();
    }

    public InterceptDefinition intercept() {
        return builder.intercept();
    }

    public InterceptFromDefinition interceptFrom() {
        return builder.interceptFrom();
    }

    public InterceptFromDefinition interceptFrom(String uri) {
        return builder.interceptFrom(uri);
    }

    public InterceptSendToEndpointDefinition interceptSendToEndpoint(String uri) {
        return builder.interceptSendToEndpoint(uri);
    }

    public void errorHandler(ErrorHandlerFactory handler) {
        builder.errorHandler(handler);
    }

    public Processor processor(Consumer<Exchange> consumer) {
        return consumer::accept;
    }
}
