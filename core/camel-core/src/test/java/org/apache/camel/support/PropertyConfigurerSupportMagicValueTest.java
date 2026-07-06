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
package org.apache.camel.support;

import java.time.Duration;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.support.component.PropertyConfigurerSupport.MAGIC_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that PropertyConfigurerSupport.property() handles the @@CamelMagicValue@@ placeholder for all primitive/wrapper
 * types, returning type-safe defaults instead of throwing NumberFormatException. This covers the camel-launcher export
 * scenario where optional kamelet properties (e.g. salesforce-source replayId of type Long) are resolved to the magic
 * placeholder.
 */
public class PropertyConfigurerSupportMagicValueTest extends ContextTestSupport {

    @Test
    public void testMagicValueToLong() {
        Long result = PropertyConfigurerSupport.property(context, Long.class, MAGIC_VALUE);
        assertNotNull(result);
        assertEquals(1L, result);
    }

    @Test
    public void testMagicValueToPrimitiveLong() {
        long result = PropertyConfigurerSupport.property(context, long.class, MAGIC_VALUE);
        assertEquals(1L, result);
    }

    @Test
    public void testMagicValueToInteger() {
        Integer result = PropertyConfigurerSupport.property(context, Integer.class, MAGIC_VALUE);
        assertNotNull(result);
        assertEquals(1, result);
    }

    @Test
    public void testMagicValueToPrimitiveInt() {
        int result = PropertyConfigurerSupport.property(context, int.class, MAGIC_VALUE);
        assertEquals(1, result);
    }

    @Test
    public void testMagicValueToDouble() {
        Double result = PropertyConfigurerSupport.property(context, Double.class, MAGIC_VALUE);
        assertNotNull(result);
        assertEquals(1d, result);
    }

    @Test
    public void testMagicValueToFloat() {
        Float result = PropertyConfigurerSupport.property(context, Float.class, MAGIC_VALUE);
        assertNotNull(result);
        assertEquals(1f, result);
    }

    @Test
    public void testMagicValueToShort() {
        Short result = PropertyConfigurerSupport.property(context, Short.class, MAGIC_VALUE);
        assertNotNull(result);
        assertEquals((short) 1, result);
    }

    @Test
    public void testMagicValueToByte() {
        Byte result = PropertyConfigurerSupport.property(context, Byte.class, MAGIC_VALUE);
        assertNotNull(result);
        assertEquals((byte) 0, result);
    }

    @Test
    public void testMagicValueToBoolean() {
        Boolean result = PropertyConfigurerSupport.property(context, Boolean.class, MAGIC_VALUE);
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testMagicValueToPrimitiveBoolean() {
        boolean result = PropertyConfigurerSupport.property(context, boolean.class, MAGIC_VALUE);
        assertEquals(true, result);
    }

    @Test
    public void testMagicValueToDuration() {
        Duration result = PropertyConfigurerSupport.property(context, Duration.class, MAGIC_VALUE);
        assertNotNull(result);
        assertEquals(Duration.ofMillis(1), result);
    }

    @Test
    public void testMagicValueToString() {
        String result = PropertyConfigurerSupport.property(context, String.class, MAGIC_VALUE);
        assertEquals(MAGIC_VALUE, result);
    }

    @Test
    public void testRegularLongConversion() {
        Long result = PropertyConfigurerSupport.property(context, Long.class, "42");
        assertNotNull(result);
        assertEquals(42L, result);
    }
}
