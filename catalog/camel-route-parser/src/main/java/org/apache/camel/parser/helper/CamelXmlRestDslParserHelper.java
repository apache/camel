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
package org.apache.camel.parser.helper;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.parser.model.RestConfigurationDetails;
import org.apache.camel.parser.model.RestServiceDetails;
import org.apache.camel.parser.model.RestVerbDetails;

public final class CamelXmlRestDslParserHelper {

    public List<RestConfigurationDetails> parseRestConfiguration(Node xmlNode, String baseDir, String fullyQualifiedFileName) {

        List<RestConfigurationDetails> answer = new ArrayList<>();

        RestConfigurationDetails detail = new RestConfigurationDetails();
        detail.setFileName(fullyQualifiedFileName);
        walkXmlTree(xmlNode, detail);
        answer.add(detail);

        return answer;
    }

    public List<RestServiceDetails> parseRestService(Node xmlNode, String baseDir, String fullyQualifiedFileName) {

        List<RestServiceDetails> answer = new ArrayList<>();

        RestServiceDetails detail = new RestServiceDetails();
        detail.setFileName(fullyQualifiedFileName);
        walkXmlTree(xmlNode, detail);
        answer.add(detail);

        return answer;
    }

    private void walkXmlTree(Node xmlNode, RestConfigurationDetails detail) {
        if ("restConfiguration".equals(xmlNode.getNodeName())) {
            String lineNumber = (String) xmlNode.getUserData(XmlLineNumberParser.LINE_NUMBER);
            String lineNumberEnd = (String) xmlNode.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
            detail.setLineNumber(lineNumber);
            detail.setLineNumberEnd(lineNumberEnd);

            NamedNodeMap map = xmlNode.getAttributes();
            detail.setComponent(extractAttribute(map, "component"));
            detail.setApiComponent(extractAttribute(map, "apiComponent"));
            detail.setProducerComponent(extractAttribute(map, "producerComponent"));
            detail.setScheme(extractAttribute(map, "scheme"));
            detail.setHost(extractAttribute(map, "host"));
            detail.setApiHost(extractAttribute(map, "apiHost"));
            detail.setPort(extractAttribute(map, "port"));
            detail.setProducerApiDoc(extractAttribute(map, "producerApiDoc"));
            detail.setContextPath(extractAttribute(map, "contextPath"));
            detail.setApiContextPath(extractAttribute(map, "apiContextPath"));
            detail.setApiContextRouteId(extractAttribute(map, "apiContextRouteId"));
            detail.setApiContextIdPattern(extractAttribute(map, "apiContextIdPattern"));
            detail.setApiContextListening(extractAttribute(map, "apiContextListening"));
            detail.setApiVendorExtension(extractAttribute(map, "apiVendorExtension"));
            detail.setHostNameResolver(extractAttribute(map, "hostNameResolver"));
            detail.setBindingMode(extractAttribute(map, "bindingMode"));
            detail.setSkipBindingOnErrorCode(extractAttribute(map, "skipBindingOnErrorCode"));
            detail.setClientRequestValidation(extractAttribute(map, "clientRequestValidation"));
            detail.setEnableCORS(extractAttribute(map, "enableCORS"));
            detail.setJsonDataFormat(extractAttribute(map, "jsonDataFormat"));
            detail.setXmlDataFormat(extractAttribute(map, "xmlDataFormat"));
        }

        if ("componentProperty".equals(xmlNode.getNodeName())
            && (xmlNode.getParentNode() != null && "restConfiguration".equals(xmlNode.getParentNode().getNodeName()))) {
            NamedNodeMap map = xmlNode.getAttributes();
            String key = extractAttribute(map, "key");
            String value = extractAttribute(map, "value");
            if (key != null && value != null) {
                detail.addComponentProperty(key, value);
            }
        } else if ("endpointProperty".equals(xmlNode.getNodeName())
            && (xmlNode.getParentNode() != null && "restConfiguration".equals(xmlNode.getParentNode().getNodeName()))) {
            NamedNodeMap map = xmlNode.getAttributes();
            String key = extractAttribute(map, "key");
            String value = extractAttribute(map, "value");
            if (key != null && value != null) {
                detail.addEndpointProperty(key, value);
            }
        } else if ("consumerProperty".equals(xmlNode.getNodeName())
            && (xmlNode.getParentNode() != null && "restConfiguration".equals(xmlNode.getParentNode().getNodeName()))) {
            NamedNodeMap map = xmlNode.getAttributes();
            String key = extractAttribute(map, "key");
            String value = extractAttribute(map, "value");
            if (key != null && value != null) {
                detail.addConsumerProperty(key, value);
            }
        } else if ("dataFormatProperty".equals(xmlNode.getNodeName())
            && (xmlNode.getParentNode() != null && "restConfiguration".equals(xmlNode.getParentNode().getNodeName()))) {
            NamedNodeMap map = xmlNode.getAttributes();
            String key = extractAttribute(map, "key");
            String value = extractAttribute(map, "value");
            if (key != null && value != null) {
                detail.addDataFormatProperty(key, value);
            }
        } else if ("apiProperty".equals(xmlNode.getNodeName())
            && (xmlNode.getParentNode() != null && "restConfiguration".equals(xmlNode.getParentNode().getNodeName()))) {
            NamedNodeMap map = xmlNode.getAttributes();
            String key = extractAttribute(map, "key");
            String value = extractAttribute(map, "value");
            if (key != null && value != null) {
                detail.addApiProperty(key, value);
            }
        } else if ("corsHeaders".equals(xmlNode.getNodeName())
            && (xmlNode.getParentNode() != null && "restConfiguration".equals(xmlNode.getParentNode().getNodeName()))) {
            NamedNodeMap map = xmlNode.getAttributes();
            String key = extractAttribute(map, "key");
            String value = extractAttribute(map, "value");
            if (key != null && value != null) {
                detail.addCorsHeader(key, value);
            }
        }

        // walk the rest of the children
        NodeList children = xmlNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                walkXmlTree(child, detail);
            }
        }
    }

    private void walkXmlTree(Node xmlNode, RestServiceDetails detail) {
        if ("rest".equals(xmlNode.getNodeName())) {
            String lineNumber = (String) xmlNode.getUserData(XmlLineNumberParser.LINE_NUMBER);
            String lineNumberEnd = (String) xmlNode.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
            detail.setLineNumber(lineNumber);
            detail.setLineNumberEnd(lineNumberEnd);
            extractAttributes(xmlNode, detail);
        }

        if (isParentRest(xmlNode)) {
            if ("delete".equals(xmlNode.getNodeName())) {
                RestVerbDetails verb = new RestVerbDetails();
                verb.setMethod("delete");
                detail.addVerb(verb);
                extractAttributes(xmlNode, verb);
            } else if ("get".equals(xmlNode.getNodeName())) {
                RestVerbDetails verb = new RestVerbDetails();
                verb.setMethod("get");
                detail.addVerb(verb);
                extractAttributes(xmlNode, verb);
            } else if ("head".equals(xmlNode.getNodeName())) {
                RestVerbDetails verb = new RestVerbDetails();
                verb.setMethod("head");
                detail.addVerb(verb);
                extractAttributes(xmlNode, verb);
            } else if ("patch".equals(xmlNode.getNodeName())) {
                RestVerbDetails verb = new RestVerbDetails();
                verb.setMethod("patch");
                detail.addVerb(verb);
                extractAttributes(xmlNode, verb);
            } else if ("post".equals(xmlNode.getNodeName())) {
                RestVerbDetails verb = new RestVerbDetails();
                verb.setMethod("post");
                detail.addVerb(verb);
                extractAttributes(xmlNode, verb);
            } else if ("put".equals(xmlNode.getNodeName())) {
                RestVerbDetails verb = new RestVerbDetails();
                verb.setMethod("put");
                detail.addVerb(verb);
                extractAttributes(xmlNode, verb);
            }

            if ("description".equals(xmlNode.getNodeName())) {
                String value = xmlNode.getTextContent();
                RestVerbDetails verb = getLastVerb(detail);
                if (verb != null) {
                    verb.setDescription(value);
                } else {
                    detail.setDescription(value);
                }
            } else if ("to".equals(xmlNode.getNodeName())) {
                NamedNodeMap map = xmlNode.getAttributes();
                String uri = extractAttribute(map, "uri");
                RestVerbDetails verb = getLastVerb(detail);
                if (verb != null) {
                    verb.setTo(uri);
                }
            } else if ("toD".equals(xmlNode.getNodeName())) {
                NamedNodeMap map = xmlNode.getAttributes();
                String uri = extractAttribute(map, "uri");
                RestVerbDetails verb = getLastVerb(detail);
                if (verb != null) {
                    verb.setToD(uri);
                }
            }
        }

        // walk the rest of the children
        NodeList children = xmlNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                walkXmlTree(child, detail);
            }
        }
    }

    private static void extractAttributes(Node xmlNode, RestServiceDetails detail) {
        NamedNodeMap map = xmlNode.getAttributes();
        detail.setConsumes(extractAttribute(map, "consumes"));
        detail.setProduces(extractAttribute(map, "produces"));
        detail.setApiDocs(extractAttribute(map, "apiDocs"));
        detail.setBindingMode(extractAttribute(map, "bindingMode"));
        detail.setClientRequestValidation(extractAttribute(map, "clientRequestValidation"));
        detail.setEnableCORS(extractAttribute(map, "enableCORS"));
        detail.setPath(extractAttribute(map, "path"));
        detail.setSkipBindingOnErrorCode(extractAttribute(map, "skipBindingOnErrorCode"));
        detail.setTag(extractAttribute(map, "tag"));
    }

    private static void extractAttributes(Node xmlNode, RestVerbDetails detail) {
        NamedNodeMap map = xmlNode.getAttributes();
        detail.setUri(extractAttribute(map, "uri"));
        detail.setConsumes(extractAttribute(map, "consumes"));
        detail.setProduces(extractAttribute(map, "produces"));
        detail.setApiDocs(extractAttribute(map, "apiDocs"));
        detail.setBindingMode(extractAttribute(map, "bindingMode"));
        detail.setClientRequestValidation(extractAttribute(map, "clientRequestValidation"));
        detail.setSkipBindingOnErrorCode(extractAttribute(map, "skipBindingOnErrorCode"));
        detail.setType(extractAttribute(map, "type"));
        detail.setOutType(extractAttribute(map, "outType"));
    }

    private static RestVerbDetails getLastVerb(RestServiceDetails detail) {
        if (detail.getVerbs() == null) {
            return null;
        }
        return detail.getVerbs().get(detail.getVerbs().size() - 1);
    }

    private static boolean isParentRest(Node node) {
        if (node == null) {
            return false;
        }
        String name = node.getNodeName();
        if ("rest".equals(name)) {
            return true;
        } else {
            return isParentRest(node.getParentNode());
        }
    }

    private static String extractAttribute(NamedNodeMap map, String name) {
        if (map != null) {
            Node attr = map.getNamedItem(name);
            if (attr != null) {
                return attr.getTextContent();
            }
        }
        return null;
    }

}
