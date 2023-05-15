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
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.camel.PropertiesLookupListener;
import org.apache.camel.StaticService;

/**
 * Component for property placeholders and loading properties from sources (such as .properties file from classpath or
 * file system)
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
     * The token for marking a placeholder as optional
     */
    String OPTIONAL_TOKEN = "?";

    /**
     * The prefix and optional tokens
     */
    String PREFIX_OPTIONAL_TOKEN = PREFIX_TOKEN + OPTIONAL_TOKEN;

    /**
     * Parses the input text and resolve all property placeholders from within the text.
     *
     * @param  uri                      input text
     * @return                          text with resolved property placeholders
     * @throws IllegalArgumentException is thrown if error during parsing
     */
    String parseUri(String uri);

    /**
     * Parses the input text and resolve all property placeholders from within the text.
     *
     * @param  uri                      input text
     * @param  keepUnresolvedOptional   whether to keep placeholders that are optional and was unresolved
     * @return                          text with resolved property placeholders
     * @throws IllegalArgumentException is thrown if error during parsing
     */
    String parseUri(String uri, boolean keepUnresolvedOptional);

    /**
     * Looks up the property with the given key
     *
     * @param  key the name of the property
     * @return     the property value if present
     */
    Optional<String> resolveProperty(String key);

    /**
     * Loads the properties from the default locations and sources.
     *
     * @return the properties loaded.
     */
    Properties loadProperties();

    /**
     * Loads the properties from the default locations and sources.
     *
     * @return a {@link Map} representing the properties loaded.
     */
    @SuppressWarnings("unchecked")
    default Map<String, Object> loadPropertiesAsMap() {
        return (Map) loadProperties();
    }

    /**
     * Loads the properties from the default locations and sources filtering them out according to a predicate.
     *
     * <pre>
     * PropertiesComponent pc = getPropertiesComponent();
     * Properties props = pc.loadProperties(key -> key.startsWith("camel.component.seda"));
     * </pre>
     *
     * @param  filter the predicate used to filter out properties based on the key.
     * @return        the properties loaded.
     */
    Properties loadProperties(Predicate<String> filter);

    /**
     * Loads the properties from the default locations and sources filtering them out according to a predicate, and maps
     * the key using the key mapper.
     *
     * <pre>
     * PropertiesComponent pc = getPropertiesComponent();
     * Properties props = pc.loadProperties(key -> key.startsWith("camel.component.seda"), StringHelper::dashToCamelCase);
     * </pre>
     *
     * @param  filter    the predicate used to filter out properties based on the key.
     * @param  keyMapper to map keys
     * @return           the properties loaded.
     */
    Properties loadProperties(Predicate<String> filter, Function<String, String> keyMapper);

    /**
     * Loads the properties from the default locations and sources filtering them out according to a predicate.
     *
     * <pre>
     * PropertiesComponent pc = getPropertiesComponent();
     * Map props = pc.loadPropertiesAsMap(key -> key.startsWith("camel.component.seda"));
     * </pre>
     *
     * @param  filter the predicate used to filter out properties based on the key.
     * @return        a {@link Map} representing the properties loaded.
     */
    @SuppressWarnings("unchecked")
    default Map<String, Object> loadPropertiesAsMap(Predicate<String> filter) {
        return (Map) loadProperties(filter);
    }

    /**
     * Gets the configured properties locations. This may be empty if the properties component has only been configured
     * with {@link PropertiesSource}.
     */
    List<String> getLocations();

    /**
     * A list of locations to load properties. You can use comma to separate multiple locations. This option will
     * override any default locations and only use the locations from this option.
     */
    void setLocation(String location);

    /**
     * Adds the list of locations to the current locations, where to load properties. You can use comma to separate
     * multiple locations.
     */
    void addLocation(String location);

    /**
     * Gets the {@link PropertiesSourceFactory}.
     */
    PropertiesSourceFactory getPropertiesSourceFactory();

    /**
     * Adds a custom {@link PropertiesSource} to use as source for loading and/or looking up property values.
     */
    void addPropertiesSource(PropertiesSource propertiesSource);

    /**
     * Gets the custom {@link PropertiesSource} by the name
     *
     * @param  name the name of the source
     * @return      the source, or null if no source exists
     */
    PropertiesSource getPropertiesSource(String name);

    /**
     * Gets the properties sources
     */
    List<PropertiesSource> getPropertiesSources();

    /**
     * Registers the {@link PropertiesFunction} as a function to this component.
     */
    void addPropertiesFunction(PropertiesFunction function);

    /**
     * Gets the {@link PropertiesFunction} by the given name
     *
     * @param  name the function name
     * @return      the function or null if no function exists
     */
    PropertiesFunction getPropertiesFunction(String name);

    /**
     * Is there a {@link PropertiesFunction} with the given name?
     */
    boolean hasPropertiesFunction(String name);

    /**
     * Whether to silently ignore if a location cannot be located, such as a properties file not found.
     */
    void setIgnoreMissingLocation(boolean ignoreMissingLocation);

    /**
     * Whether to support nested property placeholders. A nested placeholder, means that a placeholder, has also a
     * placeholder, that should be resolved (recursively).
     */
    void setNestedPlaceholder(boolean nestedPlaceholder);

    /**
     * Sets initial properties which will be added before any property locations are loaded.
     */
    void setInitialProperties(Properties initialProperties);

    /**
     * Adds an initial property which will be added before any property locations are loaded.
     *
     * @param key   the key
     * @param value the value
     */
    void addInitialProperty(String key, String value);

    /**
     * Sets a special list of override properties that take precedence and will use first, if a property exist.
     */
    void setOverrideProperties(Properties overrideProperties);

    /**
     * Adds a special override property that take precedence and will use first, if a property exist.
     *
     * @param key   the key
     * @param value the value
     */
    void addOverrideProperty(String key, String value);

    /**
     * Sets a special list of local properties (ie thread local) that take precedence and will use first, if a property
     * exist.
     */
    void setLocalProperties(Properties localProperties);

    /**
     * Gets a list of properties that are local for the current thread only (ie thread local), or <tt>null</tt> if not
     * currently in use.
     */
    Properties getLocalProperties();

    /**
     * Gets a list of properties that are local for the current thread only (ie thread local), or <tt>null</tt> if not
     * currently in use.
     *
     * @return a {@link Map} representing the local properties, or <tt>null</tt> if not currently in use.
     */
    @SuppressWarnings("unchecked")
    default Map<String, Object> getLocalPropertiesAsMap() {
        return (Map) getLocalProperties();
    }

    /**
     * Encoding to use when loading properties file from the file system or classpath.
     * <p/>
     * If no encoding has been set, then the properties files is loaded using ISO-8859-1 encoding (latin-1) as
     * documented by {@link java.util.Properties#load(java.io.InputStream)}
     * <p/>
     * Important you must set encoding before setting locations.
     */
    void setEncoding(String encoding);

    /**
     * Reload properties from the given location patterns.
     *
     * @param  pattern patterns, or null to reload from all known locations
     * @return         true if some properties was reloaded
     */
    boolean reloadProperties(String pattern);

    /**
     * Filters the given list of properties, by removing properties that are already loaded and have same key and value.
     *
     * If all properties are not changed then the properties will become empty.
     *
     * @param properties the given properties to filter.
     */
    void keepOnlyChangeProperties(Properties properties);

    /**
     * Adds the {@link PropertiesLookupListener}.
     */
    void addPropertiesLookupListener(PropertiesLookupListener propertiesLookupListener);

}
