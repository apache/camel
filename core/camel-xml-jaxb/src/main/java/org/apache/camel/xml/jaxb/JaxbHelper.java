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

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.Binder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.TypeConversionException;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spi.TypeConverterRegistry;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

public final class JaxbHelper {
    private JaxbHelper() {
    }

    public static JAXBContext getJAXBContext(CamelContext context) throws Exception {
        return (JAXBContext) context.adapt(ExtendedCamelContext.class).getModelJAXBContextFactory().newJAXBContext();
    }

    /**
     * Extract all XML namespaces from the expressions in the route
     *
     * @param route      the route
     * @param namespaces the map of namespaces to add discovered XML namespaces into
     */
    public static void extractNamespaces(RouteDefinition route, Map<String, String> namespaces) {
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

    public static NamespaceAware getNamespaceAwareFromExpression(ExpressionNode expressionNode) {
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

    /**
     * Creates a new {@link XmlConverter}
     *
     * @param  context CamelContext if provided
     * @return         a new XmlConverter instance
     */
    public static XmlConverter newXmlConverter(CamelContext context) {
        XmlConverter xmlConverter;
        if (context != null) {
            TypeConverterRegistry registry = context.getTypeConverterRegistry();
            xmlConverter = registry.getInjector().newInstance(XmlConverter.class, false);
        } else {
            xmlConverter = new XmlConverter();
        }
        return xmlConverter;
    }

    /**
     * Extract all XML namespaces from the root element in a DOM Document
     *
     * @param document   the DOM document
     * @param namespaces the map of namespaces to add new found XML namespaces
     */
    public static void extractNamespaces(Document document, Map<String, String> namespaces) throws JAXBException {
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

    public static void applyNamespaces(RouteDefinition route, Map<String, String> namespaces) {
        Iterator<ExpressionNode> it = filterTypeInOutputs(route.getOutputs(), ExpressionNode.class);
        while (it.hasNext()) {
            NamespaceAware na = getNamespaceAwareFromExpression(it.next());
            if (na != null) {
                na.setNamespaces(namespaces);
            }
        }
    }

    public static <T extends NamedNode> T modelToXml(CamelContext context, String xml, Class<T> type) throws Exception {
        JAXBContext jaxbContext = getJAXBContext(context);

        XmlConverter xmlConverter = newXmlConverter(context);
        Document dom;
        try {
            dom = xmlConverter.toDOMDocument(xml, null);
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
        if (result instanceof RouteTemplatesDefinition) {
            List<RouteTemplateDefinition> templates = ((RouteTemplatesDefinition) result).getRouteTemplates();
            for (RouteTemplateDefinition template : templates) {
                applyNamespaces(template.getRoute(), namespaces);
            }
        } else if (result instanceof RouteTemplateDefinition) {
            RouteTemplateDefinition template = (RouteTemplateDefinition) result;
            applyNamespaces(template.getRoute(), namespaces);
        } else if (result instanceof RoutesDefinition) {
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

    public static RoutesDefinition loadRoutesDefinition(CamelContext context, InputStream inputStream) throws Exception {
        XmlConverter xmlConverter = newXmlConverter(context);
        Document dom = xmlConverter.toDOMDocument(inputStream, null);

        JAXBContext jaxbContext = getJAXBContext(context);

        Map<String, String> namespaces = new LinkedHashMap<>();
        extractNamespaces(dom, namespaces);

        Binder<Node> binder = jaxbContext.createBinder();
        Object result = binder.unmarshal(dom);

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
            // ignore not supported type
            return null;
        }

        return answer;
    }

    public static RouteTemplatesDefinition loadRouteTemplatesDefinition(CamelContext context, InputStream inputStream)
            throws Exception {
        XmlConverter xmlConverter = newXmlConverter(context);
        Document dom = xmlConverter.toDOMDocument(inputStream, null);

        JAXBContext jaxbContext = getJAXBContext(context);

        Map<String, String> namespaces = new LinkedHashMap<>();
        extractNamespaces(dom, namespaces);

        Binder<Node> binder = jaxbContext.createBinder();
        Object result = binder.unmarshal(dom);

        if (result == null) {
            throw new JAXBException("Cannot unmarshal to RouteTemplatesDefinition using JAXB");
        }

        // can either be routes or a single route
        RouteTemplatesDefinition answer;
        if (result instanceof RouteTemplateDefinition) {
            RouteTemplateDefinition route = (RouteTemplateDefinition) result;
            answer = new RouteTemplatesDefinition();
            applyNamespaces(route.getRoute(), namespaces);
            answer.getRouteTemplates().add(route);
        } else if (result instanceof RouteTemplatesDefinition) {
            answer = (RouteTemplatesDefinition) result;
            for (RouteTemplateDefinition route : answer.getRouteTemplates()) {
                applyNamespaces(route.getRoute(), namespaces);
            }
        } else {
            // ignore not supported type
            return null;
        }

        return answer;
    }

    public static RestsDefinition loadRestsDefinition(CamelContext context, InputStream inputStream) throws Exception {
        // load routes using JAXB
        Unmarshaller unmarshaller = getJAXBContext(context).createUnmarshaller();
        Object result = unmarshaller.unmarshal(inputStream);

        if (result == null) {
            throw new IOException("Cannot unmarshal to rests using JAXB from input stream: " + inputStream);
        }

        // can either be routes or a single route
        RestsDefinition answer;
        if (result instanceof RestDefinition) {
            RestDefinition rest = (RestDefinition) result;
            answer = new RestsDefinition();
            answer.getRests().add(rest);
        } else if (result instanceof RestsDefinition) {
            answer = (RestsDefinition) result;
        } else {
            // ignore not supported type
            return null;
        }

        return answer;
    }
}
