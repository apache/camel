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
package org.apache.camel.converter.jaxp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.util.xml.XmlLineNumberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlConvertersEdgeCasesTest {

    // the jdk implementation of stax (camel-core tests have woodstox on the classpath)
    private static final XMLInputFactory JDK_INPUT = XMLInputFactory.newDefaultFactory();
    private static final XMLOutputFactory JDK_OUTPUT = XMLOutputFactory.newDefaultFactory();

    @TempDir
    File tempDir;

    private static XMLStreamReader reader(String xml) throws Exception {
        return JDK_INPUT.createXMLStreamReader(new StringReader(xml));
    }

    @Test
    public void testReadAllBytesOfLargeDocument() throws Exception {
        StringBuilder sb = new StringBuilder("<a>");
        for (int i = 0; i < 2000; i++) {
            sb.append("<b>").append(i).append("</b>");
        }
        sb.append("</a>");

        InputStream is = new XMLStreamReaderInputStream(reader(sb.toString()), "utf-8", JDK_OUTPUT);
        assertThat(is.read(new byte[1], 0, 0)).isZero();
        String out = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(out).endsWith("</a>").contains("<b>1999</b>");
    }

    @Test
    public void testInputStreamWithOtherCharset() throws Exception {
        InputStream is = new XMLStreamReaderInputStream(reader("<a>café</a>"), "ISO-8859-1", JDK_OUTPUT);
        String out = new String(is.readAllBytes(), StandardCharsets.ISO_8859_1);
        assertThat(out).contains("<a>café</a>");
    }

    @Test
    public void testReaderWithUnqualifiedAttribute() throws Exception {
        StringWriter sw = new StringWriter();
        new XMLStreamReaderReader(reader("<a x=\"1\"><b>t</b></a>"), JDK_OUTPUT).transferTo(sw);
        assertThat(sw.toString()).contains("<a x=\"1\"><b>t</b></a>");
    }

    @Test
    public void testReaderPositionedAtElement() throws Exception {
        XMLStreamReader r = reader("<a><b>t</b></a>");
        r.nextTag();
        InputStream is = new XMLStreamReaderInputStream(r, "utf-8", JDK_OUTPUT);
        assertThat(new String(is.readAllBytes(), StandardCharsets.UTF_8)).contains("<a><b>t</b></a>");
    }

    @Test
    public void testTextNodesInMixedContent() throws Exception {
        Document doc = new XmlConverter().toDOMDocument("<a>foo<b/>bar</a>", null);
        NodeList nl = (NodeList) XPathFactory.newInstance().newXPath().evaluate("/a/text()", doc, XPathConstants.NODESET);

        DomConverter dom = new DomConverter();
        assertThat(dom.toString(nl, null)).isEqualTo("foobar");
        assertThat(dom.toString(nl.item(0), null)).isEqualTo("foo");
    }

    @Test
    public void testAttrToString() throws Exception {
        Document doc = new XmlConverter().toDOMDocument("<a x=\"1\"/>", null);
        Attr attr = doc.getDocumentElement().getAttributeNode("x");
        assertThat(new DomConverter().toString((Node) attr, null)).isEqualTo("1");
    }

    @Test
    public void testStreamReaderFromFileUsesDeclaredEncoding() throws Exception {
        File file = new File(tempDir, "latin1.xml");
        Files.write(file.toPath(), "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a>café</a>"
                .getBytes(StandardCharsets.ISO_8859_1));

        XMLStreamReader r = new StaxConverter().createXMLStreamReader(file, null);
        try {
            r.nextTag();
            assertThat(r.getElementText()).isEqualTo("café");
        } finally {
            r.close();
        }
    }

    @Test
    public void testLineNumberParserWithElementsAfterRoot() throws Exception {
        String xml = "<beans>\n <camelContext><route/></camelContext>\n <bean id=\"b\">\n hello\n </bean>\n</beans>";
        Document doc = XmlLineNumberParser.parseXml(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), null, "camelContext", null);
        assertThat(doc.getDocumentElement().getNodeName()).isEqualTo("camelContext");
        assertThat(doc.getDocumentElement().getTextContent()).doesNotContain("hello");
    }
}
