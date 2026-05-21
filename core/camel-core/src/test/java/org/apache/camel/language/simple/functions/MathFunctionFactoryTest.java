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
import static org.junit.jupiter.api.Assertions.assertNull;

public class MathFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new MathFunctionFactory();
    }

    @Test
    public void testAbs() {
        exchange.getIn().setBody(-5);
        assertEquals(5, evaluate("abs(${body})", Integer.class));
    }

    @Test
    public void testAbsLiteral() {
        assertEquals(7.0, evaluate("abs(-7)", Double.class));
    }

    @Test
    public void testFloor() {
        exchange.getIn().setBody(3.7);
        assertEquals(3L, evaluate("floor(${body})", Long.class));
    }

    @Test
    public void testCeil() {
        exchange.getIn().setBody(3.2);
        assertEquals(4L, evaluate("ceil(${body})", Long.class));
    }

    @Test
    public void testSum() {
        assertEquals(6.0, evaluate("sum(1, 2, 3)", Double.class));
    }

    @Test
    public void testSumWithBody() {
        exchange.getIn().setBody(10);
        assertEquals(16.0, evaluate("sum(${body}, 3, 3)", Double.class));
    }

    @Test
    public void testMax() {
        assertEquals(3.0, evaluate("max(1, 2, 3)", Double.class));
    }

    @Test
    public void testMin() {
        assertEquals(1.0, evaluate("min(1, 2, 3)", Double.class));
    }

    @Test
    public void testAverage() {
        assertEquals(2.0, evaluate("average(1, 2, 3)", Double.class));
    }

    @Test
    public void testCreateCodeAbs() {
        assertEquals("Object o = null;\n        return abs(exchange, o);", createCode("abs()"));
        assertEquals("Object o = 5;\n        return abs(exchange, o);", createCode("abs(5)"));
    }

    @Test
    public void testCreateCodeFloor() {
        assertEquals("Object o = null;\n        return floor(exchange, o);", createCode("floor()"));
        assertEquals("Object o = 3.7;\n        return floor(exchange, o);", createCode("floor(3.7)"));
    }

    @Test
    public void testCreateCodeCeil() {
        assertEquals("Object o = null;\n        return ceil(exchange, o);", createCode("ceil()"));
    }

    @Test
    public void testCreateCodeSum() {
        assertEquals("sum(exchange, 1, 2, 3)", createCode("sum(1, 2, 3)"));
    }

    @Test
    public void testCreateCodeMax() {
        assertEquals("max(exchange, 1, 2)", createCode("max(1, 2)"));
    }

    @Test
    public void testCreateCodeMin() {
        assertEquals("min(exchange, 1, 2)", createCode("min(1, 2)"));
    }

    @Test
    public void testCreateCodeAverage() {
        assertEquals("average(exchange, 1, 2, 3)", createCode("average(1, 2, 3)"));
    }

    @Test
    public void testUnknownFunctionReturnsNull() {
        assertNull(createFactory().createFunction(context, "trim()", 0));
        assertNull(createFactory().createCode(context, "trim()", 0));
    }
}
