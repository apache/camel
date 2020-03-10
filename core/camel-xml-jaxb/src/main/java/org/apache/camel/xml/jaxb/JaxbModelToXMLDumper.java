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
package org.apache.camel.xml.jaxb;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.TypeConversionException;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.xml.XmlLineNumberParser;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

/**
 * JAXB based {@link ModelToXMLDumper}.
 */
@JdkService(ModelToXMLDumper.FACTORY)
public class JaxbModelToXMLDumper implements ModelToXMLDumper {

    @Override
    public String dumpModelAsXml(CamelContext context, NamedNode definition) throws Exception {
        JAXBContext jaxbContext = getJAXBContext(context);
        final Map<String, String> namespaces = new LinkedHashMap<>();

        // gather all namespaces from the routes or route which is stored on the
        // expression nodes
        if (definition instanceof RoutesDefinition) {
            List<RouteDefinition> routes = ((RoutesDefinition)definition).getRoutes();
            for (RouteDefinition route : routes) {
                extractNamespaces(route, namespaces);
            }
        } else if (definition instanceof RouteDefinition) {
            RouteDefinition route = (RouteDefinition)definition;
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

        // We invoke the type converter directly because we need to pass some
        // custom XML output options
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

    @Override
    public String dumpModelAsXml(CamelContext context, NamedNode definition, boolean resolvePlaceholders, boolean resolveDelegateEndpoints) throws Exception {
        String xml = dumpModelAsXml(context, definition);

        // if resolving placeholders we parse the xml, and resolve the property
        // placeholders during parsing
        if (resolvePlaceholders || resolveDelegateEndpoints) {
            final AtomicBoolean changed = new AtomicBoolean();
            InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            Document dom = XmlLineNumberParser.parseXml(is, new XmlLineNumberParser.XmlTextTransformer() {

                private String prev;

                @Override
                public String transform(String text) {
                    String after = text;
                    if (resolveDelegateEndpoints && "uri".equals(prev)) {
                        try {
                            // must resolve placeholder as the endpoint may use
                            // property placeholders
                            String uri = context.resolvePropertyPlaceholders(text);
                            Endpoint endpoint = context.hasEndpoint(uri);
                            if (endpoint instanceof DelegateEndpoint) {
                                endpoint = ((DelegateEndpoint)endpoint).getEndpoint();
                                after = endpoint.getEndpointUri();
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }

                    if (resolvePlaceholders) {
                        try {
                            after = context.resolvePropertyPlaceholders(after);
                        } catch (Exception e) {
                            // ignore
                        }
                    }

                    if (!changed.get()) {
                        changed.set(!text.equals(after));
                    }

                    // okay the previous must be the attribute key with uri, so
                    // it refers to an endpoint
                    prev = text;

                    return after;
                }
            });

            // okay there were some property placeholder or delegate endpoints
            // replaced so re-create the model
            if (changed.get()) {
                xml = context.getTypeConverter().mandatoryConvertTo(String.class, dom);
                ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
                NamedNode copy = ecc.getXMLRoutesDefinitionLoader().createModelFromXml(context, xml, NamedNode.class);
                xml = ecc.getModelToXMLDumper().dumpModelAsXml(context, copy);
            }
        }

        return xml;
    }

    private static JAXBContext getJAXBContext(CamelContext context) throws JAXBException {
        ModelJAXBContextFactory factory = context.adapt(ExtendedCamelContext.class).getModelJAXBContextFactory();
        return factory.newJAXBContext();
    }

    /**
     * Extract all XML namespaces from the root element in a DOM Document
     *
     * @param document the DOM document
     * @param namespaces the map of namespaces to add new found XML namespaces
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
     * Extract all XML namespaces from the expressions in the route
     *
     * @param route the route
     * @param namespaces the map of namespaces to add discovered XML namespaces
     *            into
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

    private static NamespaceAware getNamespaceAwareFromExpression(ExpressionNode expressionNode) {
        ExpressionDefinition ed = expressionNode.getExpression();

        NamespaceAware na = null;
        Expression exp = ed.getExpressionValue();
        if (exp instanceof NamespaceAware) {
            na = (NamespaceAware)exp;
        } else if (ed instanceof NamespaceAware) {
            na = (NamespaceAware)ed;
        }

        return na;
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
            xmlConverter = registry.getInjector().newInstance(XmlConverter.class, false);
        } else {
            xmlConverter = new XmlConverter();
        }
        return xmlConverter;
    }

}
