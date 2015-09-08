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
package org.apache.camel.scr;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class.
 */
public final class ScrHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ScrHelper.class);

    private ScrHelper() {
    }

    public static Map<String, String> getScrProperties(String componentName) throws Exception {
        return getScrProperties(String.format("target/classes/OSGI-INF/%s.xml", componentName), componentName);
    }

    public static Map<String, String> getScrProperties(String xmlLocation, String componentName) throws Exception {
        Map<String, String> result = new HashMap<String, String>();

        final Document dom = readXML(new File(xmlLocation));
        final XPath xPath = XPathFactory.newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI, "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl", null).newXPath();
        xPath.setNamespaceContext(new ScrNamespaceContext(dom, xPath));

        String propertyListExpression = String.format("/components/scr:component[@name='%s']/property", componentName);
        XPathExpression propertyList = xPath.compile(propertyListExpression);
        XPathExpression propertyName = xPath.compile("@name");
        XPathExpression propertyValue = xPath.compile("@value");
        NodeList nodes = (NodeList) propertyList.evaluate(dom, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            result.put((String) propertyName.evaluate(node, XPathConstants.STRING), (String) propertyValue.evaluate(node, XPathConstants.STRING));
        }
        return result;
    }

    private static Document readXML(File xml) throws Exception {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        return builder.parse(xml);
    }

    private static final class ScrNamespaceContext implements NamespaceContext {

        private final Document dom;
        private final XPath xPath;

        private ScrNamespaceContext(Document dom, XPath xPath) {
            this.dom = dom;
            this.xPath = xPath;
        }

        @Override
        public String getNamespaceURI(String prefix) {
            switch (prefix) {
            case "scr":
                try {
                    XPathExpression scrNamespace = xPath.compile("/*/namespace::*[name()='scr']");
                    Node node = (Node) scrNamespace.evaluate(dom, XPathConstants.NODE);
                    return node.getNodeValue();
                } catch (XPathExpressionException e) {
                    // ignore
                    LOG.debug("Error evaluating xpath to obtain namespace prefix. This exception is ignored and using namespace: http://www.osgi.org/xmlns/scr/v1.1.0", e);
                }
                return "http://www.osgi.org/xmlns/scr/v1.1.0";
            default:
                // noop
            }
            return XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return null;
        }
    }

}

