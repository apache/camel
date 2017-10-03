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
    @UriParam(label = "advanced", description = "To use a custom schema loader allowing for adding custom format validation. See the Everit JSON Schema documentation.")
    private JsonSchemaLoader schemaLoader = new DefaultJsonSchemaLoader();
    @UriParam(defaultValue = "true", description = "Whether to fail if no body exists.")
    private boolean failOnNullBody = true;
    @UriParam(defaultValue = "true", description = "Whether to fail if no header exists when validating against a header.")
    private boolean failOnNullHeader = true;
    @UriParam(description = "To validate against a header instead of the message body.")
    private String headerName;
    

    /**
     * We need a one-to-one relation between endpoint and a JsonSchemaReader 
     * to be able to clear the cached schema. See method
     * {@link #clearCachedSchema}.
     */
    private JsonSchemaReader schemaReader;

    public JsonSchemaValidatorEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component);
        this.resourceUri = resourceUri;
    }

    
    @ManagedOperation(description = "Clears the cached schema, forcing to re-load the schema on next request")
    public void clearCachedSchema() {        
        this.schemaReader.setSchema(null); // will cause to reload the schema
    }
    
    @Override
    public Producer createProducer() throws Exception {
        if (this.schemaReader == null) {
            this.schemaReader = new JsonSchemaReader(getCamelContext(), resourceUri, schemaLoader);
            // Load the schema once when creating the producer to fail fast if the schema is invalid.
            this.schemaReader.getSchema();
        }
        JsonValidatingProcessor validator = new JsonValidatingProcessor(this.schemaReader);
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
    
    public JsonSchemaLoader getSchemaLoader() {
        return schemaLoader;
    }
    
    /**
     * To use a custom schema loader allowing for adding custom format validation. See the Everit JSON Schema documentation.
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
