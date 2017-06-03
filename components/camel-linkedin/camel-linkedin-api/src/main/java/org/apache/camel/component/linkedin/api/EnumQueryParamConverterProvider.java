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
package org.apache.camel.component.linkedin.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converter provider for Enum Query params.
 */
@Provider
public class EnumQueryParamConverterProvider<T extends Enum<T>> implements ParamConverterProvider {
    private static final Logger LOG = LoggerFactory.getLogger(EnumQueryParamConverterProvider.class);

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType.isEnum()) {
            try {
                final Method valueMethod = rawType.getMethod("value", null);
                final Method fromValueMethod = rawType.getMethod("fromValue", String.class);

                return new ParamConverter<T>() {
                    @Override
                    public T fromString(String value) {
                        try {
                            return (T) fromValueMethod.invoke(null, value);
                        } catch (IllegalAccessException e) {
                            throw new IllegalArgumentException(e);
                        } catch (InvocationTargetException e) {
                            throw new IllegalArgumentException(e);
                        }
                    }

                    @Override
                    public String toString(T value) {
                        try {
                            return (String) valueMethod.invoke(value);
                        } catch (IllegalAccessException e) {
                            throw new IllegalArgumentException(e);
                        } catch (InvocationTargetException e) {
                            throw new IllegalArgumentException(e);
                        }
                    }
                };
            } catch (NoSuchMethodException e) {
                LOG.debug("Enumeration {} does not follow JAXB convention for conversion", rawType.getName());
            }
        }
        return null;
    }
}
