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
package org.apache.camel.parser.helper;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.parser.model.RestConfigurationDetails;

public final class CamelXmlRestDslParserHelper {

    public List<RestConfigurationDetails> parseRestConfiguration(Node xmlNode, String baseDir, String fullyQualifiedFileName) {

        List<RestConfigurationDetails> answer = new ArrayList<>();

        RestConfigurationDetails detail = new RestConfigurationDetails();
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

        // walk the rest of the children
        NodeList children = xmlNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                walkXmlTree(child, detail);
            }
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
