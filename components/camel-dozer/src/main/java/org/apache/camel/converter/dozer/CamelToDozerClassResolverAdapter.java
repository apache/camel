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
package org.apache.camel.converter.dozer;

import java.net.URL;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultClassResolver;
import org.apache.camel.spi.ClassResolver;
import org.dozer.util.DozerClassLoader;

public final class CamelToDozerClassResolverAdapter implements DozerClassLoader {

    private final ClassResolver classResolver;

    public CamelToDozerClassResolverAdapter() {
        // must have a default nor-arg constructor to allow Dozer to work with OSGi
        classResolver = new DefaultClassResolver();
    }

    public CamelToDozerClassResolverAdapter(CamelContext camelContext) {
        classResolver = camelContext.getClassResolver();
    }

    public Class<?> loadClass(String name) {
        return classResolver.resolveClass(name);
    }

    public URL loadResource(String name) {
        return DozerTypeConverterLoader.loadMappingFile(classResolver, name);
    }
}
