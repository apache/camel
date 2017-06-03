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
package org.apache.camel.converter;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import junit.framework.TestCase;

/**
 * @version 
 */
public class ObjectConverterTest extends TestCase {

    public void testIsCollection() {
        assertEquals(false, ObjectConverter.isCollection("String"));
        assertEquals(false, ObjectConverter.isCollection(null));
        assertEquals(true, ObjectConverter.isCollection(new ArrayList<Object>()));
        assertEquals(true, ObjectConverter.isCollection(new String[]{"foo", "bar"}));
    }

    public void testIterator() {
        Iterator<?> it = ObjectConverter.iterator("Claus,Jonathan");
        assertEquals("Claus", it.next());
        assertEquals("Jonathan", it.next());
        assertEquals(false, it.hasNext());
    }

    @SuppressWarnings("unchecked")
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

    public void testToByte() {
        assertEquals(Byte.valueOf("4"), ObjectConverter.toByte(Byte.valueOf("4")));
        assertEquals(Byte.valueOf("4"), ObjectConverter.toByte(Integer.valueOf("4")));
        assertEquals(Byte.valueOf("4"), ObjectConverter.toByte("4"));
        assertEquals(null, ObjectConverter.toByte(new Date()));
    }
    
    public void testToClass() {
        assertEquals(String.class, ObjectConverter.toClass(String.class, null));
        assertEquals(String.class, ObjectConverter.toClass("java.lang.String", null));
        assertEquals(null, ObjectConverter.toClass(new Integer(4), null));
        assertEquals(null, ObjectConverter.toClass("foo.Bar", null));
    }

    public void testToShort() {
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort(Short.valueOf("4")));
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort(Integer.valueOf("4")));
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort("4"));
        assertEquals(null, ObjectConverter.toShort(new Date()));
        assertEquals(Short.valueOf("0"), ObjectConverter.toShort(Double.NaN));
        assertEquals(Short.valueOf("0"), ObjectConverter.toShort(Float.NaN));
        assertEquals(Short.valueOf("4"), ObjectConverter.toShort(Short.valueOf("4")));
    }

    public void testToInteger() {
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger(Integer.valueOf("4")));
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger(Long.valueOf("4")));
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger("4"));
        assertEquals(null, ObjectConverter.toInteger(new Date()));
        assertEquals(Integer.valueOf("0"), ObjectConverter.toInteger(Double.NaN));
        assertEquals(Integer.valueOf("0"), ObjectConverter.toInteger(Float.NaN));
        assertEquals(Integer.valueOf("4"), ObjectConverter.toInteger(Integer.valueOf("4")));
    }

    public void testToLong() {
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong(Long.valueOf("4")));
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong(Integer.valueOf("4")));
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong("4"));
        assertEquals(null, ObjectConverter.toLong(new Date()));
        assertEquals(Long.valueOf("0"), ObjectConverter.toLong(Double.NaN));
        assertEquals(Long.valueOf("0"), ObjectConverter.toLong(Float.NaN));
        assertEquals(Long.valueOf("4"), ObjectConverter.toLong(Long.valueOf("4")));
    }

    public void testToFloat() {
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat(Float.valueOf("4")));
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat(Integer.valueOf("4")));
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat("4"));
        assertEquals(null, ObjectConverter.toFloat(new Date()));
        assertEquals(Float.NaN, ObjectConverter.toFloat(Double.NaN));
        assertEquals(Float.NaN, ObjectConverter.toFloat(Float.NaN));
        assertEquals(Float.valueOf("4"), ObjectConverter.toFloat(Float.valueOf("4")));
    }

    public void testToDouble() {
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble(Double.valueOf("4")));
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble(Integer.valueOf("4")));
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble("4"));
        assertEquals(null, ObjectConverter.toDouble(new Date()));
        assertEquals(Double.NaN, ObjectConverter.toDouble(Double.NaN));
        assertEquals(Double.NaN, ObjectConverter.toDouble(Float.NaN));
        assertEquals(Double.valueOf("4"), ObjectConverter.toDouble(Double.valueOf("4")));
    }

    public void testToString() {
        assertEquals("ABC", ObjectConverter.toString(new StringBuffer("ABC")));
        assertEquals("ABC", ObjectConverter.toString(new StringBuilder("ABC")));
        assertEquals("", ObjectConverter.toString(new StringBuffer("")));
        assertEquals("", ObjectConverter.toString(new StringBuilder("")));
    }

    public void testToChar() {
        assertEquals('A', ObjectConverter.toChar("A"));
        assertEquals(Character.valueOf('A'), ObjectConverter.toCharacter("A"));
    }

    public void testNaN() throws Exception {
        assertEquals(Double.NaN, ObjectConverter.toDouble(Double.NaN));
        assertEquals(Double.NaN, ObjectConverter.toDouble(Float.NaN));
        assertEquals(Float.NaN, ObjectConverter.toFloat(Double.NaN));
        assertEquals(Float.NaN, ObjectConverter.toFloat(Float.NaN));
    }

}
