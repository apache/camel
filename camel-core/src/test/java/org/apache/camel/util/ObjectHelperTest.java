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
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;

/**
 * @version $Revision$
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

    public void testCreateIteratorWithStringAndCommaSeparator() {
        String s = "a,b,c";
        Iterator<String> it = CastUtils.cast(ObjectHelper.createIterator(s, ","));
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
    }

    public void testCreateIteratorWithStringAndSemiColonSeparator() {
        String s = "a;b;c";
        Iterator<String> it = CastUtils.cast(ObjectHelper.createIterator(s, ";"));
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
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

        Iterator<String> it = CastUtils.cast(ObjectHelper.createIterator(msg));
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

}
