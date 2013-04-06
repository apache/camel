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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.support.ExpressionSupport;

/**
 * @version 
 */
public class ExpressionSupportTest extends ContextTestSupport {

    private static class MyExpression extends ExpressionSupport {

        protected String assertionFailureMessage(Exchange exchange) {
            return "foo";
        }

        @SuppressWarnings("unchecked")
        public <T> T evaluate(Exchange exchange, Class<T> type) {
            String in = exchange.getIn().getBody(String.class);
            if ("Kabom".equals(in)) {
                return null;
            }
            return (T) in;
        }
    }

    public void testExpressionSupport() throws Exception {
        MyExpression my = new MyExpression();

        Exchange e = new DefaultExchange(context);
        e.getIn().setBody("bar");

        my.assertMatches("bar", e);
    }

    public void testExpressionSupportFail() throws Exception {
        MyExpression my = new MyExpression();

        Exchange e = new DefaultExchange(context);
        e.getIn().setBody("Kabom");
        try {
            my.assertMatches("damn", e);
            fail("Should have thrown exception");
        } catch (AssertionError ae) {
            assertTrue(ae.getMessage().contains("foo"));
        }
    }
}
