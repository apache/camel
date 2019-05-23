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
 * Error binding property to a bean.
 */
public class PropertyBindingException extends RuntimeCamelException {

    private final Object target;
    private final String propertyName;

    public PropertyBindingException(Object target, String propertyName) {
        super("No such property: " + propertyName + " on bean: " + target);
        this.target = target;
        this.propertyName = propertyName;
    }

    public PropertyBindingException(Object target, String propertyName, Exception e) {
        super("Error binding property: " + propertyName + " on bean: " + target, e);
        this.target = target;
        this.propertyName = propertyName;
    }

    public PropertyBindingException(Object target, Exception e) {
        super("Error binding properties on bean: " + target, e);
        this.target = target;
        this.propertyName = null;
    }

    public Object getTarget() {
        return target;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
