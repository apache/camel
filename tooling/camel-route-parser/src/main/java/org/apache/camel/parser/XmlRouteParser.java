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
package org.apache.camel.parser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import org.apache.camel.parser.helper.CamelJavaParserHelper;
import org.apache.camel.parser.helper.CamelXmlHelper;
import org.apache.camel.parser.helper.CamelXmlTreeParserHelper;
import org.apache.camel.parser.helper.XmlLineNumberParser;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.apache.camel.parser.model.CamelNodeDetails;
import org.apache.camel.parser.model.CamelNodeDetailsFactory;
import org.apache.camel.parser.model.CamelRouteDetails;
import org.apache.camel.parser.model.CamelSimpleExpressionDetails;
import org.jboss.forge.roaster.model.util.Strings;

import static org.apache.camel.parser.helper.CamelXmlHelper.getSafeAttribute;

/**
 * A Camel XML parser that parses Camel XML routes source code.
 * <p/>
 * This implementation is higher level details, and uses the lower level parser {@link CamelJavaParserHelper}.
 */
public final class XmlRouteParser {

    private XmlRouteParser() {
    }

    /**
     * Parses the XML file and build a route model (tree) of the discovered routes in the XML source file.
     *
     * @param xml                     the xml file as input stream
     * @param baseDir                 the base of the source code
     * @param fullyQualifiedFileName  the fully qualified source code file name
     * @return a list of route model (tree) of each discovered route
     */
    public static List<CamelNodeDetails> parseXmlRouteTree(InputStream xml, String baseDir, String fullyQualifiedFileName) {
        List<CamelNodeDetails> answer = new ArrayList<>();

        // try parse it as dom
        Document dom = null;
        try {
            dom = XmlLineNumberParser.parseXml(xml);
        } catch (Exception e) {
            // ignore as the xml file may not be valid at this point
        }
        if (dom != null) {

            // find any from which is the start of the route
            CamelNodeDetailsFactory nodeFactory = CamelNodeDetailsFactory.newInstance();

            CamelXmlTreeParserHelper parser = new CamelXmlTreeParserHelper();

            List<Node> routes = CamelXmlHelper.findAllRoutes(dom);
            for (Node route : routes) {
                // parse each route and build
                String routeId = getSafeAttribute(route, "id");
                String lineNumber = (String) route.getUserData(XmlLineNumberParser.LINE_NUMBER);
                String lineNumberEnd = (String) route.getUserData(XmlLineNumberParser.LINE_NUMBER_END);

                // we only want the relative dir name from the resource directory, eg META-INF/spring/foo.xml
                String fileName = fullyQualifiedFileName;
                if (fileName.startsWith(baseDir)) {
                    fileName = fileName.substring(baseDir.length() + 1);
                }

                CamelNodeDetails node = nodeFactory.newNode(null, "route");
                node.setRouteId(routeId);
                node.setFileName(fileName);
                node.setLineNumber(lineNumber);
                node.setLineNumberEnd(lineNumberEnd);

                // parse the route and gather all its EIPs
                List<CamelNodeDetails> tree = parser.parseCamelRouteTree(route, routeId, node, baseDir, fullyQualifiedFileName);
                answer.addAll(tree);
            }
        }

        return answer;
    }

    /**
     * Parses the XML source to discover Camel endpoints.
     *
     * @param xml                     the xml file as input stream
     * @param baseDir                 the base of the source code
     * @param fullyQualifiedFileName  the fully qualified source code file name
     * @param endpoints               list to add discovered and parsed endpoints
     */
    public static void parseXmlRouteEndpoints(InputStream xml, String baseDir, String fullyQualifiedFileName,
                                              List<CamelEndpointDetails> endpoints) throws Exception {

        // find all the endpoints (currently only <endpoint> and within <route>)
        // try parse it as dom
        Document dom = null;
        try {
            dom = XmlLineNumberParser.parseXml(xml);
        } catch (Exception e) {
            // ignore as the xml file may not be valid at this point
        }
        if (dom != null) {
            List<Node> nodes = CamelXmlHelper.findAllEndpoints(dom);
            for (Node node : nodes) {
                String uri = getSafeAttribute(node, "uri");
                if (uri != null) {
                    // trim and remove whitespace noise
                    uri = trimEndpointUri(uri);
                }
                if (!Strings.isBlank(uri)) {
                    String id = getSafeAttribute(node, "id");
                    String lineNumber = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER);
                    String lineNumberEnd = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);

                    // we only want the relative dir name from the resource directory, eg META-INF/spring/foo.xml
                    String fileName = fullyQualifiedFileName;
                    if (fileName.startsWith(baseDir)) {
                        fileName = fileName.substring(baseDir.length() + 1);
                    }

                    boolean consumerOnly = false;
                    boolean producerOnly = false;
                    String nodeName = node.getNodeName();
                    if ("from".equals(nodeName) || "pollEnrich".equals(nodeName)) {
                        consumerOnly = true;
                    } else if ("to".equals(nodeName) || "enrich".equals(nodeName) || "wireTap".equals(nodeName)) {
                        producerOnly = true;
                    }

                    CamelEndpointDetails detail = new CamelEndpointDetails();
                    detail.setFileName(fileName);
                    detail.setLineNumber(lineNumber);
                    detail.setLineNumberEnd(lineNumberEnd);
                    detail.setEndpointInstance(id);
                    detail.setEndpointUri(uri);
                    detail.setEndpointComponentName(endpointComponentName(uri));
                    detail.setConsumerOnly(consumerOnly);
                    detail.setProducerOnly(producerOnly);
                    endpoints.add(detail);
                }
            }
        }
    }

    /**
     * Parses the XML source to discover Camel endpoints.
     *
     * @param xml                     the xml file as input stream
     * @param baseDir                 the base of the source code
     * @param fullyQualifiedFileName  the fully qualified source code file name
     * @param simpleExpressions       list to add discovered and parsed simple expressions
     */
    public static void parseXmlRouteSimpleExpressions(InputStream xml, String baseDir, String fullyQualifiedFileName,
                                                      List<CamelSimpleExpressionDetails> simpleExpressions) throws Exception {

        // find all the simple expressions
        // try parse it as dom
        Document dom = null;
        try {
            dom = XmlLineNumberParser.parseXml(xml);
        } catch (Exception e) {
            // ignore as the xml file may not be valid at this point
        }
        if (dom != null) {
            List<Node> nodes = CamelXmlHelper.findAllSimpleExpressions(dom);
            for (Node node : nodes) {
                String simple = node.getTextContent();
                String lineNumber = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER);
                String lineNumberEnd = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);

                // we only want the relative dir name from the resource directory, eg META-INF/spring/foo.xml
                String fileName = fullyQualifiedFileName;
                if (fileName.startsWith(baseDir)) {
                    fileName = fileName.substring(baseDir.length() + 1);
                }

                CamelSimpleExpressionDetails detail = new CamelSimpleExpressionDetails();
                detail.setFileName(fileName);
                detail.setLineNumber(lineNumber);
                detail.setLineNumberEnd(lineNumberEnd);
                detail.setSimple(simple);

                // is it used as predicate or not
                boolean asPredicate = isSimplePredicate(node);
                detail.setPredicate(asPredicate);
                detail.setExpression(!asPredicate);

                simpleExpressions.add(detail);
            }
        }
    }

    /**
     * Parses the XML source to discover Camel routes with id's assigned.
     *
     * @param xml                     the xml file as input stream
     * @param baseDir                 the base of the source code
     * @param fullyQualifiedFileName  the fully qualified source code file name
     * @param routes                  list to add discovered and parsed routes
     */
    public static void parseXmlRouteRouteIds(InputStream xml, String baseDir, String fullyQualifiedFileName,
                                             List<CamelRouteDetails> routes) throws Exception {

        // find all the endpoints (currently only <route> and within <route>)
        // try parse it as dom
        Document dom = null;
        try {
            dom = XmlLineNumberParser.parseXml(xml);
        } catch (Exception e) {
            // ignore as the xml file may not be valid at this point
        }
        if (dom != null) {
            List<Node> nodes = CamelXmlHelper.findAllRoutes(dom);
            for (Node node : nodes) {
                String id = getSafeAttribute(node, "id");
                String lineNumber = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER);
                String lineNumberEnd = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);

                // we only want the relative dir name from the resource directory, eg META-INF/spring/foo.xml
                String fileName = fullyQualifiedFileName;
                if (fileName.startsWith(baseDir)) {
                    fileName = fileName.substring(baseDir.length() + 1);
                }

                CamelRouteDetails detail = new CamelRouteDetails();
                detail.setFileName(fileName);
                detail.setLineNumber(lineNumber);
                detail.setLineNumberEnd(lineNumberEnd);
                detail.setRouteId(id);
                routes.add(detail);
            }
        }
    }

    /**
     * Using simple expressions in the XML DSL may be used in certain places as predicate only
     */
    private static boolean isSimplePredicate(Node node) {
        // need to check the parent
        Node parent = node.getParentNode();
        if (parent == null) {
            return false;
        }
        String name = parent.getNodeName();

        if (name == null) {
            return false;
        }
        if (name.equals("completionPredicate") || name.equals("completion")) {
            return true;
        }
        if (name.equals("onWhen") || name.equals("when") || name.equals("handled") || name.equals("continued")) {
            return true;
        }
        if (name.equals("retryWhile") || name.equals("filter") || name.equals("validate")) {
            return true;
        }
        // special for loop
        if (name.equals("loop")) {
            String doWhile = null;
            if (parent.getAttributes() != null) {
                Node attr = parent.getAttributes().getNamedItem("doWhile");
                if (attr != null) {
                    doWhile = attr.getTextContent();
                }
            }
            if ("true".equalsIgnoreCase(doWhile)) {
                return true;
            }
        }
        return false;
    }

    private static String endpointComponentName(String uri) {
        if (uri != null) {
            int idx = uri.indexOf(":");
            if (idx > 0) {
                return uri.substring(0, idx);
            }
        }
        return null;
    }


    private static String trimEndpointUri(String uri) {
        uri = uri.trim();
        // if the uri is using new-lines then remove whitespace noise before & and ? separator
        uri = uri.replaceAll("(\\s+)(\\&)", "$2");
        uri = uri.replaceAll("(\\&)(\\s+)", "$1");
        uri = uri.replaceAll("(\\?)(\\s+)", "$1");
        return uri;
    }

}
