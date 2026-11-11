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
package org.apache.camel.component.flowable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.eventregistry.impl.serialization.StringToXmlDocumentDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deserializes the event of an inbound channel with the xml deserializer type into a {@link Document}.
 * <p/>
 * The parser does not load external DTDs, does not include external general or parameter entities, does not expand
 * entity references and does not process XInclude, which are the settings Flowable 8 uses for this deserializer (the
 * Flowable 7 deserializer leaves them at the JDK defaults). Secure processing is turned on, and access to external DTDs
 * and schemas is restricted, as in the other XML parsing of Camel.
 */
public class CamelStringToXmlDocumentDeserializer extends StringToXmlDocumentDeserializer {

    private static final Logger LOG = LoggerFactory.getLogger(CamelStringToXmlDocumentDeserializer.class);

    @Override
    public Document deserialize(Object rawEvent) {
        try {
            DocumentBuilder documentBuilder = createDocumentBuilderFactory().newDocumentBuilder();
            try (InputStream inputStream = new ByteArrayInputStream(convertEventToBytes(rawEvent))) {
                return documentBuilder.parse(inputStream);
            }
        } catch (Exception e) {
            throw new FlowableException("Could not deserialize event to xml", e);
        }
    }

    protected DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        setAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private static void setFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException e) {
            LOG.debug("DocumentBuilderFactory does not support the feature {} with value {}", feature, value, e);
        }
    }

    private static void setAttribute(DocumentBuilderFactory factory, String attribute, String value) {
        try {
            factory.setAttribute(attribute, value);
        } catch (IllegalArgumentException e) {
            LOG.debug("DocumentBuilderFactory does not support the attribute {}", attribute, e);
        }
    }
}
