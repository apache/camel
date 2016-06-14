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

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

/**
 * Jackson Serializer for generating ';' separated strings for MultiSelect pick-lists.
 */
public class StringMultiSelectPicklistSerializer extends JsonSerializer<Object> {

    @Override
    public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        try {

            String[] a = (String[]) value;
            final int length = a.length;

            // construct a string of form value1;value2;...
            final StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < length; i++) {
                buffer.append((String) a[i].trim());
                if (i < (length - 1)) {
                    buffer.append(';');
                }
            }

            jgen.writeString(buffer.toString());

        } catch (Exception e) {
            throw new JsonGenerationException(
                    String.format("Exception writing pick list value %s of type %s: %s",
                            value, value.getClass().getName(), e.getMessage()), e);
        }
    }
}
