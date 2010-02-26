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
package org.apache.camel.language;

import java.util.Calendar;
import java.util.GregorianCalendar;

import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.LanguageTestSupport;

/**
 * @version $Revision$
 */
public class SimpleTest extends LanguageTestSupport {

    public void testConstantExpression() throws Exception {
        assertExpression("Hello World", "Hello World");
    }

    public void testSimpleExpressions() throws Exception {
        assertExpression("id", exchange.getIn().getMessageId());
        assertExpression("body", "<hello id='m123'>world!</hello>");
        assertExpression("in.body", "<hello id='m123'>world!</hello>");
        assertExpression("in.header.foo", "abc");
        assertExpression("in.headers.foo", "abc");
        assertExpression("header.foo", "abc");
        assertExpression("headers.foo", "abc");
    }

    public void testSimpleOutExpressions() throws Exception {
        exchange.getOut().setBody("Bye World");
        exchange.getOut().setHeader("quote", "Camel rocks");
        assertExpression("out.body", "Bye World");
        assertExpression("out.header.quote", "Camel rocks");
        assertExpression("out.headers.quote", "Camel rocks");
    }

    public void testSimplePropertyExpressions() throws Exception {
        exchange.setProperty("medal", "gold");
        assertExpression("property.medal", "gold");
    }

    public void testSimpleSystemPropertyExpressions() throws Exception {
        System.setProperty("who", "I was here");
        assertExpression("sys.who", "I was here");
    }

    public void testSimpleSystemEnvironmentExpressions() throws Exception {
        String path = System.getenv("PATH");
        if (path != null) {
            assertExpression("sysenv.PATH", path);
        }
    }

    public void testDateExpressions() throws Exception {
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20);
        exchange.getIn().setHeader("birthday", cal.getTime());

        assertExpression("date:header.birthday:yyyyMMdd", "19740420");

        try {
            assertExpression("date:yyyyMMdd", "19740420");
            fail("Should thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            // expected
        }
    }

    public void testDateAndTimeExpressions() throws Exception {
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20, 8, 55, 47);
        cal.set(Calendar.MILLISECOND, 123);
        exchange.getIn().setHeader("birthday", cal.getTime());

        assertExpression("date:header.birthday:yyyy-MM-dd'T'HH:mm:ss:SSS", "1974-04-20T08:55:47:123");
    }

    public void testLanguagesInContext() throws Exception {
        // evaluate so we know there is 1 language in the context
        assertExpression("id", exchange.getIn().getMessageId());

        assertEquals(1, context.getLanguageNames().size());
        assertEquals("simple", context.getLanguageNames().get(0));
    }

    public void testComplexExpressions() throws Exception {
        assertExpression("hey ${in.header.foo}", "hey abc");
        assertExpression("hey ${in.header.foo}!", "hey abc!");
        assertExpression("hey ${in.header.foo}-${in.header.foo}!", "hey abc-abc!");
        assertExpression("hey ${in.header.foo}${in.header.foo}", "hey abcabc");
        assertExpression("${in.header.foo}${in.header.foo}", "abcabc");
        assertExpression("${in.header.foo}", "abc");
        assertExpression("${in.header.foo}!", "abc!");
    }

    public void testInvalidComplexExpression() throws Exception {
        try {
            assertExpression("hey ${foo", "bad expression!");
            fail("Should have thrown an exception!");
        } catch (IllegalArgumentException e) {
            log.debug("Caught expected exception: " + e, e);
        }
    }

    public void testPredicates() throws Exception {
        assertPredicate("body");
        assertPredicate("header.foo");
        assertPredicate("header.madeUpHeader", false);
    }

    public void testExceptionMessage() throws Exception {
        exchange.setException(new IllegalArgumentException("Just testing"));
        assertExpression("exception.message", "Just testing");
        assertExpression("Hello ${exception.message} World", "Hello Just testing World");
    }

    public void testIllegalSyntax() throws Exception {
        try {
            assertExpression("hey ${xxx} how are you?", "");
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertEquals("Illegal syntax: xxx", e.getMessage());
        }

        try {
            assertExpression("${xxx}", "");
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertEquals("Illegal syntax: xxx", e.getMessage());
        }
    }

    protected String getLanguageName() {
        return "simple";
    }
}