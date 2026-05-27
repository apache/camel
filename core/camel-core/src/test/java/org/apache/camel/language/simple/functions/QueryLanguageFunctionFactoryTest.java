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
package org.apache.camel.language.simple.functions;

import org.apache.camel.Expression;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QueryLanguageFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new QueryLanguageFunctionFactory();
    }

    // --- xpath ---

    @Test
    public void testXpathBody() {
        exchange.getIn().setBody("<root><name>World</name></root>");
        assertEquals("World", evaluate("xpath('/root/name/text()')", String.class));
    }

    @Test
    public void testXpathWithHeaderInput() {
        exchange.getIn().setHeader("myHdr", "<root><val>42</val></root>");
        assertEquals("42", evaluate("xpath('header:myHdr,/root/val/text()')", String.class));
    }

    @Test
    public void testXpathMissingClosingParen() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "xpath('unclosed", 0));
    }

    // --- jq / jsonpath / simpleJsonpath: factory creates expressions (languages not on classpath in core) ---

    @Test
    public void testJqCreatesFunctionExpression() {
        Expression exp = createFactory().createFunction(context, "jq('.name')", 0);
        assertNotNull(exp);
    }

    @Test
    public void testJqWithHeaderInputCreatesExpression() {
        Expression exp = createFactory().createFunction(context, "jq('header:myHdr,.name')", 0);
        assertNotNull(exp);
    }

    @Test
    public void testJqMissingClosingParen() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "jq('unclosed", 0));
    }

    @Test
    public void testJsonpathCreatesExpression() {
        Expression exp = createFactory().createFunction(context, "jsonpath('$.name')", 0);
        assertNotNull(exp);
    }

    @Test
    public void testJsonpathMissingClosingParen() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "jsonpath('unclosed", 0));
    }

    @Test
    public void testSimpleJsonpathCreatesExpression() {
        Expression exp = createFactory().createFunction(context, "simpleJsonpath('$.name')", 0);
        assertNotNull(exp);
    }

    @Test
    public void testSimpleJsonpathMissingClosingParen() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "simpleJsonpath('unclosed", 0));
    }

    // --- createCode returns null (no CSimple support) ---

    @Test
    public void testCreateCodeReturnsNull() {
        assertNull(createFactory().createCode(context, "jq('.name')", 0));
        assertNull(createFactory().createCode(context, "jsonpath('$.name')", 0));
        assertNull(createFactory().createCode(context, "xpath('/root')", 0));
        assertNull(createFactory().createCode(context, "simpleJsonpath('$.name')", 0));
    }

    // --- unrecognized ---

    @Test
    public void testUnrecognizedFunction() {
        assertNull(createFactory().createFunction(context, "unknown", 0));
    }
}
