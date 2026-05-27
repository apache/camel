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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BeanFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    public static final class MyBean {
        public String hello(String name) {
            return "Hello " + name;
        }

        public String greet() {
            return "Greetings";
        }
    }

    @BeforeEach
    public void registerBean() {
        context.getRegistry().bind("myBean", new MyBean());
    }

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new BeanFunctionFactory();
    }

    // --- dot notation ---

    @Test
    public void testBeanDotMethod() {
        exchange.getIn().setBody("World");
        assertEquals("Hello World", evaluate("bean:myBean.hello", String.class));
    }

    @Test
    public void testBeanNoMethod() {
        assertEquals("Greetings", evaluate("bean:myBean.greet", String.class));
    }

    // --- double-colon notation ---

    @Test
    public void testBeanDoubleColonMethod() {
        exchange.getIn().setBody("World");
        assertEquals("Hello World", evaluate("bean:myBean::hello", String.class));
    }

    // --- query-string notation ---

    @Test
    public void testBeanQueryMethod() {
        exchange.getIn().setBody("World");
        assertEquals("Hello World", evaluate("bean:myBean?method=hello", String.class));
    }

    // --- createCode ---

    @Test
    public void testCreateCodeRefOnly() {
        assertEquals("bean(exchange, bean, \"myBean\", null, null)", createCode("bean:myBean"));
    }

    @Test
    public void testCreateCodeDotMethod() {
        assertEquals("bean(exchange, bean, \"myBean\", \"hello\", null)", createCode("bean:myBean.hello"));
    }

    @Test
    public void testCreateCodeDoubleColon() {
        assertEquals("bean(exchange, bean, \"myBean\", \"hello\", null)", createCode("bean:myBean::hello"));
    }

    @Test
    public void testCreateCodeQueryMethodAndScope() {
        assertEquals(
                "bean(exchange, bean, \"myBean\", \"hello\", \"Singleton\")",
                createCode("bean:myBean?method=hello&scope=Singleton"));
    }

    @Test
    public void testCreateCodeScopeOnly() {
        assertEquals(
                "bean(exchange, bean, \"myBean\", null, \"Singleton\")",
                createCode("bean:myBean?scope=Singleton"));
    }

    // --- unrecognized ---

    @Test
    public void testUnrecognizedFunction() {
        assertNull(createFactory().createFunction(context, "unknown", 0));
        assertNull(createFactory().createCode(context, "unknown", 0));
    }
}
