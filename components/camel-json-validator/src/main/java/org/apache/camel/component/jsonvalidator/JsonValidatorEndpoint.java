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
package org.apache.camel.component.jsonvalidator;

import java.io.InputStream;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ValidationException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the payload of a message using Everit JSON schema validator.
 */
@ManagedResource(description = "Managed JsonValidatorEndpoint")
@UriEndpoint(scheme = "json-validator", firstVersion = "2.20.0", title = "JSON Schema Validator", syntax = "json-validator:resourceUri",
    producerOnly = true, label = "validation")
public class JsonValidatorEndpoint extends ResourceEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(JsonValidatorEndpoint.class);

    private volatile JsonSchema schema;

    @UriParam(defaultValue = "true")
    private boolean failOnNullBody = true;
    @UriParam(defaultValue = "true")
    private boolean failOnNullHeader = true;
    @UriParam(description = "To validate against a header instead of the message body.")
    private String headerName;
    @UriParam(label = "advanced")
    private JsonValidatorErrorHandler errorHandler = new DefaultJsonValidationErrorHandler();
    @UriParam(label = "advanced")
    private JsonSchemaLoader schemaLoader = new DefaultJsonSchemaLoader();

    public JsonValidatorEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component, resourceUri);
    }

    @Override
    public void clearContentCache() {
        this.schema = null;
        super.clearContentCache();
    }
    
    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }
    
    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        InputStream is = null;

        // Get a local copy of the current schema to improve concurrency.
        JsonSchema localSchema = this.schema;
        if (localSchema == null) {
            localSchema = getOrCreateSchema();
        }
        try {
            is = getContentToValidate(exchange, InputStream.class);
            if (shouldUseHeader()) {
                if (is == null && isFailOnNullHeader()) {
                    throw new NoJsonHeaderValidationException(exchange, headerName);
                }
            } else {
                if (is == null && isFailOnNullBody()) {
                    throw new NoJsonBodyValidationException(exchange);
                }
            }
            if (is != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(is);
                Set<ValidationMessage> errors = localSchema.validate(node);

                if (errors.size() > 0) {
                    LOG.debug("Validated JSon has {} errors", errors.size());
                    this.errorHandler.handleErrors(exchange, schema, errors);
                } else {
                    LOG.debug("Validated JSon success");
                }
            }
        } catch (Exception e) {
            if (e instanceof ValidationException) {
                // already as validation error
                throw e;
            } else {
                // general error
                this.errorHandler.handleErrors(exchange, schema, e);
            }
        } finally {
            IOHelper.close(is);
        }
    }
    
    private <T> T getContentToValidate(Exchange exchange, Class<T> clazz) {
        if (shouldUseHeader()) {
            return exchange.getIn().getHeader(headerName, clazz);
        } else {
            return exchange.getIn().getBody(clazz);
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
    private JsonSchema getOrCreateSchema() throws Exception {
        synchronized (this) {
            if (this.schema == null) {
                this.schema = this.schemaLoader.createSchema(getCamelContext(), this.getResourceAsInputStream());
            }
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

    /**
     * To use a custom ValidatorErrorHandler.
     * <p/>
     * The default error handler captures the errors and throws an exception.
     */
    public void setErrorHandler(JsonValidatorErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }
    
    public JsonSchemaLoader getSchemaLoader() {
        return schemaLoader;
    }
    
    /**
     * To use a custom schema loader allowing for adding custom format validation. See Everit JSON Schema documentation.
     * The default implementation will create a schema loader builder with draft v6 support.
     */
    public void setSchemaLoader(JsonSchemaLoader schemaLoader) {
        this.schemaLoader = schemaLoader;
    }

    public boolean isFailOnNullBody() {
        return failOnNullBody;
    }

    /**
     * Whether to fail if no body exists.
     */
    public void setFailOnNullBody(boolean failOnNullBody) {
        this.failOnNullBody = failOnNullBody;
    }

    public boolean isFailOnNullHeader() {
        return failOnNullHeader;
    }

    /**
     * Whether to fail if no header exists when validating against a header.
     */
    public void setFailOnNullHeader(boolean failOnNullHeader) {
        this.failOnNullHeader = failOnNullHeader;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * To validate against a header instead of the message body.
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}
