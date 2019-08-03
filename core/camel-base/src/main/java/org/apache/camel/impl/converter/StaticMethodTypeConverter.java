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
package org.apache.camel.impl.converter;

import java.lang.reflect.Method;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.TypeConverterSupport;

/**
 * A {@link TypeConverter} implementation which invokes a static method to convert from a type to another type
 */
public class StaticMethodTypeConverter extends TypeConverterSupport {
    private final Method method;
    private final boolean useExchange;
    private final boolean allowNull;

    public StaticMethodTypeConverter(Method method, boolean allowNull) {
        this.method = method;
        this.useExchange = method.getParameterCount() == 2;
        this.allowNull = allowNull;
    }

    @Override
    public String toString() {
        return "StaticMethodTypeConverter: " + method;
    }

    @Override
    public boolean allowNull() {
        return allowNull;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        return useExchange ? (T)ObjectHelper.invokeMethod(method, null, value, exchange)
            : (T) ObjectHelper.invokeMethod(method, null, value);
    }

}
