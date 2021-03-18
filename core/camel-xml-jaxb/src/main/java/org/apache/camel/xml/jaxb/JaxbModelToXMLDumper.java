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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.camel.CamelContext;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.TypeConversionException;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.xml.XmlLineNumberParser;

import static org.apache.camel.xml.jaxb.JaxbHelper.extractNamespaces;
import static org.apache.camel.xml.jaxb.JaxbHelper.getJAXBContext;
import static org.apache.camel.xml.jaxb.JaxbHelper.modelToXml;
import static org.apache.camel.xml.jaxb.JaxbHelper.newXmlConverter;

/**
 * JAXB based {@link ModelToXMLDumper}.
 */
@JdkService(ModelToXMLDumper.FACTORY)
public class JaxbModelToXMLDumper implements ModelToXMLDumper {

    @Override
    public String dumpModelAsXml(CamelContext context, NamedNode definition) throws Exception {
        final JAXBContext jaxbContext = getJAXBContext(context);
        final Map<String, String> namespaces = new LinkedHashMap<>();

        // gather all namespaces from the routes or route which is stored on the
        // expression nodes
        if (definition instanceof RouteTemplatesDefinition) {
            List<RouteTemplateDefinition> templates = ((RouteTemplatesDefinition) definition).getRouteTemplates();
            for (RouteTemplateDefinition route : templates) {
                extractNamespaces(route.getRoute(), namespaces);
            }
        } else if (definition instanceof RouteTemplateDefinition) {
            RouteTemplateDefinition template = (RouteTemplateDefinition) definition;
            extractNamespaces(template.getRoute(), namespaces);
        } else if (definition instanceof RoutesDefinition) {
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
        for (Map.Entry<String, String> entry : namespaces.entrySet()) {
            String nsPrefix = entry.getKey();

            String prefix = nsPrefix.equals("xmlns") ? nsPrefix : "xmlns:" + nsPrefix;
            documentElement.setAttribute(prefix, entry.getValue());
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
    public String dumpModelAsXml(
            CamelContext context, NamedNode definition, boolean resolvePlaceholders, boolean resolveDelegateEndpoints)
            throws Exception {
        String xml = dumpModelAsXml(context, definition);

        // if resolving placeholders we parse the xml, and resolve the property
        // placeholders during parsing
        if (resolvePlaceholders || resolveDelegateEndpoints) {
            final AtomicBoolean changed = new AtomicBoolean();
            final InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            final Document dom = XmlLineNumberParser.parseXml(is, new XmlLineNumberParser.XmlTextTransformer() {
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
                                endpoint = ((DelegateEndpoint) endpoint).getEndpoint();
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
                NamedNode copy = modelToXml(context, xml, NamedNode.class);
                xml = ecc.getModelToXMLDumper().dumpModelAsXml(context, copy);
            }
        }

        return xml;
    }

}
