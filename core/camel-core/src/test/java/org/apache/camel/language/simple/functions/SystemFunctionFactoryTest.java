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
import org.junitpioneer.jupiter.SetSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SystemFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new SystemFunctionFactory();
    }

    // --- sys. ---

    @Test
    @SetSystemProperty(key = "who", value = "I was here")
    public void testSimpleSystemPropertyExpressions() {
        assertEquals("I was here", evaluate("sys.who", String.class));
    }

    // --- sysenv. / sysenv: / env. / env: ---

    @Test
    public void testSimpleSystemEnvironmentExpressions() {
        String path = System.getenv("PATH");
        if (path != null) {
            assertEquals(path, evaluate("sysenv.PATH", String.class));
            assertEquals(path, evaluate("sysenv:PATH", String.class));
            assertEquals(path, evaluate("env.PATH", String.class));
            assertEquals(path, evaluate("env:PATH", String.class));
        }
    }

    @Test
    public void testSimpleSystemEnvironmentExpressionsIfDash() {
        String foo = System.getenv("FOO_SERVICE_HOST");
        if (foo != null) {
            assertEquals(foo, evaluate("sysenv.FOO-SERVICE-HOST", String.class));
            assertEquals(foo, evaluate("sysenv:FOO-SERVICE-HOST", String.class));
            assertEquals(foo, evaluate("env.FOO-SERVICE-HOST", String.class));
            assertEquals(foo, evaluate("env:FOO-SERVICE-HOST", String.class));
        }
    }

    @Test
    public void testSimpleSystemEnvironmentExpressionsIfLowercase() {
        String path = System.getenv("PATH");
        if (path != null) {
            assertEquals(path, evaluate("sysenv.path", String.class));
            assertEquals(path, evaluate("sysenv:path", String.class));
            assertEquals(path, evaluate("env.path", String.class));
            assertEquals(path, evaluate("env:path", String.class));
        }
    }
}
