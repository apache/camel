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
package org.apache.camel.dsl.java.joor;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CompilePostProcessor;

public class JavaJoorClassLoader extends ClassLoader implements CompilePostProcessor {

    private final Map<String, Class<?>> classes = new HashMap<>();

    public JavaJoorClassLoader() {
        super(JavaJoorClassLoader.class.getClassLoader());
    }

    @Override
    protected Class<?> findClass(String name) {
        return classes.get(name);
    }

    @Override
    public void postCompile(CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance) {
        if (name != null && clazz != null) {
            classes.put(name, clazz);
        }
    }

}
