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
package org.apache.camel.converter.saxon;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.xpath.XPathEvaluator;
import org.apache.camel.Exchange;
import org.apache.camel.language.xpath.DefaultNamespaceContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.xml.StringSource;
import org.junit.Before;
import org.junit.Test;

public class SaxonConverterTest extends CamelTestSupport {
    private static final String CONTENT = "<a xmlns=\"http://www.apache.org/test\"><b foo=\"bar\">test</b><c><d>foobar</d></c></a>";
    private static final String CONTENT_B = "<b xmlns=\"http://www.apache.org/test\" foo=\"bar\">test</b>";
    private static final NamespaceContext NS_CONTEXT = new DefaultNamespaceContext().add("ns1", "http://www.apache.org/test");

    private Exchange exchange;
    private XPathEvaluator evaluator;
    private NodeInfo doc;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        exchange = new DefaultExchange(context);
        evaluator = new XPathEvaluator();
        doc = evaluator.getConfiguration().buildDocumentTree(new StringSource(CONTENT)).getRootNode();
    }

    @Test
    public void convertToDOMSource() throws XPathException {
        DOMSource source = context.getTypeConverter().convertTo(DOMSource.class, exchange, doc);
        assertNotNull(source);
        String string = context.getTypeConverter().convertTo(String.class, exchange, source);
        assertEquals(CONTENT, string);
    }

    @Test
    public void convertToDocument() throws XPathException {
        Document document = context.getTypeConverter().convertTo(Document.class, exchange, doc);
        assertNotNull(document);
        String string = context.getTypeConverter().convertTo(String.class, exchange, document);
        assertEquals(CONTENT, string);
    }

    @Test
    public void convertSubNodeToDocument() throws XPathException, XPathExpressionException {
        evaluator.setNamespaceContext(NS_CONTEXT);
        Object nodeObj = evaluator.evaluate("/ns1:a/ns1:b", doc, XPathConstants.NODE);
        assertNotNull(nodeObj);
        Document document = context.getTypeConverter().convertTo(Document.class, exchange, nodeObj);
        assertNotNull(document);
        String string = context.getTypeConverter().convertTo(String.class, exchange, document);
        assertEquals(CONTENT_B, string);
    }

    @Test
    public void convertSubNodeSetToDocument() throws XPathException, XPathExpressionException {
        evaluator.setNamespaceContext(NS_CONTEXT);
        Object nodeObj = evaluator.evaluate("/ns1:a/ns1:b", doc, XPathConstants.NODESET);
        assertNotNull(nodeObj);
        Document document = context.getTypeConverter().convertTo(Document.class, exchange, nodeObj);
        assertNotNull(document);
        String string = context.getTypeConverter().convertTo(String.class, exchange, document);
        assertEquals(CONTENT_B, string);
    }

    @Test
    public void convertToNode() throws XPathException {
        Node node = context.getTypeConverter().convertTo(Node.class, exchange, doc);
        assertNotNull(node);
        String string = context.getTypeConverter().convertTo(String.class, exchange, node);
        assertEquals(CONTENT, string);
    }

    @Test
    public void convertToNodeList() throws XPathException {
        List<NodeInfo> nil = new LinkedList<>();
        nil.add(doc);
        NodeList nodeList = context.getTypeConverter().convertTo(NodeList.class, exchange, nil);
        assertNotNull(nodeList);
        assertEquals(1, nodeList.getLength());
        String string = context.getTypeConverter().convertTo(String.class, exchange, nodeList);
        assertEquals(CONTENT, string);
    }

    @Test
    public void convertToInputStream() throws XPathException {
        InputStream is = context.getTypeConverter().convertTo(InputStream.class, exchange, doc);
        assertNotNull(is);
        String string = context.getTypeConverter().convertTo(String.class, exchange, is);
        assertEquals(CONTENT, string);
    }

    @Test
    public void convertToByteArray() throws XPathException {
        byte[] ba = context.getTypeConverter().convertTo(byte[].class, exchange, doc);
        assertNotNull(ba);
        String string = context.getTypeConverter().convertTo(String.class, exchange, ba);
        assertEquals(CONTENT, string);
    }

    @Test
    public void convertToNodeAndByteArray() throws XPathException {
        Node node = context.getTypeConverter().convertTo(Node.class, exchange, doc);
        assertNotNull(node);
        byte[] ba = context.getTypeConverter().convertTo(byte[].class, exchange, node);
        assertNotNull(ba);
        String string = context.getTypeConverter().convertTo(String.class, exchange, ba);
        assertEquals(CONTENT, string);
    }
}
