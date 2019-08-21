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

import java.beans.PropertyEditorManager;
import java.beans.PropertyEditorSupport;
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
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.impl.engine.DefaultFactoryFinderResolver;
import org.apache.camel.impl.engine.DefaultPackageScanClassResolver;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.IntrospectionSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ReflectionInjector;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConverterTest extends Assert {

    private static final Logger LOG = LoggerFactory.getLogger(ConverterTest.class);

    protected TypeConverter converter = new DefaultTypeConverter(new DefaultPackageScanClassResolver(), new ReflectionInjector(),
                                                                 new DefaultFactoryFinderResolver().resolveDefaultFactoryFinder(new DefaultClassResolver()), true);

    public static class IntegerPropertyEditor extends PropertyEditorSupport {
        @Override
        public void setAsText(String text) throws IllegalArgumentException {
            setValue(new Integer(text));
        }

        @Override
        public String getAsText() {
            Integer value = (Integer)getValue();
            return value != null ? value.toString() : "";
        }
    }

    @Before
    public void setUp() throws Exception {
        PropertyEditorManager.registerEditor(Integer.class, IntegerPropertyEditor.class);
        ServiceHelper.startService(converter);
    }

    @Test
    public void testIntegerPropertyEditorConversion() throws Exception {
        Integer value = converter.convertTo(Integer.class, "1000");
        assertNotNull(value);
        assertEquals("Converted to Integer", new Integer(1000), value);

        String text = converter.convertTo(String.class, value);
        assertEquals("Converted to String", "1000", text);
    }

    @Test
    public void testConvertStringToAndFromByteArray() throws Exception {
        byte[] array = converter.convertTo(byte[].class, "foo");
        assertNotNull(array);

        LOG.debug("Found array of size: " + array.length);

        String text = converter.convertTo(String.class, array);
        assertEquals("Converted to String", "foo", text);
    }

    @Test
    public void testConvertStringToAndFromCharArray() throws Exception {
        char[] array = converter.convertTo(char[].class, "foo");
        assertNotNull(array);

        LOG.debug("Found array of size: " + array.length);

        String text = converter.convertTo(String.class, array);
        assertEquals("Converted to String", "foo", text);
    }

    @Test
    public void testConvertStringAndStreams() throws Exception {
        InputStream inputStream = converter.convertTo(InputStream.class, "bar");
        assertNotNull(inputStream);

        String text = converter.convertTo(String.class, inputStream);
        assertEquals("Converted to String", "bar", text);
    }

    @Test
    public void testArrayToListAndSetConversion() throws Exception {
        String[] array = new String[] {"one", "two"};

        List<?> list = converter.convertTo(List.class, array);
        assertEquals("List size: " + list, 2, list.size());

        Collection<?> collection = converter.convertTo(Collection.class, array);
        assertEquals("Collection size: " + collection, 2, collection.size());

        Set<?> set = converter.convertTo(Set.class, array);
        assertEquals("Set size: " + set, 2, set.size());
        set = converter.convertTo(Set.class, list);
        assertEquals("Set size: " + set, 2, set.size());
    }

    @Test
    public void testCollectionToArrayConversion() throws Exception {
        List<String> list = new ArrayList<>();
        list.add("one");
        list.add("two");

        Object[] objectArray = converter.convertTo(Object[].class, list);
        assertEquals("Object[] length", 2, objectArray.length);

        String[] stringArray = converter.convertTo(String[].class, list);
        assertEquals("String[] length", 2, stringArray.length);
    }

    @Test
    public void testCollectionToPrimitiveArrayConversion() throws Exception {
        List<Integer> list = new ArrayList<>();
        list.add(5);
        list.add(6);

        Integer[] integerArray = converter.convertTo(Integer[].class, list);
        assertEquals("Integer[] length", 2, integerArray.length);

        int[] intArray = converter.convertTo(int[].class, list);
        assertEquals("int[] length", 2, intArray.length);

        // lets convert the typesafe array to a larger primitive type
        long[] longArray = converter.convertTo(long[].class, intArray);
        assertEquals("long[] length", 2, longArray.length);

        // now lets go back to a List again
        List<?> resultList = converter.convertTo(List.class, intArray);
        assertEquals("List size", 2, resultList.size());
        LOG.debug("From primitive type array we've created the list: " + resultList);
    }

    @Test
    public void testStringToFile() throws Exception {
        File file = converter.convertTo(File.class, "foo.txt");
        assertNotNull("Should have converted to a file!");
        assertEquals("file name", "foo.txt", file.getName());
    }

    @Test
    public void testFileToString() throws Exception {
        URL resource = getClass().getResource("dummy.txt");
        assertNotNull("Cannot find resource!", resource);
        File file = new File(URLDecoder.decode(resource.getFile(), "UTF-8"));
        String text = converter.convertTo(String.class, file);
        assertNotNull("Should have returned a String!", text);
        text = text.trim();
        assertTrue("Text not read correctly: " + text, text.endsWith("Hello World!"));
    }

    @Test
    public void testPrimitiveBooleanConversion() throws Exception {
        boolean value = converter.convertTo(boolean.class, null);
        assertFalse(value);
    }

    @Test
    public void testPrimitiveIntConversion() throws Exception {
        int value = converter.convertTo(int.class, 4);
        assertEquals("value", 4, value);
    }

    @Test
    public void testPrimitiveIntPropertySetter() throws Exception {
        MyBean bean = new MyBean();
        IntrospectionSupport.setProperty(converter, bean, "foo", "4");
        assertEquals("bean.foo", 4, bean.getFoo());
    }

    @Test
    public void testStringToBoolean() throws Exception {
        Boolean value = converter.convertTo(Boolean.class, "true");
        assertEquals("converted boolean value", Boolean.TRUE, value);

        value = converter.convertTo(Boolean.class, "false");
        assertEquals("converted boolean value", Boolean.FALSE, value);

        value = converter.convertTo(Boolean.class, null);
        assertEquals("converted boolean value", null, value);
    }

    @Test
    public void testStaticMethodConversionWithExchange() throws Exception {
        CamelContext camel = new DefaultCamelContext();
        Exchange e = new DefaultExchange(camel);
        e.setProperty("prefix", "foo-");
        MyBean bean = converter.convertTo(MyBean.class, e, "5:bar");
        assertEquals("converted using exchange", 5, bean.getFoo(), 5);
        assertEquals("converted using exchange", "foo-bar", bean.getBar());
    }

    @Test
    public void testInstanceMethodConversionWithExchange() throws Exception {
        String[] values = new String[] {"5", "bar"};

        CamelContext camel = new DefaultCamelContext();
        Exchange e = new DefaultExchange(camel);
        e.setProperty("prefix", "foo-");
        MyBean bean = converter.convertTo(MyBean.class, e, values);
        assertEquals("converted using exchange", 5, bean.getFoo(), 5);
        assertEquals("converted using exchange", "foo-bar", bean.getBar());
    }

    @Test
    public void testMandatoryConvertTo() {
        CamelContext camel = new DefaultCamelContext();
        Exchange e = new DefaultExchange(camel);
        try {
            converter.mandatoryConvertTo(InputStream.class, e);
            fail("Expect exception here");
        } catch (Exception ex) {
            assertTrue("Expect to get a NoTypeConversionAvailableException here", ex instanceof NoTypeConversionAvailableException);
        }
    }

    @Test
    public void testStringToChar() throws Exception {
        char ch = converter.convertTo(char.class, "A");
        assertEquals('A', ch);

        ch = converter.convertTo(char.class, " ");
        assertEquals(' ', ch);

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
