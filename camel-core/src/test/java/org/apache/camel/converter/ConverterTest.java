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

import java.beans.PropertyEditorManager;
import java.beans.PropertyEditorSupport;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ReflectionInjector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class ConverterTest extends TestCase {
    private static final transient Log LOG = LogFactory.getLog(ConverterTest.class);

    protected TypeConverter converter = new DefaultTypeConverter(new ReflectionInjector());

    public static class IntegerPropertyEditor extends PropertyEditorSupport {
        public void setAsText(String text) throws IllegalArgumentException {
            setValue(new Integer(text));
        }

        public String getAsText() {
            Integer value = (Integer) getValue();
            return value != null ? value.toString() : "";
        }
    }

    @Override
    protected void setUp() throws Exception {
        PropertyEditorManager.registerEditor(Integer.class, IntegerPropertyEditor.class);
    }

    public void testIntegerPropertyEditorConversion() throws Exception {
        Integer value = converter.convertTo(Integer.class, "1000");
        assertNotNull(value);
        assertEquals("Converted to Integer", new Integer(1000), value);

        String text = converter.convertTo(String.class, value);
        assertEquals("Converted to String", "1000", text);
    }

    public void testConvertStringToAndFromByteArray() throws Exception {
        byte[] array = converter.convertTo(byte[].class, "foo");
        assertNotNull(array);

        LOG.debug("Found array of size: " + array.length);

        String text = converter.convertTo(String.class, array);
        assertEquals("Converted to String", "foo", text);
    }

    public void testConvertStringToAndFromCharArray() throws Exception {
        char[] array = converter.convertTo(char[].class, "foo");
        assertNotNull(array);

        LOG.debug("Found array of size: " + array.length);

        String text = converter.convertTo(String.class, array);
        assertEquals("Converted to String", "foo", text);
    }

    public void testConvertStringAndStreams() throws Exception {
        InputStream inputStream = converter.convertTo(InputStream.class, "bar");
        assertNotNull(inputStream);

        String text = converter.convertTo(String.class, inputStream);
        assertEquals("Converted to String", "bar", text);
    }

    public void testArrayToListAndSetConversion() throws Exception {
        String[] array = new String[]{"one", "two"};

        List list = converter.convertTo(List.class, array);
        assertEquals("List size: " + list, 2, list.size());

        Collection collection = converter.convertTo(Collection.class, array);
        assertEquals("Collection size: " + collection, 2, collection.size());

        Set set = converter.convertTo(Set.class, array);
        assertEquals("Set size: " + set, 2, set.size());
        set = converter.convertTo(Set.class, list);
        assertEquals("Set size: " + set, 2, set.size());
    }


    public void testCollectionToArrayConversion() throws Exception {
        List list = new ArrayList();
        list.add("one");
        list.add("two");

        Object[] objectArray = converter.convertTo(Object[].class, list);
        assertEquals("Object[] length", 2, objectArray.length);

        String[] stringArray = converter.convertTo(String[].class, list);
        assertEquals("String[] length", 2, stringArray.length);
    }

    public void testCollectionToPrimitiveArrayConversion() throws Exception {
        List list = new ArrayList();
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
        List resultList = converter.convertTo(List.class, intArray);
        assertEquals("List size", 2, resultList.size());
        LOG.debug("From primitive type array we've created the list: " + resultList);
    }

    public void testStringToFile() throws Exception {
        File file = converter.convertTo(File.class, "foo.txt");
        assertNotNull("Should have converted to a file!");
        assertEquals("file name", "foo.txt", file.getName());
    }

    public void testFileToString() throws Exception {
        URL resource = getClass().getResource("dummy.txt");
        assertNotNull("Cannot find resource!");
        File file = new File(resource.getFile());
        String text = converter.convertTo(String.class, file);
        assertNotNull("Should have returned a String!", text);
        text = text.trim();
        assertTrue("Text not read correctly: " + text, text.endsWith("Hello World!"));
    }

    public void testPrimitiveBooleanConversion() throws Exception {
        boolean value = converter.convertTo(boolean.class, null);
        assertFalse(value);
    }

    public void testPrimitiveIntConversion() throws Exception {
        int value = converter.convertTo(int.class, 4);
        assertEquals("value", 4, value);
    }

    public void testPrimitiveIntPropertySetter() throws Exception {
        MyBean bean = new MyBean();
        IntrospectionSupport.setProperty(converter, bean, "foo", "4");
        assertEquals("bean.foo", 4, bean.getFoo());
    }

    public void testStringToBoolean() throws Exception {
        Boolean value = converter.convertTo(Boolean.class, "true");
        assertEquals("converted boolean value", Boolean.TRUE, value);

        value = converter.convertTo(Boolean.class, "false");
        assertEquals("converted boolean value", Boolean.FALSE, value);

        value = converter.convertTo(Boolean.class, null);
        assertEquals("converted boolean value", null, value);
    }

    public void testStaticMethodConversionWithExchange() throws Exception {
        CamelContext camel = new DefaultCamelContext();
        Exchange e = new DefaultExchange(camel);
        e.setProperty("prefix", "foo-");
        MyBean bean = converter.convertTo(MyBean.class, e, "5:bar");
        assertEquals("converted using exchange", 5, bean.getFoo(), 5);
        assertEquals("converted using exchange", "foo-bar", bean.getBar());
    }

    public void testInstanceMethodConversionWithExchange() throws Exception {
        String[] values = new String[]{"5", "bar"};

        CamelContext camel = new DefaultCamelContext();
        Exchange e = new DefaultExchange(camel);
        e.setProperty("prefix", "foo-");
        MyBean bean = converter.convertTo(MyBean.class, e, values);
        assertEquals("converted using exchange", 5, bean.getFoo(), 5);
        assertEquals("converted using exchange", "foo-bar", bean.getBar());
    }
}
