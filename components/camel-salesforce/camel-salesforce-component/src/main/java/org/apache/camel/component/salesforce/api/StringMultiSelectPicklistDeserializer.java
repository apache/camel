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

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.BeanProperty;
import org.codehaus.jackson.map.ContextualDeserializer;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.JsonMappingException;

/**
 * Jackson deserializer base class for reading ';' separated strings for MultiSelect pick-lists.
 */
public class StringMultiSelectPicklistDeserializer
    extends JsonDeserializer<Object> implements ContextualDeserializer<Object> {

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
            throw new JsonParseException("Exception reading multi-select pick list value", jp.getCurrentLocation(), e);
        }
    }

    @Override
    public JsonDeserializer<Object> createContextual(DeserializationConfig config, BeanProperty property) throws JsonMappingException {
        final Class<?> rawClass = property.getType().getRawClass();
        final Class<?> componentType = rawClass.getComponentType();
        if (componentType == null || componentType != String.class) {
            throw new JsonMappingException("Pick list String array expected for " + rawClass);
        }
        return new StringMultiSelectPicklistDeserializer();
    }
}
