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

import java.util.Properties;
import java.util.function.Predicate;

import org.apache.camel.Ordered;

/**
 * A {@link PropertiesSource} whose properties can be loaded all at once during initialization, such as a .properties
 * file.
 * <p/>
 * In addition to looking up single values, a loadable source can return its entire set of properties via
 * {@link #loadProperties()} (optionally filtered by key) and can re-read them from the location to support reloading.
 * Like any source it may implement {@link Ordered} to control precedence; the source with the highest precedence (the
 * lowest number) is used first.
 * <p/>
 * See <a href="https://camel.apache.org/manual/using-propertyplaceholder.html">Using PropertyPlaceholder</a> in the
 * Camel user manual.
 *
 * @see   PropertiesSource
 * @since 3.0
 */
public interface LoadablePropertiesSource extends PropertiesSource {

    /**
     * Loads the properties from the source
     *
     * @return the loaded properties
     */
    Properties loadProperties();

    /**
     * Loads the properties from the source filtering them out according to a predicate.
     *
     * @param  filter the predicate used to filter out properties based on the key.
     * @return        the properties loaded.
     */
    Properties loadProperties(Predicate<String> filter);

    /**
     * Re-loads the properties from the file location
     *
     * @param location the location of the properties
     */
    void reloadProperties(String location);
}
