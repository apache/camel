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
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.smooks.Smooks;
import org.smooks.api.Registry;
import org.smooks.api.SmooksException;
import org.smooks.api.io.Source;
import org.smooks.api.resource.config.ResourceConfig;
import org.smooks.engine.lookup.ResourceConfigsLookup;
import org.smooks.engine.resource.config.DefaultConfigSearch;
import org.smooks.io.source.DOMSource;
import org.smooks.io.source.JavaSource;
import org.smooks.io.source.ReaderSource;
import org.smooks.io.source.StreamSource;
import org.smooks.io.source.URLSource;

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
 * declare their own reader class (for example EDI, CSV, JSON, or a custom reader) do not use the XML reader and are
 * left untouched.
 */
public final class SmooksSecuritySupport {

    static final String ORG_XML_SAX_DRIVER = "org.xml.sax.driver";

    private static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private static final DocumentBuilderFactory SECURE_DOCUMENT_BUILDER_FACTORY = createSecureDocumentBuilderFactory();

    private SmooksSecuritySupport() {
    }

    /**
     * Returns {@code true} when the given Smooks instance parses input with its default XML reader, i.e. the
     * configuration does not declare a reader class (such as EDI, CSV, JSON, or a custom reader). A reader declared
     * without a class only sets handlers, features or parameters of the default XML reader. Only the default XML reader
     * resolves XML external entities, so only that case needs hardening.
     */
    public static boolean usesDefaultXmlReader(Smooks smooks) {
        Registry registry = smooks.getApplicationContext().getRegistry();
        List<ResourceConfig> readerConfigs
                = registry.lookup(new ResourceConfigsLookup(registry, new DefaultConfigSearch().selector(ORG_XML_SAX_DRIVER)));
        return readerConfigs == null || readerConfigs.stream().allMatch(readerConfig -> readerConfig.getResource() == null);
    }

    /**
     * Parses the given XML input stream with a parser that does not resolve external entities and wraps it as a Smooks
     * {@link Source} backed by the resulting DOM.
     */
    public static Source secureXmlSource(InputStream inputStream) throws IOException, SAXException {
        return secureXmlSource(new InputSource(inputStream));
    }

    /**
     * Parses the XML of the given Smooks {@link Source} like {@link #secureXmlSource(InputStream)}. The bytes of a
     * stream or URL source are decoded with the given content encoding, as Smooks decodes them. A {@link DOMSource} or
     * a {@link JavaSource} holds no XML text and is returned as it is; any other type of source is rejected.
     */
    static Source secureXmlSource(Source source, String contentEncoding) throws IOException, SAXException {
        if (source instanceof DOMSource || source instanceof JavaSource) {
            return source;
        }
        if (source instanceof ReaderSource<?> readerSource) {
            return secureXmlSource(readerSource.getReader(), null);
        }
        if (source instanceof StreamSource<?> streamSource) {
            return secureXmlSource(toReader(streamSource.getInputStream(), contentEncoding), null);
        }
        if (source instanceof URLSource urlSource) {
            URL url = urlSource.getURL();
            try (InputStream inputStream = url.openStream()) {
                return secureXmlSource(toReader(inputStream, contentEncoding), url.toExternalForm());
            }
        }
        throw new SmooksException(
                "A message body of type " + source.getClass().getName()
                                  + " is not supported with allowExternalEntities=false. Use a String, byte[] or InputStream"
                                  + " body, or a StreamSource, ReaderSource, URLSource, DOMSource or JavaSource body.");
    }

    private static Source secureXmlSource(Reader reader, String systemId) throws IOException, SAXException {
        // Skip a leading byte order mark as the default reader of Smooks does: the JDK parser rejects it in characters
        PushbackReader pushbackReader = new PushbackReader(reader);
        int first = pushbackReader.read();
        if (first != -1 && first != BYTE_ORDER_MARK) {
            pushbackReader.unread(first);
        }
        InputSource inputSource = new InputSource(pushbackReader);
        inputSource.setSystemId(systemId);
        return secureXmlSource(inputSource);
    }

    private static Source secureXmlSource(InputSource inputSource) throws IOException, SAXException {
        DocumentBuilder documentBuilder;
        synchronized (SECURE_DOCUMENT_BUILDER_FACTORY) {
            try {
                documentBuilder = SECURE_DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new SAXException(e);
            }
        }
        Document document = documentBuilder.parse(inputSource);
        // The DOM reader of Smooks rejects a document type node, and the parser has already applied what it declares
        DocumentType documentType = document.getDoctype();
        if (documentType != null) {
            document.removeChild(documentType);
        }
        return new DOMSource(document);
    }

    private static Reader toReader(InputStream inputStream, String contentEncoding) throws UnsupportedEncodingException {
        return contentEncoding != null
                ? new InputStreamReader(inputStream, contentEncoding)
                : new InputStreamReader(inputStream, StandardCharsets.UTF_8);
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
