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

import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson deserializer base class for reading ';' separated strings for MultiSelect pick-lists.
 */
public class MultiSelectPicklistDeserializer extends StdDeserializer<Object> implements ValueDeserializer {

    private static final long serialVersionUID = -4568286926393043366L;

    private static final String FACTORY_METHOD = "fromValue";

    private final Class<? extends Enum<?>> enumClass;
    private final Method factoryMethod;

    public MultiSelectPicklistDeserializer() {
        super(Object.class);
        this.factoryMethod = null;
        this.enumClass = null;
    }

    public MultiSelectPicklistDeserializer(JsonParser jp, Class<? extends Enum<?>> enumClass) throws DatabindException {
        super(enumClass);
        this.enumClass = enumClass;
        try {
            this.factoryMethod = enumClass.getMethod(FACTORY_METHOD, String.class);
        } catch (NoSuchMethodException e) {
            throw DatabindException.from(jp, "Invalid pick-list enum class " + enumClass.getName(), e);
        }
    }

    @Override
    public Object deserialize(JsonParser jp, DeserializationContext ctxt) {

        // validate enum class
        if (enumClass == null) {
            throw DatabindException.from(jp, "Unable to parse unknown pick-list type");
        }

        final String listValue = jp.getText();

        try {
            // parse the string of the form value1;value2;...
            final String[] value = listValue.split(";");
            final int length = value.length;
            final Object resultArray = Array.newInstance(enumClass, length);
            for (int i = 0; i < length; i++) {
                // use factory method to create object
                Array.set(resultArray, i, factoryMethod.invoke(null, value[i].trim()));
            }

            return resultArray;
        } catch (Exception e) {
            throw new StreamReadException(jp, "Exception reading multi-select pick list value", jp.currentLocation());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext context, BeanProperty property)
            throws DatabindException {
        final Class<?> rawClass = property.getType().getRawClass();
        final Class<?> componentType = rawClass.getComponentType();
        if (componentType == null || !componentType.isEnum()) {
            throw DatabindException.from(context.getParser(), "Pick list Enum array expected for " + rawClass);
        }
        return new MultiSelectPicklistDeserializer(context.getParser(), (Class<? extends Enum<?>>) componentType);
    }
}
