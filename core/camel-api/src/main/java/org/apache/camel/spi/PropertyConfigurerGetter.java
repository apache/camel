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

import java.util.Map;

/**
 * A marker interface to identify the object as being a configurer which can
 * provide details about the options the configurer supports.
 * <p/>
 * This is used in Camel to have fast property configuration of Camel components & endpoints,
 * and for EIP patterns as well.
 *
 * @see PropertyConfigurer
 */
public interface PropertyConfigurerGetter {

    /**
     * Provides a map of which options the cofigurer supports and their class type.
     *
     * @param target  the target instance such as {@link org.apache.camel.Endpoint} or {@link org.apache.camel.Component}.
     * @return configurable options from the target as a Map name -> class type.
     */
    Map<String, Object> getAllOptions(Object target);

    /**
     * Gets the property value
     *
     * @param target  the target instance such as {@link org.apache.camel.Endpoint} or {@link org.apache.camel.Component}.
     * @param name          the property name
     * @param ignoreCase    whether to ignore case for matching the property name
     * @return the property value
     */
    Object getOptionValue(Object target, String name, boolean ignoreCase);

}
