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
import java.util.Date;
import java.util.Iterator;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

public class ObjectConverterTest extends Assert {

    @Test
    public void testIterator() {
        Iterator<?> it = ObjectConverter.iterator("Claus,Jonathan");
        assertEquals("Claus", it.next());
        assertEquals("Jonathan", it.next());
        assertEquals(false, it.hasNext());
    }

    @Test
    public void testStreamIterator() {
        Iterator<?> it = ObjectConverter.iterator(Stream.of("Claus", "Jonathan", "Andrea"));
        assertEquals("Claus", it.next());
        assertEquals("Jonathan", it.next());
        assertEquals("Andrea", it.next());
        assertEquals(false, it.hasNext());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIterable() {
        for (final String name : (Iterable<String>)ObjectConverter.iterable("Claus,Jonathan")) {
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
    public void testToByte() {
        assertEquals(Byte.valueOf("4"), ObjectConverter.toByte(Byte.valueOf("4")));
        assertEquals(Byte.valueOf("4"), ObjectConverter.toByte(Integer.valueOf("4")));
        assertEquals(Byte.valueOf("4"), ObjectConverter.toByte("4"));
    }

    @Test
    public void testToClass() {
        assertEquals(String.class, ObjectConverter.toClass("java.lang.String", null));
        assertEquals(null, ObjectConverter.toClass("foo.Bar", null));
    }

    @Test
    public void testToShort() {
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort(Short.valueOf("4")));
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort(Integer.valueOf("4")));
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort("4"));
        assertEquals(null, ObjectConverter.toShort(Double.NaN));
        assertEquals(null, ObjectConverter.toShort(Float.NaN));
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort(Short.valueOf("4")));
    }

    @Test
    public void testToInteger() {
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger(Integer.valueOf("4")));
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger(Long.valueOf("4")));
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger("4"));
        assertEquals(null, ObjectConverter.toInteger(Double.NaN));
        assertEquals(null, ObjectConverter.toInteger(Float.NaN));
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger(Integer.valueOf("4")));
    }

    @Test
    public void testToLong() {
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong(Long.valueOf("4")));
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong(Integer.valueOf("4")));
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong("4"));
        assertEquals(null, ObjectConverter.toLong(Double.NaN));
        assertEquals(null, ObjectConverter.toLong(Float.NaN));
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong(Long.valueOf("4")));
    }

    @Test
    public void testToFloat() {
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat(Float.valueOf("4")));
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat(Integer.valueOf("4")));
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat("4"));
        assertEquals((Float)Float.NaN, ObjectConverter.toFloat(Double.NaN));
        assertEquals((Float)Float.NaN, ObjectConverter.toFloat(Float.NaN));
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat(Float.valueOf("4")));
    }

    @Test
    public void testToDouble() {
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble(Double.valueOf("4")));
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble(Integer.valueOf("4")));
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble("4"));
        assertEquals((Double)Double.NaN, ObjectConverter.toDouble(Double.NaN));
        assertEquals((Double)Double.NaN, ObjectConverter.toDouble(Float.NaN));
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble(Double.valueOf("4")));
    }

    @Test
    public void testToBigInteger() {
        assertEquals(BigInteger.valueOf(4), ObjectConverter.toBigInteger(Long.valueOf("4")));
        assertEquals(BigInteger.valueOf(4), ObjectConverter.toBigInteger(Integer.valueOf("4")));
        assertEquals(BigInteger.valueOf(4), ObjectConverter.toBigInteger("4"));
        assertEquals(BigInteger.valueOf(123456789L), ObjectConverter.toBigInteger("123456789"));
        assertEquals(null, ObjectConverter.toBigInteger(new Date()));
        assertEquals(null, ObjectConverter.toBigInteger(Double.NaN));
        assertEquals(null, ObjectConverter.toBigInteger(Float.NaN));
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
        assertEquals(Character.valueOf('A'), ObjectConverter.toCharacter("A"));
    }

    @Test
    public void testNaN() throws Exception {
        assertEquals((Double)Double.NaN, ObjectConverter.toDouble(Double.NaN));
        assertEquals((Double)Double.NaN, ObjectConverter.toDouble(Float.NaN));
        assertEquals((Float)Float.NaN, ObjectConverter.toFloat(Double.NaN));
        assertEquals((Float)Float.NaN, ObjectConverter.toFloat(Float.NaN));
    }

    @Test
    public void testToBoolean() throws Exception {
        assertTrue(ObjectConverter.toBoolean("true"));
        assertTrue(ObjectConverter.toBoolean("TRUE"));
        assertFalse(ObjectConverter.toBoolean("false"));
        assertFalse(ObjectConverter.toBoolean("FALSE"));
        assertNull(ObjectConverter.toBoolean("1"));
        assertNull(ObjectConverter.toBoolean(""));
        assertNull(ObjectConverter.toBoolean("yes"));

        assertTrue(ObjectConverter.toBool("true"));
        assertTrue(ObjectConverter.toBool("TRUE"));
        assertFalse(ObjectConverter.toBool("false"));
        assertFalse(ObjectConverter.toBool("FALSE"));
        // primitive boolean is more strict
        try {
            ObjectConverter.toBool("1");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            ObjectConverter.toBool("");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            ObjectConverter.toBool("yes");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

}
