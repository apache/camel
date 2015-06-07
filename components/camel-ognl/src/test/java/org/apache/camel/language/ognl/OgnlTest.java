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
package org.apache.camel.language.ognl;

import org.apache.camel.test.junit4.LanguageTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class OgnlTest extends LanguageTestSupport {

    @Test
    public void testOgnlExpressions() throws Exception {
        assertExpression("exchange", exchange);
        assertExpression("exchange.getIn().body", "<hello id='m123'>world!</hello>");
        assertExpression("getRequest().body", "<hello id='m123'>world!</hello>");
        assertExpression("request.body", "<hello id='m123'>world!</hello>");
        assertExpression("getRequest().headers['foo']", "abc");
        assertExpression("getRequest().headers.foo", "abc");
        assertExpression("request.headers.foo", "abc");
    }

    @Test
    public void testClassMethodExpression() throws Exception {
        try {
            assertExpression("@org.apache.camel.language.ognl.Animal1@getClassName()", "Animal");
            fail("Expect exception here.");
        } catch (Exception ex) {
            assertTrue("We should get the ClassNotFoundException", ex.getMessage().indexOf("ClassNotFoundException") > 0);
        }
        // setup the class resolver to load the right class for us
        exchange.getContext().setClassResolver(new MyClassResolver(context));
        assertExpression("@org.apache.camel.language.ognl.Animal1@getClassName()", "Animal");
    }
    @Test
    public void testGetOutFalseKeepsNullOutMessage() throws Exception {
        assertExpression("exchange.hasOut()", false);
        assertFalse(exchange.hasOut());
    }

    @Test
    public void testResponseCreatesOutMessage() throws Exception {
        assertExpression("response.body", null);
        assertTrue(exchange.hasOut());
    }

    protected String getLanguageName() {
        return "ognl";
    }
}
