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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.Binder;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.NamedNode;
import org.apache.camel.TypeConversionException;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.OutputDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.TemplatedRoutesDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.URISupport;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

public final class JaxbHelper {
    private static final String CAMEL_NS = "http://camel.apache.org/schema/spring";

    private JaxbHelper() {
    }

    public static JAXBContext getJAXBContext(CamelContext context) throws Exception {
        return (JAXBContext) PluginHelper.getModelJAXBContextFactory(context).newJAXBContext();
    }

    /**
     * Extract all XML namespaces from the expressions in the route
     *
     * @param route      the route
     * @param namespaces the map of namespaces to add discovered XML namespaces into
     */
    public static void extractNamespaces(RouteDefinition route, Map<String, String> namespaces) {
        Collection<ExpressionNode> col = filterTypeInOutputs(route.getOutputs(), ExpressionNode.class);
        for (ExpressionNode en : col) {
            NamespaceAware na = getNamespaceAwareFromExpression(en);
            if (na != null) {
                Map<String, String> map = na.getNamespaces();
                if (map != null && !map.isEmpty()) {
                    namespaces.putAll(map);
                }
            }
        }
    }

    /**
     * Extract all source locations from the route
     *
     * @param route     the route
     * @param locations the map of source locations for EIPs in the route
     */
    public static void extractSourceLocations(RouteDefinition route, Map<String, KeyValueHolder<Integer, String>> locations) {
        // input
        String id = route.getRouteId();
        String loc = route.getInput().getLocation();
        int line = route.getInput().getLineNumber();
        if (id != null && line != -1) {
            locations.put(id, new KeyValueHolder<>(line, loc));
        }
        // and then walk all nodes in the route graphs
        for (var def : filterTypeInOutputs(route.getOutputs(), OptionalIdentifiedDefinition.class)) {
            id = def.getId();
            loc = def.getLocation();
            line = def.getLineNumber();
            if (id != null && line != -1) {
                locations.put(id, new KeyValueHolder<>(line, loc));
            }
        }
    }

    /**
     * If the route has been built with endpoint-dsl, then the model will not have uri set which then cannot be included
     * in the JAXB model dump
     */
    public static void resolveEndpointDslUris(RouteDefinition route) {
        FromDefinition from = route.getInput();
        if (from != null && from.getEndpointConsumerBuilder() != null) {
            String uri = from.getEndpointConsumerBuilder().getRawUri();
            from.setUri(uri);
        }
        Collection<SendDefinition> col = filterTypeInOutputs(route.getOutputs(), SendDefinition.class);
        for (SendDefinition<?> to : col) {
            if (to.getEndpointProducerBuilder() != null) {
                String uri = to.getEndpointProducerBuilder().getRawUri();
                to.setUri(uri);
            }
        }
        Collection<ToDynamicDefinition> col2 = filterTypeInOutputs(route.getOutputs(), ToDynamicDefinition.class);
        for (ToDynamicDefinition to : col2) {
            if (to.getEndpointProducerBuilder() != null) {
                String uri = to.getEndpointProducerBuilder().getRawUri();
                to.setUri(uri);
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
        return new XmlConverter();
    }

    /**
     * Extract all XML namespaces from the root element in a DOM Document
     *
     * @param document   the DOM document
     * @param namespaces the map of namespaces to add new found XML namespaces
     */
    public static void extractNamespaces(Document document, Map<String, String> namespaces) {
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
        Collection<ExpressionNode> col = filterTypeInOutputs(route.getOutputs(), ExpressionNode.class);
        for (ExpressionNode en : col) {
            NamespaceAware na = getNamespaceAwareFromExpression(en);
            if (na != null) {
                na.setNamespaces(namespaces);
            }
        }
    }

    public static void applyNamespaces(RouteConfigurationDefinition config, Map<String, String> namespaces) {
        List<OutputDefinition<?>> defs = new ArrayList<>();
        defs.addAll(config.getIntercepts());
        defs.addAll(config.getInterceptFroms());
        defs.addAll(config.getInterceptSendTos());
        defs.addAll(config.getOnCompletions());
        defs.addAll(config.getOnExceptions());
        for (OutputDefinition<?> def : defs) {
            Collection<ExpressionNode> col = filterTypeInOutputs(def.getOutputs(), ExpressionNode.class);
            for (ExpressionNode en : col) {
                NamespaceAware na = getNamespaceAwareFromExpression(en);
                if (na != null) {
                    na.setNamespaces(namespaces);
                }
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
        removeNoiseFromUris(dom.getDocumentElement());

        JAXBContext jaxbContext = getJAXBContext(context);

        Map<String, String> namespaces = doExtractNamespaces(dom);

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

    public static RouteConfigurationsDefinition loadRouteConfigurationsDefinition(CamelContext context, InputStream inputStream)
            throws Exception {
        XmlConverter xmlConverter = newXmlConverter(context);
        Document dom = xmlConverter.toDOMDocument(inputStream, null);
        removeNoiseFromUris(dom.getDocumentElement());

        JAXBContext jaxbContext = getJAXBContext(context);

        Map<String, String> namespaces = doExtractNamespaces(dom);

        Binder<Node> binder = jaxbContext.createBinder();
        Object result = binder.unmarshal(dom);

        if (result == null) {
            throw new JAXBException("Cannot unmarshal to RouteConfigurationsDefinition using JAXB");
        }

        // can either be routes or a single route
        RouteConfigurationsDefinition answer;
        if (result instanceof RouteConfigurationDefinition) {
            RouteConfigurationDefinition config = (RouteConfigurationDefinition) result;
            answer = new RouteConfigurationsDefinition();
            applyNamespaces(config, namespaces);
            answer.getRouteConfigurations().add(config);
        } else if (result instanceof RouteConfigurationsDefinition) {
            answer = (RouteConfigurationsDefinition) result;
            for (RouteConfigurationDefinition config : answer.getRouteConfigurations()) {
                applyNamespaces(config, namespaces);
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
        removeNoiseFromUris(dom.getDocumentElement());

        JAXBContext jaxbContext = getJAXBContext(context);

        Map<String, String> namespaces = doExtractNamespaces(dom);

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

    private static Map<String, String> doExtractNamespaces(Document dom) {
        Map<String, String> namespaces = new LinkedHashMap<>();
        extractNamespaces(dom, namespaces);
        if (!namespaces.containsValue(CAMEL_NS)) {
            addNamespaceToDom(dom);
        }
        return namespaces;
    }

    /**
     * Un-marshals the content of the input stream to an instance of {@link TemplatedRoutesDefinition}.
     *
     * @param  context     the Camel context from which the JAXBContext is extracted
     * @param  inputStream the input stream to unmarshal
     * @return             the content unmarshalled as a {@link TemplatedRoutesDefinition}.
     * @throws Exception   if an exception occurs while unmarshalling
     */
    public static TemplatedRoutesDefinition loadTemplatedRoutesDefinition(CamelContext context, InputStream inputStream)
            throws Exception {
        XmlConverter xmlConverter = newXmlConverter(context);
        Document dom = xmlConverter.toDOMDocument(inputStream, null);
        removeNoiseFromUris(dom.getDocumentElement());

        JAXBContext jaxbContext = getJAXBContext(context);

        doExtractNamespaces(dom);

        Binder<Node> binder = jaxbContext.createBinder();
        Object result = binder.unmarshal(dom);

        if (result == null) {
            throw new JAXBException("Cannot unmarshal to TemplatedRoutesDefinition using JAXB");
        }

        // can either be routes or a single route
        TemplatedRoutesDefinition answer;
        if (result instanceof TemplatedRouteDefinition templatedRoute) {
            answer = new TemplatedRoutesDefinition();
            answer.getTemplatedRoutes().add(templatedRoute);
        } else if (result instanceof TemplatedRoutesDefinition) {
            answer = (TemplatedRoutesDefinition) result;
        } else {
            // ignore not supported type
            return null;
        }

        return answer;
    }

    private static void addNamespaceToDom(Document dom) {
        // Add the namespace URI to all elements
        Element root = dom.getDocumentElement();
        renameElementWithNamespace(dom, root, CAMEL_NS);
    }

    private static void renameElementWithNamespace(Document doc, Element elem, String camelNs) {
        doc.renameNode(elem, camelNs, elem.getLocalName());
        for (Node child = elem.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                renameElementWithNamespace(doc, (Element) child, camelNs);
            }
        }
    }

    public static RestsDefinition loadRestsDefinition(CamelContext context, InputStream inputStream) throws Exception {
        // load routes using JAXB
        Document dom = newXmlConverter(context).toDOMDocument(inputStream, null);
        removeNoiseFromUris(dom.getDocumentElement());

        if (!CAMEL_NS.equals(dom.getDocumentElement().getNamespaceURI())) {
            addNamespaceToDom(dom);
        }
        Unmarshaller unmarshaller = getJAXBContext(context).createUnmarshaller();
        Object result = unmarshaller.unmarshal(dom);

        if (result == null) {
            throw new IOException("Cannot unmarshal to rests using JAXB from input stream: " + inputStream);
        }

        // can either be routes or a single route
        RestsDefinition answer;
        if (result instanceof RestDefinition rest) {
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

    private static void removeNoiseFromUris(Element element) {
        final NamedNodeMap attrs = element.getAttributes();

        for (int index = 0; index < attrs.getLength(); index++) {
            final Attr attr = (Attr) attrs.item(index);
            final String attName = attr.getName();

            if (attName.equals("uri") || attName.endsWith("Uri")) {
                attr.setValue(URISupport.removeNoiseFromUri(attr.getValue()));
            }
        }

        final NodeList children = element.getChildNodes();

        for (int index = 0; index < children.getLength(); index++) {
            final Node child = children.item(index);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                removeNoiseFromUris((Element) child);
            }
        }
    }

    public static void removeAutoAssignedIds(Element element) {
        final NamedNodeMap attrs = element.getAttributes();

        Attr id = null;
        Attr customId = null;
        for (int index = 0; index < attrs.getLength(); index++) {
            final Attr attr = (Attr) attrs.item(index);
            final String attName = attr.getName();

            if (attName.equals("id")) {
                id = attr;
            } else if (attName.equals("customId")) {
                customId = attr;
            }
        }

        // remove auto-assigned id
        if (id != null && customId == null) {
            attrs.removeNamedItem("id");
        }
        // remove customId as its noisy
        if (customId != null) {
            attrs.removeNamedItem("customId");
        }

        final NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            final Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                removeAutoAssignedIds((Element) child);
            }
        }
    }

}
