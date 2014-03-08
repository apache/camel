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
import java.util.TreeMap;

import org.apache.camel.TypeConverter;
import org.apache.camel.impl.converter.DefaultTypeConverter;

/**
 *
 */
public class ConvertersFromResource extends CamelChildResourceSupport {
    private Class<?> type;

    public ConvertersFromResource(CamelContextResource contextResource, Class<?> type) {
        super(contextResource);
        this.type = type;
    }

    public Map<String, TypeConverter> getConverters() {
        Map<String, TypeConverter> answer = new TreeMap<String, TypeConverter>();
        DefaultTypeConverter converter = getDefaultTypeConverter();
        if (converter != null) {
            Map<Class<?>, TypeConverter> classes = converter.getToClassMappings(type);
            for (Map.Entry<Class<?>, TypeConverter> entry : classes.entrySet()) {
                Class<?> aClass = entry.getKey();
                String name = ConvertersResource.nameOf(aClass);
                answer.put(name, entry.getValue());
            }
        }
        return answer;
    }

    public Class<?> getType() {
        return type;
    }
}
