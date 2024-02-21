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
package org.apache.camel.component.properties;

import java.util.Map;

import org.apache.camel.spi.PropertiesFunction;

/**
 * Resolver for built-in and custom {@link PropertiesFunction}.
 */
public interface PropertiesFunctionResolver {

    String RESOURCE_PATH = "META-INF/services/org/apache/camel/properties-function/";

    /**
     * Registers the {@link PropertiesFunction} as a function to this component.
     */
    void addPropertiesFunction(PropertiesFunction function);

    /**
     * Gets the functions registered in this properties component.
     */
    Map<String, PropertiesFunction> getFunctions();

    /**
     * Is there a {@link PropertiesFunction} with the given name?
     */
    boolean hasFunction(String name);

    /**
     * Resolves the properties function with the given name
     *
     * @param  name the name of the properties function
     * @return      the function or <tt>null</tt> if not found
     */
    PropertiesFunction resolvePropertiesFunction(String name);

}
