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
 * A marker interface to identify the object as being configurable via a configurer class.
 * <p/>
 * This is used in Camel to have fast property configuration of Camel components & endpoints,
 * and for EIP patterns as well.
 *
 * @see PropertyConfigurerGetter
 */
public interface PropertyConfigurer {

    /**
     * Configures the property
     *
     * @param camelContext  the Camel context
     * @param target        the target instance such as {@link org.apache.camel.Endpoint} or {@link org.apache.camel.Component}.
     * @param name          the property name
     * @param value         the property value
     * @param ignoreCase    whether to ignore case for matching the property name
     * @return <tt>true</tt> if the configurer configured the property, <tt>false</tt> if the property does not exists
     */
    boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase);

}
