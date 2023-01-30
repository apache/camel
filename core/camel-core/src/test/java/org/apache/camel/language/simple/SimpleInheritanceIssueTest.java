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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.Expression;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.converter.IOConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleInheritanceIssueTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    @Test
    public void testMethodCall() throws Exception {
        MySingleParser parser = new MySingleParser();
        exchange.getIn().setBody(parser);

        Expression expression = context.resolveLanguage("simple").createExpression("${body.parse('data')}");
        String result = expression.evaluate(exchange, String.class);
        assertEquals("data", result);
    }

    @Test
    public void testMethodCallHeader() throws Exception {
        MySingleParser parser = new MySingleParser();
        exchange.getIn().setBody(parser);
        // the input stream should only be read once so we should get the byte array
        ByteArrayInputStream bais = new ByteArrayInputStream("data".getBytes());
        exchange.getIn().setHeader("input", bais);

        Expression expression = context.resolveLanguage("simple").createExpression("${body.parse(${header.input})}");
        String result = expression.evaluate(exchange, String.class);
        assertEquals("data", result);
    }

    @Test
    public void testMethodCallOverloadedHeader() throws Exception {
        MyParser parser = new MyParser();
        exchange.getIn().setBody(parser);
        // the input stream should only be read once so we should get the byte array
        ByteArrayInputStream bais = new ByteArrayInputStream("data".getBytes());
        exchange.getIn().setHeader("input", bais);

        Expression expression = context.resolveLanguage("simple").createExpression("${body.parse(${header.input})}");
        String result = expression.evaluate(exchange, String.class);
        assertEquals("data", result);
    }

    public static class MySingleParser {

        public String parse(byte[] input) {
            return new String(input);
        }

    }

    public static class MyParser {

        public String parse(byte[] input) {
            return "array";
        }

        public String parse(InputStream input) throws Exception {
            return IOConverter.toString(input, null);
        }
    }

}
