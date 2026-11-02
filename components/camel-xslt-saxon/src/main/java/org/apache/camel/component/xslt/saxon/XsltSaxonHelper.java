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
package org.apache.camel.component.xslt.saxon;

import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import net.sf.saxon.Configuration;
import net.sf.saxon.TransformerFactoryImpl;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.converter.jaxp.XMLReaderPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class XsltSaxonHelper {

    private static final Logger LOG = LoggerFactory.getLogger(XsltSaxonHelper.class);

    private static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private static final class SecureReaderPoolHolder {
        // Configured once: a SAXParserFactory on the classpath that does not recognise one of the required features fails
        // on first use of the holder rather than silently degrading on every exchange. The pool hands out a
        // reader for a single parse and is safe to share between threads.
        static final XMLReaderPool POOL = new XMLReaderPool(createSecureSaxParserFactory());
    }

    private XsltSaxonHelper() {
    }

    private static SAXParserFactory createSecureSaxParserFactory() {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
            factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
            factory.setFeature(LOAD_EXTERNAL_DTD, false);
            return factory;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException(
                    "Cannot configure a restricted SAXParserFactory for xslt-saxon input parsing", e);
        }
    }

    public static void registerSaxonConfiguration(TransformerFactoryImpl factory, Configuration saxonConfiguration) {
        if (saxonConfiguration != null) {
            factory.setConfiguration(saxonConfiguration);
        }
    }

    public static void registerSaxonConfigurationProperties(
            TransformerFactoryImpl factory,
            Map<String, Object> saxonConfigurationProperties) {
        if (saxonConfigurationProperties != null && !saxonConfigurationProperties.isEmpty()) {
            for (Map.Entry<String, Object> entry : saxonConfigurationProperties.entrySet()) {
                factory.getConfiguration().setConfigurationProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    public static void configureSecureProcessing(TransformerFactoryImpl factory, boolean secureProcessing)
            throws Exception {
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, secureProcessing);
    }

    /**
     * Restricts the external resource access of the given factory to the empty set, so that stylesheet compilation and
     * the {@code document()} function do not reach external resources. This is the same configuration the
     * {@code xslt-saxon} endpoint applies to the factory it creates; it is shared so the endpoint and
     * {@link XsltSaxonAggregationStrategy} cannot drift apart.
     */
    public static void configureExternalAccess(TransformerFactory factory) {
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (IllegalArgumentException e) {
            LOG.debug("TransformerFactory does not support {}", XMLConstants.ACCESS_EXTERNAL_DTD);
        }
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException e) {
            LOG.debug("TransformerFactory does not support {}", XMLConstants.ACCESS_EXTERNAL_STYLESHEET);
        }
    }

    /**
     * Returns a source whose parsing does not resolve references to external resources, for an input that is still to
     * be parsed. The factory's {@code ACCESS_EXTERNAL_*} attributes do not cover this parse, because the transformer
     * factory has a {@link javax.xml.transform.URIResolver} installed that resolves the reference itself, so the input
     * is parsed with a dedicated reader instead.
     * <p>
     * A {@link SAXSource} that already carries its own {@link XMLReader}, and any source that is not stream-based (for
     * example a {@code DOMSource} or a {@code StAXSource}), is returned unchanged; its content has already been parsed
     * or is read through a reader the caller controls. Only a stream-based source still to be parsed
     * ({@code StreamSource}, including Camel's {@code StringSource}/{@code BytesSource}, or a reader-less
     * {@code SAXSource}) is wrapped with the restricted reader.
     */
    public static Source secureSource(Source source) {
        if (source == null) {
            return null;
        }
        if (source instanceof SAXSource saxSource && saxSource.getXMLReader() != null) {
            return source;
        }
        InputSource inputSource = SAXSource.sourceToInputSource(source);
        if (inputSource == null) {
            return source;
        }
        try {
            XMLReader xmlReader = SecureReaderPoolHolder.POOL.createXMLReader();
            return new SAXSource(xmlReader, inputSource);
        } catch (ParserConfigurationException | SAXException e) {
            // Fail closed: this is the security control, so do not fall back to an unrestricted parse.
            throw new RuntimeCamelException(
                    "Cannot create a restricted XMLReader for the xslt-saxon input source", e);
        }
    }

    public static void registerSaxonExtensionFunctions(
            TransformerFactoryImpl factory,
            List<Object> saxonExtensionFunctions)
            throws Exception {
        if (saxonExtensionFunctions != null && !saxonExtensionFunctions.isEmpty()) {
            for (Object extensionFunction : saxonExtensionFunctions) {
                if (extensionFunction instanceof ExtensionFunctionDefinition) {
                    LOG.debug("Saxon.registerExtensionFunction {}", extensionFunction);
                    factory.getConfiguration().registerExtensionFunction((ExtensionFunctionDefinition) extensionFunction);
                }
            }
        }
    }

}
