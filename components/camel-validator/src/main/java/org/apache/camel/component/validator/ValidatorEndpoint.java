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
package org.apache.camel.component.validator;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.ls.LSResourceResolver;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.processor.validation.DefaultValidationErrorHandler;
import org.apache.camel.support.processor.validation.SchemaReader;
import org.apache.camel.support.processor.validation.ValidatingProcessor;
import org.apache.camel.support.processor.validation.ValidatorErrorHandler;

/**
 * Validate the payload using XML Schema and JAXP Validation.
 */
@ManagedResource(description = "Managed ValidatorEndpoint")
@UriEndpoint(firstVersion = "1.1.0", scheme = "validator", title = "Validator", syntax = "validator:resourceUri",
             remote = false, producerOnly = true, category = { Category.CORE, Category.VALIDATION })
public class ValidatorEndpoint extends DefaultEndpoint {

    @UriPath(description = "URL to a local resource on the classpath, or a reference to lookup a bean in the Registry,"
                           + " or a full URL to a remote resource or resource on the file system which contains the XSD to validate against.")
    @Metadata(required = true, supportFileReference = true)
    private String resourceUri;
    @UriParam(defaultValue = XMLConstants.W3C_XML_SCHEMA_NS_URI, label = "advanced",
              description = "Configures the W3C XML Schema Namespace URI.")
    private String schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    @UriParam(label = "advanced", description = "To use a custom javax.xml.validation.SchemaFactory")
    private SchemaFactory schemaFactory;
    @UriParam(label = "advanced",
              description = "To use a custom org.apache.camel.processor.validation.ValidatorErrorHandler. The default error handler captures the errors and throws an exception.")
    private ValidatorErrorHandler errorHandler = new DefaultValidationErrorHandler();
    @UriParam(defaultValue = "true", label = "advanced",
              description = "Whether the Schema instance should be shared or not. This option is introduced to work around a JDK 1.6.x bug. Xerces should not have this issue.")
    private boolean useSharedSchema = true;
    @UriParam(label = "advanced",
              description = "To use a custom LSResourceResolver.  Do not use together with resourceResolverFactory")
    private LSResourceResolver resourceResolver;
    @UriParam(label = "advanced",
              description = "To use a custom LSResourceResolver which depends on a dynamic endpoint resource URI. " + //
                            "The default resource resolver factory resturns a resource resolver which can read files from the class path and file system. Do not use together with resourceResolver.")
    private ValidatorResourceResolverFactory resourceResolverFactory;
    @UriParam(defaultValue = "true", description = "Whether to fail if no body exists.")
    private boolean failOnNullBody = true;
    @UriParam(defaultValue = "true", description = "Whether to fail if no header exists when validating against a header.")
    private boolean failOnNullHeader = true;
    @UriParam(description = "To validate against a header instead of the message body.")
    private String headerName;

    /**
     * We need a one-to-one relation between endpoint and schema reader in order to be able to clear the cached schema
     * in the schema reader. See method {@link #clearCachedSchema}.
     */
    private final SchemaReader schemaReader;
    private volatile boolean schemaReaderConfigured;

    public ValidatorEndpoint() {
        schemaReader = new SchemaReader();
    }

    public ValidatorEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component);
        this.resourceUri = resourceUri;
        schemaReader = new SchemaReader(getCamelContext(), resourceUri);
    }

    @ManagedOperation(description = "Clears the cached schema, forcing to re-load the schema on next request")
    public void clearCachedSchema() {
        // will cause to reload the schema
        schemaReader.setSchema(null);
    }

    @Override
    public Producer createProducer() throws Exception {
        if (!schemaReaderConfigured) {
            if (resourceResolver != null) {
                schemaReader.setResourceResolver(resourceResolver);
            } else if (resourceResolverFactory != null) {
                resourceResolver = resourceResolverFactory.createResourceResolver(getCamelContext(), resourceUri);
                // set the created resource resolver to the resourceResolver variable, so that it can
                // be accessed by the endpoint
                schemaReader.setResourceResolver(resourceResolver);
            } else {
                schemaReader.setResourceResolver(
                        new DefaultValidatorResourceResolverFactory().createResourceResolver(getCamelContext(), resourceUri));
            }
            schemaReader.setSchemaLanguage(getSchemaLanguage());
            schemaReader.setSchemaFactory(getSchemaFactory());

            // force loading of schema at create time otherwise concurrent
            // processing could cause thread safe issues for the
            // javax.xml.validation.SchemaFactory
            schemaReader.loadSchema();

            // configure only once
            schemaReaderConfigured = true;
        }

        ValidatingProcessor validator = new ValidatingProcessor(schemaReader);
        configureValidator(validator);

        return new ValidatorProducer(this, validator);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from validator");
    }

    protected void configureValidator(ValidatingProcessor validator) throws Exception {
        validator.setErrorHandler(getErrorHandler());
        validator.setUseSharedSchema(isUseSharedSchema());
        validator.setFailOnNullBody(isFailOnNullBody());
        validator.setFailOnNullHeader(isFailOnNullHeader());
        validator.setHeaderName(getHeaderName());
    }

    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * URL to a local resource on the classpath,or a reference to lookup a bean in the Registry, or a full URL to a
     * remote resource or resource on the file system which contains the XSD to validate against.
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public String getSchemaLanguage() {
        return schemaLanguage;
    }

    /**
     * Configures the W3C XML Schema Namespace URI.
     */
    public void setSchemaLanguage(String schemaLanguage) {
        this.schemaLanguage = schemaLanguage;
    }

    public SchemaFactory getSchemaFactory() {
        return schemaFactory;
    }

    /**
     * To use a custom javax.xml.validation.SchemaFactory
     */
    public void setSchemaFactory(SchemaFactory schemaFactory) {
        this.schemaFactory = schemaFactory;
    }

    public ValidatorErrorHandler getErrorHandler() {
        return errorHandler;
    }

    /**
     * To use a custom org.apache.camel.processor.validation.ValidatorErrorHandler.
     * <p/>
     * The default error handler captures the errors and throws an exception.
     */
    public void setErrorHandler(ValidatorErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public boolean isUseSharedSchema() {
        return useSharedSchema;
    }

    /**
     * Whether the Schema instance should be shared or not. This option is introduced to work around a JDK 1.6.x bug.
     * Xerces should not have this issue.
     */
    public void setUseSharedSchema(boolean useSharedSchema) {
        this.useSharedSchema = useSharedSchema;
    }

    public LSResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    /**
     * To use a custom LSResourceResolver. See also
     * {@link #setResourceResolverFactory(ValidatorResourceResolverFactory)}
     */
    public void setResourceResolver(LSResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    public ValidatorResourceResolverFactory getResourceResolverFactory() {
        return resourceResolverFactory;
    }

    /**
     * For creating a resource resolver which depends on the endpoint resource URI. Must not be used in combination with
     * method {@link #setResourceResolver(LSResourceResolver)}. If not set then
     * {@link DefaultValidatorResourceResolverFactory} is used
     */
    public void setResourceResolverFactory(ValidatorResourceResolverFactory resourceResolverFactory) {
        this.resourceResolverFactory = resourceResolverFactory;
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
