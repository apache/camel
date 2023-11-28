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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Templates;

import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import org.apache.camel.component.schematron.exception.SchematronConfigException;

/**
 * Schematron Engine Factory
 *
 */
public final class SchematronProcessorFactory {

    /**
     * Private constructor.
     */
    private SchematronProcessorFactory() {
        throw new IllegalStateException();
    }

    /**
     * Creates an instance of SchematronEngine
     *
     * @param  rules the given schematron rules
     * @return       an instance of SchematronEngine
     */
    public static SchematronProcessor newSchematronEngine(final Templates rules) {
        try {
            return new SchematronProcessor(getXMLReader(), rules);
        } catch (Exception e) {
            throw new SchematronConfigException(e);
        }
    }

    /**
     * Gets XMLReader.
     */
    private static XMLReader getXMLReader() throws ParserConfigurationException, SAXException {
        final SAXParserFactory fac = SAXParserFactory.newInstance();
        fac.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        fac.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        fac.setFeature("http://xml.org/sax/features/external-general-entities", false);
        fac.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        fac.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        fac.setValidating(false);

        final SAXParser parser = fac.newSAXParser();
        XMLReader reader = parser.getXMLReader();
        return reader;
    }

}
