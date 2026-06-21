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

import org.jspecify.annotations.Nullable;

/**
 * Extension of the configurer SPI that, in addition to setting properties, can describe the options a target supports.
 * <p/>
 * Where {@link PropertyConfigurer} only writes property values, a getter can report each option's type
 * ({@link #getOptionType}), read the current value ({@link #getOptionValue}), resolve the element type of collection
 * options ({@link #getCollectionValueType}), and list autowired options ({@link #getAutowiredNames}). Generated
 * configurers typically implement this so tooling and the property-binding engine can introspect components and
 * endpoints. {@link ExtendedPropertyConfigurerGetter} adds enumeration of all options.
 * <p/>
 * See <a href="https://camel.apache.org/manual/property-binding.html">Property Binding</a> in the Camel user manual.
 *
 * @see   PropertyConfigurer
 * @see   ExtendedPropertyConfigurerGetter
 * @since 3.2
 */
public interface PropertyConfigurerGetter {

    /**
     * Gets the option class type.
     *
     * @param  name       the property name
     * @param  ignoreCase whether to ignore case for matching the property name
     * @return            the class type, or <tt>null</tt> if no option exists with the name
     */
    @Nullable
    Class<?> getOptionType(String name, boolean ignoreCase);

    /**
     * Gets the names of the autowired options.
     *
     * @return the names as an array, or null if there are no autowire options.
     */
    default String @Nullable [] getAutowiredNames() {
        return null;
    }

    /**
     * This method can be used to retrieve the class type for an option if the option is a collection kind (list, map,
     * or array). For maps, then the nested type returned is the type of the value in the map (not the map key type).
     *
     * @param  target     the target instance such as {@link org.apache.camel.Endpoint} or
     *                    {@link org.apache.camel.Component}.
     * @param  name       the property name
     * @param  ignoreCase whether to ignore case for matching the property name
     * @return            the class type, or <tt>null</tt> if the option is not a collection kind or not possible to
     *                    determine
     */
    default @Nullable Object getCollectionValueType(Object target, String name, boolean ignoreCase) {
        return null;
    }

    /**
     * Gets the property value
     *
     * @param  target     the target instance such as {@link org.apache.camel.Endpoint} or
     *                    {@link org.apache.camel.Component}.
     * @param  name       the property name
     * @param  ignoreCase whether to ignore case for matching the property name
     * @return            the property value
     */
    @Nullable
    Object getOptionValue(Object target, String name, boolean ignoreCase);

}
