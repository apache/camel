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
package org.apache.camel.component.salesforce.api;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * XStream converter for handling MSPs mapped to Picklist enum array fields.
 */
public class MultiSelectPicklistConverter implements Converter {

    private static final String FACTORY_METHOD = "fromValue";

    @Override
    public void marshal(Object o, HierarchicalStreamWriter writer, MarshallingContext context) {
        // get Picklist enum element class from array class
        Class<?> arrayClass = o.getClass();
        final Class<?> aClass = arrayClass.getComponentType();

        try {
            Method getterMethod = aClass.getMethod("value");
            final int length = Array.getLength(o);

            // construct a string of form value1;value2;...
            final StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < length; i++) {
                buffer.append((String)getterMethod.invoke(Array.get(o, i)));
                if (i < (length - 1)) {
                    buffer.append(';');
                }
            }
            writer.setValue(buffer.toString());
        } catch (Exception e) {
            throw new ConversionException(String.format("Exception writing pick list value %s of type %s: %s", o, o.getClass().getName(), e.getMessage()), e);
        }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        final String listValue = reader.getValue();
        // get Picklist enum element class from array class
        final Class<?> requiredArrayType = context.getRequiredType();
        final Class<?> requiredType = requiredArrayType.getComponentType();

        try {
            Method factoryMethod = requiredType.getMethod(FACTORY_METHOD, String.class);

            // parse the string of the form value1;value2;...
            final String[] value = listValue.split(";");
            final int length = value.length;
            final Object resultArray = Array.newInstance(requiredType, length);
            for (int i = 0; i < length; i++) {
                // use factory method to create object
                Array.set(resultArray, i, factoryMethod.invoke(null, value[i].trim()));
            }
            return resultArray;
        } catch (Exception e) {
            throw new ConversionException(String.format("Exception reading pick list value %s of type %s: %s", listValue, requiredArrayType.getName(), e.getMessage()), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean canConvert(Class aClass) {
        try {
            // check whether the Class is an array, and whether the array elment
            // is a Picklist enum class
            final Class componentType = aClass.getComponentType();
            return componentType != null && Enum.class.isAssignableFrom(componentType) && componentType.getMethod(FACTORY_METHOD, String.class) != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

}
