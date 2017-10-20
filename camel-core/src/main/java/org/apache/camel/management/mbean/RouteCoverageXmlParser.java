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
package org.apache.camel.management.mbean;

import java.io.InputStream;
import java.util.Stack;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;

/**
 * An XML parser that uses SAX to enrich route stats in the route dump.
 * <p/>
 * The coverage details:
 * <ul>
 *     <li>exchangesTotal - Total number of exchanges</li>
 *     <li>totalProcessingTime - Total processing time in millis</li>
 * </ul>
 * Is included as attributes on the route nodes.
 */
public final class RouteCoverageXmlParser {

    private RouteCoverageXmlParser() {
    }

    /**
     * Parses the XML.
     *
     * @param camelContext the CamelContext
     * @param is           the XML content as an input stream
     * @return the DOM model of the routes with coverage information stored as attributes
     * @throws Exception is thrown if error parsing
     */
    public static Document parseXml(final CamelContext camelContext, final InputStream is) throws Exception {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final SAXParser parser = factory.newSAXParser();
        final DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        final Document doc = docBuilder.newDocument();

        final Stack<Element> elementStack = new Stack<Element>();
        final StringBuilder textBuffer = new StringBuilder();
        final DefaultHandler handler = new DefaultHandler() {

            @Override
            public void setDocumentLocator(final Locator locator) {
                // noop
            }

            @Override
            public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
                addTextIfNeeded();

                final Element el = doc.createElement(qName);
                // add other elements
                for (int i = 0; i < attributes.getLength(); i++) {
                    el.setAttribute(attributes.getQName(i), attributes.getValue(i));
                }

                String id = el.getAttribute("id");
                if (id != null) {
                    try {
                        if ("route".equals(qName)) {
                            ManagedRouteMBean route = camelContext.getManagedRoute(id, ManagedRouteMBean.class);
                            if (route != null) {
                                long total = route.getExchangesTotal();
                                el.setAttribute("exchangesTotal", "" + total);
                                long totalTime = route.getTotalProcessingTime();
                                el.setAttribute("totalProcessingTime", "" + totalTime);
                            }
                        } else if ("from".equals(qName)) {
                            // grab statistics from the parent route as from would be the same
                            Element parent = elementStack.peek();
                            if (parent != null) {
                                String routeId = parent.getAttribute("id");
                                ManagedRouteMBean route = camelContext.getManagedRoute(routeId, ManagedRouteMBean.class);
                                if (route != null) {
                                    long total = route.getExchangesTotal();
                                    el.setAttribute("exchangesTotal", "" + total);
                                    long totalTime = route.getTotalProcessingTime();
                                    el.setAttribute("totalProcessingTime", "" + totalTime);
                                    // from is index-0
                                    el.setAttribute("index", "0");
                                }
                            }
                        } else {
                            ManagedProcessorMBean processor = camelContext.getManagedProcessor(id, ManagedProcessorMBean.class);
                            if (processor != null) {
                                long total = processor.getExchangesTotal();
                                el.setAttribute("exchangesTotal", "" + total);
                                long totalTime = processor.getTotalProcessingTime();
                                el.setAttribute("totalProcessingTime", "" + totalTime);
                                int index = processor.getIndex();
                                el.setAttribute("index", "" + index);
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }

                // we do not want customId in output of the EIPs
                if (!"route".equals(qName)) {
                    el.removeAttribute("customId");
                }

                elementStack.push(el);
            }

            @Override
            public void endElement(final String uri, final String localName, final String qName) {
                addTextIfNeeded();
                final Element closedEl = elementStack.pop();
                if (elementStack.isEmpty()) {
                    // is this the root element?
                    doc.appendChild(closedEl);
                } else {
                    final Element parentEl = elementStack.peek();
                    parentEl.appendChild(closedEl);
                }
            }

            @Override
            public void characters(final char ch[], final int start, final int length) throws SAXException {
                textBuffer.append(ch, start, length);
            }

            /**
             * outputs text accumulated under the current node
             */
            private void addTextIfNeeded() {
                if (textBuffer.length() > 0) {
                    final Element el = elementStack.peek();
                    final Node textNode = doc.createTextNode(textBuffer.toString());
                    el.appendChild(textNode);
                    textBuffer.delete(0, textBuffer.length());
                }
            }
        };
        parser.parse(is, handler);

        return doc;
    }
}