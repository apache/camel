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

import org.apache.camel.Ordered;

/**
 * A source for properties.
 * <p/>
 * A source can implement {@link Ordered} to control the ordering of which sources are used by the Camel properties
 * component. The source with the highest precedence (the lowest number) will be used first.
 */
public interface PropertiesSource {

    /**
     * Name of properties source
     */
    String getName();

    /**
     * Gets the property with the name
     *
     * @param  name name of property
     * @return      the property value, or <tt>null</tt> if no property exists
     */
    String getProperty(String name);

    /**
     * Gets the property with the name
     *
     * @param  name         name of property
     * @param  defaultValue default value to use as fallback
     * @return              the property value, or <tt>null</tt> if no property exists
     */
    default String getProperty(String name, String defaultValue) {
        return getProperty(name);
    }

}
