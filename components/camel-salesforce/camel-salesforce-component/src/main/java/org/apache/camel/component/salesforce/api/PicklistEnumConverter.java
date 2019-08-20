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

import java.lang.reflect.Method;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * XStream converter for handling pick-list enum fields.
 */
public class PicklistEnumConverter implements Converter {

    private static final String FACTORY_METHOD = "fromValue";

    @Override
    public void marshal(Object o, HierarchicalStreamWriter writer, MarshallingContext context) {
        Class<?> aClass = o.getClass();
        try {
            Method getterMethod = aClass.getMethod("value");
            writer.setValue((String)getterMethod.invoke(o));
        } catch (Exception e) {
            throw new ConversionException(String.format("Exception writing pick list value %s of type %s: %s", o, o.getClass().getName(), e.getMessage()), e);
        }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String value = reader.getValue();
        Class<?> requiredType = context.getRequiredType();
        try {
            Method factoryMethod = requiredType.getMethod(FACTORY_METHOD, String.class);
            // use factory method to create object
            return factoryMethod.invoke(null, value);
        } catch (Exception e) {
            throw new ConversionException(String.format("Exception reading pick list value %s of type %s: %s", value, context.getRequiredType().getName(), e.getMessage()), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean canConvert(Class aClass) {
        try {
            return Enum.class.isAssignableFrom(aClass) && aClass.getMethod(FACTORY_METHOD, String.class) != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

}
