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
package org.apache.camel;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * An exception thrown if a value could not be converted to the required type
 */
public class NoTypeConversionAvailableException extends CamelException {

    private final transient @Nullable Object value;
    private final transient Class<?> type;

    public NoTypeConversionAvailableException(@Nullable Object value, Class<?> type) {
        super(createMessage(value, Objects.requireNonNull(type, "type")));
        this.value = value;
        this.type = type;
    }

    public NoTypeConversionAvailableException(@Nullable Object value, Class<?> type, Throwable cause) {
        super(createMessage(value, Objects.requireNonNull(type, "type"), Objects.requireNonNull(cause, "cause")), cause);
        this.value = value;
        this.type = type;
    }

    /**
     * Returns the value which could not be converted
     */
    public @Nullable Object getValue() {
        return value;
    }

    /**
     * Returns the required <tt>to</tt> type
     */
    public Class<?> getToType() {
        return type;
    }

    /**
     * Returns the required <tt>from</tt> type. Returns <tt>null</tt> if the provided value was null.
     */
    public @Nullable Class<?> getFromType() {
        if (value != null) {
            return value.getClass();
        } else {
            return null;
        }
    }

    /**
     * Returns an error message for no type converter available.
     */
    public static String createMessage(@Nullable Object value, Class<?> type) {
        Objects.requireNonNull(type, "type");
        return "No type converter available to convert from type: "
               + (value != null ? value.getClass().getCanonicalName() : null)
               + " to the required type: " + type.getCanonicalName();
    }

    /**
     * Returns an error message for no type converter available with the cause.
     */
    public static String createMessage(@Nullable Object value, Class<?> type, Throwable cause) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(cause, "cause");
        return "Converting Exception when converting from type: "
               + (value != null ? value.getClass().getCanonicalName() : null) + " to the required type: "
               + type.getCanonicalName() + ", which is caused by " + cause;
    }
}
