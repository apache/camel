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

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TestSupport;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.engine.DefaultPackageScanClassResolver;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.IntrospectionSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ReflectionInjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class ConverterTest extends TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ConverterTest.class);

    protected TypeConverter converter = new DefaultTypeConverter(
            new DefaultPackageScanClassResolver(), new ReflectionInjector(), true);

    @BeforeEach
    public void setUp() throws Exception {
        ServiceHelper.startService(converter);
    }

    @Test
    public void testIntegerPropertyEditorConversion() throws Exception {
        Integer value = converter.convertTo(Integer.class, "1000");
        assertNotNull(value);
        assertEquals(Integer.valueOf(1000), (Object) value, "Converted to Integer");

        String text = converter.convertTo(String.class, value);
        assertEquals("1000", text, "Converted to String");
    }

    @Test
    public void testConvertStringToAndFromByteArray() throws Exception {
        byte[] array = converter.convertTo(byte[].class, "foo");
        assertNotNull(array);

        LOG.debug("Found array of size: " + array.length);

        String text = converter.convertTo(String.class, array);
        assertEquals("foo", text, "Converted to String");
    }

    @Test
    public void testConvertStringToAndFromCharArray() throws Exception {
        char[] array = converter.convertTo(char[].class, "foo");
        assertNotNull(array);

        LOG.debug("Found array of size: " + array.length);

        String text = converter.convertTo(String.class, array);
        assertEquals("foo", text, "Converted to String");
    }

    @Test
    public void testConvertStringAndStreams() throws Exception {
        InputStream inputStream = converter.convertTo(InputStream.class, "bar");
        assertNotNull(inputStream);

        String text = converter.convertTo(String.class, inputStream);
        assertEquals("bar", text, "Converted to String");
    }

    @Test
    public void testArrayToListAndSetConversion() throws Exception {
        String[] array = new String[] { "one", "two" };

        List<?> list = converter.convertTo(List.class, array);
        assertEquals(2, list.size(), "List size: " + list);

        Collection<?> collection = converter.convertTo(Collection.class, array);
        assertEquals(2, collection.size(), "Collection size: " + collection);

        Set<?> set = converter.convertTo(Set.class, array);
        assertEquals(2, set.size(), "Set size: " + set);
        set = converter.convertTo(Set.class, list);
        assertEquals(2, set.size(), "Set size: " + set);
    }

    @Test
    public void testCollectionToArrayConversion() throws Exception {
        List<String> list = new ArrayList<>();
        list.add("one");
        list.add("two");

        Object[] objectArray = converter.convertTo(Object[].class, list);
        assertEquals(2, objectArray.length, "Object[] length");

        String[] stringArray = converter.convertTo(String[].class, list);
        assertEquals(2, stringArray.length, "String[] length");
    }

    @Test
    public void testCollectionToPrimitiveArrayConversion() throws Exception {
        List<Integer> list = new ArrayList<>();
        list.add(5);
        list.add(6);

        Integer[] integerArray = converter.convertTo(Integer[].class, list);
        assertEquals(2, integerArray.length, "Integer[] length");

        int[] intArray = converter.convertTo(int[].class, list);
        assertEquals(2, intArray.length, "int[] length");

        // lets convert the typesafe array to a larger primitive type
        long[] longArray = converter.convertTo(long[].class, intArray);
        assertEquals(2, longArray.length, "long[] length");

        // now lets go back to a List again
        List<?> resultList = converter.convertTo(List.class, intArray);
        assertEquals(2, resultList.size(), "List size");
        LOG.debug("From primitive type array we've created the list: " + resultList);
    }

    @Test
    public void testStringToFile() throws Exception {
        File file = converter.convertTo(File.class, "foo.txt");
        assertNotNull("Should have converted to a file!");
        assertEquals("foo.txt", file.getName(), "file name");
    }

    @Test
    public void testFileToString() throws Exception {
        URL resource = getClass().getResource("dummy.txt");
        assertNotNull(resource, "Cannot find resource!");
        File file = new File(URLDecoder.decode(resource.getFile(), "UTF-8"));
        String text = converter.convertTo(String.class, file);
        assertNotNull(text, "Should have returned a String!");
        text = text.trim();
        assertTrue(text.endsWith("Hello World!"), "Text not read correctly: " + text);
    }

    @Test
    public void testPrimitiveBooleanConversion() throws Exception {
        boolean value = converter.convertTo(boolean.class, null);
        assertFalse(value);
    }

    @Test
    public void testPrimitiveIntConversion() throws Exception {
        int value = converter.convertTo(int.class, 4);
        assertEquals(4, value, "value");
    }

    @Test
    public void testPrimitiveIntPropertySetter() throws Exception {
        MyBean bean = new MyBean();
        IntrospectionSupport.setProperty(converter, bean, "foo", "4");
        assertEquals(4, bean.getFoo(), "bean.foo");
    }

    @Test
    public void testStringToBoolean() throws Exception {
        Boolean value = converter.convertTo(Boolean.class, "true");
        assertEquals(Boolean.TRUE, value, "converted boolean value");

        value = converter.convertTo(Boolean.class, "false");
        assertEquals(Boolean.FALSE, value, "converted boolean value");

        value = converter.convertTo(Boolean.class, null);
        assertEquals(null, value, "converted boolean value");
    }

    @Test
    public void testStaticMethodConversionWithExchange() throws Exception {
        CamelContext camel = new DefaultCamelContext();
        Exchange e = new DefaultExchange(camel);
        e.setProperty("prefix", "foo-");
        MyBean bean = converter.convertTo(MyBean.class, e, "5:bar");
        assertEquals(5, bean.getFoo(), "converted using exchange");
        assertEquals("foo-bar", bean.getBar(), "converted using exchange");
    }

    @Test
    public void testInstanceMethodConversionWithExchange() throws Exception {
        String[] values = new String[] { "5", "bar" };

        CamelContext camel = new DefaultCamelContext();
        Exchange e = new DefaultExchange(camel);
        e.setProperty("prefix", "foo-");
        MyBean bean = converter.convertTo(MyBean.class, e, values);
        assertEquals(5, bean.getFoo(), "converted using exchange");
        assertEquals("foo-bar", bean.getBar(), "converted using exchange");
    }

    @Test
    public void testMandatoryConvertTo() {
        CamelContext camel = new DefaultCamelContext();
        Exchange e = new DefaultExchange(camel);
        try {
            converter.mandatoryConvertTo(InputStream.class, e);
            fail("Expect exception here");
        } catch (Exception ex) {
            boolean b = ex instanceof NoTypeConversionAvailableException;
            assertTrue(b, "Expect to get a NoTypeConversionAvailableException here");
        }
    }

    @Test
    public void testStringToChar() throws Exception {
        char ch = converter.convertTo(char.class, "A");
        assertEquals('A', (int) ch);

        ch = converter.convertTo(char.class, " ");
        assertEquals(' ', (int) ch);

        try {
            converter.mandatoryConvertTo(char.class, "ABC");
            fail("Should have thrown an exception");
        } catch (TypeConversionException e) {
            assertEquals("String must have exactly a length of 1: ABC", e.getCause().getMessage());
        }
    }

    @Test
    public void testNullToBoolean() throws Exception {
        boolean b = converter.convertTo(boolean.class, null);
        assertFalse(b);
    }

    @Test
    public void testNullToInt() throws Exception {
        int i = converter.convertTo(int.class, null);
        assertEquals(0, i);
    }

    @Test
    public void testToInt() throws Exception {
        int i = converter.convertTo(int.class, "0");
        assertEquals(0, i);
    }
}
