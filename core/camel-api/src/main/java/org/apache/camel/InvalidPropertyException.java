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

/**
 * An exception caused when an invalid property name is used on an object
 */
public class InvalidPropertyException extends RuntimeCamelException {

    private final transient Object owner;
    private final String propertyName;

    public InvalidPropertyException(Object owner, String propertyName) {
        this(owner, propertyName, owner != null ? owner.getClass() : Object.class);
    }

    public InvalidPropertyException(Object owner, String propertyName, Class<?> type) {
        super("No '" + propertyName + "' property available on type: " + type.getName() + " in: " + owner);
        this.owner = owner;
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Object getOwner() {
        return owner;
    }
}
