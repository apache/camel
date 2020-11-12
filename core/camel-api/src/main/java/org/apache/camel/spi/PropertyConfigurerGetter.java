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

/**
 * A marker interface to identify the object as being a configurer which can provide details about the options the
 * configurer supports.
 * <p/>
 * This is used in Camel to have fast property configuration of Camel components & endpoints, and for EIP patterns as
 * well.
 *
 * @see PropertyConfigurer
 * @see ExtendedPropertyConfigurerGetter
 */
public interface PropertyConfigurerGetter {

    /**
     * Gets the option class type.
     *
     * @param  name the property name
     * @return      the class type, or <tt>null</tt> if no option exists with the name
     */
    Class<?> getOptionType(String name, boolean ignoreCase);

    /**
     * Gets the names of the autowired options.
     *
     * @return the names as an array, or null if there are no autowire options.
     */
    default String[] getAutowiredNames() {
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
    default Object getCollectionValueType(Object target, String name, boolean ignoreCase) {
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
    Object getOptionValue(Object target, String name, boolean ignoreCase);

}
