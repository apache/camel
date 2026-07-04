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

import org.apache.camel.CamelContext;

/**
 * Configures a single property on a target object (such as a {@link org.apache.camel.Component},
 * {@link org.apache.camel.Endpoint}, or EIP) without using reflection.
 * <p/>
 * Camel generates a {@code PropertyConfigurer} implementation at build time for each configurable type, giving fast,
 * reflection-free property binding during bootstrap. The {@link #configure} method sets one named property and reports
 * whether the property was recognized, with optional case-insensitive name matching. Generated configurers implement
 * {@link GeneratedPropertyConfigurer}, types that expose their configurer implement {@link PropertyConfigurerAware},
 * and configurers that can additionally describe their options implement {@link PropertyConfigurerGetter}.
 * <p/>
 * See <a href="https://camel.apache.org/manual/property-binding.html">Property Binding</a> in the Camel user manual.
 *
 * @see   GeneratedPropertyConfigurer
 * @see   PropertyConfigurerGetter
 * @see   PropertyConfigurerAware
 * @since 3.0
 */
public interface PropertyConfigurer {

    /**
     * Configures the property
     *
     * @param  camelContext the Camel context
     * @param  target       the target instance such as {@link org.apache.camel.Endpoint} or
     *                      {@link org.apache.camel.Component}.
     * @param  name         the property name
     * @param  value        the property value
     * @param  ignoreCase   whether to ignore case for matching the property name
     * @return              <tt>true</tt> if the configurer configured the property, <tt>false</tt> if the property does
     *                      not exists
     */
    boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase);

}
