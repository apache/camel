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
package org.apache.camel.component.schematron.processor;

import java.io.StringWriter;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import org.apache.camel.component.schematron.exception.SchematronValidationException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The schematoron Engine. Validates an XML for given scheamtron rules using an XSLT implementation of the Schematron
 * Engine.
 * <p/>
 */
public class SchematronProcessor {

    private Logger logger = LoggerFactory.getLogger(SchematronProcessor.class);
    private XMLReader reader;
    private Templates templates;

    /**
     * Constructor setting the XSLT schematron templates.
     *
     * @param reader
     * @param templates
     */
    public SchematronProcessor(XMLReader reader, Templates templates) {
        this.reader = reader;
        this.templates = templates;
    }

    /**
     * Validates the given XML for given Rules.
     *
     * @param xml
     * @return
     */
    public String validate(final String xml) {
        final Source source = new SAXSource(reader, new InputSource(IOUtils.toInputStream(xml)));
        return validate(source);
    }

    /**
     * Validates the given XML for given Rules.
     *
     * @param source
     * @return
     */
    public String validate(Source source) {
        try {
            final StringWriter writer = new StringWriter();
            templates.newTransformer().transform(source, new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            logger.error(e.getMessage());
            throw new SchematronValidationException("Failed to apply Schematron validation transform", e);
        }
    }
}
