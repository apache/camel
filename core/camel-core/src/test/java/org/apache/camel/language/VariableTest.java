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

import java.util.Map;

import org.apache.camel.LanguageTestSupport;
import org.apache.camel.language.variable.VariableLanguage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VariableTest extends LanguageTestSupport {

    @Test
    public void testVariableExpressions() throws Exception {
        exchange.setVariable("varFoo", "abc");
        assertExpression("varFoo", "abc");
    }

    @Test
    public void testPredicates() throws Exception {
        context.setVariable("varFoo", true);
        assertPredicate("global:varFoo");

        exchange.setVariable("varLocalFoo", false);
        assertPredicate("varLocal", false);
    }

    @Test
    public void testVariableHeaders() throws Exception {
        exchange.setVariable("header:myKey.foo", "abc");
        exchange.setVariable("header:myKey.bar", 123);
        exchange.setVariable("myOtherKey", "Hello Again");

        assertEquals("abc", exchange.getVariable("header:myKey.foo"));
        assertEquals(123, exchange.getVariable("header:myKey.bar"));

        Map map = exchange.getVariable("header:myKey", Map.class);
        assertNotNull(map);
        assertEquals(2, map.size());
        assertEquals("abc", map.get("foo"));
        assertEquals(123, map.get("bar"));
    }

    @Test
    public void testInvalidHeaderKey() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> exchange.getVariable("header:"));
    }

    @Test
    public void testSingleton() {
        VariableLanguage prop = new VariableLanguage();
        assertTrue(prop.isSingleton());
    }

    @Override
    protected String getLanguageName() {
        return "variable";
    }
}
