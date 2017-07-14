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

import java.io.IOException;
import java.lang.reflect.Array;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson deserializer base class for reading ';' separated strings for MultiSelect pick-lists.
 */
public class StringMultiSelectPicklistDeserializer
    extends StdDeserializer<Object> implements ContextualDeserializer {

    private static final long serialVersionUID = 7380774744798254325L;

    protected StringMultiSelectPicklistDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Object deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {

        final String listValue = jp.getText();

        try {
            // parse the string of the form value1;value2;...
            final String[] value = listValue.split(";");
            final int length = value.length;
            final Object resultArray = Array.newInstance(String.class, length);
            for (int i = 0; i < length; i++) {
                // use factory method to create object
                Array.set(resultArray, i, value[i].trim());
            }

            return resultArray;
        } catch (Exception e) {
            throw new JsonParseException(jp, "Exception reading multi-select pick list value", jp.getCurrentLocation(), e);
        }
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) throws JsonMappingException {
        final Class<?> rawClass = property.getType().getRawClass();
        final Class<?> componentType = rawClass.getComponentType();
        if (componentType == null || componentType != String.class) {
            throw new JsonMappingException(context.getParser(), "Pick list String array expected for " + rawClass);
        }
        return new StringMultiSelectPicklistDeserializer(rawClass);
    }
}
