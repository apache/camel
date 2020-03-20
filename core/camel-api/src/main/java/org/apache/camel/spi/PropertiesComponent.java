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

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;

import org.apache.camel.StaticService;

/**
 * Component for property placeholders and loading properties from sources
 * (such as .properties file from classpath or file system)
 */
public interface PropertiesComponent extends StaticService {

    /**
     * Service factory key.
     */
    String FACTORY = "properties-component-factory";

    /**
     * The prefix token.
     */
    String PREFIX_TOKEN = "{{";

    /**
     * The suffix token.
     */
    String SUFFIX_TOKEN = "}}";

    /**
     * Parses the input text and resolve all property placeholders from within the text.
     *
     * @param uri  input text
     * @return text with resolved property placeholders
     * @throws IllegalArgumentException is thrown if error during parsing
     */
    String parseUri(String uri);

    /**
     * Looks up the property with the given key
     *
     * @param key  the name of the property
     * @return the property value if present
     */
    Optional<String> resolveProperty(String key);

    /**
     * Loads the properties from the default locations and sources.
     *
     * @return the properties loaded.
     */
    Properties loadProperties();

    /**
     * Loads the properties from the default locations and sources filtering them out according to a predicate.
     * </p>
     * <pre>{@code
     *     PropertiesComponent pc = getPropertiesComponent();
     *     Properties props = pc.loadProperties(key -> key.startsWith("camel.component.seda"));
     * }</pre>
     *
     * @param filter the predicate used to filter out properties based on the key.
     * @return the properties loaded.
     */
    Properties loadProperties(Predicate<String> filter);

    /**
     * Gets the configured properties locations.
     * This may be empty if the properties component has only been configured with {@link PropertiesSource}.
     */
    List<String> getLocations();

    /**
     * A list of locations to load properties. You can use comma to separate multiple locations.
     * This option will override any default locations and only use the locations from this option.
     */
    void setLocation(String location);

    /**
     * Adds the list of locations to the current locations, where to load properties.
     * You can use comma to separate multiple locations.
     */
    void addLocation(String location);

    /**
     * Adds a custom {@link PropertiesSource} to use as source for loading and/or looking up property values.
     */
    void addPropertiesSource(PropertiesSource propertiesSource);

    /**
     * Registers the {@link PropertiesFunction} as a function to this component.
     */
    void addPropertiesFunction(PropertiesFunction function);

    /**
     * Whether to silently ignore if a location cannot be located, such as a properties file not found.
     */
    void setIgnoreMissingLocation(boolean ignoreMissingLocation);

    /**
     * Sets initial properties which will be added before any property locations are loaded.
     */
    void setInitialProperties(Properties initialProperties);

    /**
     * Sets a special list of override properties that take precedence
     * and will use first, if a property exist.
     */
    void setOverrideProperties(Properties overrideProperties);

    /**
     * Encoding to use when loading properties file from the file system or classpath.
     * <p/>
     * If no encoding has been set, then the properties files is loaded using ISO-8859-1 encoding (latin-1)
     * as documented by {@link java.util.Properties#load(java.io.InputStream)}
     * <p/>
     * Important you must set encoding before setting locations.
     */
    void setEncoding(String encoding);

}
