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

import java.lang.reflect.Method;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;

public class ParameterBindingException extends RuntimeCamelException {

    private static final long serialVersionUID = 1L;
    private final Method method;
    private final int index;
    private final Class<?> parameterType;
    private final Object parameterValue;

    public ParameterBindingException(Throwable cause, Method method, int index, Class<?> parameterType, Object parameterValue) {
        super(createMessage(method, index, parameterType, parameterValue), cause);
        this.method = method;
        this.index = index;
        this.parameterType = parameterType;
        this.parameterValue = parameterValue;
    }

    public Method getMethod() {
        return method;
    }

    public int getIndex() {
        return index;
    }

    public Class<?> getParameterType() {
        return parameterType;
    }

    public Object getParameterValue() {
        return parameterValue;
    }

    private static String createMessage(Method method, int index, Class<?> parameterType, Object parameterValue) {
        return "Error during parameter binding on method: " + method + " at parameter #" + index + " with type: " + parameterType
                + " with value type: " + ObjectHelper.type(parameterValue) + " and value: " + parameterValue;
    }
}
