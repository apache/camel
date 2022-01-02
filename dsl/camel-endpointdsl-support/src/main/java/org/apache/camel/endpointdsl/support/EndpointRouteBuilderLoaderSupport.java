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
package org.apache.camel.endpointdsl.support;

import java.io.Reader;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.dsl.support.RouteBuilderLoaderSupport;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;

import static org.apache.camel.builder.endpoint.EndpointRouteBuilder.loadEndpointRoutesBuilder;

/**
 * Base class for {@link RoutesBuilderLoader} implementations with Endpoint DSL.
 */
public abstract class EndpointRouteBuilderLoaderSupport extends RouteBuilderLoaderSupport {

    protected EndpointRouteBuilderLoaderSupport(String extension) {
        super(extension);
    }

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception {
        return loadEndpointRoutesBuilder(resource, this::doLoadEndpointRouteBuilder);
    }

    protected abstract void doLoadEndpointRouteBuilder(Reader reader, EndpointRouteBuilder builder) throws Exception;
}
