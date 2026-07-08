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
import org.jspecify.annotations.Nullable;

/**
 * A source of property values for the Camel {@link PropertiesComponent} when resolving property placeholders.
 * <p/>
 * The properties component consults all registered sources (system properties, environment variables, .properties
 * files, beans, vaults, and so on) to resolve a placeholder. A source can implement {@link Ordered} to control its
 * precedence; the source with the highest precedence (the lowest number) is consulted first. Sources whose properties
 * can be loaded eagerly in bulk implement {@link LoadablePropertiesSource}, and the built-in sources are created via
 * {@link PropertiesSourceFactory}.
 * <p/>
 * See <a href="https://camel.apache.org/manual/using-propertyplaceholder.html">Using PropertyPlaceholder</a> in the
 * Camel user manual.
 *
 * @see   PropertiesComponent
 * @see   LoadablePropertiesSource
 * @see   PropertiesSourceFactory
 * @since 3.0
 */
public interface PropertiesSource {

    /**
     * The name of this properties source.
     *
     * @return the source name
     */
    String getName();

    /**
     * Gets the property with the name
     *
     * @param  name name of property
     * @return      the property value, or <tt>null</tt> if no property exists
     */
    @Nullable
    String getProperty(String name);

    /**
     * Gets the property with the name
     *
     * @param  name         name of property
     * @param  defaultValue default value to use as fallback
     * @return              the property value, or <tt>null</tt> if no property exists
     */
    default @Nullable String getProperty(String name, @Nullable String defaultValue) {
        return getProperty(name);
    }

}
