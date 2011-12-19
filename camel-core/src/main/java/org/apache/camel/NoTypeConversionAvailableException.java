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
 * @version 
 */
public class NoTypeConversionAvailableException extends CamelException {
    private static final long serialVersionUID = -8721487434390572636L;

    private final transient Object value;
    private final transient Class<?> type;

    public NoTypeConversionAvailableException(Object value, Class<?> type) {
        super(createMessage(value, type));
        this.value = value;
        this.type = type;
    }

    public NoTypeConversionAvailableException(Object value, Class<?> type, Throwable cause) {
        super(createMessage(value, type, cause), cause);
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
    public Class<?> getToType() {
        return type;
    }

    /**
     * Returns the required <tt>from</tt> type.
     * Returns <tt>null</tt> if the provided value was null.
     */
    public Class<?> getFromType() {
        if (value != null) {
            return value.getClass();
        } else {
            return null;
        }
    }

    /**
     * Returns an error message for no type converter available.
     */
    public static String createMessage(Object value, Class<?> type) {
        return "No type converter available to convert from type: " + (value != null ? value.getClass().getCanonicalName() : null)
              + " to the required type: " + type.getCanonicalName() + " with value " + value;
    }
    
    /**
     * Returns an error message for no type converter available with the cause.
     */
    public static String createMessage(Object value, Class<?> type, Throwable cause) {
        return "Converting Exception when converting from type: "
               + (value != null ? value.getClass().getCanonicalName() : null) + " to the required type: "
               + type.getCanonicalName() + " with value " + value + ", which is caused by " + cause;
    }
}
