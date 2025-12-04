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

package org.apache.camel.builder.xml;

import static org.apache.camel.language.xpath.XPathBuilder.xpath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.Test;

public class XPathTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testXPathExpressions() {
        assertExpression("/foo/bar/@xyz", "<foo><bar xyz='cheese'/></foo>", "cheese");
        assertExpression("$name", "<foo><bar xyz='cheese'/></foo>", "James");
        assertExpression("foo/bar", "<foo><bar>cheese</bar></foo>", "cheese");
        assertExpression("foo/bar/text()", "<foo><bar>cheese</bar></foo>", "cheese");
        assertExpression("/foo/@id", "<foo id='cheese'>hey</foo>", "cheese");
        assertExpression("/foo/@num", "<foo num='123'>hey</foo>", "123");
    }

    @Test
    public void testXPathPredicates() {
        assertPredicate("/foo/bar/@xyz", "<foo><bar xyz='cheese'/></foo>", true);
        assertPredicate("$name = 'James'", "<foo><bar xyz='cheese'/></foo>", true);
        assertPredicate("$name = 'Hiram'", "<foo><bar xyz='cheese'/></foo>", false);
        assertPredicate("/foo/notExist", "<foo><bar xyz='cheese'/></foo>", false);
        assertPredicate("/foo[@num = '123']", "<foo num='123'>hey</foo>", true);
    }

    @Test
    public void testXPathWithCustomVariable() {
        assertExpression(xpath("$name").stringResult().variable("name", "Hiram"), "<foo/>", "Hiram");
    }

    @Test
    public void testInvalidXPath() {
        Exception e = assertThrows(
                Exception.class,
                () -> assertPredicate("/foo/", "<foo><bar xyz='cheese'/></foo>", true),
                "Should have thrown exception");

        assertIsInstanceOf(XPathExpressionException.class, e.getCause());
    }

    @Test
    public void testXPathBooleanResult() {
        Object result =
                xpath("/foo/bar/@xyz").booleanResult().evaluate(createExchange("<foo><bar xyz='cheese'/></foo>"));
        Boolean bool = assertIsInstanceOf(Boolean.class, result);
        assertTrue(bool.booleanValue());
    }

    @Test
    public void testXPathNodeResult() {
        Object result = xpath("/foo/bar").nodeResult().evaluate(createExchange("<foo><bar xyz='cheese'/></foo>"));
        Node node = assertIsInstanceOf(Node.class, result);
        assertNotNull(node);
        String s = context.getTypeConverter().convertTo(String.class, node);
        assertEquals("<bar xyz=\"cheese\"/>", s);
    }

    @Test
    public void testXPathNodeSetResult() {
        Object result = xpath("/foo").nodeSetResult().evaluate(createExchange("<foo>bar</foo>"));
        NodeList node = assertIsInstanceOf(NodeList.class, result);
        assertNotNull(node);
        String s = context.getTypeConverter().convertTo(String.class, node);
        assertEquals("<foo>bar</foo>", s);
    }

    @Test
    public void testXPathNumberResult() {
        Object result = xpath("/foo/bar/@xyz").numberResult().evaluate(createExchange("<foo><bar xyz='123'/></foo>"));
        Double num = assertIsInstanceOf(Double.class, result);
        assertEquals("123.0", num.toString());
    }

    @Test
    public void testXPathStringResult() {
        Object result = xpath("/foo/bar/@xyz").stringResult().evaluate(createExchange("<foo><bar xyz='123'/></foo>"));
        String num = assertIsInstanceOf(String.class, result);
        assertEquals("123", num);
    }

    @Test
    public void testXPathCustomResult() {
        Object result = xpath("/foo/bar/@xyz")
                .resultType(Integer.class)
                .evaluate(createExchange("<foo><bar xyz='123'/></foo>"));
        Integer num = assertIsInstanceOf(Integer.class, result);
        assertEquals(123, num.intValue());
    }

    @Test
    public void testXPathBuilder() {
        XPathBuilder builder = xpath("/foo/bar");
        assertEquals("/foo/bar", builder.getText());
        assertEquals(XPathConstants.NODESET, builder.getResultQName());
        assertNull(builder.getResultType());
    }

    @Test
    public void testXPathWithDocument() {
        Document doc = context.getTypeConverter()
                .convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");

        Object result = xpath("/foo").evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("<foo>bar</foo>", s);
    }

    @Test
    public void testXPathWithDocumentTypeDOMSource() {
        Document doc = context.getTypeConverter()
                .convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");

        XPathBuilder builder = xpath("/foo");
        builder.setDocumentType(DOMSource.class);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("<foo>bar</foo>", s);
    }

    @Test
    public void testXPathWithDocumentTypeInputSource() {
        InputStream is = context.getTypeConverter()
                .convertTo(InputStream.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");
        InputSource doc = new InputSource(is);

        XPathBuilder builder = xpath("/foo");
        builder.setDocumentType(InputSource.class);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("<foo>bar</foo>", s);
    }

    @Test
    public void testXPathWithDocumentTypeInputSourceFluentBuilder() {
        InputStream is = context.getTypeConverter()
                .convertTo(InputStream.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");
        InputSource doc = new InputSource(is);

        XPathBuilder builder = xpath("/foo").documentType(InputSource.class);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("<foo>bar</foo>", s);
    }

    @Test
    public void testXPathWithDocumentTypeInputSourceNoResultQName() {
        InputStream is = context.getTypeConverter()
                .convertTo(InputStream.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");
        InputSource doc = new InputSource(is);

        XPathBuilder builder = xpath("/foo");
        builder.setDocumentType(InputSource.class);
        builder.setResultQName(null);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("bar", s);
    }

    @Test
    public void testXPathWithDocumentTypeDOMSourceNoResultQName() {
        Document doc = context.getTypeConverter()
                .convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");

        XPathBuilder builder = xpath("/foo");
        builder.setDocumentType(DOMSource.class);
        builder.setResultQName(null);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("bar", s);
    }

    @Test
    public void testXPathWithStringTypeDOMSourceNoResultQName() {
        XPathBuilder builder = xpath("/foo");
        builder.setResultQName(null);

        Object result = builder.evaluate(createExchange("<foo>bar</foo>"));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("bar", s);
    }

    @Test
    public void testXPathWithNamespaceBooleanResult() {
        XPathBuilder builder = xpath("/c:person[@name='James']")
                .namespace("c", "http://acme.com/cheese")
                .booleanResult();

        Object result = builder.evaluate(
                createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testXPathWithNamespaceBooleanResultType() {
        XPathBuilder builder = xpath("/c:person[@name='James']").namespace("c", "http://acme.com/cheese");
        builder.setResultType(Boolean.class);

        Object result = builder.evaluate(
                createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testXPathWithNamespaceStringResult() {
        XPathBuilder builder = xpath("/c:person/@name")
                .namespace("c", "http://acme.com/cheese")
                .stringResult();

        Object result = builder.evaluate(
                createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertEquals("James", result);
    }

    @Test
    public void testXPathWithNamespacesBooleanResult() {
        Namespaces ns = new Namespaces("c", "http://acme.com/cheese");
        XPathBuilder builder = xpath("/c:person[@name='James']").namespaces(ns).booleanResult();

        Object result = builder.evaluate(
                createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testXPathWithNamespacesStringResult() {
        Namespaces ns = new Namespaces("c", "http://acme.com/cheese");
        XPathBuilder builder = xpath("/c:person/@name").namespaces(ns).stringResult();

        Object result = builder.evaluate(
                createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertEquals("James", result);
    }

    @Test
    public void testXPathWithNamespacesNodeResult() {
        Namespaces ns = new Namespaces("c", "http://acme.com/cheese");
        XPathBuilder builder = xpath("/c:person/@name").namespaces(ns);
        builder.setResultType(Node.class);

        Object result = builder.evaluate(
                createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertTrue(result.toString().contains("James"));
    }

    public static String func(String message) {
        return "modified" + message;
    }

    protected Object assertExpression(String xpath, String xml, String expected) {
        Expression expression = XPathBuilder.xpath(xpath).stringResult();
        return assertExpression(expression, xml, expected);
    }

    protected Object assertExpression(Expression expression, String xml, String expected) {
        return assertExpression(expression, createExchange(xml), expected);
    }

    protected void assertPredicate(String xpath, String xml, boolean expected) {
        Predicate predicate = XPathBuilder.xpath(xpath);
        assertPredicate(predicate, createExchange(xml), expected);
    }

    protected Exchange createExchange(Object xml) {
        Exchange exchange = createExchangeWithBody(context, xml);
        exchange.getIn().setHeader("name", "James");
        return exchange;
    }

    @Test
    public void testXPathNotUsingExchangeMatches() {
        assertTrue(XPathBuilder.xpath("/foo/bar/@xyz").matches(context, "<foo><bar xyz='cheese'/></foo>"));
        assertFalse(XPathBuilder.xpath("/foo/bar/@xyz").matches(context, "<foo>Hello World</foo>"));
    }

    @Test
    public void testXPathNotUsingExchangeEvaluate() {
        String name = XPathBuilder.xpath("foo/bar").evaluate(context, "<foo><bar>cheese</bar></foo>", String.class);
        assertEquals("<bar>cheese</bar>", name);

        name = XPathBuilder.xpath("foo/bar/text()").evaluate(context, "<foo><bar>cheese</bar></foo>", String.class);
        assertEquals("cheese", name);

        Integer number = XPathBuilder.xpath("foo/bar").evaluate(context, "<foo><bar>123</bar></foo>", Integer.class);
        assertEquals(123, number.intValue());

        Boolean bool = XPathBuilder.xpath("foo/bar").evaluate(context, "<foo><bar>true</bar></foo>", Boolean.class);
        assertTrue(bool.booleanValue());
    }

    @Test
    public void testNotUsingExchangeResultType() {
        String xml = "<xml><a>1</a><a>2</a></xml>";

        // will evaluate as NodeSet
        XPathBuilder xpb = new XPathBuilder("/xml/a/text()");
        assertEquals("12", xpb.evaluate(context, xml, String.class));

        xpb.setResultType(String.class);
        assertEquals("1", xpb.evaluate(context, xml));
    }

    @Test
    public void testXPathSplit() {
        Object node = XPathBuilder.xpath("foo/bar")
                .nodeResult()
                .evaluate(createExchange("<foo><bar>cheese</bar><bar>cake</bar><bar>beer</bar></foo>"));
        assertNotNull(node);

        Document doc = context.getTypeConverter().convertTo(Document.class, node);
        assertNotNull(doc);
    }

    @Test
    public void testXPathSplitConcurrent() throws Exception {
        int size = 100;

        final Object node = XPathBuilder.xpath("foo/bar")
                .nodeResult()
                .evaluate(createExchange("<foo><bar>cheese</bar><bar>cake</bar><bar>beer</bar></foo>"));
        assertNotNull(node);

        // convert the node concurrently to test that XML Parser is not thread
        // safe when
        // importing nodes to a new Document, so try a test for that

        final List<Document> result = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(size);
        final CountDownLatch latch = new CountDownLatch(size);
        for (int i = 0; i < size; i++) {
            executor.submit(new Callable<Document>() {
                public Document call() {
                    try {
                        Document doc = context.getTypeConverter().convertTo(Document.class, node);
                        result.add(doc);
                        return doc;
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        // give time to convert concurrently
        assertTrue(latch.await(20, TimeUnit.SECONDS));

        Iterator<Document> it = result.iterator();
        int count = 0;
        while (it.hasNext()) {
            count++;
            Document doc = it.next();
            assertNotNull(doc);
        }
        assertEquals(size, count);
        executor.shutdownNow();
    }

    @Test
    public void testXPathNodeListTest() {
        String xml = "<foo><person id=\"1\">Claus<country>SE</country></person>"
                + "<person id=\"2\">Jonathan<country>CA</country></person></foo>";
        Document doc = context.getTypeConverter().convertTo(Document.class, xml);

        Object result = xpath("/foo/person").nodeSetResult().evaluate(createExchange(doc));
        assertNotNull(result);

        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals(StringHelper.between(xml, "<foo>", "</foo>"), s);
    }

    @Test
    public void testXPathNodeListSimpleTest() {
        String xml = "<foo><person>Claus</person></foo>";
        Document doc = context.getTypeConverter().convertTo(Document.class, xml);

        Object result = xpath("/foo/person").nodeSetResult().evaluate(createExchange(doc));
        assertNotNull(result);

        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("<person>Claus</person>", s);
    }

    @Test
    public void testXPathNodeListSimpleTestText() {
        String xml = "<foo><person>Claus</person></foo>";
        Document doc = context.getTypeConverter().convertTo(Document.class, xml);

        Object result = xpath("/foo/person/text()").nodeSetResult().evaluate(createExchange(doc));
        assertNotNull(result);

        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("Claus", s);
    }

    @Test
    public void testXPathString() {
        XPathBuilder builder = XPathBuilder.xpath("foo/bar");

        // will evaluate as XPathConstants.NODESET and have Camel convert that
        // to String
        // this should return the String incl. xml tags
        String name = builder.evaluate(context, "<foo><bar id=\"1\">cheese</bar></foo>", String.class);
        assertEquals("<bar id=\"1\">cheese</bar>", name);

        // will evaluate using XPathConstants.STRING which just return the text
        // content (eg like text())
        name = builder.evaluate(context, "<foo><bar id=\"1\">cheese</bar></foo>");
        assertEquals("cheese", name);
    }
}
