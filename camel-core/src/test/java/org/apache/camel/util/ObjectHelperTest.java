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
package org.apache.camel.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.bean.MyStaticClass;
import org.apache.camel.impl.DefaultMessage;

/**
 * @version 
 */
public class ObjectHelperTest extends TestCase {

    public void testRemoveInitialCharacters() throws Exception {
        assertEquals(ObjectHelper.removeStartingCharacters("foo", '/'), "foo");
        assertEquals(ObjectHelper.removeStartingCharacters("/foo", '/'), "foo");
        assertEquals(ObjectHelper.removeStartingCharacters("//foo", '/'), "foo");
    }

    public void testGetPropertyName() throws Exception {
        Method method = getClass().getMethod("setCheese", String.class);
        assertNotNull("should have found a method!", method);

        String name = ObjectHelper.getPropertyName(method);
        assertEquals("Property name", "cheese", name);
    }

    public void setCheese(String cheese) {
        // used in the above unit test
    }

    public void testContains() throws Exception {
        String[] array = {"foo", "bar"};
        Collection<String> collection = Arrays.asList(array);

        assertTrue(ObjectHelper.contains(array, "foo"));
        assertTrue(ObjectHelper.contains(collection, "foo"));
        assertTrue(ObjectHelper.contains("foo", "foo"));

        assertFalse(ObjectHelper.contains(array, "xyz"));
        assertFalse(ObjectHelper.contains(collection, "xyz"));
        assertFalse(ObjectHelper.contains("foo", "xyz"));
    }

    public void testEqual() {
        assertTrue(ObjectHelper.equal(null, null));
        assertTrue(ObjectHelper.equal("", ""));
        assertTrue(ObjectHelper.equal(" ", " "));
        assertTrue(ObjectHelper.equal("Hello", "Hello"));
        assertTrue(ObjectHelper.equal(123, 123));
        assertTrue(ObjectHelper.equal(true, true));

        assertFalse(ObjectHelper.equal(null, ""));
        assertFalse(ObjectHelper.equal("", null));
        assertFalse(ObjectHelper.equal(" ", "    "));
        assertFalse(ObjectHelper.equal("Hello", "World"));
        assertFalse(ObjectHelper.equal(true, false));
        assertFalse(ObjectHelper.equal(new Object(), new Object()));

        byte[] a = new byte[] {40, 50, 60};
        byte[] b = new byte[] {40, 50, 60};
        assertTrue(ObjectHelper.equal(a, b));

        a = new byte[] {40, 50, 60};
        b = new byte[] {40, 50, 60, 70};
        assertFalse(ObjectHelper.equal(a, b));
    }

    public void testEqualByteArray() {
        assertTrue(ObjectHelper.equalByteArray("Hello".getBytes(), "Hello".getBytes()));
        assertFalse(ObjectHelper.equalByteArray("Hello".getBytes(), "World".getBytes()));

        assertTrue(ObjectHelper.equalByteArray("Hello Thai Elephant \u0E08".getBytes(), "Hello Thai Elephant \u0E08".getBytes()));
        assertTrue(ObjectHelper.equalByteArray(null, null));

        byte[] empty = new byte[0];
        assertTrue(ObjectHelper.equalByteArray(empty, empty));

        byte[] a = new byte[] {40, 50, 60};
        byte[] b = new byte[] {40, 50, 60};
        assertTrue(ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60};
        b = new byte[] {40, 50, 60, 70};
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60, 70};
        b = new byte[] {40, 50, 60};
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60};
        b = new byte[0];
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = new byte[0];
        b = new byte[] {40, 50, 60};
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60};
        b = null;
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = null;
        b = new byte[] {40, 50, 60};
        assertFalse(ObjectHelper.equalByteArray(a, b));

        a = null;
        b = null;
        assertTrue(ObjectHelper.equalByteArray(a, b));
    }

    public void testCreateIterator() {
        List<String> list = new ArrayList<String>();
        Iterator<String> iterator = list.iterator();
        assertSame("Should return the same iterator", iterator, ObjectHelper.createIterator(iterator));
    }

    public void testCreateIteratorAllowEmpty() {
        String s = "a,b,,c";
        Iterator<?> it = ObjectHelper.createIterator(s, ",", true);
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("", it.next());
        assertEquals("c", it.next());
    }

    public void testCreateIteratorWithStringAndCommaSeparator() {
        String s = "a,b,c";
        Iterator<?> it = ObjectHelper.createIterator(s, ",");
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
    }

    public void testCreateIteratorWithStringAndCommaSeparatorEmptyString() {
        String s = "";
        Iterator<?> it = ObjectHelper.createIterator(s, ",", true);
        assertEquals("", it.next());
        assertFalse(it.hasNext());
    }

    public void testCreateIteratorWithStringAndSemiColonSeparator() {
        String s = "a;b;c";
        Iterator<?> it = ObjectHelper.createIterator(s, ";");
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
    }

    public void testCreateIteratorWithStringAndCommaInParanthesesSeparator() {
        String s = "bean:foo?method=bar('A','B','C')";
        Iterator<?> it = ObjectHelper.createIterator(s, ",");
        assertEquals("bean:foo?method=bar('A','B','C')", it.next());
    }

    public void testCreateIteratorWithStringAndCommaInParanthesesSeparatorTwo() {
        String s = "bean:foo?method=bar('A','B','C'),bean:bar?method=cool('A','Hello,World')";
        Iterator<?> it = ObjectHelper.createIterator(s, ",");
        assertEquals("bean:foo?method=bar('A','B','C')", it.next());
        assertEquals("bean:bar?method=cool('A','Hello,World')", it.next());
    }

    public void testCreateIteratorWithPrimitiveArrayTypes() {
        Iterator<?> it = ObjectHelper.createIterator(new byte[] {13, Byte.MAX_VALUE, 7, Byte.MIN_VALUE}, null);
        assertTrue(it.hasNext());
        assertEquals(Byte.valueOf((byte) 13), it.next());
        assertTrue(it.hasNext());
        assertEquals(Byte.MAX_VALUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Byte.valueOf((byte) 7), it.next());
        assertTrue(it.hasNext());
        assertEquals(Byte.MIN_VALUE, it.next());
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new byte[] {}, null);
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new short[] {13, Short.MAX_VALUE, 7, Short.MIN_VALUE}, null);
        assertTrue(it.hasNext());
        assertEquals(Short.valueOf((short) 13), it.next());
        assertTrue(it.hasNext());
        assertEquals(Short.MAX_VALUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Short.valueOf((short) 7), it.next());
        assertTrue(it.hasNext());
        assertEquals(Short.MIN_VALUE, it.next());
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new short[] {}, null);
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new int[] {13, Integer.MAX_VALUE, 7, Integer.MIN_VALUE}, null);
        assertTrue(it.hasNext());
        assertEquals(Integer.valueOf(13), it.next());
        assertTrue(it.hasNext());
        assertEquals(Integer.MAX_VALUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Integer.valueOf(7), it.next());
        assertTrue(it.hasNext());
        assertEquals(Integer.MIN_VALUE, it.next());
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new int[] {}, null);
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new long[] {13, Long.MAX_VALUE, 7, Long.MIN_VALUE}, null);
        assertTrue(it.hasNext());
        assertEquals(Long.valueOf(13), it.next());
        assertTrue(it.hasNext());
        assertEquals(Long.MAX_VALUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Long.valueOf(7), it.next());
        assertTrue(it.hasNext());
        assertEquals(Long.MIN_VALUE, it.next());
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new long[] {}, null);
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new float[] {13.7F, Float.MAX_VALUE, 7.13F, Float.MIN_VALUE}, null);
        assertTrue(it.hasNext());
        assertEquals(Float.valueOf(13.7F), it.next());
        assertTrue(it.hasNext());
        assertEquals(Float.MAX_VALUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Float.valueOf(7.13F), it.next());
        assertTrue(it.hasNext());
        assertEquals(Float.MIN_VALUE, it.next());
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new float[] {}, null);
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new double[] {13.7D, Double.MAX_VALUE, 7.13D, Double.MIN_VALUE}, null);
        assertTrue(it.hasNext());
        assertEquals(Double.valueOf(13.7D), it.next());
        assertTrue(it.hasNext());
        assertEquals(Double.MAX_VALUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Double.valueOf(7.13D), it.next());
        assertTrue(it.hasNext());
        assertEquals(Double.MIN_VALUE, it.next());
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new double[] {}, null);
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new char[] {'C', 'a', 'm', 'e', 'l'}, null);
        assertTrue(it.hasNext());
        assertEquals(Character.valueOf('C'), it.next());
        assertTrue(it.hasNext());
        assertEquals(Character.valueOf('a'), it.next());
        assertTrue(it.hasNext());
        assertEquals(Character.valueOf('m'), it.next());
        assertTrue(it.hasNext());
        assertEquals(Character.valueOf('e'), it.next());
        assertTrue(it.hasNext());
        assertEquals(Character.valueOf('l'), it.next());
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new char[] {}, null);
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new boolean[] {false, true, false, true, true}, null);
        assertTrue(it.hasNext());
        assertEquals(Boolean.FALSE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Boolean.TRUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Boolean.FALSE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Boolean.TRUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Boolean.TRUE, it.next());
        assertFalse(it.hasNext());

        it = ObjectHelper.createIterator(new boolean[] {}, null);
        assertFalse(it.hasNext());
    }

    public void testIsPrimitiveType() {
        assertTrue(ObjectHelper.isPrimitiveType(byte.class));
        assertTrue(ObjectHelper.isPrimitiveType(short.class));
        assertTrue(ObjectHelper.isPrimitiveType(int.class));
        assertTrue(ObjectHelper.isPrimitiveType(long.class));
        assertTrue(ObjectHelper.isPrimitiveType(float.class));
        assertTrue(ObjectHelper.isPrimitiveType(double.class));
        assertTrue(ObjectHelper.isPrimitiveType(char.class));
        assertTrue(ObjectHelper.isPrimitiveType(boolean.class));
        assertTrue(ObjectHelper.isPrimitiveType(void.class));

        assertFalse(ObjectHelper.isPrimitiveType(Object.class));
        assertFalse(ObjectHelper.isPrimitiveType(Byte.class));
        assertFalse(ObjectHelper.isPrimitiveType(Short.class));
        assertFalse(ObjectHelper.isPrimitiveType(Integer.class));
        assertFalse(ObjectHelper.isPrimitiveType(Long.class));
        assertFalse(ObjectHelper.isPrimitiveType(Float.class));
        assertFalse(ObjectHelper.isPrimitiveType(Double.class));
        assertFalse(ObjectHelper.isPrimitiveType(Character.class));
        assertFalse(ObjectHelper.isPrimitiveType(Boolean.class));
        assertFalse(ObjectHelper.isPrimitiveType(Void.class));
        assertFalse(ObjectHelper.isPrimitiveType(CamelContext.class));
        assertFalse(ObjectHelper.isPrimitiveType(null));
    }

    public void testIsPrimitiveArrayType() {
        assertTrue(ObjectHelper.isPrimitiveArrayType(byte[].class));
        assertTrue(ObjectHelper.isPrimitiveArrayType(short[].class));
        assertTrue(ObjectHelper.isPrimitiveArrayType(int[].class));
        assertTrue(ObjectHelper.isPrimitiveArrayType(long[].class));
        assertTrue(ObjectHelper.isPrimitiveArrayType(float[].class));
        assertTrue(ObjectHelper.isPrimitiveArrayType(double[].class));
        assertTrue(ObjectHelper.isPrimitiveArrayType(char[].class));
        assertTrue(ObjectHelper.isPrimitiveArrayType(boolean[].class));

        assertFalse(ObjectHelper.isPrimitiveArrayType(Object[].class));
        assertFalse(ObjectHelper.isPrimitiveArrayType(Byte[].class));
        assertFalse(ObjectHelper.isPrimitiveArrayType(Short[].class));
        assertFalse(ObjectHelper.isPrimitiveArrayType(Integer[].class));
        assertFalse(ObjectHelper.isPrimitiveArrayType(Long[].class));
        assertFalse(ObjectHelper.isPrimitiveArrayType(Float[].class));
        assertFalse(ObjectHelper.isPrimitiveArrayType(Double[].class));
        assertFalse(ObjectHelper.isPrimitiveArrayType(Character[].class));
        assertFalse(ObjectHelper.isPrimitiveArrayType(Boolean[].class));
        assertFalse(ObjectHelper.isPrimitiveArrayType(Void[].class));
        assertFalse(ObjectHelper.isPrimitiveArrayType(CamelContext[].class));
        assertFalse(ObjectHelper.isPrimitiveArrayType(null));
    }

    public void testBefore() {
        assertEquals("Hello ", ObjectHelper.before("Hello World", "World"));
        assertEquals("Hello ", ObjectHelper.before("Hello World Again", "World"));
        assertEquals(null, ObjectHelper.before("Hello Again", "Foo"));
    }

    public void testAfter() {
        assertEquals(" World", ObjectHelper.after("Hello World", "Hello"));
        assertEquals(" World Again", ObjectHelper.after("Hello World Again", "Hello"));
        assertEquals(null, ObjectHelper.after("Hello Again", "Foo"));
    }

    public void testBetween() {
        assertEquals("foo bar", ObjectHelper.between("Hello 'foo bar' how are you", "'", "'"));
        assertEquals("foo bar", ObjectHelper.between("Hello ${foo bar} how are you", "${", "}"));
        assertEquals(null, ObjectHelper.between("Hello ${foo bar} how are you", "'", "'"));
    }

    public void testIsJavaIdentifier() {
        assertEquals(true, ObjectHelper.isJavaIdentifier("foo"));
        assertEquals(false, ObjectHelper.isJavaIdentifier("foo.bar"));
        assertEquals(false, ObjectHelper.isJavaIdentifier(""));
        assertEquals(false, ObjectHelper.isJavaIdentifier(null));
    }

    public void testGetDefaultCharSet() {
        assertNotNull(ObjectHelper.getDefaultCharacterSet());
    }

    public void testConvertPrimitiveTypeToWrapper() {
        assertEquals("java.lang.Integer", ObjectHelper.convertPrimitiveTypeToWrapperType(int.class).getName());
        assertEquals("java.lang.Long", ObjectHelper.convertPrimitiveTypeToWrapperType(long.class).getName());
        assertEquals("java.lang.Double", ObjectHelper.convertPrimitiveTypeToWrapperType(double.class).getName());
        assertEquals("java.lang.Float", ObjectHelper.convertPrimitiveTypeToWrapperType(float.class).getName());
        assertEquals("java.lang.Short", ObjectHelper.convertPrimitiveTypeToWrapperType(short.class).getName());
        assertEquals("java.lang.Byte", ObjectHelper.convertPrimitiveTypeToWrapperType(byte.class).getName());
        assertEquals("java.lang.Boolean", ObjectHelper.convertPrimitiveTypeToWrapperType(boolean.class).getName());
        // non primitive just fall through
        assertEquals("java.lang.Object", ObjectHelper.convertPrimitiveTypeToWrapperType(Object.class).getName());
    }

    public void testAsString() {
        String[] args = new String[] {"foo", "bar"};
        String out = ObjectHelper.asString(args);
        assertNotNull(out);
        assertEquals("{foo, bar}", out);
    }

    public void testName() {
        assertEquals("java.lang.Integer", ObjectHelper.name(Integer.class));
        assertEquals(null, ObjectHelper.name(null));
    }

    public void testClassName() {
        assertEquals("java.lang.Integer", ObjectHelper.className(Integer.valueOf("5")));
        assertEquals(null, ObjectHelper.className(null));
    }

    public void testGetSystemPropertyDefault() {
        assertEquals("foo", ObjectHelper.getSystemProperty("CamelFooDoesNotExist", "foo"));
    }

    public void testGetSystemPropertyBooleanDefault() {
        assertEquals(true, ObjectHelper.getSystemProperty("CamelFooDoesNotExist", Boolean.TRUE));
    }

    public void testMatches() {
        List<Object> data = new ArrayList<Object>();
        data.add("foo");
        data.add("bar");
        assertEquals(true, ObjectHelper.matches(data));

        data.clear();
        data.add(Boolean.FALSE);
        data.add("bar");
        assertEquals(false, ObjectHelper.matches(data));

        data.clear();
        assertEquals(false, ObjectHelper.matches(data));
    }

    public void testToBoolean() {
        assertEquals(Boolean.TRUE, ObjectHelper.toBoolean(Boolean.TRUE));
        assertEquals(Boolean.TRUE, ObjectHelper.toBoolean("true"));
        assertEquals(Boolean.TRUE, ObjectHelper.toBoolean(Integer.valueOf("1")));
        assertEquals(Boolean.FALSE, ObjectHelper.toBoolean(Integer.valueOf("0")));
        assertEquals(null, ObjectHelper.toBoolean(new Date()));
    }

    public void testIteratorWithMessage() {
        Message msg = new DefaultMessage();
        msg.setBody("a,b,c");

        Iterator<?> it = ObjectHelper.createIterator(msg);
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
        assertFalse(it.hasNext());
    }

    public void testIteratorWithEmptyMessage() {
        Message msg = new DefaultMessage();
        msg.setBody("");

        Iterator<Object> it = ObjectHelper.createIterator(msg);
        assertFalse(it.hasNext());
    }

    public void testIteratorWithNullMessage() {
        Message msg = new DefaultMessage();
        msg.setBody(null);

        Iterator<Object> it = ObjectHelper.createIterator(msg);
        assertFalse(it.hasNext());
    }

    public void testIterable() {
        final List<String> data = new ArrayList<String>();
        data.add("A");
        data.add("B");
        data.add("C");
        Iterable<String> itb = new Iterable<String>() {
            public Iterator<String> iterator() {
                return data.iterator();
            }
        };
        Iterator<?> it = ObjectHelper.createIterator(itb);
        assertEquals("A", it.next());
        assertEquals("B", it.next());
        assertEquals("C", it.next());
        assertFalse(it.hasNext());
    }

    public void testNormalizeClassName() {
        assertEquals("Should get the right class name", "my.package-info", ObjectHelper.normalizeClassName("my.package-info"));
        assertEquals("Should get the right class name", "Integer[]", ObjectHelper.normalizeClassName("Integer[] \r"));
        assertEquals("Should get the right class name", "Hello_World", ObjectHelper.normalizeClassName("Hello_World"));
    }

    public void testLookupConstantFieldValue() {
        assertEquals("CamelFileName", ObjectHelper.lookupConstantFieldValue(Exchange.class, "FILE_NAME"));
        assertEquals(null, ObjectHelper.lookupConstantFieldValue(Exchange.class, "XXX"));
        assertEquals(null, ObjectHelper.lookupConstantFieldValue(null, "FILE_NAME"));
    }

    public void testHasDefaultPublicNoArgConstructor() {
        assertTrue(ObjectHelper.hasDefaultPublicNoArgConstructor(ObjectHelperTest.class));
        assertFalse(ObjectHelper.hasDefaultPublicNoArgConstructor(MyStaticClass.class));
    }
    
    public void testIdentityHashCode() {
        MyDummyObject dummy = new MyDummyObject("Camel");
        
        String code = ObjectHelper.getIdentityHashCode(dummy);
        String code2 = ObjectHelper.getIdentityHashCode(dummy);

        assertEquals(code, code2);

        MyDummyObject dummyB = new MyDummyObject("Camel");
        String code3 = ObjectHelper.getIdentityHashCode(dummyB);
        assertNotSame(code, code3);
    }
    
    public void testIsNaN() throws Exception {
        assertTrue(ObjectHelper.isNaN(Float.NaN));
        assertTrue(ObjectHelper.isNaN(Double.NaN));

        assertFalse(ObjectHelper.isNaN(null));
        assertFalse(ObjectHelper.isNaN(""));
        assertFalse(ObjectHelper.isNaN("1.0"));
        assertFalse(ObjectHelper.isNaN(1));
        assertFalse(ObjectHelper.isNaN(1.5f));
        assertFalse(ObjectHelper.isNaN(1.5d));
        assertFalse(ObjectHelper.isNaN(false));
        assertFalse(ObjectHelper.isNaN(true));
    }

}
