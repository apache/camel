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
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SystemFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new SystemFunctionFactory();
    }

    // --- sys. ---

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    public void testSimpleSystemPropertyExpressions() {
        System.setProperty("who", "I was here");
        assertEquals("I was here", evaluate("sys.who", String.class));
    }

    @Test
    public void testCreateCodeSys() {
        assertEquals("sys(\"java.version\")", createCode("sys.java.version"));
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

    @Test
    public void testCreateCodeSysenvDot() {
        assertEquals("sysenv(\"MY_VAR\")", createCode("sysenv.MY_VAR"));
    }

    @Test
    public void testCreateCodeSysenvColon() {
        assertEquals("sysenv(\"MY_VAR\")", createCode("sysenv:MY_VAR"));
    }

    @Test
    public void testCreateCodeEnvDot() {
        assertEquals("sysenv(\"MY_VAR\")", createCode("env.MY_VAR"));
    }

    @Test
    public void testCreateCodeEnvColon() {
        assertEquals("sysenv(\"MY_VAR\")", createCode("env:MY_VAR"));
    }

    // --- no match ---

    @Test
    public void testNoMatch() {
        assertNull(createFactory().createFunction(context, "body", 0));
    }

    @Test
    public void testNoMatchCode() {
        assertNull(createFactory().createCode(context, "body", 0));
    }
}
