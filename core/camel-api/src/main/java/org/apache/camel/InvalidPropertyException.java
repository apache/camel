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
 * An exception caused when an invalid property name is used on an object
 */
public class InvalidPropertyException extends RuntimeCamelException {

    private final transient @Nullable Object owner;
    private final String propertyName;

    /**
     * @param owner        the object that does not have the property
     * @param propertyName the name of the property that could not be found
     */
    public InvalidPropertyException(@Nullable Object owner, String propertyName) {
        this(owner, propertyName, owner != null ? owner.getClass() : Object.class);
    }

    /**
     * @param owner        the object that does not have the property
     * @param propertyName the name of the property that could not be found
     * @param type         the type of the object being inspected
     */
    public InvalidPropertyException(@Nullable Object owner, String propertyName, Class<?> type) {
        super("No '" + Objects.requireNonNull(propertyName, "propertyName") + "' property available on type: "
              + Objects.requireNonNull(type, "type").getName() + " in: " + owner);
        this.owner = owner;
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public @Nullable Object getOwner() {
        return owner;
    }
}
