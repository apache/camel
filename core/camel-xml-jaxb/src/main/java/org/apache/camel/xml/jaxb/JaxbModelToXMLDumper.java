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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;

// TODO: camel4
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NamedNode;
import org.apache.camel.TypeConversionException;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.xml.XmlLineNumberParser;

import static org.apache.camel.xml.jaxb.JaxbHelper.extractNamespaces;
import static org.apache.camel.xml.jaxb.JaxbHelper.extractSourceLocations;
import static org.apache.camel.xml.jaxb.JaxbHelper.getJAXBContext;
import static org.apache.camel.xml.jaxb.JaxbHelper.modelToXml;
import static org.apache.camel.xml.jaxb.JaxbHelper.newXmlConverter;
import static org.apache.camel.xml.jaxb.JaxbHelper.removeAutoAssignedIds;
import static org.apache.camel.xml.jaxb.JaxbHelper.resolveEndpointDslUris;

/**
 * JAXB based {@link ModelToXMLDumper}.
 */
@JdkService(ModelToXMLDumper.FACTORY)
public class JaxbModelToXMLDumper implements ModelToXMLDumper {

    @Override
    public String dumpModelAsXml(CamelContext context, NamedNode definition) throws Exception {
        return doDumpModelAsXml(context, definition, true);
    }

    public String doDumpModelAsXml(CamelContext context, NamedNode definition, boolean generatedIds) throws Exception {
        final JAXBContext jaxbContext = getJAXBContext(context);
        final Map<String, String> namespaces = new LinkedHashMap<>();
        final Map<String, KeyValueHolder<Integer, String>> locations = new HashMap<>();

        // gather all namespaces from the routes or route which is stored on the
        // expression nodes
        if (definition instanceof RouteTemplatesDefinition) {
            List<RouteTemplateDefinition> templates = ((RouteTemplatesDefinition) definition).getRouteTemplates();
            for (RouteTemplateDefinition route : templates) {
                extractNamespaces(route.getRoute(), namespaces);
                if (context.isDebugging()) {
                    extractSourceLocations(route.getRoute(), locations);
                }
                resolveEndpointDslUris(route.getRoute());
            }
        } else if (definition instanceof RouteTemplateDefinition) {
            RouteTemplateDefinition template = (RouteTemplateDefinition) definition;
            extractNamespaces(template.getRoute(), namespaces);
            if (context.isDebugging()) {
                extractSourceLocations(template.getRoute(), locations);
            }
            resolveEndpointDslUris(template.getRoute());
        } else if (definition instanceof RoutesDefinition) {
            List<RouteDefinition> routes = ((RoutesDefinition) definition).getRoutes();
            for (RouteDefinition route : routes) {
                extractNamespaces(route, namespaces);
                if (context.isDebugging()) {
                    extractSourceLocations(route, locations);
                }
                resolveEndpointDslUris(route);
            }
        } else if (definition instanceof RouteDefinition) {
            RouteDefinition route = (RouteDefinition) definition;
            extractNamespaces(route, namespaces);
            if (context.isDebugging()) {
                extractSourceLocations(route, locations);
            }
            resolveEndpointDslUris(route);
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

        if (context.isDebugging()) {
            enrichLocations(dom, locations);
        }
        sanitizeXml(dom, generatedIds);

        // Add additional namespaces to the document root element
        Element documentElement = dom.getDocumentElement();
        for (Map.Entry<String, String> entry : namespaces.entrySet()) {
            String nsPrefix = entry.getKey();
            String prefix = nsPrefix.equals("xmlns") ? nsPrefix : "xmlns:" + nsPrefix;
            documentElement.setAttribute(prefix, entry.getValue());
        }

        // We invoke the type converter directly because we need to pass some
        // custom XML output options
        Properties outputProperties = new Properties();
        outputProperties.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
        outputProperties.put(OutputKeys.ENCODING, "UTF-8");
        try {
            return xmlConverter.toStringFromDocument(dom, outputProperties);
        } catch (TransformerException e) {
            throw new IllegalStateException("Failed converting document object to string", e);
        }
    }

    @Override
    public String dumpModelAsXml(
            CamelContext context, NamedNode definition, boolean resolvePlaceholders, boolean generatedIds)
            throws Exception {
        String xml = doDumpModelAsXml(context, definition, generatedIds);

        // if resolving placeholders we parse the xml, and resolve the property
        // placeholders during parsing
        if (resolvePlaceholders) {
            final AtomicBoolean changed = new AtomicBoolean();
            final InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            final Document dom = XmlLineNumberParser.parseXml(is, new XmlLineNumberParser.XmlTextTransformer() {

                @Override
                public String transform(String text) {
                    String after = text;

                    PropertiesComponent pc = context.getPropertiesComponent();
                    Properties prop = new Properties();
                    Iterator<?> it = null;
                    if (definition instanceof RouteDefinition) {
                        it = ObjectHelper.createIterator(definition);
                    } else if (definition instanceof RoutesDefinition) {
                        it = ObjectHelper.createIterator(((RoutesDefinition) definition).getRoutes());
                    }
                    while (it != null && it.hasNext()) {
                        RouteDefinition routeDefinition = (RouteDefinition) it.next();
                        // if the route definition was created via a route template then we need to prepare its parameters when the route is being created and started
                        if (routeDefinition.isTemplate() != null && routeDefinition.isTemplate()
                                && routeDefinition.getTemplateParameters() != null) {
                            prop.putAll(routeDefinition.getTemplateParameters());
                        }
                    }
                    pc.setLocalProperties(prop);
                    try {
                        after = context.resolvePropertyPlaceholders(after);
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        // clear local after the route is dumped
                        pc.setLocalProperties(null);
                    }

                    if (!changed.get()) {
                        changed.set(!text.equals(after));
                    }

                    return after;
                }
            });

            // okay there were some property placeholder or delegate endpoints
            // replaced so re-create the model
            if (changed.get()) {
                removeAutoAssignedIds(dom.getDocumentElement());
                xml = context.getTypeConverter().mandatoryConvertTo(String.class, dom);
                NamedNode copy = modelToXml(context, xml, NamedNode.class);
                xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, copy, false, generatedIds);
            }
        }

        return xml;
    }

    @Override
    public String dumpBeansAsXml(CamelContext context, List<Object> beans) throws Exception {
        StringWriter buffer = new StringWriter();
        BeanModelWriter writer = new BeanModelWriter(buffer);

        List<RegistryBeanDefinition> list = new ArrayList<>();
        for (Object bean : beans) {
            if (bean instanceof RegistryBeanDefinition rb) {
                list.add(rb);
            }
        }
        writer.setCamelContext(context);
        writer.start();
        try {
            writer.writeBeans(list);
        } finally {
            writer.stop();
        }

        return buffer.toString();
    }

    private static void sanitizeXml(Node node, boolean generatedIds) {
        // we want to remove all customId="false" attributes as they are noisy
        if (node.hasAttributes()) {
            Node att = node.getAttributes().getNamedItem("customId");
            boolean custom = att != null && "true".equals(att.getNodeValue());
            if (att != null) {
                node.getAttributes().removeNamedItem("customId");
            }
            if (!generatedIds && !custom) {
                // remove auto-generated ids
                Node attId = node.getAttributes().getNamedItem("id");
                if (attId != null) {
                    node.getAttributes().removeNamedItem("id");
                }
            }
        }
        if (node.hasChildNodes()) {
            for (int i = 0; i < node.getChildNodes().getLength(); i++) {
                Node child = node.getChildNodes().item(i);
                sanitizeXml(child, generatedIds);
            }
        }
    }

    private static void enrichLocations(Node node, Map<String, KeyValueHolder<Integer, String>> locations) {
        if (node instanceof Element) {
            Element el = (Element) node;

            // from should grab it from parent (route)
            String id = el.getAttribute("id");
            if ("from".equals(el.getNodeName())) {
                Node parent = el.getParentNode();
                if (parent instanceof Element) {
                    id = ((Element) parent).getAttribute("id");
                }
            }
            if (id != null) {
                var loc = locations.get(id);
                if (loc != null) {
                    el.setAttribute("sourceLineNumber", loc.getKey().toString());
                    el.setAttribute("sourceLocation", loc.getValue());
                }
            }
        }
        if (node.hasChildNodes()) {
            for (int i = 0; i < node.getChildNodes().getLength(); i++) {
                Node child = node.getChildNodes().item(i);
                enrichLocations(child, locations);
            }
        }
    }

    private static class BeanModelWriter implements CamelContextAware {

        private final StringWriter buffer;
        private CamelContext camelContext;

        public BeanModelWriter(StringWriter buffer) {
            this.buffer = buffer;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        public void start() {
            // noop
        }

        public void stop() {
            // noop
        }

        public void writeBeans(List<RegistryBeanDefinition> beans) {
            if (beans.isEmpty()) {
                return;
            }
            for (RegistryBeanDefinition b : beans) {
                doWriteRegistryBeanDefinition(b);
            }
        }

        private void doWriteRegistryBeanDefinition(RegistryBeanDefinition b) {
            String type = b.getType();
            if (type.startsWith("#class:")) {
                type = type.substring(7);
            }
            buffer.write(String.format("    <bean name=\"%s\" type=\"%s\">%n", b.getName(), type));
            if (b.getProperties() != null && !b.getProperties().isEmpty()) {
                buffer.write(String.format("        <properties>%n"));
                for (Map.Entry<String, Object> entry : b.getProperties().entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    buffer.write(String.format("            <property key=\"%s\" value=\"%s\"/>%n", key, value));
                }
                buffer.write(String.format("        </properties>%n"));
            }
            buffer.write(String.format("    </bean>%n"));
        }
    }

}
