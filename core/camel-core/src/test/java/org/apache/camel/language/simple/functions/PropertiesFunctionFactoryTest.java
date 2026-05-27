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

import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PropertiesFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new PropertiesFunctionFactory();
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("myAnimal", "Donkey");
        return registry;
    }

    // --- ref: ---

    @Test
    public void testRefExpression() {
        assertIsInstanceOf(String.class, evaluate("ref:myAnimal", Object.class));
        assertEquals("Donkey", evaluate("ref:myAnimal", String.class));
        assertNull(evaluate("ref:unknown", Object.class));
    }

    @Test
    public void testCreateCodeRef() {
        assertEquals("ref(exchange, \"myBean\")", createCode("ref:myBean"));
    }

    // --- propertiesExist: ---

    @Test
    public void testPropertiesExist() {
        PropertiesComponent pc = context.getPropertiesComponent();

        assertEquals("false", evaluate("propertiesExist:myKey", String.class));
        assertEquals("true", evaluate("propertiesExist:!myKey", String.class));
        assertEquals(false, evaluate("propertiesExist:myKey", Boolean.class));
        assertEquals(true, evaluate("propertiesExist:!myKey", Boolean.class));

        pc.addInitialProperty("myKey", "abc");
        assertEquals("true", evaluate("propertiesExist:myKey", String.class));
        assertEquals("false", evaluate("propertiesExist:!myKey", String.class));
        assertEquals(true, evaluate("propertiesExist:myKey", Boolean.class));
        assertEquals(false, evaluate("propertiesExist:!myKey", Boolean.class));
    }

    @Test
    public void testPropertiesExistHasNoCodeGeneration() {
        // propertiesExist: is handled at runtime only; CSimple has no code-gen path for it
        assertNull(createFactory().createCode(context, "propertiesExist:myKey", 0));
    }

    // --- properties: ---

    @Test
    public void testCreateCodePropertiesNoDefault() {
        assertEquals("properties(exchange, \"myKey\")", createCode("properties:myKey"));
    }

    @Test
    public void testCreateCodePropertiesWithDefault() {
        assertEquals("properties(exchange, \"myKey\", \"myDefault\")", createCode("properties:myKey:myDefault"));
    }

    @Test
    public void testCreateCodeUnknown() {
        assertNull(createFactory().createCode(context, "unknown", 0));
    }
}
