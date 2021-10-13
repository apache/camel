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
package org.apache.camel.language.simple;

import org.apache.camel.LanguageTestSupport;
import org.junit.jupiter.api.Test;

public class SimpleCacheExpressionTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    @Test
    public void testCachingExpression() throws Exception {
        exchange.getIn().setHeader("foo", 123);

        assertExpression(exchange, "header.foo", "header.foo");
        assertExpression(exchange, "${header.foo}", 123);
    }

    @Test
    public void testReverseCachingExpression() throws Exception {
        exchange.getIn().setHeader("foo", 123);

        assertExpression(exchange, "${header.foo}", 123);
        assertExpression(exchange, "header.foo", "header.foo");
    }

    @Test
    public void testCachingWithNestedFunction() throws Exception {
        MyConverter converter = new MyConverter();
        exchange.getIn().setBody(converter);
        exchange.getIn().setHeader("input", "foo");

        assertExpression(exchange, "${body.upper(${header.input})}", "FOO");
        assertExpression(exchange, "body.upper(${header.input})", "body.upper(foo)");
    }

    @Test
    public void testReversedCachingWithNestedFunction() throws Exception {
        MyConverter converter = new MyConverter();
        exchange.getIn().setBody(converter);
        exchange.getIn().setHeader("input", "foo");

        assertExpression(exchange, "body.upper(${header.input})", "body.upper(foo)");
        assertExpression(exchange, "${body.upper(${header.input})}", "FOO");
    }

    public static class MyConverter {
        public String upper(String input) throws Exception {
            return input.toUpperCase();
        }
    }
}
