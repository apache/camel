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
package org.apache.camel.component.rest.openapi.validator;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.camel.util.ObjectHelper;

public class RestOpenApiOperation {
    private final Operation operation;
    private final String method;
    private final String uriTemplate;
    private final Set<Parameter> queryParams;
    private final Set<Parameter> formParams;
    private final Set<Parameter> headers;

    public RestOpenApiOperation(Operation operation, String method, String uriTemplate) {
        this.operation = operation;
        this.method = method;
        this.uriTemplate = uriTemplate;
        this.queryParams = resolveParametersForType("query");
        this.formParams = resolveParametersForType("form");
        this.headers = resolveParametersForType("header");
    }

    public Object getOperation() {
        return operation;
    }

    public String getMethod() {
        return method;
    }

    public String getUriTemplate() {
        return uriTemplate;
    }

    public Set<Parameter> getQueryParams() {
        return queryParams;
    }

    public Set<Parameter> getFormParams() {
        return formParams;
    }

    public Set<Parameter> getHeaders() {
        return headers;
    }

    private Set<Parameter> resolveParametersForType(String type) {
        List<Parameter> parameters = operation.getParameters();
        if (ObjectHelper.isEmpty(parameters)) {
            return Collections.emptySet();
        }
        return parameters.stream()
                .filter(parameter -> type.equals(parameter.getIn()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
