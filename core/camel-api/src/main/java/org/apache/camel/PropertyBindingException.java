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

    private final Object target;
    private final @Nullable String propertyName;
    private final @Nullable Object value;
    private final @Nullable String optionPrefix;
    private final @Nullable String optionKey;

    public PropertyBindingException(Object target, @Nullable String propertyName, @Nullable Object value) {
        this.target = Objects.requireNonNull(target, "target");
        this.propertyName = propertyName;
        this.value = value;
        this.optionPrefix = null;
        this.optionKey = null;
    }

    public PropertyBindingException(Object target, @Nullable String propertyName, @Nullable Object value, Throwable e) {
        initCause(Objects.requireNonNull(e, "e"));
        this.target = Objects.requireNonNull(target, "target");
        this.propertyName = propertyName;
        this.value = value;
        this.optionPrefix = null;
        this.optionKey = null;
    }

    public PropertyBindingException(Object target, Throwable e) {
        initCause(Objects.requireNonNull(e, "e"));
        this.target = Objects.requireNonNull(target, "target");
        this.propertyName = null;
        this.value = null;
        this.optionPrefix = null;
        this.optionKey = null;
    }

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
        if (key != null) {
            return "Error binding property (" + key + "=" + stringValue + ") with name: " + propertyName
                   + " on bean: " + target + " with value: " + stringValue;
        } else {
            return "Error binding properties on bean: " + target;
        }
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
