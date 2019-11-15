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
package org.apache.camel.maven.integration;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.maven.CamelSpringNamespace;
import org.apache.camel.maven.Constants;
import org.apache.camel.maven.EipDocumentationEnricherMojo;
import org.apache.camel.maven.ResourceUtils;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class EIPDocumentationMojoTest {
    EipDocumentationEnricherMojo eipDocumentationEnricherMojo = new EipDocumentationEnricherMojo();
    XPath xPath = XPathFactory.newInstance().newXPath();
    File tempFile;

    @Before
    public void setUp() throws Exception {
        eipDocumentationEnricherMojo.camelCoreDir = ResourceUtils.getResourceAsFile("integration/camel-core-integration");
        eipDocumentationEnricherMojo.camelCoreXmlDir = ResourceUtils.getResourceAsFile("integration/camel-core-integration");
        eipDocumentationEnricherMojo.camelSpringDir = ResourceUtils.getResourceAsFile("integration/camel-core-integration");
        eipDocumentationEnricherMojo.inputCamelSchemaFile = ResourceUtils.getResourceAsFile("integration/camel-spring.xsd");
        eipDocumentationEnricherMojo.pathToModelDir = "trgt/classes/org/apache/camel/model";
        eipDocumentationEnricherMojo.pathToCoreXmlModelDir = "trgt/classes/org/apache/camel/model";
        eipDocumentationEnricherMojo.pathToSpringModelDir = "trgt/classes/org/apache/camel/model";
        xPath.setNamespaceContext(new CamelSpringNamespace());
        tempFile = File.createTempFile("outputXml", ".xml");
        tempFile.deleteOnExit();
        eipDocumentationEnricherMojo.outputCamelSchemaFile = tempFile;
    }

    @Test
    public void testExecuteMojo() throws Exception {
        eipDocumentationEnricherMojo.execute();
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document doc = documentBuilder.parse(tempFile);
        validateElement(doc);
        validateAttributes(doc);
        validateParentAttribute(doc);
    }

    private void validateParentAttribute(Document doc) throws Exception {
        Element e = (Element) xPath.compile("//xs:attribute[@name='id']").evaluate(doc, XPathConstants.NODE);

        assertEquals("id", e.getAttribute(Constants.NAME_ATTRIBUTE_NAME));

        validateDocumentation(e, "id documentation");
    }

    private void validateAttributes(Document doc) throws Exception {
        Element e = (Element) xPath.compile("//xs:attribute[@name='beforeUri']").evaluate(doc, XPathConstants.NODE);

        assertEquals("beforeUri", e.getAttribute(Constants.NAME_ATTRIBUTE_NAME));

        validateDocumentation(e, "beforeUri documentation");

    }

    private void validateElement(Document doc) {
        NodeList element = doc.getElementsByTagName("xs:element");
        Element e = (Element) element.item(0);

        assertEquals("aop", e.getAttribute(Constants.NAME_ATTRIBUTE_NAME));

        validateDocumentation(e, "element documentation");
    }

    private void validateDocumentation(Element element, String expectedText) {
        Element annotation = getFirsElement(element.getChildNodes());
        Element documentation = getFirsElement(annotation.getChildNodes());

        assertEquals("xs:annotation", annotation.getTagName());
        assertEquals("xs:documentation", documentation.getTagName());

        Node cdata = documentation.getFirstChild();
        assertThat(cdata, instanceOf(CharacterData.class));

        assertThat(cdata.getTextContent(), containsString(expectedText));
    }

    private Element getFirsElement(NodeList nodeList) {
        return (Element) nodeList.item(1);
    }
}
