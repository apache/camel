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
package org.apache.camel.impl.converter;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.util.ObjectHelper;

import java.lang.reflect.Method;

/**
 * A type converter which is used to convert to and from array types
 * particularly for derived types of array component types and dealing with
 * primitive array types.
 *
 * @version $Revision: $
 */
public class EnumTypeConverter implements TypeConverter {
    public <T> T convertTo(Class<T> type, Object value) {
        if (type.isEnum() && value != null) {
            String text = value.toString();
            Method method = null;
            try {
                method = type.getMethod("valueOf", String.class);
            }
            catch (NoSuchMethodException e) {
                throw new RuntimeCamelException("Could not find valueOf method on enum type: " + type.getName());
            }
            return (T) ObjectHelper.invokeMethod(method, null, text);
        }
        return null;
    }
}