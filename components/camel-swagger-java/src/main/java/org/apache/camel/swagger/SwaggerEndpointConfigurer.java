/**
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
package org.apache.camel.swagger;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.component.rest.RestEndpoint;
import org.apache.camel.spi.RestEndpointConfigurer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;

public class SwaggerEndpointConfigurer implements RestEndpointConfigurer {

    @Override
    public void configureEndpoint(final CamelContext context, final RestEndpoint endpoint, final String uri,
            final String remaining, final Map<String, Object> parameters) {

        final String location = endpoint.getApiDoc();

        if (ObjectHelper.isEmpty(location)) {
            throw new IllegalArgumentException("Swagger api-doc must be configured using the apiDoc option");
        }

        String[] parts = remaining.split(":");
        if (parts.length < 2 || ObjectHelper.isEmpty(parts[1])) {
            throw new IllegalArgumentException(
                    "The endpoint URI must contain an operationId, the syntax for the URI is `rest:swagger:operationId`, given: `"
                        + uri + "`");
        }
        final String operationId = parts[1];

        final Swagger swagger = loadSwaggerDefinition(location);

        final Map<String, Path> paths = swagger.getPaths();

        String uriTemplate = null;
        Path path = null;
        String method = null;

        for (final Entry<String, Path> pathEntry : paths.entrySet()) {
            final Path somePath = pathEntry.getValue();

            final Optional<Entry<HttpMethod, Operation>> operation = somePath.getOperationMap().entrySet().stream()
                    .filter(operationEntry -> operationId.equals(operationEntry.getValue().getOperationId())).findAny();
            if (operation.isPresent()) {
                path = somePath;
                uriTemplate = pathEntry.getKey();
                method = operation.get().getKey().name();

                break;
            }
        }

        if (path == null) {
            throw new IllegalArgumentException("Swagger definition loaded from location `" + location
                + "` does not contain the operation with id: `" + operationId + "`");
        }

        final String basePath = swagger.getBasePath();
        if (ObjectHelper.isNotEmpty(basePath)) {
            endpoint.setPath(basePath);
        }

        endpoint.setUriTemplate(uriTemplate);
        endpoint.setMethod(method);
    }

    Swagger loadSwaggerDefinition(final String location) {
        final SwaggerParser parser = new SwaggerParser();

        return parser.read(location);
    }
}
