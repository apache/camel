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

import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BodyFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new BodyFunctionFactory();
    }

    @Test
    public void testBody() {
        exchange.getIn().setBody("Hello World");
        assertEquals("Hello World", evaluate("body", String.class));
    }

    @Test
    public void testInBody() {
        exchange.getIn().setBody("Hello World");
        assertEquals("Hello World", evaluate("in.body", String.class));
    }

    @Test
    public void testBodyAs() {
        exchange.getIn().setBody(42);
        assertEquals("42", evaluate("bodyAs(String)", String.class));
    }

    @Test
    public void testMandatoryBodyAs() {
        exchange.getIn().setBody(42);
        assertEquals(42, evaluate("mandatoryBodyAs(Integer)", Integer.class));
    }

    @Test
    public void testBodyOgnl() {
        exchange.getIn().setBody("Hello World");
        assertEquals(11, evaluate("body.length()", Integer.class));
    }

    @Test
    public void testBodyType() {
        exchange.getIn().setBody("Hello World");
        assertNotNull(evaluate("bodyType"));
    }

    @Test
    public void testCreateCodeBody() {
        assertEquals("body", createCode("body"));
    }

    @Test
    public void testCreateCodeInBody() {
        assertEquals("body", createCode("in.body"));
    }

    @Test
    public void testCreateCodeBodyType() {
        assertEquals("bodyType(exchange)", createCode("bodyType"));
    }

    @Test
    public void testCreateCodePrettyBody() {
        assertEquals("prettyBody(exchange)", createCode("prettyBody"));
    }

    @Test
    public void testCreateCodeToJsonBody() {
        assertEquals("toJsonBody(exchange, false)", createCode("toJsonBody"));
    }

    @Test
    public void testCreateCodeToPrettyJsonBody() {
        assertEquals("toJsonBody(exchange, true)", createCode("toPrettyJsonBody"));
    }

    @Test
    public void testCreateCodeBodyOneLine() {
        assertEquals("bodyOneLine(exchange)", createCode("bodyOneLine"));
    }

    @Test
    public void testCreateCodeBodyAs() {
        assertEquals("bodyAs(message, String.class)", createCode("bodyAs(String)"));
        assertEquals("bodyAs(message, java.lang.Integer.class)", createCode("bodyAs(java.lang.Integer)"));
    }

    @Test
    public void testCreateCodeMandatoryBodyAs() {
        assertEquals("mandatoryBodyAs(message, String.class)", createCode("mandatoryBodyAs(String)"));
    }

    @Test
    public void testCreateCodeBodyOgnl() {
        assertEquals("body.getLength()", createCode("body.length"));
    }
}
