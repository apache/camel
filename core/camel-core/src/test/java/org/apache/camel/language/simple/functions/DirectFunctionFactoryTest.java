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
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DirectFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new DirectFunctionFactory();
    }

    // --- expression evaluation ---

    @Test
    public void testId() {
        assertNotNull(evaluate("id", String.class));
    }

    @Test
    public void testExchangeId() {
        assertEquals(exchange.getExchangeId(), evaluate("exchangeId", String.class));
    }

    @Test
    public void testExchange() {
        assertSame(exchange, evaluate("exchange", Exchange.class));
    }

    @Test
    public void testExceptionNull() {
        assertNull(evaluate("exception"));
    }

    @Test
    public void testExceptionMessage() {
        exchange.setException(new IllegalStateException("boom"));
        assertEquals("boom", evaluate("exception.message", String.class));
    }

    @Test
    public void testExceptionStacktrace() {
        exchange.setException(new IllegalStateException("boom"));
        String trace = evaluate("exception.stacktrace", String.class);
        assertNotNull(trace);
    }

    @Test
    public void testThreadId() {
        assertNotNull(evaluate("threadId"));
    }

    @Test
    public void testThreadName() {
        assertNotNull(evaluate("threadName", String.class));
    }

    @Test
    public void testHostname() {
        assertNotNull(evaluate("hostname", String.class));
    }

    @Test
    public void testCamelId() {
        assertEquals(context.getName(), evaluate("camelId", String.class));
    }

    @Test
    public void testNull() {
        assertNull(evaluate("null"));
    }

    // --- code generation ---

    @Test
    public void testCreateCodeId() {
        assertEquals("message.getMessageId()", createCode("id"));
    }

    @Test
    public void testCreateCodeMessageTimestamp() {
        assertEquals("message.getMessageTimestamp()", createCode("messageTimestamp"));
    }

    @Test
    public void testCreateCodeExchangeId() {
        assertEquals("exchange.getExchangeId()", createCode("exchangeId"));
    }

    @Test
    public void testCreateCodeExchange() {
        assertEquals("exchange", createCode("exchange"));
    }

    @Test
    public void testCreateCodeLogExchange() {
        assertEquals("logExchange(exchange)", createCode("logExchange"));
    }

    @Test
    public void testCreateCodeException() {
        assertEquals("exception(exchange)", createCode("exception"));
    }

    @Test
    public void testCreateCodeExceptionMessage() {
        assertEquals("exceptionMessage(exchange)", createCode("exception.message"));
    }

    @Test
    public void testCreateCodeExceptionStacktrace() {
        assertEquals("exceptionStacktrace(exchange)", createCode("exception.stacktrace"));
    }

    @Test
    public void testCreateCodeThreadId() {
        assertEquals("threadId()", createCode("threadId"));
    }

    @Test
    public void testCreateCodeThreadName() {
        assertEquals("threadName()", createCode("threadName"));
    }

    @Test
    public void testCreateCodeHostname() {
        assertEquals("hostName()", createCode("hostname"));
    }

    @Test
    public void testCreateCodeCamelId() {
        assertEquals("context.getName()", createCode("camelId"));
    }

    @Test
    public void testCreateCodeRouteId() {
        assertEquals("routeId(exchange)", createCode("routeId"));
    }

    @Test
    public void testCreateCodeFromRouteId() {
        assertEquals("fromRouteId(exchange)", createCode("fromRouteId"));
    }

    @Test
    public void testCreateCodeStepId() {
        assertEquals("stepId(exchange)", createCode("stepId"));
    }

    @Test
    public void testCreateCodeNull() {
        assertEquals("null", createCode("null"));
    }

    @Test
    public void testCreateCodeUnknown() {
        assertNull(createFactory().createCode(context, "unknown", 0));
    }
}
