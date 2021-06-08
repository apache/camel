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
package org.apache.camel.test.junit5.properties;

import java.lang.reflect.Field;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;

import static org.junit.platform.commons.util.ReflectionUtils.makeAccessible;

public class TestPropertiesSource implements org.apache.camel.spi.PropertiesSource {

    private final CamelContext context;
    private final Object instance;

    public TestPropertiesSource(CamelContext context, Object instance) {
        this.context = context;
        this.instance = instance;
    }

    @Override
    public String getName() {
        return "TestPropertiesSource[" + instance + "]";
    }

    @Override
    public String getProperty(String name) {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    try {
                        Object value = makeAccessible(f).get(instance);
                        if (value != null) {
                            return context.getTypeConverter().mandatoryConvertTo(String.class, value);
                        }
                    } catch (Throwable t) {
                        throw new RuntimeCamelException("Unable to retrieve property " + name + " from field " + f, t);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

}
