/**
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
package org.apache.camel.component.properties;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.FilePathResolver;
import org.apache.camel.util.LRUSoftCache;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/properties">Properties Component</a> allows you to use property placeholders when defining Endpoint URIs
 *
 * @version 
 */
public class PropertiesComponent extends DefaultComponent {

    /**
     * The default prefix token.
     */
    public static final String DEFAULT_PREFIX_TOKEN = "{{";
    
    /**
     * The default suffix token.
     */
    public static final String DEFAULT_SUFFIX_TOKEN = "}}";
    
    /**
     * The default prefix token.
     * @deprecated Use {@link #DEFAULT_PREFIX_TOKEN} instead.
     */
    @Deprecated
    public static final String PREFIX_TOKEN = DEFAULT_PREFIX_TOKEN;
    
    /**
     * The default suffix token.
     * @deprecated Use {@link #DEFAULT_SUFFIX_TOKEN} instead.
     */
    @Deprecated
    public static final String SUFFIX_TOKEN = DEFAULT_SUFFIX_TOKEN;

    /**
     *  Never check system properties.
     */
    public static final int SYSTEM_PROPERTIES_MODE_NEVER = 0;

    /**
     * Check system properties if not resolvable in the specified properties.
     */
    public static final int SYSTEM_PROPERTIES_MODE_FALLBACK = 1;

    /**
     * Check system properties first, before trying the specified properties.
     * This allows system properties to override any other property source.
     * <p/>
     * This is the default.
     */
    public static final int SYSTEM_PROPERTIES_MODE_OVERRIDE = 2;

    /**
     * Key for stores special override properties that containers such as OSGi can store
     * in the OSGi service registry
     */
    public static final String OVERRIDE_PROPERTIES = PropertiesComponent.class.getName() + ".OverrideProperties";

    private static final Logger LOG = LoggerFactory.getLogger(PropertiesComponent.class);
    private final Map<CacheKey, Properties> cacheMap = new LRUSoftCache<CacheKey, Properties>(1000);
    private final Map<String, PropertiesFunction> functions = new HashMap<String, PropertiesFunction>();
    private PropertiesResolver propertiesResolver = new DefaultPropertiesResolver(this);
    private PropertiesParser propertiesParser = new DefaultPropertiesParser(this);
    private String[] locations;
    private boolean ignoreMissingLocation;
    private String encoding;
    private boolean cache = true;
    private String propertyPrefix;
    private String propertyPrefixResolved;
    private String propertySuffix;
    private String propertySuffixResolved;
    private boolean fallbackToUnaugmentedProperty = true;
    private String prefixToken = DEFAULT_PREFIX_TOKEN;
    private String suffixToken = DEFAULT_SUFFIX_TOKEN;
    private Properties initialProperties;
    private Properties overrideProperties;
    private int systemPropertiesMode = SYSTEM_PROPERTIES_MODE_OVERRIDE;

    public PropertiesComponent() {
        // include out of the box functions
        addFunction(new EnvPropertiesFunction());
        addFunction(new SysPropertiesFunction());
        addFunction(new ServicePropertiesFunction());
        addFunction(new ServiceHostPropertiesFunction());
        addFunction(new ServicePortPropertiesFunction());
    }
    
    public PropertiesComponent(String location) {
        this();
        setLocation(location);
    }

    public PropertiesComponent(String... locations) {
        this();
        setLocations(locations);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String[] paths = locations;

        // override default locations
        String locations = getAndRemoveParameter(parameters, "locations", String.class);
        Boolean ignoreMissingLocationLoc = getAndRemoveParameter(parameters, "ignoreMissingLocation", Boolean.class);
        if (locations != null) {
            LOG.trace("Overriding default locations with location: {}", locations);
            paths = locations.split(",");
        }
        if (ignoreMissingLocationLoc != null) {
            ignoreMissingLocation = ignoreMissingLocationLoc;
        }

        String endpointUri = parseUri(remaining, paths);
        LOG.debug("Endpoint uri parsed as: {}", endpointUri);
        return getCamelContext().getEndpoint(endpointUri);
    }

    public String parseUri(String uri) throws Exception {
        return parseUri(uri, locations);
    }

    public String parseUri(String uri, String... paths) throws Exception {
        Properties prop = new Properties();

        // use initial properties
        if (null != initialProperties) {
            prop.putAll(initialProperties);
        }

        // use locations
        if (paths != null) {
            // location may contain JVM system property or OS environment variables
            // so we need to parse those
            String[] locations = parseLocations(paths);

            // check cache first
            CacheKey key = new CacheKey(locations);
            Properties locationsProp = cache ? cacheMap.get(key) : null;
            if (locationsProp == null) {
                locationsProp = propertiesResolver.resolveProperties(getCamelContext(), ignoreMissingLocation, locations);
                if (cache) {
                    cacheMap.put(key, locationsProp);
                }
            }
            prop.putAll(locationsProp);
        }

        // use override properties
        if (overrideProperties != null) {
            // make a copy to avoid affecting the original properties
            Properties override = new Properties();
            override.putAll(prop);
            override.putAll(overrideProperties);
            prop = override;
        }

        // enclose tokens if missing
        if (!uri.contains(prefixToken) && !uri.startsWith(prefixToken)) {
            uri = prefixToken + uri;
        }
        if (!uri.contains(suffixToken) && !uri.endsWith(suffixToken)) {
            uri = uri + suffixToken;
        }

        LOG.trace("Parsing uri {} with properties: {}", uri, prop);
        
        if (propertiesParser instanceof AugmentedPropertyNameAwarePropertiesParser) {
            return ((AugmentedPropertyNameAwarePropertiesParser) propertiesParser).parseUri(uri, prop, prefixToken, suffixToken,
                                                                                            propertyPrefixResolved, propertySuffixResolved, fallbackToUnaugmentedProperty);
        } else {
            return propertiesParser.parseUri(uri, prop, prefixToken, suffixToken);
        }
    }

    /**
     * Is this component created as a default by {@link org.apache.camel.CamelContext} during starting up Camel.
     */
    public boolean isDefaultCreated() {
        return locations == null;
    }

    public String[] getLocations() {
        return locations;
    }

    public void setLocations(String[] locations) {
        // make sure to trim as people may use new lines when configuring using XML
        // and do this in the setter as Spring/Blueprint resolves placeholders before Camel is being started
        if (locations != null && locations.length > 0) {
            for (int i = 0; i < locations.length; i++) {
                String loc = locations[i];
                locations[i] = loc.trim();
            }
        }

        this.locations = locations;
    }

    public void setLocation(String location) {
        setLocations(location.split(","));
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Encoding to use when loading properties file from the file system or classpath.
     * <p/>
     * If no encoding has been set, then the properties files is loaded using ISO-8859-1 encoding (latin-1)
     * as documented by {@link java.util.Properties#load(java.io.InputStream)}
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public PropertiesResolver getPropertiesResolver() {
        return propertiesResolver;
    }

    public void setPropertiesResolver(PropertiesResolver propertiesResolver) {
        this.propertiesResolver = propertiesResolver;
    }

    public PropertiesParser getPropertiesParser() {
        return propertiesParser;
    }

    public void setPropertiesParser(PropertiesParser propertiesParser) {
        this.propertiesParser = propertiesParser;
    }

    public boolean isCache() {
        return cache;
    }

    public void setCache(boolean cache) {
        this.cache = cache;
    }
    
    public String getPropertyPrefix() {
        return propertyPrefix;
    }

    public void setPropertyPrefix(String propertyPrefix) {
        this.propertyPrefix = propertyPrefix;
        this.propertyPrefixResolved = propertyPrefix;
        if (ObjectHelper.isNotEmpty(this.propertyPrefix)) {
            this.propertyPrefixResolved = FilePathResolver.resolvePath(this.propertyPrefix);
        }
    }

    public String getPropertySuffix() {
        return propertySuffix;
    }

    public void setPropertySuffix(String propertySuffix) {
        this.propertySuffix = propertySuffix;
        this.propertySuffixResolved = propertySuffix;
        if (ObjectHelper.isNotEmpty(this.propertySuffix)) {
            this.propertySuffixResolved = FilePathResolver.resolvePath(this.propertySuffix);
        }
    }

    public boolean isFallbackToUnaugmentedProperty() {
        return fallbackToUnaugmentedProperty;
    }

    public void setFallbackToUnaugmentedProperty(boolean fallbackToUnaugmentedProperty) {
        this.fallbackToUnaugmentedProperty = fallbackToUnaugmentedProperty;
    }

    public boolean isIgnoreMissingLocation() {
        return ignoreMissingLocation;
    }

    public void setIgnoreMissingLocation(boolean ignoreMissingLocation) {
        this.ignoreMissingLocation = ignoreMissingLocation;
    }

    public String getPrefixToken() {
        return prefixToken;
    }

    /**
     * Sets the value of the prefix token used to identify properties to replace.  Setting a value of
     * {@code null} restores the default token (@link {@link #DEFAULT_PREFIX_TOKEN}).
     */
    public void setPrefixToken(String prefixToken) {
        if (prefixToken == null) {
            this.prefixToken = DEFAULT_PREFIX_TOKEN;
        } else {
            this.prefixToken = prefixToken;
        }
    }

    public String getSuffixToken() {
        return suffixToken;
    }

    /**
     * Sets the value of the suffix token used to identify properties to replace.  Setting a value of
     * {@code null} restores the default token (@link {@link #DEFAULT_SUFFIX_TOKEN}).
     */
    public void setSuffixToken(String suffixToken) {
        if (suffixToken == null) {
            this.suffixToken = DEFAULT_SUFFIX_TOKEN;
        } else {
            this.suffixToken = suffixToken;
        }
    }

    public Properties getInitialProperties() {
        return initialProperties;
    }

    /**
     * Sets initial properties which will be used before any locations are resolved.
     *
     * @param initialProperties properties that are added first
     */
    public void setInitialProperties(Properties initialProperties) {
        this.initialProperties = initialProperties;
    }

    public Properties getOverrideProperties() {
        return overrideProperties;
    }

    /**
     * Sets a special list of override properties that take precedence
     * and will use first, if a property exist.
     *
     * @param overrideProperties properties that is used first
     */
    public void setOverrideProperties(Properties overrideProperties) {
        this.overrideProperties = overrideProperties;
    }

    /**
     * Gets the functions registered in this properties component.
     */
    public Map<String, PropertiesFunction> getFunctions() {
        return functions;
    }

    /**
     * Registers the {@link org.apache.camel.component.properties.PropertiesFunction} as a function to this component.
     */
    public void addFunction(PropertiesFunction function) {
        this.functions.put(function.getName(), function);
    }

    /**
     * Is there a {@link org.apache.camel.component.properties.PropertiesFunction} with the given name?
     */
    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    public int getSystemPropertiesMode() {
        return systemPropertiesMode;
    }

    /**
     * Sets the system property mode.
     *
     * @see #SYSTEM_PROPERTIES_MODE_NEVER
     * @see #SYSTEM_PROPERTIES_MODE_FALLBACK
     * @see #SYSTEM_PROPERTIES_MODE_OVERRIDE
     */
    public void setSystemPropertiesMode(int systemPropertiesMode) {
        this.systemPropertiesMode = systemPropertiesMode;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (systemPropertiesMode != SYSTEM_PROPERTIES_MODE_NEVER
                && systemPropertiesMode != SYSTEM_PROPERTIES_MODE_FALLBACK
                && systemPropertiesMode != SYSTEM_PROPERTIES_MODE_OVERRIDE) {
            throw new IllegalArgumentException("Option systemPropertiesMode has invalid value: " + systemPropertiesMode);
        }

        // inject the component to the parser
        if (propertiesParser instanceof DefaultPropertiesParser) {
            ((DefaultPropertiesParser) propertiesParser).setPropertiesComponent(this);
        }
    }

    @Override
    protected void doStop() throws Exception {
        cacheMap.clear();
        super.doStop();
    }

    private String[] parseLocations(String[] locations) {
        List<String> answer = new ArrayList<String>();

        for (String location : locations) {
            LOG.trace("Parsing location: {} ", location);

            try {
                location = FilePathResolver.resolvePath(location);
                LOG.debug("Parsed location: {} ", location);
                if (ObjectHelper.isNotEmpty(location)) {
                    answer.add(location);
                }
            } catch (IllegalArgumentException e) {
                if (!ignoreMissingLocation) {
                    throw e;
                } else {
                    LOG.debug("Ignored missing location: {}", location);
                }
            }
        }

        // must return a not-null answer
        return answer.toArray(new String[answer.size()]);
    }

    /**
     * Key used in the locations cache
     */
    private static final class CacheKey implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String[] locations;

        private CacheKey(String[] locations) {
            this.locations = locations;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheKey that = (CacheKey) o;

            if (!Arrays.equals(locations, that.locations)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return locations != null ? Arrays.hashCode(locations) : 0;
        }

        @Override
        public String toString() {
            return "LocationKey[" + Arrays.asList(locations).toString() + "]";
        }
    }

}
