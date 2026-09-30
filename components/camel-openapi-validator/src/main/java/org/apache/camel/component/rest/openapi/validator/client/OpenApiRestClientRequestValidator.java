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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.interaction.ApiOperationResolver;
import com.atlassian.oai.validator.model.ApiOperationMatch;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.JsonValidationReportFormat;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.parameters.Parameter;
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

    private volatile OpenAPI cachedOpenAPI;
    private volatile Map<String, String> cachedLevels;
    private volatile OpenApiInteractionValidator cachedValidator;
    private volatile CachedResolver cachedResolver;

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
        if (path == null) {
            path = "/";
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
            boolean customHeader
                    = !startsWithIgnoreCase(key, "Camel") && !filter.applyFilterToCamelHeaders(key, value, exchange);
            if (customHeader) {
                if (value instanceof Collection<?> values) {
                    // A header sent more than once arrives as a Collection (CollectionHelper.appendEntry).
                    // Converting that to a single String would hand the validator the collection's
                    // toString(), such as "[a, b]" - a value the client never sent - so the schema would
                    // be checked against fabricated data, and a repeated scalar parameter would never be
                    // reported. Pass the values on instead, as the query parameters below already do.
                    List<String> headerValues = new ArrayList<>(values.size());
                    for (Object headerValue : values) {
                        String text = exchange.getContext().getTypeConverter()
                                .convertTo(String.class, exchange, headerValue);
                        if (text != null) {
                            headerValues.add(text);
                        }
                    }
                    if (headerValues.size() > 1 && isArrayHeader(openAPI, method, path, key)) {
                        // RFC 9110 section 5.3: repeating a list-based field is equivalent to one field
                        // with the values joined by commas, which is the form the validator expects
                        builder.withHeader(key, String.join(",", headerValues));
                    } else {
                        builder.withHeader(key, headerValues);
                    }
                } else {
                    builder.withHeader(key, exchange.getMessage().getHeader(key, String.class));
                }
            }
        }
        // Use query parameters, if present
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=", 2);
                String qKey = urlDecode(keyValue[0]);
                String qValue = keyValue.length == 2 ? urlDecode(keyValue[1]) : "";
                builder.withQueryParam(qKey, qValue);
            }
        }

        Map<String, String> effectiveLevels = new HashMap<>();
        RestConfiguration rc = exchange.getContext().getRestConfiguration();
        if (rc.getValidationLevels() != null) {
            effectiveLevels.putAll(rc.getValidationLevels());
        }
        if (!validationContent.requiredBody()) {
            effectiveLevels.put("validation.request.body.missing", "IGNORE");
        }

        OpenApiInteractionValidator validator = getOrCreateValidator(openAPI, effectiveLevels);
        ValidationReport report = validator.validateRequest(builder.build());

        // create report if error or DEBUG logging
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

    private OpenApiInteractionValidator getOrCreateValidator(OpenAPI openAPI, Map<String, String> levels) {
        if (cachedValidator != null && cachedOpenAPI == openAPI && Objects.equals(cachedLevels, levels)) {
            return cachedValidator;
        }
        LevelResolver.Builder lr = LevelResolver.create();
        for (var e : levels.entrySet()) {
            String key = e.getKey();
            var level = ValidationReport.Level.valueOf(e.getValue());
            if ("defaultLevel".equalsIgnoreCase(key)) {
                lr.withDefaultLevel(level);
            } else {
                lr.withLevel(key, level);
            }
        }
        OpenApiInteractionValidator v = OpenApiInteractionValidator.createFor(openAPI)
                .withLevelResolver(lr.build())
                .build();
        cachedOpenAPI = openAPI;
        cachedLevels = new HashMap<>(levels);
        cachedValidator = v;
        return v;
    }

    /**
     * Whether the operation the request resolves to declares the given header as an array. The operation is resolved
     * the same way the validator resolves it, so this sees the parameters the validator checks.
     */
    private boolean isArrayHeader(OpenAPI openAPI, String method, String path, String headerName) {
        if (method == null) {
            return false;
        }
        ApiOperationMatch match;
        try {
            match = getOrCreateResolver(openAPI).findApiOperation(path,
                    Request.Method.valueOf(method.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            // unknown HTTP method, which the validator reports on its own
            return false;
        }
        if (!match.isPathFound() || !match.isOperationAllowed()) {
            return false;
        }
        List<Parameter> parameters = match.getApiOperation().getOperation().getParameters();
        if (parameters == null) {
            return false;
        }
        for (Parameter parameter : parameters) {
            if ("header".equals(parameter.getIn()) && headerName.equalsIgnoreCase(parameter.getName())) {
                // same check as the validator's ParameterValidator. An OpenAPI 3.1 contract is parsed into a
                // JsonSchema instead, which the validator does not treat as an array either, so array headers
                // of a 3.1 contract are not recognised here
                return parameter.getSchema() instanceof ArraySchema;
            }
        }
        return false;
    }

    private ApiOperationResolver getOrCreateResolver(OpenAPI openAPI) {
        CachedResolver cached = cachedResolver;
        if (cached != null && cached.openAPI() == openAPI) {
            return cached.resolver();
        }
        ApiOperationResolver resolver = new ApiOperationResolver(openAPI, null, false);
        cachedResolver = new CachedResolver(openAPI, resolver);
        return resolver;
    }

    private record CachedResolver(OpenAPI openAPI, ApiOperationResolver resolver) {
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    private static boolean startsWithIgnoreCase(String s, String prefix) {
        return s.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
