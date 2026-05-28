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

public class CollateFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new CollateFunctionFactory();
    }

    @Test
    public void testCollateEven() {
        List<Object> data = new ArrayList<>();
        data.add("A");
        data.add("B");
        data.add("C");
        data.add("D");
        data.add("E");
        data.add("F");
        exchange.getIn().setBody(data);

        Iterator<?> it = (Iterator<?>) evaluate("collate(3)");
        List<?> chunk = (List<?>) it.next();
        List<?> chunk2 = (List<?>) it.next();
        assertFalse(it.hasNext());

        assertEquals(3, chunk.size());
        assertEquals(3, chunk2.size());
        assertEquals("A", chunk.get(0));
        assertEquals("B", chunk.get(1));
        assertEquals("C", chunk.get(2));
        assertEquals("D", chunk2.get(0));
        assertEquals("E", chunk2.get(1));
        assertEquals("F", chunk2.get(2));
    }

    @Test
    public void testCollateOdd() {
        List<Object> data = new ArrayList<>();
        data.add("A");
        data.add("B");
        data.add("C");
        data.add("D");
        data.add("E");
        data.add("F");
        data.add("G");
        exchange.getIn().setBody(data);

        Iterator<?> it = (Iterator<?>) evaluate("collate(3)");
        List<?> chunk = (List<?>) it.next();
        List<?> chunk2 = (List<?>) it.next();
        List<?> chunk3 = (List<?>) it.next();
        assertFalse(it.hasNext());

        assertEquals(3, chunk.size());
        assertEquals(3, chunk2.size());
        assertEquals(1, chunk3.size());
        assertEquals("A", chunk.get(0));
        assertEquals("B", chunk.get(1));
        assertEquals("C", chunk.get(2));
        assertEquals("D", chunk2.get(0));
        assertEquals("E", chunk2.get(1));
        assertEquals("F", chunk2.get(2));
        assertEquals("G", chunk3.get(0));
    }

    @Test
    public void testCollateDynamic() {
        List<Object> data = new ArrayList<>();
        data.add("A");
        data.add("B");
        data.add("C");
        data.add("D");
        data.add("E");
        data.add("F");
        data.add("G");
        exchange.getIn().setBody(data);
        exchange.getIn().setHeader("num", 3);

        Iterator<?> it = (Iterator<?>) evaluate("collate(${header.num})");
        List<?> chunk = (List<?>) it.next();
        List<?> chunk2 = (List<?>) it.next();
        List<?> chunk3 = (List<?>) it.next();
        assertFalse(it.hasNext());

        assertEquals(3, chunk.size());
        assertEquals(3, chunk2.size());
        assertEquals(1, chunk3.size());
        assertEquals("A", chunk.get(0));
        assertEquals("B", chunk.get(1));
        assertEquals("C", chunk.get(2));
        assertEquals("D", chunk2.get(0));
        assertEquals("E", chunk2.get(1));
        assertEquals("F", chunk2.get(2));
        assertEquals("G", chunk3.get(0));
    }

    @Test
    public void testCreateCode() {
        assertEquals("collate(exchange, 10)", createCode("collate(10)"));
        assertEquals("collate(exchange, ${header.max})", createCode("collate(${header.max})"));
    }
}
