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
package org.apache.camel.language.jxpath;

import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.test.junit4.LanguageTestSupport;
import org.junit.Test;

/**
 * Test for {@link JXPathExpression} and {@link JXPathLanguage}
 */
public class JXPathTest extends LanguageTestSupport {

    protected PersonBean body = new PersonBean("James", "London");

    @Test
    public void testJXPathExpressions() throws Exception {
        assertExpression(".", exchange);
        assertExpression("./in/body", "<hello id='m123'>world!</hello>");
        assertExpression("in/body", "<hello id='m123'>world!</hello>");
        assertExpression("in/headers", exchange.getIn().getHeaders());
        assertExpression("in/headers/@foo", "abc");
    }

    @Test
    public void testJXPathPredicates() throws Exception {
        assertPredicate("in/headers/@foo = 'abc'");
        assertInvalidPredicate("in/headders/@foo = 'abc'");
    }

    @Test
    public void testExceptions() throws Exception {
        assertInvalidExpression(".@.");
        assertInvalidExpression("ins/body");
    }

    private void assertInvalidExpression(String expression) {
        try {
            assertExpression(expression, null);
            fail("Expected an ExpressionEvaluationException");
        } catch (ExpressionEvaluationException e) {
            // nothing to do -- test success
        }
    }

    private void assertInvalidPredicate(String predicate) {
        try {
            assertPredicate(predicate);
            fail("Expected an ExpressionEvaluationException");
        } catch (AssertionError e) {
            //nothing to do -- test success
        }
    }

    @Override
    protected String getLanguageName() {
        return "jxpath";
    }

}
