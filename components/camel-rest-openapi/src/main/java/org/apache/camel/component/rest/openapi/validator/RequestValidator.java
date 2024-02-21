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

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.ValidationReport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

public class RequestValidator {
    private static final Pattern REST_PATH_PARAM_PATTERN = Pattern.compile("\\{([^}]+)}");

    private final OpenApiInteractionValidator openApiInteractionValidator;
    private final RestOpenApiOperation restOpenApiOperation;
    private final RequestValidationCustomizer requestValidationCustomizer;

    public RequestValidator(OpenApiInteractionValidator openApiInteractionValidator,
                            RestOpenApiOperation restOpenApiOperation,
                            RequestValidationCustomizer requestValidationCustomizer) {
        this.openApiInteractionValidator = openApiInteractionValidator;
        this.restOpenApiOperation = restOpenApiOperation;
        this.requestValidationCustomizer = requestValidationCustomizer;
    }

    public Set<String> validate(Exchange exchange) {
        Message message = exchange.getMessage();
        String contentType = message.getHeader(Exchange.CONTENT_TYPE, String.class);
        String charsetFromExchange = getCharsetFromExchange(exchange);
        Charset charset = null;
        if (ObjectHelper.isNotEmpty(charsetFromExchange)) {
            charset = Charset.forName(charsetFromExchange);
        }

        SimpleRequest.Builder builder
                = new SimpleRequest.Builder(restOpenApiOperation.getMethod(), resolvePathParams(exchange));
        builder.withContentType(contentType);

        // Validate request body if available
        Object body = message.getBody();
        if (ObjectHelper.isNotEmpty(body)) {
            if (body instanceof InputStream) {
                builder.withBody((InputStream) body);
            } else if (body instanceof byte[]) {
                builder.withBody((byte[]) body);
            } else {
                TypeConverter typeConverter = exchange.getContext().getTypeConverter();
                String stringBody = typeConverter.tryConvertTo(String.class, body);
                builder.withBody(stringBody, charset);
            }
        }

        // Validate required operation query params
        restOpenApiOperation.getQueryParams()
                .stream()
                .filter(parameter -> Objects.nonNull(parameter.getRequired()) && parameter.getRequired())
                .forEach(parameter -> {
                    Object header = exchange.getMessage().getHeader(parameter.getName());
                    if (ObjectHelper.isNotEmpty(header)) {
                        if (header instanceof String) {
                            builder.withQueryParam(parameter.getName(), (String) header);
                        } else if (header instanceof List) {
                            builder.withQueryParam(parameter.getName(), (List<String>) header);
                        }
                    }
                });

        // Validate operation required headers
        restOpenApiOperation.getHeaders()
                .stream()
                .filter(parameter -> Objects.nonNull(parameter.getRequired()) && parameter.getRequired())
                .forEach(parameter -> {
                    Object header = exchange.getMessage().getHeader(parameter.getName());
                    if (ObjectHelper.isNotEmpty(header)) {
                        if (header instanceof String) {
                            builder.withHeader(parameter.getName(), (String) header);
                        } else if (header instanceof List) {
                            builder.withHeader(parameter.getName(), (List<String>) header);
                        }
                    }
                });

        // Apply any extra customizations to the validation request
        requestValidationCustomizer.customizeSimpleRequestBuilder(builder, restOpenApiOperation, exchange);

        // Perform validation and capture errors
        Set<String> validationErrors = new LinkedHashSet<>();
        openApiInteractionValidator.validateRequest(builder.build())
                .getMessages()
                .stream()
                .filter(validationMessage -> validationMessage.getLevel().equals(ValidationReport.Level.ERROR))
                .map(ValidationReport.Message::getMessage)
                .forEach(validationErrors::add);

        // Reset stream cache (if available) so it can be read again
        MessageHelper.resetStreamCache(message);

        return Collections.unmodifiableSet(validationErrors);
    }

    protected String resolvePathParams(Exchange exchange) {
        String path = restOpenApiOperation.getUriTemplate();
        Matcher matcher = REST_PATH_PARAM_PATTERN.matcher(path);
        String pathToProcess = path;
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String paramValue = exchange.getMessage().getHeader(paramName, String.class);
            if (ObjectHelper.isNotEmpty(paramValue)) {
                pathToProcess = pathToProcess.replace("{" + paramName + "}", UnsafeUriCharactersEncoder.encode(paramValue));
            }
        }
        return pathToProcess;
    }

    protected String getCharsetFromExchange(Exchange exchange) {
        String charset = null;
        if (exchange != null) {
            String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
            if (ObjectHelper.isNotEmpty(contentType)) {
                charset = IOHelper.getCharsetNameFromContentType(contentType);
                if (ObjectHelper.isEmpty(charset)) {
                    charset = exchange.getProperty(ExchangePropertyKey.CHARSET_NAME, String.class);
                }
            }
        }
        return charset;
    }
}
