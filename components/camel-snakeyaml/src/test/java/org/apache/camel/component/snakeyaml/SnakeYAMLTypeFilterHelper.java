/**
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

package org.apache.camel.component.snakeyaml;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.snakeyaml.model.RexPojo;
import org.apache.camel.component.snakeyaml.model.TestPojo;
import org.apache.camel.component.snakeyaml.model.UnsafePojo;
import org.yaml.snakeyaml.constructor.ConstructorException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class SnakeYAMLTypeFilterHelper {
    private SnakeYAMLTypeFilterHelper() {
    }

    static void testSafeConstructor(ProducerTemplate template) throws Exception {
        try {
            template.sendBody(
                "direct:safe-constructor",
                "!!org.apache.camel.component.snakeyaml.model.TestPojo {name: Camel}");

            fail("As SnakeYAML uses SafeConstructor, custom types should not be allowed");
        } catch (CamelExecutionException e) {
            assertTrue(e.getCause() instanceof ConstructorException);
        }
    }

    static void testTypeConstructor(ProducerTemplate template) throws Exception {
        Object result = template.requestBody(
            "direct:type-constructor",
            "!!org.apache.camel.component.snakeyaml.model.TestPojo {name: Camel}");

        assertNotNull(result);
        assertTrue(result instanceof TestPojo);

        try {
            template.sendBody(
                "direct:type-constructor",
                "!!org.apache.camel.component.snakeyaml.model.UnsafePojo {name: Camel}");

            fail("As SnakeYAML filters class is can unmarshall, UnsafePojo should not be allowed");
        } catch (CamelExecutionException e) {
            // Wrapped by SnakeYAML
            assertTrue(e.getCause() instanceof ConstructorException);
            // Thrown by SnakeYAMLDataFormat
            assertTrue(e.getCause().getCause() instanceof IllegalArgumentException);
        }
    }

    static void testTypeConstructorFromDefinition(ProducerTemplate template) throws Exception {
        Object result;

        // TestPojo --> from definition type:
        result = template.requestBody(
            "direct:type-constructor-strdef",
            "!!org.apache.camel.component.snakeyaml.model.TestPojo {name: Camel}");

        assertNotNull(result);
        assertTrue(result instanceof TestPojo);

        // RexPojo --> from definition rex:
        result = template.requestBody(
            "direct:type-constructor-strdef",
            "!!org.apache.camel.component.snakeyaml.model.RexPojo {name: Camel}");

        assertNotNull(result);
        assertTrue(result instanceof RexPojo);

        try {
            template.sendBody(
                "direct:type-constructor-strdef",
                "!!org.apache.camel.component.snakeyaml.model.UnsafePojo {name: Camel}");

            fail("As SnakeYAML filters class is can unmarshall, UnsafePojo should not be allowed");
        } catch (CamelExecutionException e) {
            // Wrapped by SnakeYAML
            assertTrue(e.getCause() instanceof ConstructorException);
            // Thrown by SnakeYAMLDataFormat
            assertTrue(e.getCause().getCause() instanceof IllegalArgumentException);
        }
    }

    static void testAllowAllConstructor(ProducerTemplate template) throws Exception {
        Object testPojo = template.requestBody(
            "direct:all-constructor",
            "!!org.apache.camel.component.snakeyaml.model.TestPojo {name: Camel}");

        assertNotNull(testPojo);
        assertTrue(testPojo instanceof TestPojo);

        Object unsafePojo = template.requestBody(
            "direct:all-constructor",
            "!!org.apache.camel.component.snakeyaml.model.UnsafePojo {name: Camel}");

        assertNotNull(unsafePojo);
        assertTrue(unsafePojo instanceof UnsafePojo);
    }
}
