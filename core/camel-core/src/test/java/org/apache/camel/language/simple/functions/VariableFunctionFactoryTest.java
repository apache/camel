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

import java.util.Map;

import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VariableFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new VariableFunctionFactory();
    }

    @Test
    public void testVariable() {
        context.getVariable("myVar", Object.class);
        exchange.setVariable("myVar", "hello");
        assertEquals("hello", evaluate("variable.myVar", String.class));
    }

    @Test
    public void testVariableAs() {
        exchange.setVariable("num", "99");
        assertEquals(99, evaluate("variableAs(num, Integer)", Integer.class));
    }

    @Test
    public void testVariables() {
        exchange.setVariable("foo", "bar");
        Map<?, ?> vars = evaluate("variables", Map.class);
        assertNotNull(vars);
        assertEquals("bar", vars.get("foo"));
    }

    @Test
    public void testCreateCodeVariable() {
        assertEquals("variable(exchange, \"myVar\")", createCode("variable.myVar"));
    }

    @Test
    public void testCreateCodeVariables() {
        assertEquals("variables(exchange)", createCode("variables"));
    }

    @Test
    public void testCreateCodeVariablesSize() {
        assertEquals("variablesSize(exchange)", createCode("variables.size"));
        assertEquals("variablesSize(exchange)", createCode("variables.size()"));
        assertEquals("variablesSize(exchange)", createCode("variables.length"));
        assertEquals("variablesSize(exchange)", createCode("variables.length()"));
    }

    @Test
    public void testCreateCodeVariableAs() {
        assertEquals("variableAs(exchange, \"num\", Integer.class)", createCode("variableAs(num, Integer)"));
        assertEquals("variableAs(exchange, \"num\", java.lang.Integer.class)",
                createCode("variableAs(num, java.lang.Integer)"));
    }
}
