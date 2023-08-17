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
package org.apache.camel.converter;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ObjectConverterTest {

    @Test
    public void testIterator() {
        Iterator<?> it = ObjectConverter.iterator("Claus,Jonathan");
        assertEquals("Claus", it.next());
        assertEquals("Jonathan", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void testStreamIterator() {
        Iterator<?> it = ObjectConverter.iterator(Stream.of("Claus", "Jonathan", "Andrea"));
        assertEquals("Claus", it.next());
        assertEquals("Jonathan", it.next());
        assertEquals("Andrea", it.next());
        assertFalse(it.hasNext());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIterable() {
        for (final String name : (Iterable<String>) ObjectConverter.iterable("Claus,Jonathan")) {
            switch (name) {
                case "Claus":
                case "Jonathan":
                    break;
                default:
                    fail();
            }
        }
    }

    @Test
    public void testToByte() throws Exception {
        assertEquals(Byte.valueOf("4"), ObjectConverter.toByte(Byte.valueOf("4")));
        assertEquals(Byte.valueOf("4"), ObjectConverter.toByte(Integer.valueOf("4")));
        assertEquals(Byte.valueOf("4"), ObjectConverter.toByte("4"));
        assertEquals(Byte.valueOf("4"), ObjectConverter.toByte("4".getBytes(StandardCharsets.UTF_8), null));
    }

    @Test
    public void testToClass() {
        assertEquals(String.class, ObjectConverter.toClass("java.lang.String", null));
        assertNull(ObjectConverter.toClass("foo.Bar", null));
    }

    @Test
    public void testToShort() throws Exception {
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort(Short.valueOf("4")));
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort(Integer.valueOf("4")));
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort("4"));
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort("4".getBytes(StandardCharsets.UTF_8), null));
        assertNull(ObjectConverter.toShort(Double.NaN));
        assertNull(ObjectConverter.toShort(Float.NaN));
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort(Short.valueOf("4")));
    }

    @Test
    public void testToInteger() throws Exception {
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger(Integer.valueOf("4")));
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger(Long.valueOf("4")));
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger("4"));
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger("4".getBytes(StandardCharsets.UTF_8), null));
        assertNull(ObjectConverter.toInteger(Double.NaN));
        assertNull(ObjectConverter.toInteger(Float.NaN));
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger(Integer.valueOf("4")));
        assertEquals(Integer.valueOf("1234"), ObjectConverter.toInteger(new byte[] { 49, 50, 51, 52 }, null));
    }

    @Test
    public void testToLong() throws Exception {
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong(Long.valueOf("4")));
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong(Integer.valueOf("4")));
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong("4"));
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong("4".getBytes(StandardCharsets.UTF_8), null));
        assertNull(ObjectConverter.toLong(Double.NaN));
        assertNull(ObjectConverter.toLong(Float.NaN));
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong(Long.valueOf("4")));
        assertEquals(Long.valueOf("1234"), ObjectConverter.toLong(new byte[] { 49, 50, 51, 52 }, null));
    }

    @Test
    public void testToFloat() throws Exception {
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat(Float.valueOf("4")));
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat(Integer.valueOf("4")));
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat("4"));
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat("4".getBytes(StandardCharsets.UTF_8), null));
        assertEquals((Float) Float.NaN, ObjectConverter.toFloat(Double.NaN));
        assertEquals((Float) Float.NaN, ObjectConverter.toFloat(Float.NaN));
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat(Float.valueOf("4")));
    }

    @Test
    public void testToDouble() throws Exception {
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble(Double.valueOf("4")));
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble(Integer.valueOf("4")));
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble("4"));
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble("4".getBytes(StandardCharsets.UTF_8), null));
        assertEquals((Double) Double.NaN, ObjectConverter.toDouble(Double.NaN));
        assertEquals((Double) Double.NaN, ObjectConverter.toDouble(Float.NaN));
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble(Double.valueOf("4")));
    }

    @Test
    public void testToBigInteger() {
        assertEquals(BigInteger.valueOf(4), ObjectConverter.toBigInteger(Long.valueOf("4")));
        assertEquals(BigInteger.valueOf(4), ObjectConverter.toBigInteger(Integer.valueOf("4")));
        assertEquals(BigInteger.valueOf(4), ObjectConverter.toBigInteger("4"));
        assertEquals(BigInteger.valueOf(123456789L), ObjectConverter.toBigInteger("123456789"));
        assertNull(ObjectConverter.toBigInteger(new Date()));
        assertNull(ObjectConverter.toBigInteger(Double.NaN));
        assertNull(ObjectConverter.toBigInteger(Float.NaN));
        assertEquals(BigInteger.valueOf(4), ObjectConverter.toBigInteger(Long.valueOf("4")));
        assertEquals(new BigInteger("14350442579497085228"), ObjectConverter.toBigInteger("14350442579497085228"));
    }

    @Test
    public void testToString() {
        assertEquals("ABC", ObjectConverter.toString(new StringBuffer("ABC")));
        assertEquals("ABC", ObjectConverter.toString(new StringBuilder("ABC")));
        assertEquals("", ObjectConverter.toString(new StringBuffer("")));
        assertEquals("", ObjectConverter.toString(new StringBuilder("")));
    }

    @Test
    public void testToChar() {
        assertEquals('A', ObjectConverter.toChar("A"));
        assertEquals('A', ObjectConverter.toChar("A".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Character.valueOf('A'), ObjectConverter.toCharacter("A"));
        assertEquals(Character.valueOf('A'), ObjectConverter.toCharacter("A".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testNaN() throws Exception {
        assertEquals((Double) Double.NaN, ObjectConverter.toDouble(Double.NaN));
        assertEquals((Double) Double.NaN, ObjectConverter.toDouble(Float.NaN));
        assertEquals((Float) Float.NaN, ObjectConverter.toFloat(Double.NaN));
        assertEquals((Float) Float.NaN, ObjectConverter.toFloat(Float.NaN));
    }

    @Test
    public void testToBoolean() {
        assertTrue(ObjectConverter.toBoolean("true"));
        assertTrue(ObjectConverter.toBoolean("true".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ObjectConverter.toBoolean("TRUE"));
        assertFalse(ObjectConverter.toBoolean("false"));
        assertFalse(ObjectConverter.toBoolean("FALSE"));
        assertNull(ObjectConverter.toBoolean("1"));
        assertNull(ObjectConverter.toBoolean(""));
        assertNull(ObjectConverter.toBoolean("yes"));

        assertTrue(ObjectConverter.toBool("true"));
        assertTrue(ObjectConverter.toBool("true".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ObjectConverter.toBool("TRUE"));
        assertFalse(ObjectConverter.toBool("false"));
        assertFalse(ObjectConverter.toBool("FALSE"));

        // primitive boolean is stricter
        assertThrows(IllegalArgumentException.class, () -> ObjectConverter.toBool("1"), "Should throw exception");
        assertThrows(IllegalArgumentException.class, () -> ObjectConverter.toBool(""), "Should throw exception");
        assertThrows(IllegalArgumentException.class, () -> ObjectConverter.toBool("yes"), "Should throw exception");
    }

}
