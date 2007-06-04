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

import org.apache.camel.Converter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Some core java.lang based
 * <a href="http://activemq.apache.org/camel/type-converter.html">Type Converters</a>
 *
 * @version $Revision$
 */
@Converter
public class ObjectConverter {
    public static boolean isCollection(Object value) {
        // TODO we should handle primitive array types?
        return value instanceof Collection || (value != null && value.getClass().isArray());
    }

    /**
     * Creates an iterator over the value if the value is a collection, an Object[] or a primitive type array; otherwise
     * to simplify the caller's code, we just create a singleton collection iterator over a single value
     */
    @Converter
    public static Iterator iterator(Object value) {
        if (value == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        else if (value instanceof Collection) {
            Collection collection = (Collection) value;
            return collection.iterator();
        }
        else if (value.getClass().isArray()) {
            // TODO we should handle primitive array types?
            return Arrays.asList(value).iterator();
        }
        else {
            return Collections.singletonList(value).iterator();
        }
    }

    /**
     * Converts the given value to a boolean, handling strings or Boolean objects;
     * otherwise returning false if the value could not be converted to a boolean
     */
    @Converter
    public static boolean toBool(Object value) {
        Boolean answer = toBoolean(value);
        if (answer != null) {
            return answer.booleanValue();
        }
        return false;
    }

    /**
     * Converts the given value to a Boolean, handling strings or Boolean objects;
     * otherwise returning null if the value cannot be converted to a boolean
     */
    @Converter
    public static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return "true".equalsIgnoreCase(value.toString()) ? Boolean.TRUE : Boolean.FALSE;
        }
        return null;
    }
}
