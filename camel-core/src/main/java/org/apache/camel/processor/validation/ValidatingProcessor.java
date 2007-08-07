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

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.SAXException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * A processor which validates the XML version of the inbound message body
 * against some schema either in XSD or RelaxNG
 * 
 * @version $Revision: 453155 $
 */
public class ValidatingProcessor implements Processor {
    private Schema schema;
    private ValidatorErrorHandler errorHandler = new DefaultValidationErrorHandler();

    // for lazy creation of the Schema
    private String schemaLanguage = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    private Source schemaSource;
    private SchemaFactory schemaFactory;
    private URL schemaUrl;
    private File schemaFile;

    public void process(Exchange exchange) throws Exception {
        Schema schema = getSchema();
        Validator validator = schema.newValidator();

        Source source = exchange.getIn().getBody(DOMSource.class);
        if (source == null) {
            throw new NoXmlBodyValidationException(exchange);
        }

        // create a new errorHandler and set it on the validator
        errorHandler.reset();
        validator.setErrorHandler(errorHandler);

        DOMResult result = new DOMResult();
        validator.validate(source, result);

        errorHandler.handleErrors(exchange, schema, result);
        /*
         * Fault fault = exchange.createFault(); if (errorHandler.hasErrors()) { //
         * set the schema and source document as properties on the fault
         * fault.setProperty("org.apache.servicemix.schema", schema);
         * fault.setProperty("org.apache.servicemix.xml", source);
         * 
         *//*
             * check if this error handler supports the capturing of error
             * messages.
             *//*
             * if (errorHandler.capturesMessages()) {
             * 
             *//*
             * In descending order of preference select a format to use. If
             * neither DOMSource, StringSource or String are supported throw a
             * messaging exception.
             *//*
             * if (errorHandler.supportsMessageFormat(DOMSource.class)) {
             * fault.setContent( (DOMSource)
             * errorHandler.getMessagesAs(DOMSource.class)); } else if
             * (errorHandler.supportsMessageFormat(StringSource.class)) {
             * fault.setContent(sourceTransformer.toDOMSource( (StringSource)
             * errorHandler.getMessagesAs(StringSource.class))); } else if
             * (errorHandler.supportsMessageFormat(String.class)) {
             * fault.setContent( sourceTransformer.toDOMSource( new
             * StringSource( (String)
             * errorHandler.getMessagesAs(String.class)))); } else { throw new
             * MessagingException("MessageAwareErrorHandler implementation " +
             * errorHandler.getClass().getName() + " does not support a
             * compatible error message format."); } } else {
             *//*
             * we can't do much here if the ErrorHandler implementation does not
             * support capturing messages
             *//*
             * fault.setContent(new DOMSource(result.getNode(),
             * result.getSystemId())); } throw new FaultException("Failed to
             * validate against schema: " + schema, exchange, fault); } else { //
             * Retrieve the ouput of the validation // as it may have been
             * changed by the validator out.setContent(new
             * DOMSource(result.getNode(), result.getSystemId())); } }
             */
    }

    // Properties
    // -----------------------------------------------------------------------

    public Schema getSchema() throws IOException, SAXException {
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

    public Source getSchemaSource() throws IOException {
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

    public SchemaFactory getSchemaFactory() {
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

    // Implementation methods
    // -----------------------------------------------------------------------

    protected SchemaFactory createSchemaFactory() {
        return SchemaFactory.newInstance(schemaLanguage);
    }

    protected Source createSchemaSource() throws IOException {
        throw new IllegalArgumentException("You must specify a schema, "
                                           + "schemaFile, schemaSource or schemaUrl property");
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
