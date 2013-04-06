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
package org.apache.camel.web.resources;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.camel.impl.converter.DefaultTypeConverter;

/**
 *
 */
public class ConvertersResource extends CamelChildResourceSupport {
    public ConvertersResource(CamelContextResource contextResource) {
        super(contextResource);
    }

    public Map<String, Class<?>> getFromClassTypes() {
        Map<String, Class<?>> answer = new TreeMap<String, Class<?>>();
        DefaultTypeConverter converter = getDefaultTypeConverter();
        if (converter != null) {
            Set<Class<?>> classes = converter.getFromClassMappings();
            for (Class<?> aClass : classes) {
                String name = nameOf(aClass);
                answer.put(name, aClass);
            }
        }
        return answer;
    }


    /**
     * Returns type converters from the given type
     */
    @Path("{type}")
/*
    TODO this doesn't work in JAX-RS yet

    public ConvertersFromResource getConvertersFrom(@PathParam("type") Class type) {
*/

    public ConvertersFromResource getConvertersFrom(@PathParam("type") String typeName) {
        Class<?> type = getCamelContext().getClassResolver().resolveClass(typeName, getClass().getClassLoader());
        if (type == null) {
            return null;
        }
        return new ConvertersFromResource(getContextResource(), type);
    }


    public static String nameOf(Class<?> aClass) {
        return aClass.getCanonicalName();
    }
}
