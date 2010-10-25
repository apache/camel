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
package org.apache.camel.processor.validation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;

import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * A processor which validates the XML version of the inbound message body
 * against some schema either in XSD or RelaxNG
 * 
 * @version $Revision$
 */
public class ValidatingProcessor implements Processor {
    // for lazy creation of the Schema
    private String schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    private Schema schema;
    private Source schemaSource;
    private SchemaFactory schemaFactory;
    private URL schemaUrl;
    private File schemaFile;
    private ValidatorErrorHandler errorHandler = new DefaultValidationErrorHandler();
    private boolean useDom;
    private boolean useSharedSchema = true;

    public void process(Exchange exchange) throws Exception {
        Schema schema;
        if (isUseSharedSchema()) {
            schema = getSchema();
        } else {
            schema = createSchema();
        }

        Validator validator = schema.newValidator();

        Source source;
        Result result;
        if (useDom) {
            source = exchange.getIn().getBody(DOMSource.class);
            result = new DOMResult();
        } else {
            source = exchange.getIn().getBody(SAXSource.class);
            result = new SAXResult();
        }
        if (source == null) {
            throw new NoXmlBodyValidationException(exchange);
        }

        // create a new errorHandler and set it on the validator
        // must be a local instance to avoid problems with concurrency (to be thread safe)
        ValidatorErrorHandler handler = errorHandler.getClass().newInstance();
        validator.setErrorHandler(handler);

        try {
            validator.validate(source, result);
        } catch (SAXParseException e) {
            // can be thrown for non well formed XML
            throw new SchemaValidationException(exchange, schema, 
                                                Collections.singletonList(e), 
                                                Collections.<SAXParseException>emptyList(), 
                                                Collections.<SAXParseException>emptyList());
        }

        handler.handleErrors(exchange, schema, result);
    }

    public void loadSchema() throws Exception {
        // force loading of schema
        schema = createSchema();
    }

    // Properties
    // -----------------------------------------------------------------------

    public synchronized Schema getSchema() throws IOException, SAXException {
        if (schema == null) {
            schema = createSchema();
        }
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    public String getSchemaLanguage() {
        return schemaLanguage;
    }

    public void setSchemaLanguage(String schemaLanguage) {
        this.schemaLanguage = schemaLanguage;
    }

    public synchronized Source getSchemaSource() throws IOException {
        if (schemaSource == null) {
            schemaSource = createSchemaSource();
        }
        return schemaSource;
    }

    public void setSchemaSource(Source schemaSource) {
        this.schemaSource = schemaSource;
    }

    public URL getSchemaUrl() {
        return schemaUrl;
    }

    public void setSchemaUrl(URL schemaUrl) {
        this.schemaUrl = schemaUrl;
    }

    public File getSchemaFile() {
        return schemaFile;
    }

    public void setSchemaFile(File schemaFile) {
        this.schemaFile = schemaFile;
    }

    public synchronized SchemaFactory getSchemaFactory() {
        if (schemaFactory == null) {
            schemaFactory = createSchemaFactory();
        }
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

    /**
     * Sets whether DOMSource and DOMResult should be used, or
     * SaxSource and SaxResult.
     *
     * @param useDom true to use DOM otherwise Sax is used
     */
    public void setUseDom(boolean useDom) {
        this.useDom = useDom;
    }

    public boolean isUseSharedSchema() {
        return useSharedSchema;
    }

    public void setUseSharedSchema(boolean useSharedSchema) {
        this.useSharedSchema = useSharedSchema;
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    protected SchemaFactory createSchemaFactory() {
        return SchemaFactory.newInstance(schemaLanguage);
    }

    protected Source createSchemaSource() throws IOException {
        throw new IllegalArgumentException("You must specify either a schema, schemaFile, schemaSource or schemaUrl property");
    }

    protected Schema createSchema() throws SAXException, IOException {
        SchemaFactory factory = getSchemaFactory();

        URL url = getSchemaUrl();
        if (url != null) {
            return factory.newSchema(url);
        }
        File file = getSchemaFile();
        if (file != null) {
            return factory.newSchema(file);
        }
        return factory.newSchema(getSchemaSource());
    }

}
