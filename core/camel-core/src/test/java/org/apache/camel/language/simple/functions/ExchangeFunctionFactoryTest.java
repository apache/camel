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

import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExchangeFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new ExchangeFunctionFactory();
    }

    // --- camelContext ---

    @Test
    public void testCamelContextVersion() {
        String version = evaluate("camelContext.version", String.class);
        assertEquals(context.getVersion(), version);
    }

    @Test
    public void testCamelContextInvalidOgnl() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "camelContext[bad", 0));
    }

    // --- exception ---

    @Test
    public void testExceptionMessage() {
        exchange.setException(new IllegalArgumentException("oops"));
        assertEquals("oops", evaluate("exception.message", String.class));
    }

    @Test
    public void testExceptionInvalidOgnl() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "exception[bad", 0));
    }

    // --- exchangeProperty ---

    @Test
    public void testExchangePropertyDotNotation() {
        exchange.setProperty("color", "blue");
        assertEquals("blue", evaluate("exchangeProperty.color", String.class));
    }

    @Test
    public void testExchangePropertyColonNotation() {
        exchange.setProperty("color", "red");
        assertEquals("red", evaluate("exchangeProperty:color", String.class));
    }

    @Test
    public void testExchangePropertyBracketNotation() {
        exchange.setProperty("color", "green");
        assertEquals("green", evaluate("exchangeProperty[color]", String.class));
    }

    @Test
    public void testExchangePropertyInvalidOgnl() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "exchangeProperty.foobar[bar", 0));
    }

    // --- exchange OGNL ---

    @Test
    public void testExchangeExchangeId() {
        String id = evaluate("exchange.exchangeId", String.class);
        assertEquals(exchange.getExchangeId(), id);
    }

    @Test
    public void testExchangeInvalidOgnl() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "exchange[bad", 0));
    }

    // --- createCode ---

    @Test
    public void testCreateCodeCamelContext() {
        assertEquals("context.getName()", createCode("camelContext.name"));
    }

    @Test
    public void testCreateCodeExchangeProperty() {
        assertEquals("exchangeProperty(exchange, \"color\")", createCode("exchangeProperty.color"));
    }

    @Test
    public void testCreateCodeExchangePropertyAs() {
        assertEquals("exchangePropertyAs(exchange, \"count\", Integer.class)",
                createCode("exchangePropertyAs(count, Integer)"));
    }

    @Test
    public void testCreateCodeExchangePropertyAsIndex() {
        assertEquals("exchangePropertyAsIndex(exchange, String.class, \"list\", \"0\")",
                createCode("exchangePropertyAsIndex(list, String, 0)"));
    }

    @Test
    public void testCreateCodeException() {
        assertEquals("exception(exchange).getMessage()", createCode("exception.message"));
    }

    @Test
    public void testCreateCodeExceptionInvalidOgnl() {
        assertThrows(SimpleParserException.class,
                () -> createFactory().createCode(context, "exception[bad", 0));
    }

    @Test
    public void testCreateCodeExceptionAs() {
        assertEquals("exceptionAs(exchange, IllegalArgumentException.class)",
                createCode("exceptionAs(IllegalArgumentException)"));
    }

    @Test
    public void testCreateCodeExchange() {
        assertEquals("exchange.getExchangeId()", createCode("exchange.exchangeId"));
    }

    // --- unrecognized ---

    @Test
    public void testUnrecognizedFunction() {
        assertNull(createFactory().createFunction(context, "unknown", 0));
        assertNull(createFactory().createCode(context, "unknown", 0));
    }
}
