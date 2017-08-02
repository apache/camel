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

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.bean.MyOtherFooBean;
import org.apache.camel.component.bean.MyOtherFooBean.AbstractClassSize;
import org.apache.camel.component.bean.MyOtherFooBean.Clazz;
import org.apache.camel.component.bean.MyOtherFooBean.InterfaceSize;
import org.apache.camel.component.bean.MyStaticClass;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultMessage;

/**
 * @version 
 */
public class ObjectHelperTest extends TestCase {

    public void testLoadResourceAsStream() {
        InputStream res1 = ObjectHelper.loadResourceAsStream("org/apache/camel/util/ObjectHelperResourceTestFile.properties");
        InputStream res2 = ObjectHelper.loadResourceAsStream("/org/apache/camel/util/ObjectHelperResourceTestFile.properties");

        assertNotNull("Cannot load resource without leading \"/\"", res1);
        assertNotNull("Cannot load resource with leading \"/\"", res2);

        IOHelper.close(res1, res2);
    }

    public void testLoadResource() {
        URL url1 = ObjectHelper.loadResourceAsURL("org/apache/camel/util/ObjectHelperResourceTestFile.properties");
        URL url2 = ObjectHelper.loadResourceAsURL("/org/apache/camel/util/ObjectHelperResourceTestFile.properties");

        assertNotNull("Cannot load resource without leading \"/\"", url1);
        assertNotNull("Cannot load resource with leading \"/\"", url2);
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

    public void testCreateIteratorPattern() {
        String s = "a\nb\rc";
        Iterator<?> it = ObjectHelper.createIterator(s, "\n|\r", false, true);
        assertEquals("a", it.next());
        assertEquals("b", it.next());
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
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertEquals("no more element available for '' at the index 1", nsee.getMessage());
        }
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

    // CHECKSTYLE:OFF
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
        assertEquals(Short.valueOf((short) 13), it.next());
        assertTrue(it.hasNext());
        assertEquals(Short.MAX_VALUE, it.next());
        assertTrue(it.hasNext());
        assertEquals(Short.valueOf((short) 7), it.next());
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

    public void testIsEmpty() {
        assertTrue(ObjectHelper.isEmpty(null));
        assertTrue(ObjectHelper.isEmpty(""));
        assertTrue(ObjectHelper.isEmpty(" "));
        assertFalse(ObjectHelper.isEmpty("A"));
        assertFalse(ObjectHelper.isEmpty(" A"));
        assertFalse(ObjectHelper.isEmpty(" A "));
        assertFalse(ObjectHelper.isEmpty(new Object()));
    }

    public void testIsNotEmpty() {
        assertFalse(ObjectHelper.isNotEmpty(null));
        assertFalse(ObjectHelper.isNotEmpty(""));
        assertFalse(ObjectHelper.isNotEmpty(" "));
        assertTrue(ObjectHelper.isNotEmpty("A"));
        assertTrue(ObjectHelper.isNotEmpty(" A"));
        assertTrue(ObjectHelper.isNotEmpty(" A "));
        assertTrue(ObjectHelper.isNotEmpty(new Object()));
    }

    public void testIteratorWithComma() {
        Iterator<?> it = ObjectHelper.createIterator("Claus,Jonathan");
        assertEquals("Claus", it.next());
        assertEquals("Jonathan", it.next());
        assertEquals(false, it.hasNext());
    }

    public void testIteratorWithOtherDelimiter() {
        Iterator<?> it = ObjectHelper.createIterator("Claus#Jonathan", "#");
        assertEquals("Claus", it.next());
        assertEquals("Jonathan", it.next());
        assertEquals(false, it.hasNext());
    }

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

    public void testGetCamelContextPropertiesWithPrefix() {
        CamelContext context = new DefaultCamelContext();
        Map<String, String> properties = context.getGlobalOptions();
        properties.put("camel.object.helper.test1", "test1");
        properties.put("camel.object.helper.test2", "test2");
        properties.put("camel.object.test", "test");

        Properties result = ObjectHelper.getCamelPropertiesWithPrefix("camel.object.helper.", context);
        assertEquals("Get a wrong size properties", 2, result.size());
        assertEquals("It should contain the test1", "test1", result.get("test1"));
        assertEquals("It should contain the test2", "test2", result.get("test2"));
    }

    public void testEvaluateAsPredicate() throws Exception {
        assertEquals(false, ObjectHelper.evaluateValuePredicate(null));
        assertEquals(true, ObjectHelper.evaluateValuePredicate(123));

        assertEquals(true, ObjectHelper.evaluateValuePredicate("true"));
        assertEquals(true, ObjectHelper.evaluateValuePredicate("TRUE"));
        assertEquals(false, ObjectHelper.evaluateValuePredicate("false"));
        assertEquals(false, ObjectHelper.evaluateValuePredicate("FALSE"));
        assertEquals(true, ObjectHelper.evaluateValuePredicate("foobar"));
        assertEquals(true, ObjectHelper.evaluateValuePredicate(""));
        assertEquals(true, ObjectHelper.evaluateValuePredicate(" "));

        List<String> list = new ArrayList<String>();
        assertEquals(false, ObjectHelper.evaluateValuePredicate(list));
        list.add("foo");
        assertEquals(true, ObjectHelper.evaluateValuePredicate(list));
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
        assertEquals("java.lang.Character", ObjectHelper.convertPrimitiveTypeToWrapperType(char.class).getName());
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

    public void testIteratorWithEmptyMessage() {
        Message msg = new DefaultMessage(new DefaultCamelContext());
        msg.setBody("");

        Iterator<Object> it = ObjectHelper.createIterator(msg);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
            assertEquals("no more element available for '' at the index 0", nsee.getMessage());
        }
    }

    public void testIteratorWithNullMessage() {
        Message msg = new DefaultMessage(new DefaultCamelContext());
        msg.setBody(null);

        Iterator<Object> it = ObjectHelper.createIterator(msg);
        assertFalse(it.hasNext());
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
        }
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
        try {
            it.next();
            fail("Should have thrown exception");
        } catch (NoSuchElementException nsee) {
            // expected
        }
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

    public void testNotNull() {
        Long expected = 3L;
        Long actual = ObjectHelper.notNull(expected, "expected");
        assertSame("Didn't get the same object back!", expected, actual);

        Long actual2 = ObjectHelper.notNull(expected, "expected", "holder");
        assertSame("Didn't get the same object back!", expected, actual2);

        Long expected2 = null;
        try {
            ObjectHelper.notNull(expected2, "expected2");
            fail("Should have thrown exception");
        } catch (IllegalArgumentException iae) {
            assertEquals("expected2 must be specified", iae.getMessage());
        }

        try {
            ObjectHelper.notNull(expected2, "expected2", "holder");
            fail("Should have thrown exception");
        } catch (IllegalArgumentException iae) {
            assertEquals("expected2 must be specified on: holder", iae.getMessage());
        }
    }

    public void testSameMethodIsOverride() throws Exception {
        Method m = MyOtherFooBean.class.getMethod("toString", Object.class);
        assertTrue(ObjectHelper.isOverridingMethod(m, m, false));
    }

    public void testOverloadIsNotOverride() throws Exception {
        Method m1 = MyOtherFooBean.class.getMethod("toString", Object.class);
        Method m2 = MyOtherFooBean.class.getMethod("toString", String.class);
        assertFalse(ObjectHelper.isOverridingMethod(m2, m1, false));
    }

    public void testOverrideEquivalentSignatureFromSiblingClassIsNotOverride() throws Exception {
        Method m1 = Double.class.getMethod("intValue");
        Method m2 = Float.class.getMethod("intValue");
        assertFalse(ObjectHelper.isOverridingMethod(m2, m1, false));
    }

    public void testOverrideEquivalentSignatureFromUpperClassIsOverride() throws Exception {
        Method m1 = Double.class.getMethod("intValue");
        Method m2 = Number.class.getMethod("intValue");
        assertTrue(ObjectHelper.isOverridingMethod(m2, m1, false));
    }

    public void testInheritedMethodCanOverrideInterfaceMethod() throws Exception {
        Method m1 = AbstractClassSize.class.getMethod("size");
        Method m2 = InterfaceSize.class.getMethod("size");
        assertTrue(ObjectHelper.isOverridingMethod(Clazz.class, m2, m1, false));
    }

    public void testNonInheritedMethodCantOverrideInterfaceMethod() throws Exception {
        Method m1 = AbstractClassSize.class.getMethod("size");
        Method m2 = InterfaceSize.class.getMethod("size");
        assertFalse(ObjectHelper.isOverridingMethod(InterfaceSize.class, m2, m1, false));
    }
}
