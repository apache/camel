/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.converter;

import junit.framework.TestCase;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.beans.PropertyEditorManager;
import java.beans.PropertyEditorSupport;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @version $Revision$
 */
public class ConverterTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(ConverterTest.class);

    protected TypeConverter converter = new DefaultTypeConverter();

    public static class IntegerPropertyEditor extends PropertyEditorSupport {
        public void setAsText(String text) throws IllegalArgumentException {
            setValue(new Integer(text));
        }

        public String getAsText() {
            Integer value = (Integer) getValue();
            return (value != null ? value.toString() : "");
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

    public void testConvertStringAndBytes() throws Exception {
        byte[] array = converter.convertTo(byte[].class, "foo");
        assertNotNull(array);

        log.debug("Found array of size: " + array.length);

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
        log.debug("From primitive type array we've created the list: " + resultList);

    }

    public void testPrimitiveBooleanConversion() throws Exception {
        boolean value = converter.convertTo(boolean.class, null);
        assertFalse(value);
    }
}
