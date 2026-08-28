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
}
