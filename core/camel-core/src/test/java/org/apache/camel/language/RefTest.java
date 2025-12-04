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

package org.apache.camel.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.camel.Exchange;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.Test;

public class RefTest extends LanguageTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("myExp", new MyExpression());
        return jndi;
    }

    @Test
    public void testRefExpressions() {
        assertExpression("myExp", "Hello World");
    }

    @Test
    public void testRefExpressionsNotFound() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> assertExpression("foo", "Hello World"),
                "Should have thrown an exception");

        assertEquals("Cannot find expression or predicate in registry with ref: foo", e.getMessage());
    }

    @Test
    public void testRefDynamicExpressions() {
        exchange.getMessage().setHeader("foo", "myExp");
        assertExpression("${header.foo}", "Hello World");
    }

    @Test
    public void testRefDynamicExpressionsNotFound() {
        exchange.getMessage().setHeader("foo", "myExp2");
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> assertExpression("${header.foo}", "Hello World"),
                "Should have thrown an exception");

        assertEquals("Cannot find expression or predicate in registry with ref: myExp2", e.getMessage());
    }

    @Test
    public void testPredicates() {
        assertPredicate("myExp");
    }

    @Override
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
