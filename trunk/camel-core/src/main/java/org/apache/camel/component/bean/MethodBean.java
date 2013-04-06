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
package org.apache.camel.component.bean;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * Represents a {@link Serializable} version of a {@link Method}
 *
 * @version 
 */
public class MethodBean implements Serializable {
    private static final long serialVersionUID = -789408217201706532L;

    private String name;
    private Class<?> type;
    private Class<?>[] parameterTypes;

    public MethodBean() {
    }

    public MethodBean(Method method) {
        this.name = method.getName();
        this.type = method.getDeclaringClass();
        this.parameterTypes = method.getParameterTypes();
    }

    public Method getMethod() throws NoSuchMethodException {
        return type.getMethod(name, parameterTypes);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Class<?> getType() {
        return type;
    }

    public void setType(Class<?> type) {
        this.type = type;
    }
}
