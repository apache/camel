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
package org.apache.camel.component.salesforce.api;

import java.lang.reflect.Array;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * XStream converter for handling MSPs mapped to String array fields.
 */
public class StringMultiSelectPicklistConverter implements Converter {

    @Override
    public void marshal(Object o, HierarchicalStreamWriter writer, MarshallingContext context) {
        try {
            final int length = Array.getLength(o);

            // construct a string of form value1;value2;...
            final StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < length; i++) {
                buffer.append((String) o);
                if (i < (length - 1)) {
                    buffer.append(';');
                }
            }
            writer.setValue(buffer.toString());
        } catch (Exception e) {
            throw new ConversionException(
                    String.format("Exception writing pick list value %s of type %s: %s",
                            o, o.getClass().getName(), e.getMessage()), e);
        }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        final String listValue = reader.getValue();
        final Class<?> requiredArrayType = context.getRequiredType();

        try {
            // parse the string of the form value1;value2;...
            final String[] value = listValue.split(";");
            final int length = value.length;
            final String[] resultArray = new String[length];
            for (int i = 0; i < length; i++) {
                // use factory method to create object
                resultArray[i] = value[i].trim();
                Array.set(resultArray, i, value[i].trim());
            }
            return resultArray;
        } catch (Exception e) {
            throw new ConversionException(
                    String.format("Exception reading pick list value %s of type %s: %s",
                            listValue, requiredArrayType.getName(), e.getMessage()), e);
        }
    }

    @Override
    public boolean canConvert(Class aClass) {
        // check whether the Class is an array, and whether the array element is a String
        final Class<?> componentType = aClass.getComponentType();
        return componentType != null && String.class == componentType;
    }

}
