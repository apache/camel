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

import static org.apache.camel.builder.xml.XPathBuilder.xpath;

/**
 * @version $Revision$
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
    }

    public void testXPathPredicates() throws Exception {
        assertPredicate("/foo/bar/@xyz", "<foo><bar xyz='cheese'/></foo>", true);
        assertPredicate("$name = 'James'", "<foo><bar xyz='cheese'/></foo>", true);
        assertPredicate("$name = 'Hiram'", "<foo><bar xyz='cheese'/></foo>", false);
        assertPredicate("/foo/notExist", "<foo><bar xyz='cheese'/></foo>", false);
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
        assertEquals("bar", s);
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
        assertEquals("bar", s);
    }

    public void testXPathWithDocumentTypeDOMSource() throws Exception {
        Document doc = context.getTypeConverter().convertTo(Document.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");

        XPathBuilder builder = xpath("/foo");
        builder.setDocumentType(DOMSource.class);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("bar", s);
    }

    public void testXPathWithDocumentTypeInputSource() throws Exception {
        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");
        InputSource doc = new InputSource(is);

        XPathBuilder builder = xpath("/foo");
        builder.setDocumentType(InputSource.class);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("bar", s);
    }

    public void testXPathWithDocumentTypeInputSourceFluentBuilder() throws Exception {
        InputStream is = context.getTypeConverter().convertTo(InputStream.class, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>");
        InputSource doc = new InputSource(is);

        XPathBuilder builder = xpath("/foo").documentType(InputSource.class);

        Object result = builder.evaluate(createExchange(doc));
        assertNotNull(result);
        String s = context.getTypeConverter().convertTo(String.class, result);
        assertEquals("bar", s);
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

    public void testUsingJavaExtensions() throws Exception {
        Object instance;

        // we may not have Xalan on the classpath
        try {
            instance = Class.forName("org.apache.xalan.extensions.XPathFunctionResolverImpl").newInstance();

            if (instance instanceof XPathFunctionResolver) {
                XPathFunctionResolver functionResolver = (XPathFunctionResolver)instance;
    
                XPathBuilder builder = xpath("java:" + getClass().getName() + ".func(string(/header/value))").namespace("java", "http://xml.apache.org/xalan/java").functionResolver(functionResolver);
    
                String xml = "<header><value>12</value></header>";
                Object value = assertExpression(builder, xml, "modified12");
                log.debug("Evaluated xpath: " + builder.getText() + " on XML: " + xml + " result: " + value);
            }
        } catch (Throwable e) {
            log.debug("Could not find Xalan on the classpath so ignoring this test case: " + e);
        }
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
}
