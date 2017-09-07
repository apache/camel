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
package org.apache.camel.model;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.bind.Binder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.NamedNode;
import org.apache.camel.TypeConversionException;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

/**
 * Helper for the Camel {@link org.apache.camel.model model} classes.
 */
public final class ModelHelper {

    private ModelHelper() {
        // utility class
    }

    /**
     * Dumps the definition as XML
     *
     * @param context    the CamelContext, if <tt>null</tt> then {@link org.apache.camel.spi.ModelJAXBContextFactory} is not in use
     * @param definition the definition, such as a {@link org.apache.camel.NamedNode}
     * @return the output in XML (is formatted)
     * @throws JAXBException is throw if error marshalling to XML
     */
    public static String dumpModelAsXml(CamelContext context, NamedNode definition) throws JAXBException {
        JAXBContext jaxbContext = getJAXBContext(context);
        final Map<String, String> namespaces = new LinkedHashMap<>();

        // gather all namespaces from the routes or route which is stored on the expression nodes
        if (definition instanceof RoutesDefinition) {
            List<RouteDefinition> routes = ((RoutesDefinition) definition).getRoutes();
            for (RouteDefinition route : routes) {
                extractNamespaces(route, namespaces);
            }
        } else if (definition instanceof RouteDefinition) {
            RouteDefinition route = (RouteDefinition) definition;
            extractNamespaces(route, namespaces);
        }

        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
        StringWriter buffer = new StringWriter();
        marshaller.marshal(definition, buffer);

        XmlConverter xmlConverter = newXmlConverter(context);
        String xml = buffer.toString();
        Document dom;
        try {
            dom = xmlConverter.toDOMDocument(xml, null);
        } catch (Exception e) {
            throw new TypeConversionException(xml, Document.class, e);
        }

        // Add additional namespaces to the document root element
        Element documentElement = dom.getDocumentElement();
        for (String nsPrefix : namespaces.keySet()) {
            String prefix = nsPrefix.equals("xmlns") ? nsPrefix : "xmlns:" + nsPrefix;
            documentElement.setAttribute(prefix, namespaces.get(nsPrefix));
        }

        // We invoke the type converter directly because we need to pass some custom XML output options
        Properties outputProperties = new Properties();
        outputProperties.put(OutputKeys.INDENT, "yes");
        outputProperties.put(OutputKeys.STANDALONE, "yes");
        outputProperties.put(OutputKeys.ENCODING, "UTF-8");
        try {
            return xmlConverter.toStringFromDocument(dom, outputProperties);
        } catch (TransformerException e) {
            throw new IllegalStateException("Failed converting document object to string", e);
        }
    }

    /**
     * Marshal the xml to the model definition
     *
     * @param context the CamelContext, if <tt>null</tt> then {@link org.apache.camel.spi.ModelJAXBContextFactory} is not in use
     * @param xml     the xml
     * @param type    the definition type to return, will throw a {@link ClassCastException} if not the expected type
     * @return the model definition
     * @throws javax.xml.bind.JAXBException is thrown if error unmarshalling from xml to model
     */
    public static <T extends NamedNode> T createModelFromXml(CamelContext context, String xml, Class<T> type) throws JAXBException {
        return modelToXml(context, null, xml, type);
    }

    /**
     * Marshal the xml to the model definition
     *
     * @param context the CamelContext, if <tt>null</tt> then {@link org.apache.camel.spi.ModelJAXBContextFactory} is not in use
     * @param stream  the xml stream
     * @param type    the definition type to return, will throw a {@link ClassCastException} if not the expected type
     * @return the model definition
     * @throws javax.xml.bind.JAXBException is thrown if error unmarshalling from xml to model
     */
    public static <T extends NamedNode> T createModelFromXml(CamelContext context, InputStream stream, Class<T> type) throws JAXBException {
        return modelToXml(context, stream, null, type);
    }

    /**
     * Marshal the xml to the model definition
     *
     * @param context the CamelContext, if <tt>null</tt> then {@link org.apache.camel.spi.ModelJAXBContextFactory} is not in use
     * @param inputStream the xml stream
     * @throws Exception is thrown if an error is encountered unmarshalling from xml to model
     */
    public static RoutesDefinition loadRoutesDefinition(CamelContext context, InputStream inputStream) throws Exception {
        XmlConverter xmlConverter = newXmlConverter(context);
        Document dom = xmlConverter.toDOMDocument(inputStream, null);
        return loadRoutesDefinition(context, dom);
    }

    /**
     * Marshal the xml to the model definition
     *
     * @param context the CamelContext, if <tt>null</tt> then {@link org.apache.camel.spi.ModelJAXBContextFactory} is not in use
     * @param node the xml node
     * @throws Exception is thrown if an error is encountered unmarshalling from xml to model
     */
    public static RoutesDefinition loadRoutesDefinition(CamelContext context, Node node) throws Exception {
        JAXBContext jaxbContext = getJAXBContext(context);

        Map<String, String> namespaces = new LinkedHashMap<>();

        Document dom = node instanceof Document ? (Document) node : node.getOwnerDocument();
        extractNamespaces(dom, namespaces);

        Binder<Node> binder = jaxbContext.createBinder();
        Object result = binder.unmarshal(node);

        if (result == null) {
            throw new JAXBException("Cannot unmarshal to RoutesDefinition using JAXB");
        }

        // can either be routes or a single route
        RoutesDefinition answer;
        if (result instanceof RouteDefinition) {
            RouteDefinition route = (RouteDefinition) result;
            answer = new RoutesDefinition();
            applyNamespaces(route, namespaces);
            answer.getRoutes().add(route);
        } else if (result instanceof RoutesDefinition) {
            answer = (RoutesDefinition) result;
            for (RouteDefinition route : answer.getRoutes()) {
                applyNamespaces(route, namespaces);
            }
        } else {
            throw new IllegalArgumentException("Unmarshalled object is an unsupported type: " + ObjectHelper.className(result) + " -> " + result);
        }

        return answer;
    }

    private static <T extends NamedNode> T modelToXml(CamelContext context, InputStream is, String xml, Class<T> type) throws JAXBException {
        JAXBContext jaxbContext = getJAXBContext(context);

        XmlConverter xmlConverter = newXmlConverter(context);
        Document dom = null;
        try {
            if (is != null) {
                dom = xmlConverter.toDOMDocument(is, null);
            } else if (xml != null) {
                dom = xmlConverter.toDOMDocument(xml, null);
            }
        } catch (Exception e) {
            throw new TypeConversionException(xml, Document.class, e);
        }
        if (dom == null) {
            throw new IllegalArgumentException("InputStream and XML is both null");
        }

        Map<String, String> namespaces = new LinkedHashMap<>();
        extractNamespaces(dom, namespaces);

        Binder<Node> binder = jaxbContext.createBinder();
        Object result = binder.unmarshal(dom);

        if (result == null) {
            throw new JAXBException("Cannot unmarshal to " + type + " using JAXB");
        }

        // Restore namespaces to anything that's NamespaceAware
        if (result instanceof RoutesDefinition) {
            List<RouteDefinition> routes = ((RoutesDefinition) result).getRoutes();
            for (RouteDefinition route : routes) {
                applyNamespaces(route, namespaces);
            }
        } else if (result instanceof RouteDefinition) {
            RouteDefinition route = (RouteDefinition) result;
            applyNamespaces(route, namespaces);
        }

        return type.cast(result);
    }

    private static JAXBContext getJAXBContext(CamelContext context) throws JAXBException {
        JAXBContext jaxbContext;
        if (context == null) {
            jaxbContext = createJAXBContext();
        } else {
            jaxbContext = context.getModelJAXBContextFactory().newJAXBContext();
        }
        return jaxbContext;
    }

    private static void applyNamespaces(RouteDefinition route, Map<String, String> namespaces) {
        Iterator<ExpressionNode> it = filterTypeInOutputs(route.getOutputs(), ExpressionNode.class);
        while (it.hasNext()) {
            NamespaceAware na = getNamespaceAwareFromExpression(it.next());
            if (na != null) {
                na.setNamespaces(namespaces);
            }
        }
    }

    private static NamespaceAware getNamespaceAwareFromExpression(ExpressionNode expressionNode) {
        ExpressionDefinition ed = expressionNode.getExpression();

        NamespaceAware na = null;
        Expression exp = ed.getExpressionValue();
        if (exp instanceof NamespaceAware) {
            na = (NamespaceAware) exp;
        } else if (ed instanceof NamespaceAware) {
            na = (NamespaceAware) ed;
        }

        return na;
    }

    private static JAXBContext createJAXBContext() throws JAXBException {
        // must use classloader from CamelContext to have JAXB working
        return JAXBContext.newInstance(Constants.JAXB_CONTEXT_PACKAGES, CamelContext.class.getClassLoader());
    }

    /**
     * Extract all XML namespaces from the expressions in the route
     *
     * @param route       the route
     * @param namespaces  the map of namespaces to add discovered XML namespaces into
     */
    private static void extractNamespaces(RouteDefinition route, Map<String, String> namespaces) {
        Iterator<ExpressionNode> it = filterTypeInOutputs(route.getOutputs(), ExpressionNode.class);
        while (it.hasNext()) {
            NamespaceAware na = getNamespaceAwareFromExpression(it.next());

            if (na != null) {
                Map<String, String> map = na.getNamespaces();
                if (map != null && !map.isEmpty()) {
                    namespaces.putAll(map);
                }
            }
        }
    }

    /**
     * Extract all XML namespaces from the root element in a DOM Document
     *
     * @param document    the DOM document
     * @param namespaces  the map of namespaces to add new found XML namespaces
     */
    private static void extractNamespaces(Document document, Map<String, String> namespaces) throws JAXBException {
        NamedNodeMap attributes = document.getDocumentElement().getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node item = attributes.item(i);
            String nsPrefix = item.getNodeName();
            if (nsPrefix != null && nsPrefix.startsWith("xmlns")) {
                String nsValue = item.getNodeValue();
                String[] nsParts = nsPrefix.split(":");
                if (nsParts.length == 1) {
                    namespaces.put(nsParts[0], nsValue);
                } else if (nsParts.length == 2) {
                    namespaces.put(nsParts[1], nsValue);
                } else {
                    // Fallback on adding the namespace prefix as we find it
                    namespaces.put(nsPrefix, nsValue);
                }
            }
        }
    }

    /**
     * Creates a new {@link XmlConverter}
     *
     * @param context CamelContext if provided
     * @return a new XmlConverter instance
     */
    private static XmlConverter newXmlConverter(CamelContext context) {
        XmlConverter xmlConverter;
        if (context != null) {
            TypeConverterRegistry registry = context.getTypeConverterRegistry();
            xmlConverter = registry.getInjector().newInstance(XmlConverter.class);
        } else {
            xmlConverter = new XmlConverter();
        }
        return xmlConverter;
    }

}
