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
package org.apache.camel.impl.console;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;

import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "processor-detail", description = "Show configured options for all processors in a route")
public class ProcessorDetailDevConsole extends AbstractDevConsole {

    @Metadata(label = "query",
              description = "The route id to get processor details for (use * for all routes)",
              javaType = "java.lang.String")
    public static final String ROUTE_ID = "routeId";

    public record ProcessorEntry(
            @Metadata(description = "The processor ID") String id,
            @Metadata(description = "The processor type (the XML element name, or 'from' for the route input)") String type,
            @Metadata(description = "The endpoint URI (only present for the route input entry)") String endpointUri,
            @Metadata(description = "The source line number (only present when known)") Integer line,
            @Metadata(description = "The processor's configured options, as attribute/child-element name to string value") Map<String, String> options) {
    }

    public record RouteDetail(
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "The route's processors, starting with the route input") List<ProcessorEntry> processors) {
    }

    public record Response(
            @Metadata(description = "The route ID (only present when a specific route was requested)") String routeId,
            @Metadata(description = "The route's processors (only present when a specific route was requested)") List<ProcessorEntry> processors,
            @Metadata(description = "The route details for all routes (only present when routeId is * or omitted)") List<RouteDetail> routes) {
    }

    public ProcessorDetailDevConsole() {
        super("camel", "processor-detail", "Processor Detail", "Show configured options for all processors in a route");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        Response response = doCallResponse(options);
        StringBuilder sb = new StringBuilder();

        if (response.routes() != null) {
            for (RouteDetail route : response.routes()) {
                appendRouteText(sb, route.routeId(), route.processors());
            }
        } else {
            appendRouteText(sb, response.routeId(), response.processors());
        }
        return sb.toString();
    }

    private static void appendRouteText(StringBuilder sb, String routeId, List<ProcessorEntry> processors) {
        if (routeId != null) {
            sb.append(String.format("Route: %s%n", routeId));
            if (processors != null) {
                for (ProcessorEntry p : processors) {
                    sb.append(String.format("  %s (%s)%n", p.id(), p.type()));
                    Map<String, String> opts = p.options();
                    if (opts != null) {
                        for (Map.Entry<String, String> e : opts.entrySet()) {
                            sb.append(String.format("    %s = %s%n", e.getKey(), e.getValue()));
                        }
                    }
                }
            }
        }
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        return JsonRecordSupport.toJsonObject(doCallResponse(options));
    }

    private Response doCallResponse(Map<String, Object> options) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String routeId = optionString(options, ROUTE_ID);
        if (routeId == null || routeId.isBlank()) {
            routeId = subPath;
        }

        if (routeId == null || routeId.isBlank()) {
            routeId = "*";
        }

        ManagedCamelContext mcc
                = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc == null) {
            return new Response(null, null, null);
        }

        if ("*".equals(routeId)) {
            List<ManagedRouteMBean> managedRoutes = mcc.getManagedRoutes();
            if (managedRoutes == null || managedRoutes.isEmpty()) {
                return new Response(null, null, null);
            }
            List<RouteDetail> routes = new ArrayList<>();
            for (ManagedRouteMBean mr : managedRoutes) {
                RouteDetail routeDetail = buildRouteDetail(mr);
                if (routeDetail != null) {
                    routes.add(routeDetail);
                }
            }
            return new Response(null, null, routes);
        }

        ManagedRouteMBean mr = mcc.getManagedRoute(routeId);
        if (mr == null) {
            return new Response(null, null, null);
        }
        RouteDetail routeDetail = buildRouteDetail(mr);
        return new Response(routeDetail.routeId(), routeDetail.processors(), null);
    }

    private static RouteDetail buildRouteDetail(ManagedRouteMBean mr) {
        String routeId = mr.getRouteId();
        List<ProcessorEntry> processors = new ArrayList<>();

        Integer fromLine = null;

        try {
            String xml = mr.dumpRouteAsXml(false, true, true);
            if (xml != null && !xml.isBlank()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(xml)));
                Element routeElement = doc.getDocumentElement();

                // extract source line number for the from entry from the <from> child element
                NodeList fromNodes = routeElement.getElementsByTagName("from");
                if (fromNodes.getLength() > 0) {
                    fromLine = extractSourceLineNumber((Element) fromNodes.item(0));
                }

                collectProcessors(routeElement, processors);
            }
        } catch (Exception e) {
            // ignore
        }

        ProcessorEntry fromEntry = new ProcessorEntry(routeId, "from", mr.getEndpointUri(), fromLine, new LinkedHashMap<>());
        processors.add(0, fromEntry);

        return new RouteDetail(routeId, processors);
    }

    private static void collectProcessors(Element parent, List<ProcessorEntry> processors) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element elem = (Element) node;
            String id = elem.getAttribute("id");
            if (id == null || id.isEmpty()) {
                // recurse into structural elements that may contain processors
                collectProcessors(elem, processors);
                continue;
            }

            String type = elem.getTagName();
            // skip <from> elements — already handled as the manual fromEntry with endpointUri
            if ("from".equals(type)) {
                continue;
            }

            Integer line = extractSourceLineNumber(elem);

            Map<String, String> opts = new LinkedHashMap<>();
            NamedNodeMap attrs = elem.getAttributes();
            for (int j = 0; j < attrs.getLength(); j++) {
                Attr attr = (Attr) attrs.item(j);
                String name = attr.getName();
                if (!"id".equals(name) && !"customId".equals(name) && !name.startsWith("xmlns")
                        && !"sourceLineNumber".equals(name) && !"sourceLocation".equals(name)) {
                    opts.put(name, attr.getValue());
                }
            }

            // collect expression/language child elements (e.g. <simple>, <jsonpath>, <header>,
            // <correlationExpression>, <completionPredicate>, etc.)
            collectExpressionChildren(elem, opts);

            processors.add(new ProcessorEntry(id, type, null, line, opts));

            // recurse into child elements (nested EIPs like split > to, choice > when > to)
            collectProcessors(elem, processors);
        }
    }

    private static void collectExpressionChildren(Element parent, Map<String, String> opts) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;

            // skip child elements that are processors (have an id or contain processors with ids)
            if (hasProcessorDescendants(child)) {
                continue;
            }

            String tag = child.getTagName();
            String text = child.getTextContent();
            if (text != null) {
                text = text.trim();
            }

            // check if this is a wrapper element (e.g. <correlationExpression>, <completionPredicate>)
            // that contains a language element inside
            Element langChild = findLanguageChild(child);
            if (langChild != null) {
                String langTag = langChild.getTagName();
                String langText = langChild.getTextContent();
                if (langText != null) {
                    langText = langText.trim();
                }
                opts.put(tag, langTag + (langText != null && !langText.isEmpty() ? "(" + langText + ")" : ""));
            } else if (text != null && !text.isEmpty()) {
                // direct expression element (e.g. <simple>..., <jsonpath>..., <header>...)
                opts.put(tag, text);
            }
        }
    }

    private static boolean hasProcessorDescendants(Element elem) {
        String id = elem.getAttribute("id");
        if (id != null && !id.isEmpty()) {
            return true;
        }
        NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && hasProcessorDescendants((Element) node)) {
                return true;
            }
        }
        return false;
    }

    private static Element findLanguageChild(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) node;
            }
        }
        return null;
    }

    private static Integer extractSourceLineNumber(Element elem) {
        String lineStr = elem.getAttribute("sourceLineNumber");
        if (lineStr != null && !lineStr.isEmpty()) {
            try {
                return Integer.parseInt(lineStr);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return null;
    }
}
