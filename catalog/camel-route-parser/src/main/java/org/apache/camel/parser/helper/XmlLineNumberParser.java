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
package org.apache.camel.parser.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * An XML parser that uses SAX to include line and column number for each XML element in the parsed Document.
 * <p/>
 * The line number and column number can be obtained from a Node/Element using
 * <pre>
 *   String lineNumber = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER);
 *   String lineNumberEnd = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
 *   String columnNumber = (String) node.getUserData(XmlLineNumberParser.COLUMN_NUMBER);
 *   String columnNumberEnd = (String) node.getUserData(XmlLineNumberParser.COLUMN_NUMBER_END);
 * </pre>
 * Mind that start and end numbers are the same for single-level XML tags.
 */
public final class XmlLineNumberParser {

    public static final String LINE_NUMBER = "lineNumber";
    public static final String COLUMN_NUMBER = "colNumber";
    public static final String LINE_NUMBER_END = "lineNumberEnd";
    public static final String COLUMN_NUMBER_END = "colNumberEnd";

    private XmlLineNumberParser() {
    }

    /**
     * Parses the XML.
     *
     * @param is the XML content as an input stream
     * @return the DOM model
     * @throws Exception is thrown if error parsing
     */
    public static Document parseXml(final InputStream is) throws Exception {
        return parseXml(is, null, null);
    }

    /**
     * Parses the XML.
     *
     * @param is the XML content as an input stream
     * @param rootNames one or more root names that is used as baseline for beginning the parsing, for example camelContext to start parsing
     *                  when Camel is discovered. Multiple names can be defined separated by comma
     * @param forceNamespace an optional namespace to force assign to each node. This may be needed for JAXB unmarshalling from XML -> POJO.
     * @return the DOM model
     * @throws Exception is thrown if error parsing
     */
    public static Document parseXml(final InputStream is, final String rootNames, final String forceNamespace) throws Exception {
        final Document doc;
        SAXParser parser;
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        parser = factory.newSAXParser();
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // turn off validator and loading external dtd
        dbf.setValidating(false);
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://xml.org/sax/features/namespaces", false);
        dbf.setFeature("http://xml.org/sax/features/validation", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        final DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        doc = docBuilder.newDocument();

        final Stack<Element> elementStack = new Stack<>();
        final StringBuilder textBuffer = new StringBuilder();
        final DefaultHandler handler = new DefaultHandler() {
            private Locator locator;
            private boolean found;
            private final Map<String, String> localNs = new HashMap<>();
            private final Map<String, String> anonymousNs = new LinkedHashMap<>();

            @Override
            public void setDocumentLocator(final Locator locator) {
                this.locator = locator; // Save the locator, so that it can be used later for line tracking when traversing nodes.
                this.found = rootNames == null;
            }

            private boolean isRootName(String qName) {
                for (String root : rootNames.split(",")) {
                    if (qName.equals(root)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
                addTextIfNeeded();

                if (rootNames != null && !found) {
                    if (isRootName(qName)) {
                        found = true;
                    }
                }

                if (found) {
                    Element el;
                    if (forceNamespace != null) {
                        el = doc.createElementNS(forceNamespace, qName);
                    } else {
                        String ns = null;
                        // are we using namespace prefixes
                        int pos = qName.indexOf(':');
                        if (pos > 0) {
                            if (attributes != null) {
                                String prefix = qName.substring(0, pos);
                                ns = attributes.getValue("xmlns:" + prefix);
                                if (ns != null) {
                                    localNs.put(prefix, ns);
                                } else {
                                    ns = localNs.get(prefix);
                                }
                            }
                        } else {
                            // maybe there is an anonymous namespace (xmlns)
                            if (attributes != null) {
                                ns = attributes.getValue("xmlns");
                                if (ns != null) {
                                    anonymousNs.put(qName, ns);
                                } else if (!anonymousNs.isEmpty()) {
                                    // grab latest anonymous namespace to use as the namespace as
                                    // this child tag should use the parents+ namespace
                                    List<String> values = new ArrayList<>(anonymousNs.values());
                                    ns = values.get(values.size() - 1);
                                }
                            }
                        }
                        if (ns != null) {
                            el = doc.createElementNS(ns, qName);
                        } else {
                            el = doc.createElement(qName);
                        }
                    }

                    if (attributes != null) {
                        for (int i = 0; i < attributes.getLength(); i++) {
                            el.setAttribute(attributes.getQName(i), attributes.getValue(i));
                        }
                    }

                    String ln = String.valueOf(this.locator.getLineNumber());
                    String cn = String.valueOf(this.locator.getColumnNumber());
                    el.setUserData(LINE_NUMBER, ln, null);
                    el.setUserData(COLUMN_NUMBER, cn, null);
                    elementStack.push(el);
                }
            }

            @Override
            public void endElement(final String uri, final String localName, final String qName) {
                if (!found) {
                    return;
                }

                addTextIfNeeded();

                final Element closedEl = elementStack.isEmpty() ? null : elementStack.pop();
                if (closedEl != null) {
                    if (elementStack.isEmpty()) {
                        // Is this the root element?
                        doc.appendChild(closedEl);
                    } else {
                        final Element parentEl = elementStack.peek();
                        parentEl.appendChild(closedEl);
                    }

                    String ln = String.valueOf(this.locator.getLineNumber());
                    String cn = String.valueOf(this.locator.getColumnNumber());
                    closedEl.setUserData(LINE_NUMBER_END, ln, null);
                    closedEl.setUserData(COLUMN_NUMBER_END, cn, null);
                }

                anonymousNs.remove(qName);
            }

            @Override
            public void characters(final char ch[], final int start, final int length) throws SAXException {
                textBuffer.append(ch, start, length);
            }

            @Override
            public InputSource resolveEntity(String publicId, String systemId) throws IOException, SAXException {
                // do not resolve external dtd
                return new InputSource(new StringReader(""));
            }

            // Outputs text accumulated under the current node
            private void addTextIfNeeded() {
                if (textBuffer.length() > 0) {
                    final Element el = elementStack.isEmpty() ? null : elementStack.peek();
                    if (el != null) {
                        final Node textNode = doc.createTextNode(textBuffer.toString());
                        el.appendChild(textNode);
                        textBuffer.delete(0, textBuffer.length());
                    }
                }
            }
        };
        parser.parse(is, handler);

        return doc;
    }

}
