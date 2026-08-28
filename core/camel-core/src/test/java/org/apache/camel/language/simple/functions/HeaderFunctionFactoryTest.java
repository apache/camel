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

public class HeaderFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new HeaderFunctionFactory();
    }

    @Test
    public void testHeader() {
        exchange.getIn().setHeader("foo", "bar");
        assertEquals("bar", evaluate("header.foo", String.class));
    }

    @Test
    public void testInHeader() {
        exchange.getIn().setHeader("foo", "bar");
        assertEquals("bar", evaluate("in.header.foo", String.class));
    }

    @Test
    public void testHeaders() {
        exchange.getIn().setHeader("foo", "bar");
        Map<?, ?> headers = evaluate("headers", Map.class);
        assertNotNull(headers);
        assertEquals("bar", headers.get("foo"));
    }

    @Test
    public void testHeaderAs() {
        exchange.getIn().setHeader("num", "42");
        assertEquals(42, evaluate("headerAs(num, Integer)", Integer.class));
    }
}
