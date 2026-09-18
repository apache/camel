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
package org.apache.camel.component.smooks;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.smooks.Smooks;
import org.smooks.api.Registry;
import org.smooks.api.io.Source;
import org.smooks.api.resource.config.ResourceConfig;
import org.smooks.engine.lookup.ResourceConfigsLookup;
import org.smooks.engine.resource.config.DefaultConfigSearch;
import org.smooks.io.source.DOMSource;

/**
 * Secures the XML input handed to Smooks against XML external entity resolution.
 * <p/>
 * When Smooks parses XML with its default reader (Woodstox), external XML entities are resolved. Rather than
 * reconfiguring the Smooks reader - which is shared across every source type and would break the non-XML readers used
 * for marshalling and for EDI/CSV/JSON - this helper parses untrusted XML with a {@code DocumentBuilder} that does not
 * resolve external entities, and hands Smooks the resulting DOM. This matches the secure XML parser configuration
 * applied by the other Camel XML components.
 * <p/>
 * Hardening is only relevant when the Smooks configuration parses XML with its default reader. Configurations that
 * declare their own reader (for example EDI, CSV, JSON, or a custom reader) do not use the XML reader and are left
 * untouched.
 */
public final class SmooksSecuritySupport {

    static final String ORG_XML_SAX_DRIVER = "org.xml.sax.driver";

    private static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private static final DocumentBuilderFactory SECURE_DOCUMENT_BUILDER_FACTORY = createSecureDocumentBuilderFactory();

    private SmooksSecuritySupport() {
    }

    /**
     * Returns {@code true} when the given Smooks instance parses input with its default XML reader, i.e. the
     * configuration does not declare its own reader (such as EDI, CSV, JSON, or a custom reader). Only the default XML
     * reader resolves XML external entities, so only that case needs hardening.
     */
    public static boolean usesDefaultXmlReader(Smooks smooks) {
        Registry registry = smooks.getApplicationContext().getRegistry();
        List<ResourceConfig> readerConfigs
                = registry.lookup(new ResourceConfigsLookup(registry, new DefaultConfigSearch().selector(ORG_XML_SAX_DRIVER)));
        return readerConfigs == null || readerConfigs.isEmpty();
    }

    /**
     * Parses the given XML input stream with a parser that does not resolve external entities and wraps it as a Smooks
     * {@link Source} backed by the resulting DOM.
     */
    public static Source secureXmlSource(InputStream inputStream) throws IOException, SAXException {
        DocumentBuilder documentBuilder;
        synchronized (SECURE_DOCUMENT_BUILDER_FACTORY) {
            try {
                documentBuilder = SECURE_DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new SAXException(e);
            }
        }
        Document document = documentBuilder.parse(new InputSource(inputStream));
        return new DOMSource(document);
    }

    private static DocumentBuilderFactory createSecureDocumentBuilderFactory() {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        setFeature(documentBuilderFactory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeature(documentBuilderFactory, EXTERNAL_GENERAL_ENTITIES, false);
        setFeature(documentBuilderFactory, EXTERNAL_PARAMETER_ENTITIES, false);
        setFeature(documentBuilderFactory, LOAD_EXTERNAL_DTD, false);
        return documentBuilderFactory;
    }

    private static void setFeature(DocumentBuilderFactory documentBuilderFactory, String feature, boolean value) {
        try {
            documentBuilderFactory.setFeature(feature, value);
        } catch (ParserConfigurationException e) {
            // The parser in use does not recognise this feature; the remaining controls still provide protection
        }
    }
}
