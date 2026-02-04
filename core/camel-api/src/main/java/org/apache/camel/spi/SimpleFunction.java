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

import org.apache.camel.Exchange;

/**
 * A custom simple language function
 *
 * This allows to plugin custom functions to the built-in simple language.
 */
public interface SimpleFunction {

    /**
     * The name of the function.
     *
     * Notice the name must not clash with any of the built-in function names.
     */
    String getName();

    /**
     * Applies the function to the given input
     *
     * @param  exchange  the current exchange
     * @param  input     the input object, can be null
     * @return           the response
     * @throws Exception can be thrown if there was an error
     */
    Object apply(Exchange exchange, Object input) throws Exception;

    /**
     * Whether this custom function allows null as input value.
     *
     * This is default false to avoid {@link NullPointerException} and how the built-in functions also behaves.
     */
    default boolean allowNull() {
        return false;
    }

}
