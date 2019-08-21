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
package org.apache.camel.util;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.bean.MyOtherFooBean;
import org.apache.camel.component.bean.MyOtherFooBean.AbstractClassSize;
import org.apache.camel.component.bean.MyOtherFooBean.Clazz;
import org.apache.camel.component.bean.MyOtherFooBean.InterfaceSize;
import org.apache.camel.component.bean.MyStaticClass;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.ObjectHelper;
import org.junit.Assert;
import org.junit.Test;

public class ObjectHelperTest extends Assert {

    @Test
    public void testLoadResourceAsStream() {
        InputStream res1 = org.apache.camel.util.ObjectHelper.loadResourceAsStream("org/apache/camel/util/ObjectHelperResourceTestFile.properties");
        InputStream res2 = org.apache.camel.util.ObjectHelper.loadResourceAsStream("/org/apache/camel/util/ObjectHelperResourceTestFile.properties");

        assertNotNull("Cannot load resource without leading \"/\"", res1);
        assertNotNull("Cannot load resource with leading \"/\"", res2);

        IOHelper.close(res1, res2);
    }

    @Test
    public void testLoadResource() {
        URL url1 = org.apache.camel.util.ObjectHelper.loadResourceAsURL("org/apache/camel/util/ObjectHelperResourceTestFile.properties");
        URL url2 = org.apache.camel.util.ObjectHelper.loadResourceAsURL("/org/apache/camel/util/ObjectHelperResourceTestFile.properties");

        assertNotNull("Cannot load resource without leading \"/\"", url1);
        assertNotNull("Cannot load resource with leading \"/\"", url2);
    }

    @Test
    public void testGetPropertyName() throws Exception {
        Method method = getClass().getMethod("setCheese", String.class);
        assertNotNull("should have found a method!", method);

        String name = org.apache.camel.util.ObjectHelper.getPropertyName(method);
        assertEquals("Property name", "cheese", name);
    }

    public void setCheese(String cheese) {
        // used in the above unit test
    }

    @Test
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

    @Test
    public void testContainsStringBuilder() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello World");

        assertTrue(ObjectHelper.contains(sb, "World"));
        assertTrue(ObjectHelper.contains(sb, new StringBuffer("World")));
        assertTrue(ObjectHelper.contains(sb, new StringBuilder("World")));

        assertFalse(ObjectHelper.contains(sb, "Camel"));
        assertFalse(ObjectHelper.contains(sb, new StringBuffer("Camel")));
        assertFalse(ObjectHelper.contains(sb, new StringBuilder("Camel")));
    }

    @Test
    public void testContainsStringBuffer() throws Exception {
        StringBuffer sb = new StringBuffer();
        sb.append("Hello World");

        assertTrue(ObjectHelper.contains(sb, "World"));
        assertTrue(ObjectHelper.contains(sb, new StringBuffer("World")));
        assertTrue(ObjectHelper.contains(sb, new StringBuilder("World")));

        assertFalse(ObjectHelper.contains(sb, "Camel"));
        assertFalse(ObjectHelper.contains(sb, new StringBuffer("Camel")));
        assertFalse(ObjectHelper.contains(sb, new StringBuilder("Camel")));
    }

    @Test
    public void testEqual() {
        assertTrue(org.apache.camel.util.ObjectHelper.equal(null, null));
        assertTrue(org.apache.camel.util.ObjectHelper.equal("", ""));
        assertTrue(org.apache.camel.util.ObjectHelper.equal(" ", " "));
        assertTrue(org.apache.camel.util.ObjectHelper.equal("Hello", "Hello"));
        assertTrue(org.apache.camel.util.ObjectHelper.equal(123, 123));
        assertTrue(org.apache.camel.util.ObjectHelper.equal(true, true));

        assertFalse(org.apache.camel.util.ObjectHelper.equal(null, ""));
        assertFalse(org.apache.camel.util.ObjectHelper.equal("", null));
        assertFalse(org.apache.camel.util.ObjectHelper.equal(" ", "    "));
        assertFalse(org.apache.camel.util.ObjectHelper.equal("Hello", "World"));
        assertFalse(org.apache.camel.util.ObjectHelper.equal(true, false));
        assertFalse(org.apache.camel.util.ObjectHelper.equal(new Object(), new Object()));

        byte[] a = new byte[] {40, 50, 60};
        byte[] b = new byte[] {40, 50, 60};
        assertTrue(org.apache.camel.util.ObjectHelper.equal(a, b));

        a = new byte[] {40, 50, 60};
        b = new byte[] {40, 50, 60, 70};
        assertFalse(org.apache.camel.util.ObjectHelper.equal(a, b));
    }

    @Test
    public void testEqualByteArray() {
        assertTrue(org.apache.camel.util.ObjectHelper.equalByteArray("Hello".getBytes(), "Hello".getBytes()));
        assertFalse(org.apache.camel.util.ObjectHelper.equalByteArray("Hello".getBytes(), "World".getBytes()));

        assertTrue(org.apache.camel.util.ObjectHelper.equalByteArray("Hello Thai Elephant \u0E08".getBytes(), "Hello Thai Elephant \u0E08".getBytes()));
        assertTrue(org.apache.camel.util.ObjectHelper.equalByteArray(null, null));

        byte[] empty = new byte[0];
        assertTrue(org.apache.camel.util.ObjectHelper.equalByteArray(empty, empty));

        byte[] a = new byte[] {40, 50, 60};
        byte[] b = new byte[] {40, 50, 60};
        assertTrue(org.apache.camel.util.ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60};
        b = new byte[] {40, 50, 60, 70};
        assertFalse(org.apache.camel.util.ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60, 70};
        b = new byte[] {40, 50, 60};
        assertFalse(org.apache.camel.util.ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60};
        b = new byte[0];
        assertFalse(org.apache.camel.util.ObjectHelper.equalByteArray(a, b));

        a = new byte[0];
        b = new byte[] {40, 50, 60};
        assertFalse(org.apache.camel.util.ObjectHelper.equalByteArray(a, b));

        a = new byte[] {40, 50, 60};
        b = null;
        assertFalse(org.apache.camel.util.ObjectHelper.equalByteArray(a, b));

        a = null;
        b = new byte[] {40, 50, 60};
        assertFalse(org.apache.camel.util.ObjectHelper.equalByteArray(a, b));

        a = null;
        b = null;
        assertTrue(org.apache.camel.util.ObjectHelper.equalByteArray(a, b));
    }

    @Test
    public void testCreateIterator() {
        List<String> list = new ArrayList<>();
        Iterator<String> iterator = list.iterator();
        assertSame("Should return the same iterator", iterator, ObjectHelper.createIterator(iterator));
    }

    @Test
    public void testCreateIteratorAllowEmpty() {
        String s = "a,b,,c";
        Iterator<?> it = ObjectHelper.createIterator(s, ",", true);
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("", it.next());
        assertEquals("c", it.next());
    }

    @Test
    public void testCreateIteratorPattern() {
        String s = "a\nb\rc";
        Iterator<?> it = ObjectHelper.createIterator(s, "\n|\r", false, true);
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
    }

    @Test
    public void testCreateIteratorWithStringAndCommaSeparator() {
        String s = "a,b,c";
        Iterator<?> it = ObjectHelper.createIterator(s, ",");
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
    }

    @Test
    public void testCreateIteratorWithStringAndCommaSeparatorEmptyString() {
        String s = "";
        Iterator<?> it = ObjectHelper.createIterator(s, ",", true);
        assertEquals("", it.next());
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertEquals("no more element available for '' at the index 1", nsee.getMessage());
        }
    }

    @Test
    public void testCreateIteratorWithStringAndSemiColonSeparator() {
        String s = "a;b;c";
        Iterator<?> it = ObjectHelper.createIterator(s, ";");
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
    }

    @Test
    public void testCreateIteratorWithStringAndCommaInParanthesesSeparator() {
        String s = "bean:foo?method=bar('A','B','C')";
        Iterator<?> it = ObjectHelper.createIterator(s, ",");
        assertEquals("bean:foo?method=bar('A','B','C')", it.next());
    }

    @Test
    public void testCreateIteratorWithStringAndCommaInParanthesesSeparatorTwo() {
        String s = "bean:foo?method=bar('A','B','C'),bean:bar?method=cool('A','Hello,World')";
        Iterator<?> it = ObjectHelper.createIterator(s, ",");
        assertEquals("bean:foo?method=bar('A','B','C')", it.next());
        assertEquals("bean:bar?method=cool('A','Hello,World')", it.next());
    }

    // CHECKSTYLE:OFF
    @Test
    public void testCreateIteratorWithPrimitiveArrayTypes() {
        Iterator<?> it = ObjectHelper.createIterator(new byte[] {13, Byte.MAX_VALUE, 7, Byte.MIN_VALUE}, null);
        assertTrue(it.hasNext());
        assertEquals(Byte.valueOf((byte)13), it.next());
        assertTrue(it.hasNext());
        assertEquals(Byte.MAX_VALUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Byte.valueOf((byte)7), it.next());
        assertTrue(it.hasNext());
        assertEquals(Byte.MIN_VALUE, it.next());
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[B@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 4"));
        }

        it = ObjectHelper.createIterator(new byte[] {}, null);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[B@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 0"));
        }

        it = ObjectHelper.createIterator(new short[] {13, Short.MAX_VALUE, 7, Short.MIN_VALUE}, null);
        assertTrue(it.hasNext());
        assertEquals(Short.valueOf((short)13), it.next());
        assertTrue(it.hasNext());
        assertEquals(Short.MAX_VALUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Short.valueOf((short)7), it.next());
        assertTrue(it.hasNext());
        assertEquals(Short.MIN_VALUE, it.next());
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[S@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 4"));
        }

        it = ObjectHelper.createIterator(new short[] {}, null);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[S@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 0"));
        }

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
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[I@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 4"));
        }

        it = ObjectHelper.createIterator(new int[] {}, null);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[I@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 0"));
        }

        it = ObjectHelper.createIterator(new long[] {13L, Long.MAX_VALUE, 7L, Long.MIN_VALUE}, null);
        assertTrue(it.hasNext());
        assertEquals(Long.valueOf(13), it.next());
        assertTrue(it.hasNext());
        assertEquals(Long.MAX_VALUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Long.valueOf(7), it.next());
        assertTrue(it.hasNext());
        assertEquals(Long.MIN_VALUE, it.next());
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[J@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 4"));
        }

        it = ObjectHelper.createIterator(new long[] {}, null);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[J@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 0"));
        }

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
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[F@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 4"));
        }

        it = ObjectHelper.createIterator(new float[] {}, null);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[F@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 0"));
        }

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
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[D@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 4"));
        }

        it = ObjectHelper.createIterator(new double[] {}, null);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[D@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 0"));
        }

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
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[C@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 5"));
        }

        it = ObjectHelper.createIterator(new char[] {}, null);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[C@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 0"));
        }

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
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[Z@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 5"));
        }

        it = ObjectHelper.createIterator(new boolean[] {}, null);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for '[Z@"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 0"));
        }
    }
    // CHECKSTYLE:ON

    @Test
    public void testArrayAsIterator() throws Exception {
        String[] data = {"a", "b"};

        Iterator<?> iter = ObjectHelper.createIterator(data);
        assertTrue("should have next", iter.hasNext());
        Object a = iter.next();
        assertEquals("a", "a", a);
        assertTrue("should have next", iter.hasNext());
        Object b = iter.next();
        assertEquals("b", "b", b);
        assertFalse("should not have a next", iter.hasNext());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(org.apache.camel.util.ObjectHelper.isEmpty(null));
        assertTrue(org.apache.camel.util.ObjectHelper.isEmpty(""));
        assertTrue(org.apache.camel.util.ObjectHelper.isEmpty(" "));
        assertFalse(org.apache.camel.util.ObjectHelper.isEmpty("A"));
        assertFalse(org.apache.camel.util.ObjectHelper.isEmpty(" A"));
        assertFalse(org.apache.camel.util.ObjectHelper.isEmpty(" A "));
        assertFalse(org.apache.camel.util.ObjectHelper.isEmpty(new Object()));
    }

    @Test
    public void testIsNotEmpty() {
        assertFalse(org.apache.camel.util.ObjectHelper.isNotEmpty(null));
        assertFalse(org.apache.camel.util.ObjectHelper.isNotEmpty(""));
        assertFalse(org.apache.camel.util.ObjectHelper.isNotEmpty(" "));
        assertTrue(org.apache.camel.util.ObjectHelper.isNotEmpty("A"));
        assertTrue(org.apache.camel.util.ObjectHelper.isNotEmpty(" A"));
        assertTrue(org.apache.camel.util.ObjectHelper.isNotEmpty(" A "));
        assertTrue(org.apache.camel.util.ObjectHelper.isNotEmpty(new Object()));
    }

    @Test
    public void testIteratorWithComma() {
        Iterator<?> it = ObjectHelper.createIterator("Claus,Jonathan");
        assertEquals("Claus", it.next());
        assertEquals("Jonathan", it.next());
        assertEquals(false, it.hasNext());
    }

    @Test
    public void testIteratorWithOtherDelimiter() {
        Iterator<?> it = ObjectHelper.createIterator("Claus#Jonathan", "#");
        assertEquals("Claus", it.next());
        assertEquals("Jonathan", it.next());
        assertEquals(false, it.hasNext());
    }

    @Test
    public void testIteratorEmpty() {
        Iterator<?> it = ObjectHelper.createIterator("");
        assertEquals(false, it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertEquals("no more element available for '' at the index 0", nsee.getMessage());
        }

        it = ObjectHelper.createIterator("    ");
        assertEquals(false, it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertEquals("no more element available for '    ' at the index 0", nsee.getMessage());
        }

        it = ObjectHelper.createIterator(null);
        assertEquals(false, it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    @Test
    public void testIteratorIdempotentNext() {
        Iterator<?> it = ObjectHelper.createIterator("a");
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        it.next();
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertEquals("no more element available for 'a' at the index 1", nsee.getMessage());
        }
    }

    @Test
    public void testIteratorIdempotentNextWithNodeList() {
        NodeList nodeList = new NodeList() {

            public Node item(int index) {
                return null;
            }

            public int getLength() {
                return 1;
            }
        };

        Iterator<?> it = ObjectHelper.createIterator(nodeList);
        assertTrue(it.hasNext());
        assertTrue(it.hasNext());
        it.next();
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertTrue(nsee.getMessage(), nsee.getMessage().startsWith("no more element available for 'org.apache.camel.util.ObjectHelperTest$"));
            assertTrue(nsee.getMessage(), nsee.getMessage().endsWith("at the index 1"));
        }
    }

    @Test
    public void testGetCamelContextPropertiesWithPrefix() {
        CamelContext context = new DefaultCamelContext();
        Map<String, String> properties = context.getGlobalOptions();
        properties.put("camel.object.helper.test1", "test1");
        properties.put("camel.object.helper.test2", "test2");
        properties.put("camel.object.test", "test");

        Properties result = CamelContextHelper.getCamelPropertiesWithPrefix("camel.object.helper.", context);
        assertEquals("Get a wrong size properties", 2, result.size());
        assertEquals("It should contain the test1", "test1", result.get("test1"));
        assertEquals("It should contain the test2", "test2", result.get("test2"));
    }

    @Test
    public void testEvaluateAsPredicate() throws Exception {
        assertEquals(false, org.apache.camel.util.ObjectHelper.evaluateValuePredicate(null));
        assertEquals(true, org.apache.camel.util.ObjectHelper.evaluateValuePredicate(123));

        assertEquals(true, org.apache.camel.util.ObjectHelper.evaluateValuePredicate("true"));
        assertEquals(true, org.apache.camel.util.ObjectHelper.evaluateValuePredicate("TRUE"));
        assertEquals(false, org.apache.camel.util.ObjectHelper.evaluateValuePredicate("false"));
        assertEquals(false, org.apache.camel.util.ObjectHelper.evaluateValuePredicate("FALSE"));
        assertEquals(true, org.apache.camel.util.ObjectHelper.evaluateValuePredicate("foobar"));
        assertEquals(true, org.apache.camel.util.ObjectHelper.evaluateValuePredicate(""));
        assertEquals(true, org.apache.camel.util.ObjectHelper.evaluateValuePredicate(" "));

        List<String> list = new ArrayList<>();
        assertEquals(false, org.apache.camel.util.ObjectHelper.evaluateValuePredicate(list));
        list.add("foo");
        assertEquals(true, org.apache.camel.util.ObjectHelper.evaluateValuePredicate(list));
    }

    @Test
    public void testIsPrimitiveArrayType() {
        assertTrue(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(byte[].class));
        assertTrue(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(short[].class));
        assertTrue(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(int[].class));
        assertTrue(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(long[].class));
        assertTrue(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(float[].class));
        assertTrue(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(double[].class));
        assertTrue(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(char[].class));
        assertTrue(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(boolean[].class));

        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(Object[].class));
        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(Byte[].class));
        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(Short[].class));
        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(Integer[].class));
        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(Long[].class));
        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(Float[].class));
        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(Double[].class));
        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(Character[].class));
        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(Boolean[].class));
        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(Void[].class));
        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(CamelContext[].class));
        assertFalse(org.apache.camel.util.ObjectHelper.isPrimitiveArrayType(null));
    }

    @Test
    public void testGetDefaultCharSet() {
        assertNotNull(org.apache.camel.util.ObjectHelper.getDefaultCharacterSet());
    }

    @Test
    public void testConvertPrimitiveTypeToWrapper() {
        assertEquals("java.lang.Integer", org.apache.camel.util.ObjectHelper.convertPrimitiveTypeToWrapperType(int.class).getName());
        assertEquals("java.lang.Long", org.apache.camel.util.ObjectHelper.convertPrimitiveTypeToWrapperType(long.class).getName());
        assertEquals("java.lang.Double", org.apache.camel.util.ObjectHelper.convertPrimitiveTypeToWrapperType(double.class).getName());
        assertEquals("java.lang.Float", org.apache.camel.util.ObjectHelper.convertPrimitiveTypeToWrapperType(float.class).getName());
        assertEquals("java.lang.Short", org.apache.camel.util.ObjectHelper.convertPrimitiveTypeToWrapperType(short.class).getName());
        assertEquals("java.lang.Byte", org.apache.camel.util.ObjectHelper.convertPrimitiveTypeToWrapperType(byte.class).getName());
        assertEquals("java.lang.Boolean", org.apache.camel.util.ObjectHelper.convertPrimitiveTypeToWrapperType(boolean.class).getName());
        assertEquals("java.lang.Character", org.apache.camel.util.ObjectHelper.convertPrimitiveTypeToWrapperType(char.class).getName());
        // non primitive just fall through
        assertEquals("java.lang.Object", org.apache.camel.util.ObjectHelper.convertPrimitiveTypeToWrapperType(Object.class).getName());
    }

    @Test
    public void testAsString() {
        String[] args = new String[] {"foo", "bar"};
        String out = org.apache.camel.util.ObjectHelper.asString(args);
        assertNotNull(out);
        assertEquals("{foo, bar}", out);
    }

    @Test
    public void testName() {
        assertEquals("java.lang.Integer", org.apache.camel.util.ObjectHelper.name(Integer.class));
        assertEquals(null, org.apache.camel.util.ObjectHelper.name(null));
    }

    @Test
    public void testClassName() {
        assertEquals("java.lang.Integer", org.apache.camel.util.ObjectHelper.className(Integer.valueOf("5")));
        assertEquals(null, org.apache.camel.util.ObjectHelper.className(null));
    }

    @Test
    public void testGetSystemPropertyDefault() {
        assertEquals("foo", org.apache.camel.util.ObjectHelper.getSystemProperty("CamelFooDoesNotExist", "foo"));
    }

    @Test
    public void testGetSystemPropertyBooleanDefault() {
        assertEquals(true, org.apache.camel.util.ObjectHelper.getSystemProperty("CamelFooDoesNotExist", Boolean.TRUE));
    }

    @Test
    public void testMatches() {
        List<Object> data = new ArrayList<>();
        data.add("foo");
        data.add("bar");
        assertEquals(true, org.apache.camel.util.ObjectHelper.matches(data));

        data.clear();
        data.add(Boolean.FALSE);
        data.add("bar");
        assertEquals(false, org.apache.camel.util.ObjectHelper.matches(data));

        data.clear();
        assertEquals(false, org.apache.camel.util.ObjectHelper.matches(data));
    }

    @Test
    public void testToBoolean() {
        assertEquals(Boolean.TRUE, org.apache.camel.util.ObjectHelper.toBoolean(Boolean.TRUE));
        assertEquals(Boolean.TRUE, org.apache.camel.util.ObjectHelper.toBoolean("true"));
        assertEquals(Boolean.TRUE, org.apache.camel.util.ObjectHelper.toBoolean(Integer.valueOf("1")));
        assertEquals(Boolean.FALSE, org.apache.camel.util.ObjectHelper.toBoolean(Integer.valueOf("0")));
        assertEquals(null, org.apache.camel.util.ObjectHelper.toBoolean(new Date()));
    }

    @Test
    public void testIteratorWithMessage() {
        Message msg = new DefaultMessage(new DefaultCamelContext());
        msg.setBody("a,b,c");

        Iterator<?> it = ObjectHelper.createIterator(msg);
        assertEquals("a", it.next());
        assertEquals("b", it.next());
        assertEquals("c", it.next());
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    @Test
    public void testIteratorWithEmptyMessage() {
        Message msg = new DefaultMessage(new DefaultCamelContext());
        msg.setBody("");

        Iterator<?> it = ObjectHelper.createIterator(msg);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertEquals("no more element available for '' at the index 0", nsee.getMessage());
        }
    }

    @Test
    public void testIteratorWithNullMessage() {
        Message msg = new DefaultMessage(new DefaultCamelContext());
        msg.setBody(null);

        Iterator<?> it = ObjectHelper.createIterator(msg);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    @Test
    public void testIterable() {
        final List<String> data = new ArrayList<>();
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
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    @Test
    public void testLookupConstantFieldValue() {
        assertEquals("CamelFileName", org.apache.camel.util.ObjectHelper.lookupConstantFieldValue(Exchange.class, "FILE_NAME"));
        assertEquals(null, org.apache.camel.util.ObjectHelper.lookupConstantFieldValue(Exchange.class, "XXX"));
        assertEquals(null, org.apache.camel.util.ObjectHelper.lookupConstantFieldValue(null, "FILE_NAME"));
    }

    @Test
    public void testHasDefaultPublicNoArgConstructor() {
        assertTrue(org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor(ObjectHelperTest.class));
        assertFalse(org.apache.camel.util.ObjectHelper.hasDefaultPublicNoArgConstructor(MyStaticClass.class));
    }

    @Test
    public void testIdentityHashCode() {
        MyDummyObject dummy = new MyDummyObject("Camel");

        String code = org.apache.camel.util.ObjectHelper.getIdentityHashCode(dummy);
        String code2 = org.apache.camel.util.ObjectHelper.getIdentityHashCode(dummy);

        assertEquals(code, code2);

        MyDummyObject dummyB = new MyDummyObject("Camel");
        String code3 = org.apache.camel.util.ObjectHelper.getIdentityHashCode(dummyB);
        assertNotSame(code, code3);
    }

    @Test
    public void testIsNaN() throws Exception {
        assertTrue(org.apache.camel.util.ObjectHelper.isNaN(Float.NaN));
        assertTrue(org.apache.camel.util.ObjectHelper.isNaN(Double.NaN));

        assertFalse(org.apache.camel.util.ObjectHelper.isNaN(null));
        assertFalse(org.apache.camel.util.ObjectHelper.isNaN(""));
        assertFalse(org.apache.camel.util.ObjectHelper.isNaN("1.0"));
        assertFalse(org.apache.camel.util.ObjectHelper.isNaN(1));
        assertFalse(org.apache.camel.util.ObjectHelper.isNaN(1.5f));
        assertFalse(org.apache.camel.util.ObjectHelper.isNaN(1.5d));
        assertFalse(org.apache.camel.util.ObjectHelper.isNaN(false));
        assertFalse(org.apache.camel.util.ObjectHelper.isNaN(true));
    }

    @Test
    public void testNotNull() {
        Long expected = 3L;
        Long actual = org.apache.camel.util.ObjectHelper.notNull(expected, "expected");
        assertSame("Didn't get the same object back!", expected, actual);

        Long actual2 = org.apache.camel.util.ObjectHelper.notNull(expected, "expected", "holder");
        assertSame("Didn't get the same object back!", expected, actual2);

        Long expected2 = null;
        try {
            org.apache.camel.util.ObjectHelper.notNull(expected2, "expected2");
            fail("Should have thrown exception");
        } catch (IllegalArgumentException iae) {
            assertEquals("expected2 must be specified", iae.getMessage());
        }

        try {
            org.apache.camel.util.ObjectHelper.notNull(expected2, "expected2", "holder");
            fail("Should have thrown exception");
        } catch (IllegalArgumentException iae) {
            assertEquals("expected2 must be specified on: holder", iae.getMessage());
        }
    }

    @Test
    public void testSameMethodIsOverride() throws Exception {
        Method m = MyOtherFooBean.class.getMethod("toString", Object.class);
        assertTrue(org.apache.camel.util.ObjectHelper.isOverridingMethod(m, m, false));
    }

    @Test
    public void testOverloadIsNotOverride() throws Exception {
        Method m1 = MyOtherFooBean.class.getMethod("toString", Object.class);
        Method m2 = MyOtherFooBean.class.getMethod("toString", String.class);
        assertFalse(org.apache.camel.util.ObjectHelper.isOverridingMethod(m2, m1, false));
    }

    @Test
    public void testOverrideEquivalentSignatureFromSiblingClassIsNotOverride() throws Exception {
        Method m1 = Double.class.getMethod("intValue");
        Method m2 = Float.class.getMethod("intValue");
        assertFalse(org.apache.camel.util.ObjectHelper.isOverridingMethod(m2, m1, false));
    }

    @Test
    public void testOverrideEquivalentSignatureFromUpperClassIsOverride() throws Exception {
        Method m1 = Double.class.getMethod("intValue");
        Method m2 = Number.class.getMethod("intValue");
        assertTrue(org.apache.camel.util.ObjectHelper.isOverridingMethod(m2, m1, false));
    }

    @Test
    public void testInheritedMethodCanOverrideInterfaceMethod() throws Exception {
        Method m1 = AbstractClassSize.class.getMethod("size");
        Method m2 = InterfaceSize.class.getMethod("size");
        assertTrue(org.apache.camel.util.ObjectHelper.isOverridingMethod(Clazz.class, m2, m1, false));
    }

    @Test
    public void testNonInheritedMethodCantOverrideInterfaceMethod() throws Exception {
        Method m1 = AbstractClassSize.class.getMethod("size");
        Method m2 = InterfaceSize.class.getMethod("size");
        assertFalse(org.apache.camel.util.ObjectHelper.isOverridingMethod(InterfaceSize.class, m2, m1, false));
    }

    @Test
    public void testAsList() {
        List<Object> out0 = org.apache.camel.util.ObjectHelper.asList(null);
        assertNotNull(out0);
        assertTrue(out0 instanceof List && out0.size() == 0);

        List<Object> out1 = org.apache.camel.util.ObjectHelper.asList(new Object[0]);
        assertNotNull(out1);
        assertTrue(out1 instanceof List && out1.size() == 0);

        String[] args = new String[] {"foo", "bar"};
        List<Object> out2 = org.apache.camel.util.ObjectHelper.asList(args);
        assertNotNull(out2);
        assertTrue(out2 instanceof List && out2.size() == 2);
        assertEquals("foo", out2.get(0));
        assertEquals("bar", out2.get(1));
    }

}
