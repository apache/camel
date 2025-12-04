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

package org.apache.camel.component.rest.openapi.validator.client;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.JsonValidationReportFormat;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.camel.Exchange;
import org.apache.camel.component.rest.openapi.RestOpenApiComponent;
import org.apache.camel.component.rest.openapi.RestOpenApiHelper;
import org.apache.camel.http.base.HttpHeaderFilterStrategy;
import org.apache.camel.spi.RestClientRequestValidator;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService(RestClientRequestValidator.FACTORY)
public class OpenApiRestClientRequestValidator implements RestClientRequestValidator {

    private static final Logger LOG = LoggerFactory.getLogger(OpenApiRestClientRequestValidator.class);

    private final HttpHeaderFilterStrategy filter = new HttpHeaderFilterStrategy();

    public OpenApiRestClientRequestValidator() {
        // add extra additional HTTP request headers to skip
        filter.getOutFilter().add("accept");
        filter.getOutFilter().add("authorization");
        filter.getOutFilter().add("content-encoding");
        filter.getOutFilter().add("cookie");
        filter.getOutFilter().add("origin");
        filter.getOutFilter().add("user-agent");
    }

    @Override
    public ValidationError validate(Exchange exchange, ValidationContext validationContent) {
        OpenAPI openAPI = exchange.getProperty(Exchange.REST_OPENAPI, OpenAPI.class);
        if (openAPI == null) {
            return null;
        }

        String method = exchange.getMessage().getHeader(Exchange.HTTP_METHOD, String.class);
        String path = exchange.getMessage().getHeader(Exchange.HTTP_PATH, String.class);

        // find the base-path which can be configured in various places
        RestOpenApiComponent comp = (RestOpenApiComponent) exchange.getContext().hasComponent("rest-openapi");
        String basePath = RestOpenApiHelper.determineBasePath(exchange.getContext(), comp, null, openAPI);
        // need to clip base-path
        if (path != null && path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }

        String accept = exchange.getMessage().getHeader("Accept", String.class);
        String contentType = ExchangeHelper.getContentType(exchange);
        String body = MessageHelper.extractBodyAsString(exchange.getIn());

        SimpleRequest.Builder builder = new SimpleRequest.Builder(method, path, false);
        if (contentType != null) {
            builder.withContentType(contentType);
        }
        if (accept != null) {
            builder.withAccept(accept);
        }
        if (body != null) {
            builder.withBody(body);
        }
        // Use all non-Camel/non-HTTP headers
        for (var header : exchange.getMessage().getHeaders().entrySet()) {
            String key = header.getKey();
            Object value = header.getValue();
            boolean customHeader =
                    !startsWithIgnoreCase(key, "Camel") && !filter.applyFilterToCamelHeaders(key, value, exchange);
            if (customHeader) {
                builder.withHeader(key, exchange.getMessage().getHeader(key, String.class));
            }
        }
        // Use query parameters, if present
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    builder.withQueryParam(keyValue[0], keyValue[1]);
                } else if (keyValue.length == 1) {
                    builder.withQueryParam(keyValue[0], "");
                }
            }
        }

        LevelResolver.Builder lr = LevelResolver.create();
        RestConfiguration rc = exchange.getContext().getRestConfiguration();
        if (rc.getValidationLevels() != null) {
            for (var e : rc.getValidationLevels().entrySet()) {
                String key = e.getKey();
                var level = ValidationReport.Level.valueOf(e.getValue());
                if ("defaultLevel".equalsIgnoreCase(key)) {
                    lr.withDefaultLevel(level);
                } else {
                    lr.withLevel(key, level);
                }
            }
        }
        OpenApiInteractionValidator validator = OpenApiInteractionValidator.createFor(openAPI)
                .withLevelResolver(lr.build())
                .build();
        ValidationReport report = validator.validateRequest(builder.build());

        // create report if error of DEBUG logging
        if (report.hasErrors() || LOG.isDebugEnabled()) {
            String msg;
            if (accept != null && accept.contains("application/json")) {
                msg = JsonValidationReportFormat.getInstance().apply(report);
            } else {
                msg = SimpleValidationReportFormat.getInstance().apply(report);
            }
            LOG.debug("Client Request Validation: {}", msg);
            if (report.hasErrors()) {
                return new ValidationError(400, msg);
            }
        }

        return null;
    }

    private static boolean startsWithIgnoreCase(String s, String prefix) {
        return s.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
