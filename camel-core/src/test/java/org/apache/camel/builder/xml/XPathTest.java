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
package org.apache.camel.builder.xml;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.TestSupport;
import static org.apache.camel.builder.xml.XPathBuilder.*;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;

import javax.xml.xpath.XPathFunctionResolver;

/**
 * @version $Revision$
 */
public class XPathTest extends TestSupport {

    public void testXPathExpressions() throws Exception {
        assertExpression("/foo/bar/@xyz", "<foo><bar xyz='cheese'/></foo>", "cheese");
        assertExpression("$name", "<foo><bar xyz='cheese'/></foo>", "James");
        assertExpression("foo/bar", "<foo><bar>cheese</bar></foo>", "cheese");
        assertExpression("foo/bar/text()", "<foo><bar>cheese</bar></foo>", "cheese");
    }

    public void testXPathPredicates() throws Exception {
        assertPredicate("/foo/bar/@xyz", "<foo><bar xyz='cheese'/></foo>", true);
        assertPredicate("$name = 'James'", "<foo><bar xyz='cheese'/></foo>", true);
        assertPredicate("$name = 'Hiram'", "<foo><bar xyz='cheese'/></foo>", false);
        assertPredicate("/foo/notExist", "<foo><bar xyz='cheese'/></foo>", false);
    }

    public void testXPathWithCustomVariable() throws Exception {
        assertExpression(xpath("$name").variable("name", "Hiram"), "<foo/>", "Hiram");
    }

    public void testUsingJavaExtensions() throws Exception {
        Object instance = null;

        // we may not have Xalan on the classpath
        try {
            instance = Class.forName("org.apache.xalan.extensions.XPathFunctionResolverImpl").newInstance();
        }
        catch (Throwable e) {
            log.debug("Could not find Xalan on the classpath so ignoring this test case: " + e);
        }
        
        if (instance instanceof XPathFunctionResolver) {
            XPathFunctionResolver functionResolver = (XPathFunctionResolver) instance;

            XPathBuilder builder = xpath("java:"
                    + getClass().getName() + ".func(string(/header/value))")
                    .namespace("java", "http://xml.apache.org/xalan/java")
                    .functionResolver(functionResolver);

            String xml = "<header><value>12</value></header>";
            Object value = assertExpression(builder, xml, "modified12");
            log.debug("Evaluated xpath: " + builder.getText() + " on XML: " + xml + " result: " + value);
        }
    }

    public static String func(String s) {
        return "modified" + s;
    }

    protected Object assertExpression(String xpath, String xml, String expected) {
        Expression expression = XPathBuilder.xpath(xpath);
        return assertExpression(expression, xml, expected);
    }

    protected Object assertExpression(Expression expression, String xml, String expected) {
        return assertExpression(expression, createExchange(xml), expected);
    }

    protected void assertPredicate(String xpath, String xml, boolean expected) {
        Predicate predicate = XPathBuilder.xpath(xpath);
        assertPredicate(predicate, createExchange(xml), expected);
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
