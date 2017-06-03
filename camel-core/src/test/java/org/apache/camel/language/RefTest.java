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

import org.apache.camel.Exchange;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.support.ExpressionAdapter;

public class RefTest extends LanguageTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myExp", new MyExpression());
        return jndi;
    }

    public void testRefExpressions() throws Exception {
        assertExpression("myExp", "Hello World");
    }
 
    public void testRefExpressionsNotFound() throws Exception {
        try {
            assertExpression("foo", "Hello World");
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Cannot find expression or predicate in registry with ref: foo", e.getMessage());
        }
    }

    public void testPredicates() throws Exception {
        assertPredicate("myExp");
    }

    protected String getLanguageName() {
        return "ref";
    }

    private static class MyExpression extends ExpressionAdapter {

        @Override
        public Object evaluate(Exchange exchange) {
            return "Hello World";
        }
    }
}
