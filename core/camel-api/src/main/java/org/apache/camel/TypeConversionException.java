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
 * Exception when failing during type conversion.
 */
public class TypeConversionException extends RuntimeCamelException {

    private final transient @Nullable Object value;
    private final transient Class<?> type;

    /**
     * @param value the value that could not be converted
     * @param type  the expected target type
     * @param cause the cause of the failure
     */
    public TypeConversionException(@Nullable Object value, Class<?> type, Throwable cause) {
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
     * Returns an error message for type conversion failed.
     */
    public static String createMessage(@Nullable Object value, Class<?> type, Throwable cause) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(cause, "cause");
        return "Error during type conversion from type: " + (value != null ? value.getClass().getCanonicalName() : null)
               + " to the required type: " + type.getCanonicalName() + " with value " + value + " due to "
               + cause.getClass().getName() + ": " + cause.getMessage();
    }

}
