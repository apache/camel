/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder.xpath;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.xpath.XPathFunctionResolver;

/**
 * @version $Revision$
 */
public class XPathTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(XPathTest.class);

    public void testXPathExpressions() throws Exception {
        assertExpression("/foo/bar/@xyz", "cheese", "<foo><bar xyz='cheese'/></foo>");
        assertExpression("$name", "James", "<foo><bar xyz='cheese'/></foo>");
        assertExpression("foo/bar", "cheese", "<foo><bar>cheese</bar></foo>");
        assertExpression("foo/bar/text()", "cheese", "<foo><bar>cheese</bar></foo>");
    }

    public void testXPathPredicates() throws Exception {
        assertPredicate("/foo/bar/@xyz", true, "<foo><bar xyz='cheese'/></foo>");
        assertPredicate("$name = 'James'", true, "<foo><bar xyz='cheese'/></foo>");
        assertPredicate("$name = 'Hiram'", false, "<foo><bar xyz='cheese'/></foo>");
        assertPredicate("/foo/notExist", false, "<foo><bar xyz='cheese'/></foo>");
    }

    public void testUsingJavaExtensions() throws Exception {
        Object instance = null;

        // we may not have Xalan on the classpath
        try {
            instance = Class.forName("org.apache.xalan.extensions.XPathFunctionResolverImpl").newInstance();
        }
        catch (Throwable e) {
            log.info("Could not find Xalan on the classpath so ignoring this test case: " + e);
        }
        
        if (instance instanceof XPathFunctionResolver) {
            XPathFunctionResolver functionResolver = (XPathFunctionResolver) instance;

            XPathBuilder builder = XPathBuilder.xpath("java:"
                    + getClass().getName() + ".func(string(/header/value))")
                    .namespace("java", "http://xml.apache.org/xalan/java")
                    .functionResolver(functionResolver);

            String xml = "<header><value>12</value></header>";
            Object value = assertExpression(builder.createExpression(), "modified12", xml);
            log.debug("Evaluated xpath: " + builder.getText() + " on XML: " + xml + " result: " + value);
        }
    }

    public static String func(String s) {
        return "modified" + s;
    }

    protected void assertExpression(String xpath, String expected, String xml) {
        Expression expression = XPathBuilder.xpath(xpath).createExpression();
        Object value = assertExpression(expression, expected, xml);

        log.debug("Evaluated xpath: " + xpath + " on XML: " + xml + " result: " + value);
    }

    protected Object assertExpression(Expression expression, String expected, String xml) {
        Exchange exchange = createExchange(xml);
        Object value = expression.evaluate(exchange);
        assertEquals("Expression: " + expression, expected, value);
        return value;
    }

    protected void assertPredicate(String xpath, boolean expected, String xml) {
        Predicate predicate = XPathBuilder.xpath(xpath).createPredicate();
        boolean value = assertPredicate(predicate, expected, xml);

        log.debug("Evaluated xpath: " + xpath + " on XML: " + xml + " result: " + value);

    }

    protected boolean assertPredicate(Predicate predicate, boolean expected, String xml) {
        Exchange exchange = createExchange(xml);
        boolean value = predicate.matches(exchange);
        assertEquals("Predicate: " + predicate, expected, value);
        return value;
    }

    protected Exchange createExchange(String xml) {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        Message message = exchange.getIn();
        message.setHeader("name", "James");
        message.setBody(xml);
        return exchange;
    }
}
