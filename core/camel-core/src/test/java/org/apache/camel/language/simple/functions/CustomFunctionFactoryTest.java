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

import org.apache.camel.Exchange;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleFunction;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CustomFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @BeforeEach
    public void registerFunction() {
        PluginHelper.getSimpleFunctionRegistry(context).addFunction(new SimpleFunction() {
            @Override
            public String getName() {
                return "shout";
            }

            @Override
            public Object apply(Exchange exchange, Object input) {
                return input.toString().toUpperCase() + "!";
            }
        });
    }

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new CustomFunctionFactory();
    }

    // --- function(name) with default body input ---

    @Test
    public void testFunctionWithBody() {
        exchange.getIn().setBody("hello");
        assertEquals("HELLO!", evaluate("function(shout)", String.class));
    }

    // --- function(name, exp) with explicit expression ---

    @Test
    public void testFunctionWithExplicitExp() {
        exchange.getIn().setHeader("msg", "world");
        assertEquals("WORLD!", evaluate("function(shout,${header.msg})", String.class));
    }

    // --- bare name syntax (look-ahead path in SimpleFunctionExpression) ---

    @Test
    public void testBareNameSyntax() {
        exchange.getIn().setBody("camel");
        assertEquals("CAMEL!", evaluate("shout", String.class));
    }

    // --- error cases ---

    @Test
    public void testMissingClosingParen() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "function(shout", 0));
    }

    @Test
    public void testEmptyName() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "function()", 0));
    }

    // --- createCode always returns null (no CSimple support) ---

    @Test
    public void testCreateCodeReturnsNull() {
        assertNull(createFactory().createCode(context, "function(shout)", 0));
    }

    // --- unrecognized ---

    @Test
    public void testUnrecognizedFunction() {
        assertNull(createFactory().createFunction(context, "unknown", 0));
    }
}
