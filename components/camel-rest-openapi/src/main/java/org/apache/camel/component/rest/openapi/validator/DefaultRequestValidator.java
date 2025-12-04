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

import static org.apache.camel.support.http.RestUtil.isValidOrAcceptedContentType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.swagger.v3.oas.models.media.Content;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;

public class DefaultRequestValidator implements RequestValidator {

    private RestOpenApiOperation operation;

    @Override
    public void setOperation(RestOpenApiOperation operation) {
        this.operation = operation;
    }

    @Override
    public RestOpenApiOperation getOperation() {
        return operation;
    }

    @Override
    public Set<String> validate(Exchange exchange, RestOpenApiOperation o) {
        // Perform validation and capture errors
        Set<String> validationErrors = new LinkedHashSet<>();

        Message message = exchange.getMessage();
        String contentType = message.getHeader(Exchange.CONTENT_TYPE, String.class);

        // Validate content-type
        String consumes = null;
        if (o.getOperation().getRequestBody() != null) {
            Content c = o.getOperation().getRequestBody().getContent();
            if (c != null) {
                consumes = c.keySet().stream().sorted().collect(Collectors.joining(","));
            }
        }
        if (contentType != null && !isValidOrAcceptedContentType(consumes, contentType)) {
            validationErrors.add("Request Content-Type header '" + contentType + "' does not match any allowed types");
        }

        // Validate body
        boolean requiredBody = false;
        if (o.getOperation().getRequestBody() != null) {
            requiredBody = Boolean.TRUE == o.getOperation().getRequestBody().getRequired();
        }
        if (requiredBody) {
            Object body = message.getBody();
            if (body != null) {
                body = MessageHelper.extractBodyAsString(message);
            }
            if (ObjectHelper.isEmpty(body)) {
                validationErrors.add("A request body is required but none found.");
            }
        }
        // special for json to check if the body is valid json
        if (contentType != null && isValidOrAcceptedContentType("application/json", contentType)) {
            Object body = message.getBody();
            if (body != null) {
                String text = MessageHelper.extractBodyAsString(message);
                if (text != null) {
                    JsonMapper om = new JsonMapper();
                    try {
                        om.readTree(text);
                    } catch (Exception e) {
                        validationErrors.add("Unable to parse JSON");
                    }
                }
            }
        }

        // Validate required operation query params
        o.getQueryParams().stream()
                .filter(parameter -> Objects.nonNull(parameter.getRequired()) && parameter.getRequired())
                .forEach(parameter -> {
                    Object header = message.getHeader(parameter.getName());
                    if (ObjectHelper.isEmpty(header)) {
                        validationErrors.add(
                                "Query parameter '" + parameter.getName() + "' is required but none found.");
                    }
                });

        // Validate operation required headers
        o.getHeaders().stream()
                .filter(parameter -> Objects.nonNull(parameter.getRequired()) && parameter.getRequired())
                .forEach(parameter -> {
                    Object header = message.getHeader(parameter.getName());
                    if (ObjectHelper.isEmpty(header)) {
                        validationErrors.add(
                                "Header parameter '" + parameter.getName() + "' is required but none found.");
                    }
                });

        // Reset stream cache (if available) so it can be read again
        MessageHelper.resetStreamCache(message);

        return Collections.unmodifiableSet(validationErrors);
    }
}
