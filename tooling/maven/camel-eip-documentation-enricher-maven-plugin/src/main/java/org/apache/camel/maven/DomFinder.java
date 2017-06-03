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
package org.apache.camel.maven;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Finds xml elements where documentation can be added.
 */
public class DomFinder {
    private final Document document;
    private final XPath xPath;

    public DomFinder(Document document, XPath xPath) {
        this.document = document;
        this.xPath = xPath;
    }

    public NodeList findElementsAndTypes() throws XPathExpressionException {
        return (NodeList) xPath.compile("/xs:schema/xs:element")
                .evaluate(document, XPathConstants.NODESET);
    }

    public NodeList findAttributesElements(String name) throws XPathExpressionException {
        return (NodeList) xPath.compile(
                "/xs:schema/xs:complexType[@name='" + name + "']//xs:attribute")
                .evaluate(document, XPathConstants.NODESET);
    }

    public String findBaseType(String name) throws XPathExpressionException {
        return (String) xPath.compile(
                "/xs:schema/xs:complexType[@name='" + name + "']//xs:extension/@base")
                .evaluate(document, XPathConstants.STRING);
    }
}
