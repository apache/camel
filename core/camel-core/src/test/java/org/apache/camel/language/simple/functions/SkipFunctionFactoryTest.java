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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SkipFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new SkipFunctionFactory();
    }

    @Test
    public void testSkip() {
        List<Object> data = new ArrayList<>();
        data.add("A");
        data.add("B");
        data.add("C");
        data.add("D");
        data.add("E");
        data.add("F");
        exchange.getIn().setBody(data);

        Iterator<?> it = (Iterator<?>) evaluate("skip(2)");
        assertEquals("C", it.next());
        assertEquals("D", it.next());
        assertEquals("E", it.next());
        assertEquals("F", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void testSkipDynamic() {
        List<Object> data = new ArrayList<>();
        data.add("A");
        data.add("B");
        data.add("C");
        data.add("D");
        data.add("E");
        data.add("F");
        exchange.getIn().setBody(data);
        exchange.getIn().setHeader("num", 4);

        Iterator<?> it = (Iterator<?>) evaluate("skip(${header.num})");
        assertEquals("E", it.next());
        assertEquals("F", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void testCreateCode() {
        assertEquals("skip(exchange, 10)", createCode("skip(10)"));
        assertEquals("skip(exchange, ${header.max})", createCode("skip(${header.max})"));
        assertEquals("skip(exchange, ${random(2,3)})", createCode("skip(${random(2,3)})"));
    }
}
