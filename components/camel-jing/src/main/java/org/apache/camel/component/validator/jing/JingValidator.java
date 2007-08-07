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
package org.apache.camel.component.validator.jing;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.thaiopensource.relaxng.SchemaFactory;
import com.thaiopensource.util.PropertyMap;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.IncorrectSchemaException;
import com.thaiopensource.validate.Schema;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.Validator;
import com.thaiopensource.xml.sax.Jaxp11XMLReaderCreator;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.processor.validation.DefaultValidationErrorHandler;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.io.Resource;

/**
 * A validator which uses the <a
 * href="http://www.thaiopensource.com/relaxng/jing.html">Jing</a> library to
 * validate XML against RelaxNG
 * 
 * @version $Revision: 1.1 $
 */
public class JingValidator implements Processor {
    private static final transient Log LOG = LogFactory.getLog(JingValidator.class);
    private Schema schema;
    private SchemaFactory schemaFactory;
    private String schemaNamespace = XMLConstants.RELAXNG_NS_URI;
    private Resource schemaResource;
    private InputSource inputSource;
    private boolean compactSyntax;

    public void process(Exchange exchange) throws Exception {
        Jaxp11XMLReaderCreator xmlCreator = new Jaxp11XMLReaderCreator();
        DefaultValidationErrorHandler errorHandler = new DefaultValidationErrorHandler();

        PropertyMapBuilder mapBuilder = new PropertyMapBuilder();
        mapBuilder.put(ValidateProperty.XML_READER_CREATOR, xmlCreator);
        mapBuilder.put(ValidateProperty.ERROR_HANDLER, errorHandler);
        PropertyMap propertyMap = mapBuilder.toPropertyMap();

        Validator validator = getSchema().createValidator(propertyMap);

        Message in = exchange.getIn();
        SAXSource saxSource = in.getBody(SAXSource.class);
        if (saxSource == null) {
            Source source = ExchangeHelper.getMandatoryInBody(exchange, Source.class);
            saxSource = ExchangeHelper.convertToMandatoryType(exchange, SAXSource.class, source);
        }
        InputSource bodyInput = saxSource.getInputSource();

        // now lets parse the body using the validator
        XMLReader reader = xmlCreator.createXMLReader();
        reader.setContentHandler(validator.getContentHandler());
        reader.setDTDHandler(validator.getDTDHandler());
        reader.setErrorHandler(errorHandler);
        reader.parse(bodyInput);

        errorHandler.handleErrors(exchange, schema);
    }

    // Properties
    // -------------------------------------------------------------------------

    public Schema getSchema() throws IOException, IncorrectSchemaException, SAXException {
        if (schema == null) {
            SchemaFactory factory = getSchemaFactory();
            schema = factory.createSchema(getInputSource());
        }
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    public InputSource getInputSource() throws IOException {
        if (inputSource == null) {
            Resource resource = getSchemaResource();
            if (resource == null) {
                throw new IllegalArgumentException("No schemaResource or inputSource specified");
            } else {
                InputStream inputStream = resource.getInputStream();
                if (inputStream == null) {
                    throw new IllegalArgumentException("No inputStream available for: " + resource);
                }
                inputSource = new InputSource(inputStream);
            }
        }
        return inputSource;
    }

    public void setInputSource(InputSource inputSource) {
        this.inputSource = inputSource;
    }

    public SchemaFactory getSchemaFactory() {
        if (schemaFactory == null) {
            schemaFactory = new SchemaFactory();
            schemaFactory.setCompactSyntax(compactSyntax);
            schemaFactory.setXMLReaderCreator(new Jaxp11XMLReaderCreator());
        }
        return schemaFactory;
    }

    public void setSchemaFactory(SchemaFactory schemaFactory) {
        this.schemaFactory = schemaFactory;
    }

    public Resource getSchemaResource() {
        return schemaResource;
    }

    public void setSchemaResource(Resource schemaResource) {
        this.schemaResource = schemaResource;
    }

    public String getSchemaNamespace() {
        return schemaNamespace;
    }

    public void setSchemaNamespace(String schemaNamespace) {
        this.schemaNamespace = schemaNamespace;
    }

    public boolean isCompactSyntax() {
        return compactSyntax;
    }

    public void setCompactSyntax(boolean compactSyntax) {
        this.compactSyntax = compactSyntax;
    }
}
