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
package org.apache.camel.component.jsonvalidator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.StreamCache;
import org.apache.camel.ValidationException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validate the JSON in the message body or in a header against a JSON Schema, with the NetworkNT JSON Schema Validator.
 */
@ManagedResource(description = "Managed JsonValidatorEndpoint")
@UriEndpoint(scheme = "json-validator", firstVersion = "2.20.0", title = "JSON Schema Validator",
             syntax = "json-validator:resourceUri",
             remote = false, producerOnly = true, category = { Category.VALIDATION })
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  format = "json", dependsOn = "headerName",
                                  byOption = @BodyType.When(option = "failOnNullBody", value = "false",
                                                            carriers = {
                                                                    Carrier.NULL, Carrier.TEXT, Carrier.BYTES,
                                                                    Carrier.STREAM, Carrier.FILE }),
                                  description = "The JSON to validate: text, bytes, a stream or a file. A Map, a POJO or no"
                                                + " body fails: marshal it first; with headerName the header is validated"
                                                + " instead"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   description = "Unchanged: JSON that does not match the schema, or text that is not JSON,"
                                                 + " fails with a JsonValidationException"))
public class JsonValidatorEndpoint extends ResourceEndpoint {

    private volatile Schema schema;

    @UriParam(defaultValue = "true",
              description = "Whether to fail with a NoJsonBodyValidationException when the message has no body. If false, a"
                            + " message without a body is not validated and continues unchanged. Not used with headerName,"
                            + " where failOnNullHeader applies instead.")
    private boolean failOnNullBody = true;
    @UriParam(defaultValue = "true",
              description = "Whether to fail with a NoJsonHeaderValidationException when the header of the headerName option"
                            + " is missing or null. If false, the message is then not validated and continues unchanged. Only"
                            + " used with headerName.")
    private boolean failOnNullHeader = true;
    @UriParam(description = "The name of the header that holds the JSON to validate, in place of the message body. If not"
                            + " set, the message body is validated.")
    private String headerName;
    @UriParam(label = "advanced",
              description = "A custom JsonValidatorErrorHandler that receives the schema validation errors, or the exception"
                            + " when the JSON cannot be read or parsed. The default handler throws a JsonValidationException."
                            + " A handler that does not throw lets the message continue.")
    private JsonValidatorErrorHandler errorHandler = new DefaultJsonValidationErrorHandler();
    @UriParam(label = "advanced",
              description = "A custom JsonUriSchemaLoader that creates the schema from the resourceUri, for example to add"
                            + " custom formats or keywords. The default loader takes the JSON Schema version from the $schema"
                            + " property of the schema, and uses draft 2019-09 when $schema is missing or unknown.")
    private JsonUriSchemaLoader uriSchemaLoader = new DefaultJsonUriSchemaLoader();
    @UriParam(label = "advanced",
              description = "Comma-separated list of Jackson 3 DeserializationFeature names, such as FAIL_ON_TRAILING_TOKENS,"
                            + " to enable on a copy of the ObjectMapper that parses the JSON to validate. An unknown name fails"
                            + " the start of the endpoint. The schema itself is parsed with a separate mapper.")
    private String enabledDeserializationFeatures;
    @UriParam(label = "advanced",
              description = "Comma-separated list of Jackson 3 DeserializationFeature names to disable on a copy of the"
                            + " ObjectMapper that parses the JSON to validate. An unknown name fails the start of the endpoint."
                            + " The schema itself is parsed with a separate mapper.")
    private String disabledDeserializationFeatures;
    @UriParam(label = "advanced",
              description = "The Jackson 3 (tools.jackson) ObjectMapper that parses the JSON to validate. If not set, the"
                            + " objectMapper and useDefaultObjectMapper options of the component decide, and otherwise the"
                            + " endpoint creates a mapper with the Jackson 2 defaults.")
    private ObjectMapper objectMapper;

    public JsonValidatorEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component, resourceUri);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public void clearContentCache() {
        this.schema = null;
        super.clearContentCache();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (objectMapper == null) {
            // the Jackson 2 defaults, so the same bodies pass or fail as before Jackson 3
            objectMapper = JsonMapper.builderWithJackson2Defaults().build();
        }
        if (enabledDeserializationFeatures != null || disabledDeserializationFeatures != null) {
            // a Jackson 3 mapper is immutable: configure a copy
            MapperBuilder<?, ?> builder = objectMapper.rebuild();
            for (DeserializationFeature feature : features(enabledDeserializationFeatures)) {
                builder.enable(feature);
            }
            for (DeserializationFeature feature : features(disabledDeserializationFeatures)) {
                builder.disable(feature);
            }
            objectMapper = builder.build();
        }
    }

    private static List<DeserializationFeature> features(String names) {
        List<DeserializationFeature> answer = new ArrayList<>();
        if (names == null) {
            return answer;
        }
        for (String name : names.split(",")) {
            name = name.trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                answer.add(DeserializationFeature.valueOf(name));
            } catch (IllegalArgumentException e) {
                // Jackson 3 moved some features, such as READ_ENUMS_USING_TO_STRING, to EnumFeature or DateTimeFeature
                throw new IllegalArgumentException(
                        "Unknown deserialization feature: " + name + ". Use the names of "
                                                   + DeserializationFeature.class.getName(),
                        e);
            }
        }
        return answer;
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        StreamCache cache = null;

        // if the content is an input stream then its likely not re-readable so we need to make it stream cached
        Object content = getContentToValidate(exchange);
        if (!(content instanceof StreamCache) && content instanceof InputStream) {
            cache = exchange.getContext().getTypeConverter().convertTo(StreamCache.class, exchange, content);
            if (cache != null) {
                if (shouldUseHeader()) {
                    exchange.getIn().setHeader(headerName, cache);
                } else {
                    exchange.getIn().setBody(cache);
                }
            }
        }

        // Get a local copy of the current schema to improve concurrency.
        Schema localSchema = this.schema;
        if (localSchema == null) {
            localSchema = getOrCreateSchema();
        }
        try {
            if (shouldUseHeader()) {
                if (content == null && isFailOnNullHeader()) {
                    throw new NoJsonHeaderValidationException(exchange, headerName);
                }
            } else {
                if (content == null && isFailOnNullBody()) {
                    throw new NoJsonBodyValidationException(exchange);
                }
            }
            if (content != null) {
                // favour using stream caching
                if (cache == null) {
                    cache = exchange.getContext().getTypeConverter().convertTo(StreamCache.class, exchange, content);
                }
                try (InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange,
                        cache != null ? cache : content)) {
                    JsonNode node = objectMapper.readTree(is);
                    if (node == null) {
                        throw new NoJsonBodyValidationException(exchange);
                    }
                    List<Error> errors = localSchema.validate(node);

                    if (!errors.isEmpty()) {
                        this.log.debug("Validated JSON has {} errors", errors.size());
                        this.errorHandler.handleErrors(exchange, schema, new HashSet<>(errors));
                    } else {
                        this.log.debug("Validated JSON success");
                    }
                }
            }
        } catch (ValidationException e) {
            throw e; // already as validation error
        } catch (Exception e) {
            // general error
            this.errorHandler.handleErrors(exchange, schema, e);
        } finally {
            if (cache != null) {
                cache.reset();
            }
        }
    }

    private Object getContentToValidate(Exchange exchange) {
        if (shouldUseHeader()) {
            return exchange.getIn().getHeader(headerName);
        } else {
            return exchange.getIn().getBody();
        }
    }

    private boolean shouldUseHeader() {
        return headerName != null;
    }

    /**
     * Synchronized method to create a schema if is does not already exist.
     *
     * @return The currently loaded schema
     */
    private Schema getOrCreateSchema() throws Exception {
        getInternalLock().lock();
        try {
            if (this.schema == null) {
                this.schema = this.uriSchemaLoader.createSchema(getCamelContext(), getResourceUri());
            }
        } finally {
            getInternalLock().unlock();
        }
        return this.schema;
    }

    @Override
    protected String createEndpointUri() {
        return "json-validator:" + getResourceUri();
    }

    public JsonValidatorErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(JsonValidatorErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public JsonUriSchemaLoader getUriSchemaLoader() {
        return uriSchemaLoader;
    }

    public void setUriSchemaLoader(JsonUriSchemaLoader uriSchemaLoader) {
        this.uriSchemaLoader = uriSchemaLoader;
    }

    public boolean isFailOnNullBody() {
        return failOnNullBody;
    }

    public void setFailOnNullBody(boolean failOnNullBody) {
        this.failOnNullBody = failOnNullBody;
    }

    public boolean isFailOnNullHeader() {
        return failOnNullHeader;
    }

    public void setFailOnNullHeader(boolean failOnNullHeader) {
        this.failOnNullHeader = failOnNullHeader;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getEnabledDeserializationFeatures() {
        return enabledDeserializationFeatures;
    }

    public void setEnabledDeserializationFeatures(String enabledDeserializationFeatures) {
        this.enabledDeserializationFeatures = enabledDeserializationFeatures;
    }

    public String getDisabledDeserializationFeatures() {
        return disabledDeserializationFeatures;
    }

    public void setDisabledDeserializationFeatures(String disabledDeserializationFeatures) {
        this.disabledDeserializationFeatures = disabledDeserializationFeatures;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
}
