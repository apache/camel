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
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RandomFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new RandomFunctionFactory();
    }

    @Test
    public void testRandomExpression() {
        int min = 1;
        int max = 10;

        for (int i = 0; i < 30; i++) {
            Expression expression = createFactory().createFunction(context, "random(1,10)", 0);
            expression.init(context);
            int num = expression.evaluate(exchange, Integer.class);
            assertTrue(min <= num && num < max);
        }
        for (int i = 0; i < 30; i++) {
            Expression expression = createFactory().createFunction(context, "random(10)", 0);
            expression.init(context);
            int num = expression.evaluate(exchange, Integer.class);
            assertTrue(0 <= num && num < max);
        }

        int num1 = evaluate("random(1, 10)", Integer.class);
        assertTrue(min <= num1 && num1 < max);

        int num2 = evaluate("random( 10)", Integer.class);
        assertTrue(0 <= num2 && num2 < max);

        Exception e1 = assertThrows(Exception.class,
                () -> evaluate("random(10,21,30)", Object.class),
                "Should have thrown exception");
        assertEquals("Valid syntax: ${random(min,max)} or ${random(max)} was: random(10,21,30)", e1.getCause().getMessage());

        Exception e2 = assertThrows(Exception.class,
                () -> evaluate("random()", Object.class),
                "Should have thrown exception");
        assertEquals("Valid syntax: ${random(min,max)} or ${random(max)} was: random()", e2.getCause().getMessage());

        exchange.getIn().setHeader("max", 20);
        int num = evaluate("random(10,${header.max})", Integer.class);
        assertTrue(num >= 0 && num < 20, "Should be 10..20");
    }

    @Test
    public void testCreateCode() {
        assertEquals("random(exchange, 0, 10)", createCode("random(10)"));
        assertEquals("random(exchange, 10, 20)", createCode("random(10, 20)"));
        assertEquals("random(exchange, 10, ${header.max})", createCode("random(10, ${header.max})"));
        assertEquals("random(exchange, ${header.min}, ${header.max})", createCode("random(${header.min}, ${header.max})"));
    }
}
