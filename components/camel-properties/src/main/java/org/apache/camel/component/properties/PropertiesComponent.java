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
package org.apache.camel.component.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.FilePathResolver;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/properties">Properties Component</a> allows you to use property placeholders when defining Endpoint URIs
 */
@Component("properties")
@ManagedResource(description = "Managed PropertiesComponent")
public class PropertiesComponent extends DefaultComponent implements org.apache.camel.spi.PropertiesComponent, StaticService {

    // TODO: PropertySource / LoadablePropertySource to camel-api
    // TODO: sources and locationSources merged into 1
    // TODO: cache to DefaultPropertiesLookup
    // TODO: API on PropertiesComponent in SPI to Optional<String> lookupProperty(String name);
    // TODO: Remove PropertiesResolver

    /**
     *  Never check system properties.
     */
    public static final int SYSTEM_PROPERTIES_MODE_NEVER = 0;

    /**
     * Check system properties if not resolvable in the specified properties.
     */
    public static final int SYSTEM_PROPERTIES_MODE_FALLBACK = 1;

    /**
     * Check system properties variables) first, before trying the specified properties.
     * This allows system properties to override any other property source
     * (environment variable and then system properties takes precedence).
     * <p/>
     * This is the default.
     */
    public static final int SYSTEM_PROPERTIES_MODE_OVERRIDE = 2;

    /**
     *  Never check OS environment variables.
     */
    public static final int ENVIRONMENT_VARIABLES_MODE_NEVER = 0;

    /**
     * Check OS environment variables if not resolvable in the specified properties.
     * <p/>
     * This is the default.
     */
    public static final int ENVIRONMENT_VARIABLES_MODE_FALLBACK = 1;

    /**
     * Check OS environment variables first, before trying the specified properties.
     * This allows environment variables to override any other property source
     * (environment variable and then system properties takes precedence).
     */
    public static final int ENVIRONMENT_VARIABLES_MODE_OVERRIDE = 2;

    /**
     * Key for stores special override properties that containers such as OSGi can store
     * in the OSGi service registry
     */
    public static final String OVERRIDE_PROPERTIES = PropertiesComponent.class.getName() + ".OverrideProperties";

    private static final Logger LOG = LoggerFactory.getLogger(PropertiesComponent.class);

    private final Map<String, PropertiesFunction> functions = new LinkedHashMap<>();
    private PropertiesResolver propertiesResolver;
    private PropertiesParser propertiesParser = new DefaultPropertiesParser(this);
    private final PropertiesLookup propertiesLookup = new DefaultPropertiesLookup(this);
    private final List<PropertiesSource> sources = new ArrayList<>();
    private final List<LocationPropertiesSource> locationSources = new ArrayList<>();

    private List<PropertiesLocation> locations = Collections.emptyList();
    private transient Properties cachedLoadedProperties;

    @Metadata
    private boolean ignoreMissingLocation;
    @Metadata
    private String encoding;
    @Metadata(defaultValue = "true")
    private boolean cache = true;
    @Metadata(defaultValue = "true")
    private boolean defaultFallbackEnabled = true;
    @Metadata(label = "advanced", defaultValue = DEFAULT_PREFIX_TOKEN)
    private String prefixToken = DEFAULT_PREFIX_TOKEN;
    @Metadata(label = "advanced", defaultValue = DEFAULT_SUFFIX_TOKEN)
    private String suffixToken = DEFAULT_SUFFIX_TOKEN;
    @Metadata(label = "advanced")
    private Properties initialProperties;
    @Metadata(label = "advanced")
    private Properties overrideProperties;
    @Metadata(defaultValue = "" + SYSTEM_PROPERTIES_MODE_OVERRIDE, enums = "0,1,2")
    private int systemPropertiesMode = SYSTEM_PROPERTIES_MODE_OVERRIDE;
    @Metadata(defaultValue = "" + SYSTEM_PROPERTIES_MODE_FALLBACK, enums = "0,1,2")
    private int environmentVariableMode = ENVIRONMENT_VARIABLES_MODE_OVERRIDE;

    public PropertiesComponent() {
        super();
        // include out of the box functions
        addFunction(new EnvPropertiesFunction());
        addFunction(new SysPropertiesFunction());
        addFunction(new ServicePropertiesFunction());
        addFunction(new ServiceHostPropertiesFunction());
        addFunction(new ServicePortPropertiesFunction());
    }

    /**
     * A list of locations to load properties. You can use comma to separate multiple locations.
     */
    public PropertiesComponent(String location) {
        this();
        setLocation(location);
    }

    /**
     * A list of locations to load properties.
     */
    public PropertiesComponent(String... locations) {
        this();
        setLocations(locations);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String endpointUri = parseUri(remaining);
        if (LOG.isDebugEnabled()) {
            log.debug("Endpoint uri parsed as: {}", URISupport.sanitizeUri(endpointUri));
        }

        Endpoint delegate = getCamelContext().getEndpoint(endpointUri);
        PropertiesEndpoint answer = new PropertiesEndpoint(uri, delegate, this);

        setProperties(answer, parameters);
        return answer;
    }

    public String parseUri(String uri) {
        return parseUri(uri, propertiesLookup);
    }

    public Properties loadProperties() {
        if (cache) {
            if (cachedLoadedProperties == null) {
                cachedLoadedProperties = doLoadProperties();
            }
            return cachedLoadedProperties;
        } else {
            return doLoadProperties();
        }
    }

    protected Properties doLoadProperties() {
        Properties prop = new OrderedProperties();

        // use initial properties
        if (initialProperties != null) {
            prop.putAll(initialProperties);
        }

        if (!locationSources.isEmpty()) {
            for (PropertiesSource ps : locationSources) {
                if (ps instanceof LoadablePropertiesSource) {
                    LoadablePropertiesSource lps = (LoadablePropertiesSource) ps;
                    Properties p = lps.loadProperties();
                    prop.putAll(p);
                }
            }
        }
        if (!sources.isEmpty()) {
            for (PropertiesSource ps : sources) {
                if (ps instanceof LoadablePropertiesSource) {
                    LoadablePropertiesSource lps = (LoadablePropertiesSource) ps;
                    Properties p = lps.loadProperties();
                    prop.putAll(p);
                }
            }
        }

        // use legacy properties resolver
        if (propertiesResolver != null) {
            Properties p = propertiesResolver.resolveProperties(getCamelContext(), ignoreMissingLocation, locations);
            if (p != null && !p.isEmpty()) {
                prop.putAll(p);
            }
        }

        // use override properties
        if (overrideProperties != null) {
            // make a copy to avoid affecting the original properties
            Properties override = new OrderedProperties();
            override.putAll(prop);
            override.putAll(overrideProperties);
            prop = override;
        }

        return prop;
    }

    protected String parseUri(String uri, PropertiesLookup properties) {
        // enclose tokens if missing
        if (!uri.contains(prefixToken) && !uri.startsWith(prefixToken)) {
            uri = prefixToken + uri;
        }
        if (!uri.contains(suffixToken) && !uri.endsWith(suffixToken)) {
            uri = uri + suffixToken;
        }

        log.trace("Parsing uri {}", uri);
        return propertiesParser.parseUri(uri, properties, prefixToken, suffixToken, defaultFallbackEnabled);
    }

    /**
     * Gets the configured locations
     */
    public List<PropertiesLocation> getLocations() {
        return locations;
    }

    /**
     * A list of locations to load properties.
     * This option will override any default locations and only use the locations from this option.
     */
    public void setLocations(List<PropertiesLocation> locations) {
        // reset locations
        locations = parseLocations(locations);
        this.locations = Collections.unmodifiableList(locations);

        // we need to reset them as sources as well
        this.locationSources.clear();
        for (PropertiesLocation loc : locations) {
            addPropertiesLocationsAsPropertiesSource(loc);
        }
    }

    /**
     * A list of locations to load properties.
     * This option will override any default locations and only use the locations from this option.
     */
    public void setLocations(String[] locationStrings) {
        List<PropertiesLocation> locations = new ArrayList<>();
        if (locationStrings != null) {
            for (String locationString : locationStrings) {
                locations.add(new PropertiesLocation(locationString));
            }
        }

        setLocations(locations);
    }

    /**
     * A list of locations to load properties.
     * This option will override any default locations and only use the locations from this option.
     */
    public void setLocations(Collection<String> locationStrings) {
        List<PropertiesLocation> locations = new ArrayList<>();
        if (locationStrings != null) {
            for (String locationString : locationStrings) {
                locations.add(new PropertiesLocation(locationString));
            }
        }

        setLocations(locations);
    }

    public void addLocation(String location) {
        if (location != null) {
            List<PropertiesLocation> newLocations = new ArrayList<>();
            for (String loc : location.split(",")) {
                newLocations.add(new PropertiesLocation(loc));
            }
            List<PropertiesLocation> current = getLocations();
            if (!current.isEmpty()) {
                newLocations.addAll(0, current);
            }
            setLocations(newLocations);
        }
    }

    /**
     * A list of locations to load properties. You can use comma to separate multiple locations.
     * This option will override any default locations and only use the locations from this option.
     */
    public void setLocation(String location) {
        if (location != null) {
            setLocations(location.split(","));
        }
    }

    @ManagedAttribute(description = "Encoding to use when loading properties file from the file system or classpath")
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

    @Deprecated
    public PropertiesResolver getPropertiesResolver() {
        return propertiesResolver;
    }

    /**
     * To use a custom PropertiesResolver
     */
    @Deprecated
    public void setPropertiesResolver(PropertiesResolver propertiesResolver) {
        this.propertiesResolver = propertiesResolver;
    }

    public PropertiesParser getPropertiesParser() {
        return propertiesParser;
    }

    /**
     * To use a custom PropertiesParser
     */
    public void setPropertiesParser(PropertiesParser propertiesParser) {
        this.propertiesParser = propertiesParser;
    }

    @ManagedAttribute(description = "Whether to cache loaded properties")
    public boolean isCache() {
        return cache;
    }

    /**
     * Whether or not to cache loaded properties. The default value is true.
     */
    public void setCache(boolean cache) {
        this.cache = cache;
    }
    
    @ManagedAttribute(description = "Whether to support using fallback values if a property cannot be found")
    public boolean isDefaultFallbackEnabled() {
        return defaultFallbackEnabled;
    }

    /**
     * If false, the component does not attempt to find a default for the key by looking after the colon separator.
     */
    public void setDefaultFallbackEnabled(boolean defaultFallbackEnabled) {
        this.defaultFallbackEnabled = defaultFallbackEnabled;
    }

    @ManagedAttribute(description = "Ignore missing location")
    public boolean isIgnoreMissingLocation() {
        return ignoreMissingLocation;
    }

    /**
     * Whether to silently ignore if a location cannot be located, such as a properties file not found.
     */
    public void setIgnoreMissingLocation(boolean ignoreMissingLocation) {
        this.ignoreMissingLocation = ignoreMissingLocation;
    }

    @ManagedAttribute(description = "Prefix token")
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

    @ManagedAttribute(description = "Suffix token")
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

    @ManagedAttribute(description = "System properties mode")
    public int getSystemPropertiesMode() {
        return systemPropertiesMode;
    }

    /**
     * Sets the system property mode.
     *
     * The default mode (override) is to use system properties if present,
     * and override any existing properties.
     *
     * @see #SYSTEM_PROPERTIES_MODE_NEVER
     * @see #SYSTEM_PROPERTIES_MODE_FALLBACK
     * @see #SYSTEM_PROPERTIES_MODE_OVERRIDE
     */
    public void setSystemPropertiesMode(int systemPropertiesMode) {
        this.systemPropertiesMode = systemPropertiesMode;
    }

    @ManagedAttribute(description = "Environment variable mode")
    public int getEnvironmentVariableMode() {
        return environmentVariableMode;
    }

    /**
     * Sets the OS environment variables mode.
     *
     * The default mode (override) is to use OS environment variables if present,
     * and override any existing properties.
     *
     * @see #ENVIRONMENT_VARIABLES_MODE_NEVER
     * @see #ENVIRONMENT_VARIABLES_MODE_FALLBACK
     * @see #ENVIRONMENT_VARIABLES_MODE_OVERRIDE
     */
    public void setEnvironmentVariableMode(int environmentVariableMode) {
        this.environmentVariableMode = environmentVariableMode;
    }

    /**
     * Clears the cache
     */
    @ManagedOperation(description = "Clears the cache")
    public void clearCache() {
        this.cachedLoadedProperties = null;
    }

    /**
     * Adds a custom {@link PropertiesSource}
     */
    public void addPropertiesSource(PropertiesSource propertiesSource) {
        if (propertiesSource instanceof CamelContextAware) {
            ((CamelContextAware) propertiesSource).setCamelContext(getCamelContext());
        }
        if (propertiesSource instanceof LocationPropertiesSource) {
            locationSources.add((LocationPropertiesSource) propertiesSource);
        } else {
            sources.add(propertiesSource);
        }
        if (isInit()) {
            // if we are already initialized we need to init the properties source also
            ServiceHelper.initService(propertiesSource);
        }
    }

    public List<PropertiesSource> getSources() {
        return sources;
    }

    public List<LocationPropertiesSource> getLocationSources() {
        return locationSources;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // discover any 3rd party properties sources
        try {
            FactoryFinder factoryFinder = getCamelContext().adapt(ExtendedCamelContext.class).getFactoryFinder("META-INF/services/org/apache/camel");
            Class<?> type = factoryFinder.findClass("properties-source-factory");
            if (type != null) {
                Object obj = getCamelContext().getInjector().newInstance(type, false);
                if (obj instanceof PropertiesSource) {
                    PropertiesSource ps = (PropertiesSource) obj;
                    addPropertiesSource(ps);
                    LOG.info("PropertiesComponent added custom PropertiesSource: {}", ps);
                } else if (obj != null) {
                    LOG.warn("PropertiesComponent cannot add custom PropertiesSource as the type is not a org.apache.camel.component.properties.PropertiesSource but: " + type.getName());
                }
            }
        } catch (NoFactoryAvailableException e) {
            // ignore
        } catch (Exception e) {
            LOG.debug("Error discovering and using custom PropertiesSource due to " + e.getMessage() + ". This exception is ignored", e);
        }

        ServiceHelper.initService(locationSources);
        ServiceHelper.initService(sources);
    }

    @Override
    protected void doStart() throws Exception {
        // sort the sources
        locationSources.sort(OrderedComparator.get());
        sources.sort(OrderedComparator.get());
        ServiceHelper.startService(locationSources, sources);

        if (systemPropertiesMode != SYSTEM_PROPERTIES_MODE_NEVER
                && systemPropertiesMode != SYSTEM_PROPERTIES_MODE_FALLBACK
                && systemPropertiesMode != SYSTEM_PROPERTIES_MODE_OVERRIDE) {
            throw new IllegalArgumentException("Option systemPropertiesMode has invalid value: " + systemPropertiesMode);
        }
        if (environmentVariableMode != ENVIRONMENT_VARIABLES_MODE_NEVER
                && environmentVariableMode != ENVIRONMENT_VARIABLES_MODE_FALLBACK
                && environmentVariableMode != ENVIRONMENT_VARIABLES_MODE_OVERRIDE) {
            throw new IllegalArgumentException("Option environmentVariableMode has invalid value: " + environmentVariableMode);
        }

        // inject the component to the parser
        if (propertiesParser instanceof DefaultPropertiesParser) {
            ((DefaultPropertiesParser) propertiesParser).setPropertiesComponent(this);
        }
    }

    @Override
    protected void doStop() throws Exception {
        cachedLoadedProperties = null;
        ServiceHelper.stopAndShutdownServices(locationSources, sources);
    }

    private void addPropertiesLocationsAsPropertiesSource(PropertiesLocation location) {
        if ("ref".equals(location.getResolver())) {
            addPropertiesSource(new RefPropertiesSource(this, location));
        } else if ("file".equals(location.getResolver())) {
            addPropertiesSource(new FilePropertiesSource(this, location));
        } else if ("classpath".equals(location.getResolver())) {
            addPropertiesSource(new ClasspathPropertiesSource(this, location));
        } else {
            // classpath is also default
            addPropertiesSource(new ClasspathPropertiesSource(this, location));
        }
    }
    private List<PropertiesLocation> parseLocations(List<PropertiesLocation> locations) {
        List<PropertiesLocation> answer = new ArrayList<>();

        for (PropertiesLocation location : locations) {
            log.trace("Parsing location: {}", location);

            try {
                String path = FilePathResolver.resolvePath(location.getPath());
                log.debug("Parsed location: {}", path);
                if (ObjectHelper.isNotEmpty(path)) {
                    answer.add(new PropertiesLocation(
                        location.getResolver(),
                        path,
                        location.isOptional())
                    );
                }
            } catch (IllegalArgumentException e) {
                if (!ignoreMissingLocation && !location.isOptional()) {
                    throw e;
                } else {
                    log.debug("Ignored missing location: {}", location);
                }
            }
        }

        // must return a not-null answer
        return answer;
    }

}
