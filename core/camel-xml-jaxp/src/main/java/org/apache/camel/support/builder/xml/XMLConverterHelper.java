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
package org.apache.camel.support.builder.xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XML converter support.
 */
public class XMLConverterHelper {

    private static final Logger LOG = LoggerFactory.getLogger(XMLConverterHelper.class);
    private static final ErrorHandler DOCUMENT_BUILDER_LOGGING_ERROR_HANDLER = new DocumentBuilderLoggingErrorHandler();
    private static final String DOCUMENT_BUILDER_FACTORY_FEATURE = "org.apache.camel.xmlconverter.documentBuilderFactory.feature";
    private static final String JDK_FALLBACK_TRANSFORMER_FACTORY = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";

    private volatile DocumentBuilderFactory documentBuilderFactory;
    private volatile TransformerFactory transformerFactory;

    public XMLConverterHelper() {
    }

    public XMLConverterHelper(DocumentBuilderFactory documentBuilderFactory) {
        this.documentBuilderFactory = documentBuilderFactory;
    }

    public void setDocumentBuilderFactory(DocumentBuilderFactory documentBuilderFactory) {
        this.documentBuilderFactory = documentBuilderFactory;
    }

    public TransformerFactory getTransformerFactory() {
        if (transformerFactory == null) {
            transformerFactory = createTransformerFactory();
        }
        return transformerFactory;
    }

    public void setTransformerFactory(TransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
    }

    public Document toDOMDocument(final Node node) throws ParserConfigurationException, TransformerException {
        ObjectHelper.notNull(node, "node");

        // If the node is the document, just cast it
        if (node instanceof Document) {
            return (Document)node;
            // If the node is an element
        } else if (node instanceof Element) {
            Element elem = (Element)node;
            // If this is the root element, return its owner document
            if (elem.getOwnerDocument().getDocumentElement() == elem) {
                return elem.getOwnerDocument();
                // else, create a new doc and copy the element inside it
            } else {
                Document doc = createDocument();
                // import node must not occur concurrent on the same node (must
                // be its owner)
                // so we need to synchronize on it
                synchronized (node.getOwnerDocument()) {
                    doc.appendChild(doc.importNode(node, true));
                }
                return doc;
            }
            // other element types are not handled
        } else {
            throw new TransformerException("Unable to convert DOM node to a Document: " + node);
        }
    }

    public DOMSource toDOMSource(Node node) throws ParserConfigurationException, TransformerException {
        Document document = toDOMDocument(node);
        return new DOMSource(document);
    }

    public DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        try {
            // Set secure processing
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        } catch (ParserConfigurationException e) {
            LOG.warn("DocumentBuilderFactory doesn't support the feature {} with value {}, due to {}.", new Object[] {XMLConstants.FEATURE_SECURE_PROCESSING, true, e});
        }
        try {
            // Disable the external-general-entities by default
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (ParserConfigurationException e) {
            LOG.warn("DocumentBuilderFactory doesn't support the feature {} with value {}, due to {}.",
                     new Object[] {"http://xml.org/sax/features/external-general-entities", false, e});
        }
        // setup the SecurityManager by default if it's apache xerces
        try {
            Class<?> smClass = ObjectHelper.loadClass("org.apache.xerces.util.SecurityManager");
            if (smClass != null) {
                Object sm = smClass.getDeclaredConstructor().newInstance();
                // Here we just use the default setting of the SeurityManager
                factory.setAttribute("http://apache.org/xml/properties/security-manager", sm);
            }
        } catch (Exception e) {
            LOG.warn("DocumentBuilderFactory doesn't support the attribute {}, due to {}.", new Object[] {"http://apache.org/xml/properties/security-manager", e});
        }
        // setup the feature from the system property
        setupFeatures(factory);
        return factory;
    }

    public TransformerFactory createTransformerFactory() {
        TransformerFactory factory;
        TransformerFactoryConfigurationError cause;
        try {
            factory = TransformerFactory.newInstance();
        } catch (TransformerFactoryConfigurationError e) {
            cause = e;
            // try fallback from the JDK
            try {
                LOG.debug("Cannot create/load TransformerFactory due: {}. Will attempt to use JDK fallback TransformerFactory: {}", e.getMessage(), JDK_FALLBACK_TRANSFORMER_FACTORY);
                factory = TransformerFactory.newInstance(JDK_FALLBACK_TRANSFORMER_FACTORY, null);
            } catch (Throwable t) {
                // okay we cannot load fallback then throw original exception
                throw cause;
            }
        }
        LOG.debug("Created TransformerFactory: {}", factory);

        // Enable the Security feature by default
        try {
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException e) {
            LOG.warn("TransformerFactory doesn't support the feature {} with value {}, due to {}.", javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, "true", e);
        }
        factory.setErrorListener(new XmlErrorListener());
        configureSaxonTransformerFactory(factory);
        return factory;
    }

    /**
     * Make a Saxon TransformerFactory more JAXP compliant by configuring it to
     * send &lt;xsl:message&gt; output to the ErrorListener.
     */
    private void configureSaxonTransformerFactory(TransformerFactory factory) {
        // check whether we have a Saxon TransformerFactory ("net.sf.saxon" for open source editions (HE / B)
        // and "com.saxonica" for commercial editions (PE / EE / SA))
        Class<?> factoryClass = factory.getClass();
        if (factoryClass.getName().startsWith("net.sf.saxon")
            || factoryClass.getName().startsWith("com.saxonica")) {

            // just in case there are multiple class loaders with different Saxon versions, use the
            // TransformerFactory's class loader to find Saxon support classes
            ClassLoader loader = factoryClass.getClassLoader();

            // try to find Saxon's MessageWarner class that redirects <xsl:message> to the ErrorListener
            Class<?> messageWarner = null;
            try {
                // Saxon >= 9.3
                messageWarner = loader.loadClass("net.sf.saxon.serialize.MessageWarner");
            } catch (ClassNotFoundException cnfe) {
                try {
                    // Saxon < 9.3 (including Saxon-B / -SA)
                    messageWarner = loader.loadClass("net.sf.saxon.event.MessageWarner");
                } catch (ClassNotFoundException cnfe2) {
                    LOG.warn("Error loading Saxon's net.sf.saxon.serialize.MessageWarner class from the classpath!"
                        + " <xsl:message> output will not be redirected to the ErrorListener!");
                }
            }

            if (messageWarner != null) {
                // set net.sf.saxon.FeatureKeys.MESSAGE_EMITTER_CLASS
                factory.setAttribute("http://saxon.sf.net/feature/messageEmitterClass", messageWarner.getName());
            }
        }
    }

    public Document createDocument() throws ParserConfigurationException {
        DocumentBuilder builder = createDocumentBuilder();
        return builder.newDocument();
    }

    public DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
        return createDocumentBuilder(getDocumentBuilderFactory());
    }

    public DocumentBuilderFactory getDocumentBuilderFactory() {
        if (documentBuilderFactory == null) {
            documentBuilderFactory = createDocumentBuilderFactory();
        }
        return documentBuilderFactory;
    }

    public DocumentBuilder createDocumentBuilder(DocumentBuilderFactory factory) throws ParserConfigurationException {
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(DOCUMENT_BUILDER_LOGGING_ERROR_HANDLER);
        return builder;
    }

    protected void setupFeatures(DocumentBuilderFactory factory) {
        Properties properties = System.getProperties();
        List<String> features = new ArrayList<>();
        for (Map.Entry<Object, Object> prop : properties.entrySet()) {
            String key = (String)prop.getKey();
            if (key.startsWith(DOCUMENT_BUILDER_FACTORY_FEATURE)) {
                String uri = StringHelper.after(key, ":");
                Boolean value = Boolean.valueOf((String)prop.getValue());
                try {
                    factory.setFeature(uri, value);
                    features.add("feature " + uri + " value " + value);
                } catch (ParserConfigurationException e) {
                    LOG.warn("DocumentBuilderFactory doesn't support the feature {} with value {}, due to {}.", uri, value, e);
                }
            }
        }
        if (features.size() > 0) {
            StringBuilder featureString = new StringBuilder();
            // just log the configured feature
            for (String feature : features) {
                if (featureString.length() != 0) {
                    featureString.append(", ");
                }
                featureString.append(feature);
            }
            LOG.info("DocumentBuilderFactory has been set with features {{}}.", featureString);
        }

    }

    private static class DocumentBuilderLoggingErrorHandler implements ErrorHandler {

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            LOG.warn(exception.getMessage(), exception);
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            LOG.error(exception.getMessage(), exception);
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            LOG.error(exception.getMessage(), exception);
        }
    }

    /**
     * A {@link javax.xml.transform.ErrorListener} that logs the errors.
     */
    private static class XmlErrorListener implements ErrorListener {

        @Override
        public void warning(TransformerException e) throws TransformerException {
            LOG.warn(e.getMessage(), e);
        }

        @Override
        public void error(TransformerException e) throws TransformerException {
            LOG.error(e.getMessage(), e);
        }

        @Override
        public void fatalError(TransformerException e) throws TransformerException {
            LOG.error(e.getMessage(), e);
        }
    }
}
