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
import org.apache.camel.ExchangePattern;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TypeFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new TypeFunctionFactory();
    }

    // --- type: ---

    @Test
    public void testTypeConstant() {
        assertEquals(Exchange.FILE_NAME, evaluate("type:org.apache.camel.Exchange.FILE_NAME", String.class));
        assertEquals(ExchangePattern.InOut, evaluate("type:org.apache.camel.ExchangePattern.InOut", ExchangePattern.class));

        Exception e1 = assertThrows(Exception.class,
                () -> evaluate("type:org.apache.camel.ExchangePattern.", Object.class));
        assertIsInstanceOf(ClassNotFoundException.class, e1.getCause());

        Exception e2 = assertThrows(Exception.class,
                () -> evaluate("type:org.apache.camel.ExchangePattern.UNKNOWN", Object.class));
        assertIsInstanceOf(ClassNotFoundException.class, e2.getCause());
    }

    @Test
    public void testTypeConstantInnerClass() {
        assertEquals(123, evaluate("type:org.apache.camel.language.simple.Constants$MyInnerStuff.FOO", Integer.class));
        assertEquals(456, evaluate("type:org.apache.camel.language.simple.Constants.BAR", Integer.class));
    }

    @Test
    public void testCreateCodeTypeWithField() {
        assertEquals("type(exchange, java.lang.Integer.class, \"MAX_VALUE\")",
                createCode("type:java.lang.Integer.MAX_VALUE"));
    }

    @Test
    public void testCreateCodeTypeNoField() {
        // no dots in the remainder -> field is null, only class emitted
        assertEquals("type(exchange, String.class)", createCode("type:String"));
    }

    @Test
    public void testCreateCodeUnknown() {
        assertNull(createFactory().createCode(context, "unknown", 0));
    }
}
