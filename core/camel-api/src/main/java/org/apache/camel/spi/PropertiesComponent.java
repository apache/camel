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

import org.apache.camel.Component;
import org.apache.camel.StaticService;

/**
 * Component for property placeholders and loading properties from sources
 * (such as .properties file from classpath or file system)
 */
public interface PropertiesComponent extends Component, StaticService {

    /**
     * The prefix token.
     */
    String PREFIX_TOKEN = "{{";

    /**
     * The suffix token.
     */
    String SUFFIX_TOKEN = "}}";

    /**
     * Has the component been created as a default by {@link org.apache.camel.CamelContext} during starting up Camel.
     */
    String DEFAULT_CREATED = "PropertiesComponentDefaultCreated";

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
     * Loads the properties from the default locations.
     *
     * @return the properties loaded.
     */
    Properties loadProperties();

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

}
