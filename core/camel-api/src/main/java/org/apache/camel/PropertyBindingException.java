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
 * Error binding property to a bean.
 *
 * @since 3.0
 */
public class PropertyBindingException extends RuntimeCamelException {

    private static final int MAX_CAUSE_DEPTH = 100;

    private final Object target;
    private final @Nullable String propertyName;
    private final @Nullable Object value;
    private final @Nullable String optionPrefix;
    private final @Nullable String optionKey;

    /**
     * @param target       the target bean on which property binding failed
     * @param propertyName the name of the property that could not be bound, or {@code null}
     * @param value        the value that could not be bound, or {@code null}
     */
    public PropertyBindingException(Object target, @Nullable String propertyName, @Nullable Object value) {
        this.target = Objects.requireNonNull(target, "target");
        this.propertyName = propertyName;
        this.value = value;
        this.optionPrefix = null;
        this.optionKey = null;
    }

    /**
     * @param target       the target bean on which property binding failed
     * @param propertyName the name of the property that could not be bound, or {@code null}
     * @param value        the value that could not be bound, or {@code null}
     * @param e            the cause of the failure
     */
    public PropertyBindingException(Object target, @Nullable String propertyName, @Nullable Object value, Throwable e) {
        initCause(Objects.requireNonNull(e, "e"));
        this.target = Objects.requireNonNull(target, "target");
        this.propertyName = propertyName;
        this.value = value;
        this.optionPrefix = null;
        this.optionKey = null;
    }

    /**
     * @param target the target bean on which property binding failed
     * @param e      the cause of the failure
     */
    public PropertyBindingException(Object target, Throwable e) {
        initCause(Objects.requireNonNull(e, "e"));
        this.target = Objects.requireNonNull(target, "target");
        this.propertyName = null;
        this.value = null;
        this.optionPrefix = null;
        this.optionKey = null;
    }

    /**
     * @param target       the target bean on which property binding failed
     * @param propertyName the name of the property that could not be bound, or {@code null}
     * @param value        the value that could not be bound, or {@code null}
     * @param optionPrefix the option prefix used when resolving the property, or {@code null}
     * @param optionKey    the option key used when resolving the property, or {@code null}
     * @param e            the cause of the failure
     */
    public PropertyBindingException(Object target, @Nullable String propertyName, @Nullable Object value,
                                    @Nullable String optionPrefix, @Nullable String optionKey, Throwable e) {
        initCause(Objects.requireNonNull(e, "e"));
        this.target = Objects.requireNonNull(target, "target");
        this.propertyName = propertyName;
        this.value = value;
        this.optionPrefix = optionPrefix;
        this.optionKey = optionKey;
    }

    @Override
    public String getMessage() {
        String stringValue = value != null ? value.toString() : "";
        String key = propertyName;
        if (optionPrefix != null && optionKey != null) {
            key = optionPrefix.endsWith(".") ? optionPrefix + optionKey : optionPrefix + "." + optionKey;
        }
        String reason = rootCauseMessage();
        if (key != null) {
            return "Error binding property (" + key + "=" + stringValue + ") with name: " + propertyName
                   + " on bean: " + target + " with value: " + stringValue + (reason != null ? ": " + reason : "");
        } else {
            return "Error binding properties on bean: " + target + (reason != null ? ": " + reason : "");
        }
    }

    /**
     * The message of the deepest cause, the reason the binding failed (host must be an absolute URI, no type converter
     * available): the first line is what a person reads, and it said only that the binding failed (CAMEL-24836).
     */
    private @Nullable String rootCauseMessage() {
        Throwable t = getCause();
        Throwable deepest = null;
        // bounded: a cause chain assembled outside initCause (a getCause override, deserialization) may loop
        for (int i = 0; t != null && i < MAX_CAUSE_DEPTH; i++) {
            deepest = t;
            t = t.getCause();
        }
        if (deepest == null || deepest instanceof PropertyBindingException) {
            return null;
        }
        String msg = deepest.getMessage();
        return msg != null && !msg.isBlank() ? msg.trim() : null;
    }

    public Object getTarget() {
        return target;
    }

    public @Nullable String getPropertyName() {
        return propertyName;
    }

    public @Nullable Object getValue() {
        return value;
    }

    public @Nullable String getOptionPrefix() {
        return optionPrefix;
    }

    public @Nullable String getOptionKey() {
        return optionKey;
    }

}
