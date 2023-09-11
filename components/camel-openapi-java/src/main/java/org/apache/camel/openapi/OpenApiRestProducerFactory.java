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
package org.apache.camel.openapi;

import java.util.Map;
import java.util.StringJoiner;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.apache.camel.CamelContext;
import org.apache.camel.Producer;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.support.CamelContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenApiRestProducerFactory implements RestProducerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(OpenApiRestProducerFactory.class);

    @Override
    public Producer createProducer(
            CamelContext camelContext, String host,
            String verb, String basePath, String uriTemplate, String queryParameters,
            String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters)
            throws Exception {

        String apiDoc = (String) parameters.get("apiDoc");
        // load json model
        if (apiDoc == null) {
            throw new IllegalArgumentException("OpenApi api-doc must be configured using the apiDoc option");
        }

        String path = uriTemplate != null ? uriTemplate : basePath;
        // path must start with a leading slash
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        OpenAPI openApi = loadOpenApiModel(apiDoc);
        Operation operation = getOpenApiOperation(openApi, verb, path);
        if (operation == null) {
            throw new IllegalArgumentException("OpenApi api-doc does not contain operation for " + verb + ":" + path);
        }

        // validate if we have the query parameters also
        if (queryParameters != null) {
            for (Parameter param : operation.getParameters()) {
                if ("query".equals(param.getIn()) && Boolean.TRUE.equals(param.getRequired())) {
                    // check if we have the required query parameter defined
                    String key = param.getName();
                    String token = key + "=";
                    boolean hasQuery = queryParameters.contains(token);
                    if (!hasQuery) {
                        throw new IllegalArgumentException(
                                "OpenApi api-doc does not contain query parameter " + key + " for " + verb + ":" + path);
                    }
                }
            }
        }

        String componentName = (String) parameters.get("componentName");

        return createHttpProducer(camelContext, openApi, operation, host, verb, path, queryParameters,
                produces, consumes, componentName, parameters);
    }

    OpenAPI loadOpenApiModel(String apiDoc) throws Exception {
        final OpenAPIParser openApiParser = new OpenAPIParser();
        final SwaggerParseResult openApi = openApiParser.readLocation(apiDoc, null, null);

        if (openApi != null && openApi.getOpenAPI() != null) {
            //   checkV2specification(openApi.getOpenAPI(), uri);
            return openApi.getOpenAPI();
        }

        // In theory there should be a message in the parse result but it has disappeared...
        throw new IllegalArgumentException(
                "The given OpenApi specification could not be loaded from `" + apiDoc + "`.");

    }

    private Operation getOpenApiOperation(OpenAPI openApi, String verb, String path) {
        // path may include base path so skip that
        String basePath = RestOpenApiSupport.getBasePathFromOasDocument(openApi);
        if (basePath != null && path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }

        PathItem modelPath = openApi.getPaths().get(path);
        if (modelPath == null) {
            return null;
        }

        // get,put,post,head,delete,patch,options
        Operation op = null;
        PathItem.HttpMethod method = PathItem.HttpMethod.valueOf(verb.toUpperCase());
        if (method != null) {
            return modelPath.readOperationsMap().get(method);
        }
        return op;
    }

    private Producer createHttpProducer(
            CamelContext camelContext, OpenAPI openApi, Operation operation,
            String host, String verb, String path, String queryParameters,
            String consumes, String produces,
            String componentName, Map<String, Object> parameters)
            throws Exception {

        LOG.debug("Using OpenApi operation: {} with {} {}", operation, verb, path);

        RestProducerFactory factory = (RestProducerFactory) parameters.remove("restProducerFactory");

        if (factory != null) {
            LOG.debug("Using RestProducerFactory: {}", factory);

            if (produces == null) {
                StringJoiner producesBuilder = new StringJoiner(",");
                if (operation.getResponses() != null) {
                    for (ApiResponse response : operation.getResponses().values()) {
                        if (response.getContent() != null) {
                            for (String mediaType : response.getContent().keySet()) {
                                producesBuilder.add(mediaType);
                            }
                        }
                    }
                }
                produces = producesBuilder.length() == 0 ? null : producesBuilder.toString();
            }
            if (consumes == null) {
                StringJoiner consumesBuilder = new StringJoiner(",");
                if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                    for (String mediaType : operation.getRequestBody().getContent().keySet()) {
                        consumesBuilder.add(mediaType);
                    }
                }
                consumes = consumesBuilder.length() == 0 ? null : consumesBuilder.toString();
            }

            String basePath;
            String uriTemplate;
            if (host == null) {

                //if no explicit host has been configured then use host and base path from the openApi api-doc
                host = RestOpenApiSupport.getHostFromOasDocument(openApi);
                basePath = RestOpenApiSupport.getBasePathFromOasDocument(openApi);
                uriTemplate = path;

            } else {
                // path includes also uri template
                basePath = path;
                uriTemplate = null;
            }

            RestConfiguration config = CamelContextHelper.getRestConfiguration(camelContext, null, componentName);
            return factory.createProducer(camelContext, host, verb, basePath, uriTemplate, queryParameters, consumes, produces,
                    config, parameters);

        } else {
            throw new IllegalStateException("Cannot find RestProducerFactory in Registry or as a Component to use");
        }
    }
}
