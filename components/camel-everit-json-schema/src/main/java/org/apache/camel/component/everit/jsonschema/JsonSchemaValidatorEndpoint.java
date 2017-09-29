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
package org.apache.camel.component.everit.jsonschema;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;


/**
 * Validates the payload of a message using XML Schema and JAXP Validation.
 */
@ManagedResource(description = "Managed JSON ValidatorEndpoint")
@UriEndpoint(scheme = "json-validator", title = "JSON Schema Validator", syntax = "json-validator:resourceUri", producerOnly = true, label = "core,validation")
public class JsonSchemaValidatorEndpoint extends DefaultEndpoint {

    @UriPath(description = "URL to a local resource on the classpath, or a reference to lookup a bean in the Registry,"
            + " or a full URL to a remote resource or resource on the file system which contains the JSON Schema to validate against.")
    @Metadata(required = "true")
    private String resourceUri;
    @UriParam(label = "advanced", description = "To use a custom org.apache.camel.component.everit.jsonschema.JsonValidatorErrorHandler. " 
            + "The default error handler captures the errors and throws an exception.")
    private JsonValidatorErrorHandler errorHandler = new DefaultJsonValidationErrorHandler();
    @UriParam(defaultValue = "true", description = "Whether to fail if no body exists.")
    private boolean failOnNullBody = true;
    @UriParam(defaultValue = "true", description = "Whether to fail if no header exists when validating against a header.")
    private boolean failOnNullHeader = true;
    @UriParam(description = "To validate against a header instead of the message body.")
    private String headerName;

    /**
     * We need a one-to-one relation between endpoint and a Schema 
     * to be able to clear the cached schema. See method
     * {@link #clearCachedSchema}.
     */
    private Schema schema;

    public JsonSchemaValidatorEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component);
        this.resourceUri = resourceUri;
    }

    private Schema loadSchema() throws IOException {
        ObjectHelper.notNull(getCamelContext(), "camelContext");
        ObjectHelper.notNull(this.resourceUri, "resourceUri");
        try (InputStream inputStream = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), this.resourceUri)) {
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            // LOG.debug("JSON schema: {}", rawSchema);
            return SchemaLoader.load(rawSchema);
        }
    }

    @ManagedOperation(description = "Clears the cached schema, forcing to re-load the schema on next request")
    public void clearCachedSchema() {        
        this.schema = null; // will cause to reload the schema
    }
    
    @Override
    public Producer createProducer() throws Exception {
        if (this.schema == null) {
            this.schema = loadSchema();
        }
        JsonValidatingProcessor validator = new JsonValidatingProcessor(this.schema);
        configureValidator(validator);

        return new JsonSchemaValidatorProducer(this, validator);
    }

    private void configureValidator(JsonValidatingProcessor validator) {
        validator.setErrorHandler(errorHandler);
        validator.setFailOnNullBody(failOnNullBody);
        validator.setFailOnNullHeader(failOnNullHeader);
        validator.setHeaderName(headerName);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from validator");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    
    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * URL to a local resource on the classpath, or a reference to lookup a bean in the Registry,
     * or a full URL to a remote resource or resource on the file system which contains the JSON Schema to validate against.
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    
    public JsonValidatorErrorHandler getErrorHandler() {
        return errorHandler;
    }

    /**
     * To use a custom org.apache.camel.processor.validation.ValidatorErrorHandler.
     * <p/>
     * The default error handler captures the errors and throws an exception.
     */
    public void setErrorHandler(JsonValidatorErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
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
