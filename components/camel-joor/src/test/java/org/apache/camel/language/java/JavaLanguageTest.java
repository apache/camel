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
package org.apache.camel.language.java;

import org.apache.camel.test.junit5.LanguageTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaLanguageTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "java";
    }

    @Test
    public void testJoorExpressions() {
        assertExpression("return \"Hello World 1\";", "Hello World 1");
        assertExpression("return \"Hello World 2\"", "Hello World 2");
        assertExpression("\"Hello World 3\"", "Hello World 3");

        assertExpression("return 'Hello World 1';", "Hello World 1");
        assertExpression("return 'Hello World 2'", "Hello World 2");
        assertExpression("'Hello World 3'", "Hello World 3");
    }

    @Test
    public void testExchange() {
        exchange.getIn().setBody("World");

        assertExpression("return \"Hello \" + exchange.getIn().getBody();", "Hello World");
        assertExpression("return \"Hello \" + exchange.getIn().getBody()", "Hello World");
        assertExpression("\"Hello \" + exchange.getIn().getBody()", "Hello World");

        assertExpression("return 'Hello ' + exchange.getIn().getBody();", "Hello World");
        assertExpression("return 'Hello ' + exchange.getIn().getBody()", "Hello World");
        assertExpression("'Hello ' + exchange.getIn().getBody()", "Hello World");
    }

    @Test
    public void testExchangeHeader() {
        exchange.getIn().setHeader("foo", 22);

        assertExpression("return 2 * exchange.getIn().getHeader(\"foo\", int.class);", "44");
        assertExpression("return 3 * exchange.getIn().getHeader(\"foo\", int.class);", "66");
        assertExpression("4 * exchange.getIn().getHeader(\"foo\", int.class)", "88");

        assertExpression("return 2 * exchange.getIn().getHeader('foo', int.class);", "44");
        assertExpression("return 3 * exchange.getIn().getHeader('foo', int.class);", "66");
        assertExpression("4 * exchange.getIn().getHeader('foo', int.class)", "88");
    }

    @Test
    public void testExchangeBody() {
        exchange.getIn().setBody("Hello big world how are you");

        assertExpression("message.getBody(String.class).toUpperCase()", "HELLO BIG WORLD HOW ARE YOU");
        assertExpression("bodyAs(String).toLowerCase()", "hello big world how are you");
    }

    @Test
    public void testMultiStatements() {
        assertExpression("exchange.getIn().setHeader(\"tiger\", \"Tony\"); return null;", null);
        assertExpression("exchange.getIn().setHeader('tiger', 'Tony'); return null;", null);
        assertEquals("Tony", exchange.getIn().getHeader("tiger"));

        exchange.getIn().setHeader("user", "Donald");
        assertExpression("Object user = message.getHeader('user'); return user != null ? 'User: ' + user : 'No user exists';",
                "User: Donald");
        assertExpression("var user = message.getHeader('user'); return user != null ? 'User: ' + user : 'No user exists';",
                "User: Donald");
        exchange.getIn().removeHeader("user");
        assertExpression("Object user = message.getHeader('user'); return user != null ? 'User: ' + user : 'No user exists';",
                "No user exists");
    }

    @Test
    public void testOptionalBody() {
        exchange.getIn().setBody("22");
        assertExpression("optionalBody.isPresent()", true);
        assertExpression("optionalBody.get()", "22");

        exchange.getIn().setBody(null);
        assertExpression("optionalBody.isPresent()", false);
    }

    @Test
    public void testExchangeBodyAs() {
        exchange.getIn().setBody("22");

        assertExpression("2 * bodyAs(int.class)", "44");
        assertExpression("2 * bodyAs(int)", "44");
        assertExpression("3 * bodyAs(Integer.class)", "66");
        assertExpression("3 * bodyAs(Integer)", "66");
        assertExpression("3 * bodyAs(java.lang.Integer.class)", "66");
        assertExpression("3 * bodyAs(java.lang.Integer)", "66");
        assertExpression("var num = bodyAs(int); return num * 4", "88");
    }

    @Test
    public void testExchangeOptionalBodyAs() {
        exchange.getIn().setBody("22");

        assertExpression("2 * optionalBodyAs(int.class).get()", "44");
        assertExpression("2 * optionalBodyAs(int).get()", "44");
        assertExpression("3 * optionalBodyAs(Integer.class).get()", "66");
        assertExpression("3 * optionalBodyAs(Integer).get()", "66");
        assertExpression("3 * optionalBodyAs(java.lang.Integer.class).get()", "66");
        assertExpression("3 * optionalBodyAs(java.lang.Integer).get()", "66");
        assertExpression("var num = optionalBodyAs(int).get(); return num * 4", "88");
    }

    @Test
    public void testExchangeHeaderAs() {
        exchange.getIn().setHeader("foo", 22);

        assertExpression("2 * headerAs('foo', int.class)", "44");
        assertExpression("2 * headerAs('foo', int)", "44");
        assertExpression("3 * headerAs('foo', Integer.class)", "66");
        assertExpression("3 * headerAs('foo', Integer)", "66");
        assertExpression("3 * headerAs('foo', java.lang.Integer.class)", "66");
        assertExpression("3 * headerAs('foo', java.lang.Integer)", "66");
        assertExpression("var num = headerAs('foo', int); return num * 4", "88");

        assertExpression("2 * headerAs(\"foo\", int.class)", "44");
        assertExpression("2 * headerAs(\"foo\", int)", "44");
        assertExpression("3 * headerAs(\"foo\", Integer.class)", "66");
        assertExpression("3 * headerAs(\"foo\", Integer)", "66");
        assertExpression("3 * headerAs(\"foo\", java.lang.Integer.class)", "66");
        assertExpression("3 * headerAs(\"foo\", java.lang.Integer)", "66");
        assertExpression("var num = headerAs(\"foo\", int); return num * 4", "88");
    }

    @Test
    public void testExchangeHeaderAsDefaultValue() {
        exchange.getIn().setHeader("foo", 22);

        assertExpression("2 * headerAs('dog', 33, int.class)", "66");
        assertExpression("3 * headerAs('dog', 33, Integer.class)", "99");
        assertExpression("2 * headerAs('dog', 33, int)", "66");
        assertExpression("3 * headerAs('dog', 33, Integer)", "99");

        assertExpression("'Hello ' + headerAs('dog', 'World', String)", "Hello World");
    }

    @Test
    public void testExchangeOptionalHeaderAs() {
        exchange.getIn().setHeader("foo", 22);

        assertExpression("2 * optionalHeaderAs('foo', int.class).get()", "44");
        assertExpression("2 * optionalHeaderAs('foo', int).get()", "44");
        assertExpression("3 * optionalHeaderAs('foo', Integer.class).get()", "66");
        assertExpression("3 * optionalHeaderAs('foo', Integer).get()", "66");
        assertExpression("3 * optionalHeaderAs('foo', java.lang.Integer.class).get()", "66");
        assertExpression("3 * optionalHeaderAs('foo', java.lang.Integer).get()", "66");
        assertExpression("var num = optionalHeaderAs('foo', int).get(); return num * 4", "88");

        assertExpression("2 * optionalHeaderAs(\"foo\", int.class).get()", "44");
        assertExpression("2 * optionalHeaderAs(\"foo\", int).get()", "44");
        assertExpression("3 * optionalHeaderAs(\"foo\", Integer.class).get()", "66");
        assertExpression("3 * optionalHeaderAs(\"foo\", Integer).get()", "66");
        assertExpression("3 * optionalHeaderAs(\"foo\", java.lang.Integer.class).get()", "66");
        assertExpression("3 * optionalHeaderAs(\"foo\", java.lang.Integer).get()", "66");
        assertExpression("var num = optionalHeaderAs(\"foo\", int).get(); return num * 4", "88");
    }

    @Test
    public void testExchangePropertyAs() {
        exchange.setProperty("bar", 22);

        assertExpression("2 * exchangePropertyAs('bar', int.class)", "44");
        assertExpression("2 * exchangePropertyAs('bar', int)", "44");
        assertExpression("3 * exchangePropertyAs('bar', Integer.class)", "66");
        assertExpression("3 * exchangePropertyAs('bar', Integer)", "66");
        assertExpression("3 * exchangePropertyAs('bar', java.lang.Integer.class)", "66");
        assertExpression("3 * exchangePropertyAs('bar', java.lang.Integer)", "66");
        assertExpression("var num = exchangePropertyAs('bar', int); return num * 4", "88");

        assertExpression("2 * exchangePropertyAs(\"bar\", int.class)", "44");
        assertExpression("2 * exchangePropertyAs(\"bar\", int)", "44");
        assertExpression("3 * exchangePropertyAs(\"bar\", Integer.class)", "66");
        assertExpression("3 * exchangePropertyAs(\"bar\", Integer)", "66");
        assertExpression("3 * exchangePropertyAs(\"bar\", java.lang.Integer.class)", "66");
        assertExpression("3 * exchangePropertyAs(\"bar\", java.lang.Integer)", "66");
        assertExpression("var num = exchangePropertyAs(\"bar\", int); return num * 4", "88");
    }

    @Test
    public void testExchangePropertyDefaultValueAs() {
        exchange.setProperty("bar", 22);

        assertExpression("2 * exchangePropertyAs('dog', 33, int.class)", "66");
        assertExpression("3 * exchangePropertyAs('dog', 33, Integer.class)", "99");
        assertExpression("2 * exchangePropertyAs('dog', 33, int)", "66");
        assertExpression("3 * exchangePropertyAs('dog', 33, Integer)", "99");

        assertExpression("'Hello ' + exchangePropertyAs('dog', 'World', String)", "Hello World");
    }

    @Test
    public void testOptionalExchangePropertyAs() {
        exchange.setProperty("bar", 22);

        assertExpression("2 * optionalExchangePropertyAs('bar', int.class).get()", "44");
        assertExpression("2 * optionalExchangePropertyAs('bar', int).get()", "44");
        assertExpression("3 * optionalExchangePropertyAs('bar', Integer.class).get()", "66");
        assertExpression("3 * optionalExchangePropertyAs('bar', Integer).get()", "66");
        assertExpression("3 * optionalExchangePropertyAs('bar', java.lang.Integer.class).get()", "66");
        assertExpression("3 * optionalExchangePropertyAs('bar', java.lang.Integer).get()", "66");
        assertExpression("var num = optionalExchangePropertyAs('bar', int).get(); return num * 4", "88");

        assertExpression("2 * optionalExchangePropertyAs(\"bar\", int.class).get()", "44");
        assertExpression("2 * optionalExchangePropertyAs(\"bar\", int).get()", "44");
        assertExpression("3 * optionalExchangePropertyAs(\"bar\", Integer.class).get()", "66");
        assertExpression("3 * optionalExchangePropertyAs(\"bar\", Integer).get()", "66");
        assertExpression("3 * optionalExchangePropertyAs(\"bar\", java.lang.Integer.class).get()", "66");
        assertExpression("3 * optionalExchangePropertyAs(\"bar\", java.lang.Integer).get()", "66");
        assertExpression("var num = optionalExchangePropertyAs(\"bar\", int).get(); return num * 4", "88");
    }

}
