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
package org.apache.camel.spi;

import java.util.Set;

import org.apache.camel.Expression;
import org.apache.camel.StaticService;

/**
 * Registry for custom simple functions.
 */
public interface SimpleFunctionRegistry extends StaticService {

    /**
     * Add a custom simple function
     *
     * @param name       name of custom simple function
     * @param expression the expression to use as the function
     */
    void addFunction(String name, Expression expression);

    /**
     * Add a custom simple function
     *
     * @param function the function to add
     */
    void addFunction(SimpleFunction function);

    /**
     * Remove a custom simple function
     *
     * @param name name of function
     */
    void removeFunction(String name);

    /**
     * Gets the function (will resolve custom functions from registry)
     *
     * @param  name name of function
     * @return      the function, or <tt>null</tt> if no function exists
     */
    Expression getFunction(String name);

    /**
     * Returns a set with all the custom function names currently in use
     */
    Set<String> getCustomFunctionNames();

    /**
     * Returns a set with all the core/built-in function names
     */
    Set<String> getCoreFunctionNames();

    /**
     * Number of custom functions currently in use
     */
    int customSize();

    /**
     * Number of core functions
     */
    int coreSize();

}
