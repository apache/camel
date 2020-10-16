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
package org.apache.camel.language.joor;

import org.apache.camel.test.junit5.LanguageTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JoorLanguageTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "joor";
    }

    @Test
    public void testJoorExpressions() throws Exception {
        assertExpression("return \"Hello World 1\";", "Hello World 1");
        assertExpression("return \"Hello World 2\"", "Hello World 2");
        assertExpression("\"Hello World 3\"", "Hello World 3");

        assertExpression("return 'Hello World 1';", "Hello World 1");
        assertExpression("return 'Hello World 2'", "Hello World 2");
        assertExpression("'Hello World 3'", "Hello World 3");
    }

    @Test
    public void testExchange() throws Exception {
        exchange.getIn().setBody("World");

        assertExpression("return \"Hello \" + exchange.getIn().getBody();", "Hello World");
        assertExpression("return \"Hello \" + exchange.getIn().getBody()", "Hello World");
        assertExpression("\"Hello \" + exchange.getIn().getBody()", "Hello World");

        assertExpression("return 'Hello ' + exchange.getIn().getBody();", "Hello World");
        assertExpression("return 'Hello ' + exchange.getIn().getBody()", "Hello World");
        assertExpression("'Hello ' + exchange.getIn().getBody()", "Hello World");
    }

    @Test
    public void testExchangeHeader() throws Exception {
        exchange.getIn().setHeader("foo", 22);

        assertExpression("return 2 * exchange.getIn().getHeader(\"foo\", int.class);", "44");
        assertExpression("return 3 * exchange.getIn().getHeader(\"foo\", int.class);", "66");
        assertExpression("4 * exchange.getIn().getHeader(\"foo\", int.class)", "88");

        assertExpression("return 2 * exchange.getIn().getHeader('foo', int.class);", "44");
        assertExpression("return 3 * exchange.getIn().getHeader('foo', int.class);", "66");
        assertExpression("4 * exchange.getIn().getHeader('foo', int.class)", "88");
    }

    @Test
    public void testExchangeBody() throws Exception {
        exchange.getIn().setBody("Hello big world how are you");

        assertExpression("message.getBody(String.class).toUpperCase()", "HELLO BIG WORLD HOW ARE YOU");
    }

    @Test
    public void testMultiStatements() throws Exception {
        assertExpression("exchange.getIn().setHeader(\"tiger\", \"Tony\"); return null;", null);
        assertExpression("exchange.getIn().setHeader('tiger', 'Tony'); return null;", null);
        assertEquals("Tony", exchange.getIn().getHeader("tiger"));

        exchange.getIn().setHeader("user", "Donald");
        assertExpression("Object user = message.getHeader('user'); return user != null ? 'User: ' + user : 'No user exists';",
                "User: Donald");
        exchange.getIn().removeHeader("user");
        assertExpression("Object user = message.getHeader('user'); return user != null ? 'User: ' + user : 'No user exists';",
                "No user exists");
    }

}
