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
package org.apache.camel.builder.xml;

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
import javax.xml.xpath.XPathFunctionResolver;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;


import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.builder.xml.XPathBuilder.xpath;

/**
 * @version 
 */
public class XPathTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testXPathExpressions() throws Exception {
        assertExpression("/foo/bar/@xyz", "<foo><bar xyz='cheese'/></foo>", "cheese");
        assertExpression("$name", "<foo><bar xyz='cheese'/></foo>", "James");
        assertExpression("foo/bar", "<foo><bar>cheese</bar></foo>", "cheese");
        assertExpression("foo/bar/text()", "<foo><bar>cheese</bar></foo>", "cheese");
        assertExpression("/foo/@id", "<foo id='cheese'>hey</foo>", "cheese");
        assertExpression("/foo/@num", "<foo num='123'>hey</foo>", "123");
    }

    public void testXPathPredicates() throws Exception {
        assertPredicate("/foo/bar/@xyz", "<foo><bar xyz='cheese'/></foo>", true);
        assertPredicate("$name = 'James'", "<foo><bar xyz='cheese'/></foo>", true);
        assertPredicate("$name = 'Hiram'", "<foo><bar xyz='cheese'/></foo>", false);
        assertPredicate("/foo/notExist", "<foo><bar xyz='cheese'/></foo>", false);
        assertPredicate("/foo[@num = '123']", "<foo num='123'>hey</foo>", true);
    }

    public void testXPathWithCustomVariable() throws Exception {
        assertExpression(xpath("$name").stringResult().variable("name", "Hiram"), "<foo/>", "Hiram");
    }

    public void testInvalidXPath() throws Exception {
        try {
            assertPredicate("/foo/", "<foo><bar xyz='cheese'/></foo>", true);
            fail("Should have thrown exception");
        } catch (InvalidXPathExpression e) {
            assertEquals("/foo/", e.getXpath());
            assertIsInstanceOf(XPathExpressionException.class, e.getCause());
        }
    }

    public void testXPathBooleanResult() throws Exception {
        Object result = xpath("/foo/bar/@xyz").booleanResult().evaluate(createExchange("<foo><bar xyz='cheese'/></foo>"));
        Boolean bool = assertIsInstanceOf(Boolean.class, result);
        assertEquals(true, bool.booleanValue());
    }

    public void testXPathNodeResult() throws Exception {
        Object result = xpath("/foo/bar").nodeResult().evaluate(createExchange("<foo><bar xyz='cheese'/></foo>"));
        Node node = assertIsInstanceOf(Node.class, result);
        assertNotNull(node);
        String s = context.getTypeConverter().convertTo(String.class, node);
        assertEquals("<bar xyz=\"cheese\"/>", s);
    }

    public void testXPathNodeSetResult() throws Exception {
        Object result = xpath("/foo").nodeSetResult().evaluate(createExchange("<foo>bar</foo>"));
        NodeList node = assertIsInstanceOf(NodeList.class, result);
        assertNotNull(node);
        String s = context.getTypeConverter().convertTo(String.class, node);
        assertEquals("<foo>bar</foo>", s);
    }

    public void testXPathNumberResult() throws Exception {
        Object result = xpath("/foo/bar/@xyz").numberResult().evaluate(createExchange("<foo><bar xyz='123'/></foo>"));
        Double num = assertIsInstanceOf(Double.class, result);
        assertEquals("123.0", num.toString());
    }

    public void testXPathStringResult() throws Exception {
        Object result = xpath("/foo/bar/@xyz").stringResult().evaluate(createExchange("<foo><bar xyz='123'/></foo>"));
        String num = assertIsInstanceOf(String.class, result);
        assertEquals("123", num);
    }

    public void testXPathCustomResult() throws Exception {
        Object result = xpath("/foo/bar/@xyz").resultType(Integer.class).evaluate(createExchange("<foo><bar xyz='123'/></foo>"));
        Integer num = assertIsInstanceOf(Integer.class, result);
        assertEquals(123, num.intValue());
    }

    public void testXPathBuilder() throws Exception {
        XPathBuilder builder = xpath("/foo/bar");
        assertEquals("/foo/bar", builder.getText());
        assertEquals(XPathConstants.NODESET, builder.getResultQName());
        assertNull(builder.getResultType());
    }

    public void testXPathWithDocument() throws Exception {
        Document doc = context.getTypeConverter().convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");

        Object result = xpath("/foo").evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("<foo>bar</foo>", s);
    }

    public void testXPathWithDocumentTypeDOMSource() throws Exception {
        Document doc = context.getTypeConverter().convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");

        XPathBuilder builder = xpath("/foo");
        builder.setDocumentType(DOMSource.class);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("<foo>bar</foo>", s);
    }

    public void testXPathWithDocumentTypeInputSource() throws Exception {
        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");
        InputSource doc = new InputSource(is);

        XPathBuilder builder = xpath("/foo");
        builder.setDocumentType(InputSource.class);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("<foo>bar</foo>", s);
    }

    public void testXPathWithDocumentTypeInputSourceFluentBuilder() throws Exception {
        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");
        InputSource doc = new InputSource(is);

        XPathBuilder builder = xpath("/foo").documentType(InputSource.class);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("<foo>bar</foo>", s);
    }

    public void testXPathWithDocumentTypeInputSourceNoResultQName() throws Exception {
        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");
        InputSource doc = new InputSource(is);

        XPathBuilder builder = xpath("/foo");
        builder.setDocumentType(InputSource.class);
        builder.setResultQName(null);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("bar", s);
    }

    public void testXPathWithDocumentTypeDOMSourceNoResultQName() throws Exception {
        Document doc = context.getTypeConverter().convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");

        XPathBuilder builder = xpath("/foo");
        builder.setDocumentType(DOMSource.class);
        builder.setResultQName(null);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("bar", s);
    }

    public void testXPathWithStringTypeDOMSourceNoResultQName() throws Exception {
        XPathBuilder builder = xpath("/foo");
        builder.setResultQName(null);

        Object result = builder.evaluate(createExchange("<foo>bar</foo>"));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("bar", s);
    }

    public void testXPathWithNamespaceBooleanResult() throws Exception {
        XPathBuilder builder = xpath("/c:person[@name='James']").namespace("c", "http://acme.com/cheese").booleanResult();
        
        Object result = builder.evaluate(createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result);
    }

    public void testXPathWithNamespaceBooleanResultType() throws Exception {
        XPathBuilder builder = xpath("/c:person[@name='James']").namespace("c", "http://acme.com/cheese");
        builder.setResultType(Boolean.class);

        Object result = builder.evaluate(createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result);
    }

    public void testXPathWithNamespaceStringResult() throws Exception {
        XPathBuilder builder = xpath("/c:person/@name").namespace("c", "http://acme.com/cheese").stringResult();
        
        Object result = builder.evaluate(createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertEquals("James", result);
    }

    public void testXPathWithNamespacesBooleanResult() throws Exception {
        Namespaces ns = new Namespaces("c", "http://acme.com/cheese");
        XPathBuilder builder = xpath("/c:person[@name='James']").namespaces(ns).booleanResult();

        Object result = builder.evaluate(createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result);
    }

    public void testXPathWithNamespacesStringResult() throws Exception {
        Namespaces ns = new Namespaces("c", "http://acme.com/cheese");
        XPathBuilder builder = xpath("/c:person/@name").namespaces(ns).stringResult();

        Object result = builder.evaluate(createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertEquals("James", result);
    }

    public void testXPathWithNamespacesNodeResult() throws Exception {
        Namespaces ns = new Namespaces("c", "http://acme.com/cheese");
        XPathBuilder builder = xpath("/c:person/@name").namespaces(ns);
        builder.setResultType(Node.class);

        Object result = builder.evaluate(createExchange("<person xmlns=\"http://acme.com/cheese\" name='James' city='London'/>"));
        assertNotNull(result);
        assertTrue(result.toString().contains("James"));
    }

    public void testUsingJavaExtensions() throws Exception {
        Object instance = null;

        // we may not have Xalan on the classpath
        try {
            instance = Class.forName("org.apache.xalan.extensions.XPathFunctionResolverImpl").newInstance();
        } catch (Throwable e) {
            
            log.debug("Could not find Xalan on the classpath so ignoring this test case: " + e);
        }
        if (instance instanceof XPathFunctionResolver) {
            XPathFunctionResolver functionResolver = (XPathFunctionResolver)instance;

            XPathBuilder builder = xpath("java:" + getClass().getName() + ".func(string(/header/value))")
                .namespace("java", "http://xml.apache.org/xalan/java").functionResolver(functionResolver)
                .stringResult();

            String xml = "<header><value>12</value></header>";
            // it can throw the exception if we put the xalan into the test class path
            assertExpression(builder, xml, "modified12");
        }

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

    public void testXPathNotUsingExchangeMatches() throws Exception {
        assertTrue(XPathBuilder.xpath("/foo/bar/@xyz").matches(context, "<foo><bar xyz='cheese'/></foo>"));
        assertFalse(XPathBuilder.xpath("/foo/bar/@xyz").matches(context, "<foo>Hello World</foo>"));
    }

    public void testXPathNotUsingExchangeEvaluate() throws Exception {
        String name = XPathBuilder.xpath("foo/bar").evaluate(context, "<foo><bar>cheese</bar></foo>", String.class);
        assertEquals("<bar>cheese</bar>", name);

        name = XPathBuilder.xpath("foo/bar/text()").evaluate(context, "<foo><bar>cheese</bar></foo>", String.class);
        assertEquals("cheese", name);

        Integer number = XPathBuilder.xpath("foo/bar").evaluate(context, "<foo><bar>123</bar></foo>", Integer.class);
        assertEquals(123, number.intValue());

        Boolean bool = XPathBuilder.xpath("foo/bar").evaluate(context, "<foo><bar>true</bar></foo>", Boolean.class);
        assertEquals(true, bool.booleanValue());
    }

    public void testNotUsingExchangeResultType() throws Exception {
        String xml = "<xml><a>1</a><a>2</a></xml>";

        // will evaluate as NodeSet
        XPathBuilder xpb = new XPathBuilder("/xml/a/text()");
        assertEquals("12", xpb.evaluate(context, xml, String.class));

        xpb.setResultType(String.class);
        assertEquals("1", xpb.evaluate(context, xml));
    }

    public void testXPathSplit() throws Exception {
        Object node = XPathBuilder.xpath("foo/bar").nodeResult()
                .evaluate(createExchange("<foo><bar>cheese</bar><bar>cake</bar><bar>beer</bar></foo>"));
        assertNotNull(node);

        Document doc = context.getTypeConverter().convertTo(Document.class, node);
        assertNotNull(doc);
    }

    public void testXPathSplitConcurrent() throws Exception {
        int size = 100;

        final Object node = XPathBuilder.xpath("foo/bar").nodeResult()
                .evaluate(createExchange("<foo><bar>cheese</bar><bar>cake</bar><bar>beer</bar></foo>"));
        assertNotNull(node);

        // convert the node concurrently to test that XML Parser is not thread safe when
        // importing nodes to a new Document, so try a test for that

        final List<Document> result = new ArrayList<Document>();
        ExecutorService executor = Executors.newFixedThreadPool(size);
        final CountDownLatch latch = new CountDownLatch(size);
        for (int i = 0; i < size; i++) {
            executor.submit(new Callable<Document>() {
                public Document call() throws Exception {
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

    public void testXPathNodeListTest() throws Exception {
        String xml = "<foo><person id=\"1\">Claus<country>SE</country></person>"
                + "<person id=\"2\">Jonathan<country>CA</country></person></foo>";
        Document doc = context.getTypeConverter().convertTo(Document.class, xml);

        Object result = xpath("/foo/person").nodeSetResult().evaluate(createExchange(doc));
        assertNotNull(result);

        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals(ObjectHelper.between(xml, "<foo>", "</foo>"), s);
    }

    public void testXPathNodeListSimpleTest() throws Exception {
        String xml = "<foo><person>Claus</person></foo>";
        Document doc = context.getTypeConverter().convertTo(Document.class, xml);

        Object result = xpath("/foo/person").nodeSetResult().evaluate(createExchange(doc));
        assertNotNull(result);

        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("<person>Claus</person>", s);
    }

    public void testXPathNodeListSimpleTestText() throws Exception {
        String xml = "<foo><person>Claus</person></foo>";
        Document doc = context.getTypeConverter().convertTo(Document.class, xml);

        Object result = xpath("/foo/person/text()").nodeSetResult().evaluate(createExchange(doc));
        assertNotNull(result);

        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("Claus", s);
    }

    public void testXPathString() throws Exception {
        XPathBuilder builder = XPathBuilder.xpath("foo/bar");

        // will evaluate as XPathConstants.NODESET and have Camel convert that to String
        // this should return the String incl. xml tags
        String name = builder.evaluate(context, "<foo><bar id=\"1\">cheese</bar></foo>", String.class);
        assertEquals("<bar id=\"1\">cheese</bar>", name);

        // will evaluate using XPathConstants.STRING which just return the text content (eg like text())
        name = builder.evaluate(context, "<foo><bar id=\"1\">cheese</bar></foo>");
        assertEquals("cheese", name);
    }

}
