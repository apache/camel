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
package org.apache.camel.impl;

import java.lang.reflect.Field;

import org.apache.camel.spi.UriParam;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.JsonSchemaHelper;

import static org.apache.camel.util.StringQuoteHelper.doubleQuote;

/**
 * Represents the configuration of a URI query parameter value to allow type conversion
 * and better validation of the configuration of URIs and Endpoints
 */
@Deprecated
public class ParameterConfiguration {
    private final String name;
    private final Class<?> parameterType;

    public ParameterConfiguration(String name, Class<?> parameterType) {
        this.name = name;
        this.parameterType = parameterType;
    }

    @Override
    public String toString() {
        return "ParameterConfiguration[" + name + " on " + parameterType + "]";
    }

    /**
     * Returns the name of the parameter value
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the type of the parameter value
     */
    public Class<?> getParameterType() {
        return parameterType;
    }

    /**
     * Factory method to create a new ParameterConfiguration from a field
     */
    public static ParameterConfiguration newInstance(String name, Field field, UriParam uriParam) {
        return new AnnotatedParameterConfiguration(name, field.getType(), field);
    }

    /**
     * Returns the JSON format of this parameter configuration
     */
    public String toJson() {
        if (parameterType.isEnum()) {
            String typeName = "string";
            CollectionStringBuffer sb = new CollectionStringBuffer();
            for (Object value : parameterType.getEnumConstants()) {
                sb.append(doubleQuote(value.toString()));
            }
            return doubleQuote(name) + ": { \"type\": " + doubleQuote(typeName)
                    + ", \"javaType\": \"" + parameterType.getCanonicalName() + "\""
                    + ", \"enum\": [ " + sb.toString() + " ] }";
        } else if (parameterType.isArray()) {
            String typeName = "array";
            return doubleQuote(name) + ": { \"type\": " + doubleQuote(typeName)
                    + ", \"javaType\": \"" + parameterType.getCanonicalName() + "\" }";
        } else {
            String typeName = JsonSchemaHelper.getType(parameterType);
            return doubleQuote(name) + ": { \"type\": " + doubleQuote(typeName)
                    + ", \"javaType\": \"" + parameterType.getCanonicalName() + "\" }";
        }
    }
}
