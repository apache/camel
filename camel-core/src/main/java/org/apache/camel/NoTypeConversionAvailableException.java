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
package org.apache.camel;

/**
 * An exception thrown if a value could not be converted to the required type
 *
 * @version $Revision$
 */
public class NoTypeConversionAvailableException extends RuntimeCamelException {
    private final Object value;
    private final Class type;

    public NoTypeConversionAvailableException(Object value, Class type) {
        super("No type converter available to convert from type: " + (value != null ? value.getClass() : null)
              + " to the required type: " + type.getCanonicalName() + " with value " + value);
        this.value = value;
        this.type = type;
    }

    /**
     * Returns the value which could not be converted
     */
    public Object getValue() {
        return value;
    }

    /**
     * Returns the required <tt>to</tt> type
     */
    public Class getType() {
        return type;
    }

    /**
     * Returns the required <tt>from</tt> type.
     * Returns <tt>null</tt> if the provided value was null.
     */
    public Class getFromType() {
        if (value != null) {
            return value.getClass();
        } else {
            return null;
        }
    }

}
