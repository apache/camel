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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "processor-detail", description = "Show configured options for all processors in a route")
public class ProcessorDetailDevConsole extends AbstractDevConsole {

    @Metadata(label = "query",
              description = "The route id to get processor details for (use * for all routes)",
              javaType = "java.lang.String")
    public static final String ROUTE_ID = "routeId";

    public ProcessorDetailDevConsole() {
        super("camel", "processor-detail", "Processor Detail", "Show configured options for all processors in a route");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        JsonObject json = doCallJson(options);
        StringBuilder sb = new StringBuilder();

        JsonArray routes = (JsonArray) json.get("routes");
        if (routes != null) {
            for (Object routeObj : routes) {
                appendRouteText(sb, (JsonObject) routeObj);
            }
        } else {
            appendRouteText(sb, json);
        }
        return sb.toString();
    }

    private static void appendRouteText(StringBuilder sb, JsonObject routeJson) {
        String routeId = routeJson.getString("routeId");
        if (routeId != null) {
            sb.append(String.format("Route: %s%n", routeId));
            JsonArray processors = (JsonArray) routeJson.get("processors");
            if (processors != null) {
                for (Object obj : processors) {
                    JsonObject p = (JsonObject) obj;
                    sb.append(String.format("  %s (%s)%n", p.getString("id"), p.getString("type")));
                    JsonObject opts = p.getMap("options");
                    if (opts != null) {
                        for (Map.Entry<String, Object> e : opts.entrySet()) {
                            sb.append(String.format("    %s = %s%n", e.getKey(), e.getValue()));
                        }
                    }
                }
            }
        }
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String routeId = optionString(options, ROUTE_ID);
        if (routeId == null || routeId.isBlank()) {
            routeId = subPath;
        }

        JsonObject root = new JsonObject();
        if (routeId == null || routeId.isBlank()) {
            return root;
        }

        ManagedCamelContext mcc
                = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc == null) {
            return root;
        }

        if ("*".equals(routeId)) {
            List<ManagedRouteMBean> managedRoutes = mcc.getManagedRoutes();
            if (managedRoutes == null || managedRoutes.isEmpty()) {
                return root;
            }
            JsonArray routes = new JsonArray();
            for (ManagedRouteMBean mr : managedRoutes) {
                JsonObject routeJson = buildRouteDetail(mr);
                if (routeJson != null) {
                    routes.add(routeJson);
                }
            }
            root.put("routes", routes);
            return root;
        }

        ManagedRouteMBean mr = mcc.getManagedRoute(routeId);
        if (mr == null) {
            return root;
        }
        return buildRouteDetail(mr);
    }

    private static JsonObject buildRouteDetail(ManagedRouteMBean mr) {
        String routeId = mr.getRouteId();
        JsonObject root = new JsonObject();
        root.put("routeId", routeId);
        JsonArray processors = new JsonArray();

        JsonObject fromEntry = new JsonObject();
        fromEntry.put("id", routeId);
        fromEntry.put("type", "from");
        fromEntry.put("endpointUri", mr.getEndpointUri());
        fromEntry.put("options", new JsonObject());
        processors.add(fromEntry);

        try {
            String xml = mr.dumpRouteAsXml();
            if (xml != null && !xml.isBlank()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(xml)));
                Element routeElement = doc.getDocumentElement();

                collectProcessors(routeElement, processors);
            }
        } catch (Exception e) {
            // ignore
        }

        root.put("processors", processors);
        return root;
    }

    private static void collectProcessors(Element parent, JsonArray processors) {
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
            JsonObject entry = new JsonObject();
            entry.put("id", id);
            entry.put("type", type);

            JsonObject opts = new JsonObject();
            NamedNodeMap attrs = elem.getAttributes();
            for (int j = 0; j < attrs.getLength(); j++) {
                Attr attr = (Attr) attrs.item(j);
                String name = attr.getName();
                if (!"id".equals(name) && !"customId".equals(name) && !name.startsWith("xmlns")) {
                    opts.put(name, attr.getValue());
                }
            }

            // collect expression/language child elements (e.g. <simple>, <jsonpath>, <header>,
            // <correlationExpression>, <completionPredicate>, etc.)
            collectExpressionChildren(elem, opts);

            entry.put("options", opts);
            processors.add(entry);

            // recurse into child elements (nested EIPs like split > to, choice > when > to)
            collectProcessors(elem, processors);
        }
    }

    private static void collectExpressionChildren(Element parent, JsonObject opts) {
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
}
