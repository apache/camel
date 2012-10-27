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
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.LRUSoftCache;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/properties">properties</a> component.
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
     * Key for stores special override properties that containers such as OSGi can store
     * in the OSGi service registry
     */
    public static final String OVERRIDE_PROPERTIES = PropertiesComponent.class.getName() + ".OverrideProperties";

    // must be non greedy patterns
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{env:(.*?)\\}", Pattern.DOTALL);
    private static final Pattern SYS_PATTERN = Pattern.compile("\\$\\{(.*?)\\}", Pattern.DOTALL);

    private static final transient Logger LOG = LoggerFactory.getLogger(PropertiesComponent.class);
    private final Map<CacheKey, Properties> cacheMap = new LRUSoftCache<CacheKey, Properties>(1000);
    private PropertiesResolver propertiesResolver = new DefaultPropertiesResolver();
    private PropertiesParser propertiesParser = new DefaultPropertiesParser();
    private String[] locations;
    private boolean ignoreMissingLocation;
    private boolean cache = true;
    private String propertyPrefix;
    private String propertySuffix;
    private boolean fallbackToUnaugmentedProperty = true;
    private String prefixToken = DEFAULT_PREFIX_TOKEN;
    private String suffixToken = DEFAULT_SUFFIX_TOKEN;
    private Properties overrideProperties;
    
    public PropertiesComponent() {
    }
    
    public PropertiesComponent(String location) {
        setLocation(location);
    }

    public PropertiesComponent(String... locations) {
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
        Properties prop = null;
        if (paths != null) {
            // location may contain JVM system property or OS environment variables
            // so we need to parse those
            String[] locations = parseLocations(paths);
    
            // check cache first
            CacheKey key = new CacheKey(locations);
            prop = cache ? cacheMap.get(key) : null;
            if (prop == null) {
                prop = propertiesResolver.resolveProperties(getCamelContext(), ignoreMissingLocation, locations);
                if (cache) {
                    cacheMap.put(key, prop);
                }
            }
        }

        // use override properties
        if (prop != null && overrideProperties != null) {
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
                                                                                            propertyPrefix, propertySuffix, fallbackToUnaugmentedProperty);
        } else {
            return propertiesParser.parseUri(uri, prop, prefixToken, suffixToken);
        }
    }

    public String[] getLocations() {
        return locations;
    }

    public void setLocations(String[] locations) {
        this.locations = locations;
    }

    public void setLocation(String location) {
        setLocations(location.split(","));
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
    }

    public String getPropertySuffix() {
        return propertySuffix;
    }

    public void setPropertySuffix(String propertySuffix) {
        this.propertySuffix = propertySuffix;
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

    @Override
    protected void doStop() throws Exception {
        cacheMap.clear();
        super.doStop();
    }

    private String[] parseLocations(String[] locations) {
        String[] answer = new String[locations.length];

        for (int i = 0; i < locations.length; i++) {
            String location = locations[i];
            LOG.trace("Parsing location: {} ", location);

            Matcher matcher = ENV_PATTERN.matcher(location);
            while (matcher.find()) {
                String key = matcher.group(1);
                String value = System.getenv(key);
                if (ObjectHelper.isEmpty(value)) {
                    throw new IllegalArgumentException("Cannot find system environment with key: " + key);
                }
                // must quote the replacement to have it work as literal replacement
                value = Matcher.quoteReplacement(value);
                location = matcher.replaceFirst(value);
                // must match again as location is changed
                matcher = ENV_PATTERN.matcher(location);
            }

            matcher = SYS_PATTERN.matcher(location);
            while (matcher.find()) {
                String key = matcher.group(1);
                String value = System.getProperty(key);
                if (ObjectHelper.isEmpty(value)) {
                    throw new IllegalArgumentException("Cannot find JVM system property with key: " + key);
                }
                // must quote the replacement to have it work as literal replacement
                value = Matcher.quoteReplacement(value);
                location = matcher.replaceFirst(value);
                // must match again as location is changed
                matcher = SYS_PATTERN.matcher(location);
            }

            LOG.debug("Parsed location: {} ", location);
            answer[i] = location;
        }

        return answer;
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
