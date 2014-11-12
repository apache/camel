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
package org.apache.camel.component.validator;

import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.ls.LSResourceResolver;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.processor.validation.DefaultValidationErrorHandler;
import org.apache.camel.processor.validation.ValidatingProcessor;
import org.apache.camel.processor.validation.ValidatorErrorHandler;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "validator")
public class ValidatorEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(ValidatorEndpoint.class);

    @UriPath
    private String resourceUri;
    @UriParam
    private String schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    @UriParam
    private SchemaFactory schemaFactory;
    @UriParam
    private ValidatorErrorHandler errorHandler = new DefaultValidationErrorHandler();
    @UriParam(defaultValue = "false")
    private boolean useDom;
    @UriParam(defaultValue = "true")
    private boolean useSharedSchema = true;
    @UriParam
    private LSResourceResolver resourceResolver;
    @UriParam(defaultValue = "true")
    private boolean failOnNullBody = true;
    @UriParam(defaultValue = "true")
    private boolean failOnNullHeader = true;
    @UriParam
    private String headerName;

    public ValidatorEndpoint() {
    }

    public ValidatorEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component);
        this.resourceUri = resourceUri;
    }

    @Override
    public Producer createProducer() throws Exception {
        ValidatingProcessor validator = new ValidatingProcessor();

        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext().getClassResolver(), resourceUri);
        byte[] bytes = null;
        try {
            bytes = IOConverter.toBytes(is);
        } finally {
            // and make sure to close the input stream after the schema has been loaded
            IOHelper.close(is);
        }

        validator.setSchemaAsByteArray(bytes);
        LOG.debug("{} using schema resource: {}", this, resourceUri);
        configureValidator(validator);

        // force loading of schema at create time otherwise concurrent
        // processing could cause thread safe issues for the javax.xml.validation.SchemaFactory
        validator.loadSchema();

        return new ValidatorProducer(this, validator);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from validator");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    protected void configureValidator(ValidatingProcessor validator) throws Exception {
        if (resourceResolver != null) {
            validator.setResourceResolver(resourceResolver);
        } else {
            validator.setResourceResolver(new DefaultLSResourceResolver(getCamelContext(), resourceUri));
        }
        validator.setSchemaLanguage(getSchemaLanguage());
        validator.setSchemaFactory(getSchemaFactory());
        validator.setErrorHandler(getErrorHandler());
        validator.setUseDom(isUseDom());
        validator.setUseSharedSchema(isUseSharedSchema());
        validator.setFailOnNullBody(isFailOnNullBody());
        validator.setFailOnNullHeader(isFailOnNullHeader());
        validator.setHeaderName(getHeaderName());
    }

    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * URL to a local resource on the classpath or a full URL to a remote resource or resource on the file system which contains the XSD to validate against.
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public String getSchemaLanguage() {
        return schemaLanguage;
    }

    public void setSchemaLanguage(String schemaLanguage) {
        this.schemaLanguage = schemaLanguage;
    }

    public SchemaFactory getSchemaFactory() {
        return schemaFactory;
    }

    public void setSchemaFactory(SchemaFactory schemaFactory) {
        this.schemaFactory = schemaFactory;
    }

    public ValidatorErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(ValidatorErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public boolean isUseDom() {
        return useDom;
    }

    public void setUseDom(boolean useDom) {
        this.useDom = useDom;
    }

    public boolean isUseSharedSchema() {
        return useSharedSchema;
    }

    public void setUseSharedSchema(boolean useSharedSchema) {
        this.useSharedSchema = useSharedSchema;
    }

    public LSResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    public void setResourceResolver(LSResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
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
}
