/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.impl.converter;

import org.apache.camel.TypeConverter;
import org.apache.camel.RuntimeCamelException;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/**
 * A {@link TypeConverter} implementation which instantiates an object
 * so that an instance method can be used as a type converter
 *
 * @version $Revision$
 */
public class InstanceMethodTypeConverter implements TypeConverter {
    private Object instance;
    private final TypeConverterRegistry repository;
    private final Class type;
    private final Method method;

    public InstanceMethodTypeConverter(TypeConverterRegistry repository, Class type, Method method) {
        this.repository = repository;
        this.type = type;
        this.method = method;
    }


    @Override
    public String toString() {
        return "InstanceMethodTypeConverter: " + method;
    }


    public synchronized <T> T convertTo(Class<T> type, Object value) {
        if (instance == null) {
            instance = createInstance();
            if (instance == null) {
                throw new RuntimeCamelException("Could not instantiate aninstance of: " + type.getName());
            }
        }
        try {
            return (T) method.invoke(instance, value);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeCamelException(e);
        }
        catch (InvocationTargetException e) {
            throw new RuntimeCamelException(e.getCause());
        }
    }

    protected Object createInstance() {
        return repository.getInjector().newInstance(type);
    }
}
