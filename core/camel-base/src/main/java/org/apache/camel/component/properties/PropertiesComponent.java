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
import java.util.Optional;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.LoadablePropertiesSource;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FilePathResolver;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OrderedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The properties component allows you to use property placeholders in Camel.
 */
@ManagedResource(description = "Managed PropertiesComponent")
@JdkService(org.apache.camel.spi.PropertiesComponent.FACTORY)
public class PropertiesComponent extends ServiceSupport implements org.apache.camel.spi.PropertiesComponent, StaticService, CamelContextAware {

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

    private CamelContext camelContext;
    private final Map<String, PropertiesFunction> functions = new LinkedHashMap<>();
    private PropertiesParser propertiesParser = new DefaultPropertiesParser(this);
    private final PropertiesLookup propertiesLookup = new DefaultPropertiesLookup(this);
    private final List<PropertiesSource> sources = new ArrayList<>();
    private List<PropertiesLocation> locations = new ArrayList<>();
    private String location;
    private boolean ignoreMissingLocation;
    private String encoding;
    private boolean defaultFallbackEnabled = true;
    private Properties initialProperties;
    private Properties overrideProperties;
    private int systemPropertiesMode = SYSTEM_PROPERTIES_MODE_OVERRIDE;
    private int environmentVariableMode = ENVIRONMENT_VARIABLES_MODE_OVERRIDE;
    private boolean autoDiscoverPropertiesSources = true;

    public PropertiesComponent() {
        // include out of the box functions
        addPropertiesFunction(new EnvPropertiesFunction());
        addPropertiesFunction(new SysPropertiesFunction());
        addPropertiesFunction(new ServicePropertiesFunction());
        addPropertiesFunction(new ServiceHostPropertiesFunction());
        addPropertiesFunction(new ServicePortPropertiesFunction());
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
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String parseUri(String uri) {
        return parseUri(uri, propertiesLookup);
    }

    @Override
    public Optional<String> resolveProperty(String key) {
        String value = parseUri(key, propertiesLookup);
        return Optional.of(value);
    }

    @Override
    public Properties loadProperties() {
        // this method may be replaced by loadProperties(k -> true) but the underlying sources
        // may have some optimization for bulk load so let's keep it

        Properties prop = new OrderedProperties();

        // use initial properties
        if (initialProperties != null) {
            prop.putAll(initialProperties);
        }

        if (!sources.isEmpty()) {
            // sources are ordered according to {@link org.apache.camel.support.OrderComparator} so
            // it is needed to iterate them in reverse order otherwise lower priority sources may
            // override properties from higher priority ones
            for (int i = sources.size(); i-- > 0;) {
                PropertiesSource ps = sources.get(i);
                if (ps instanceof LoadablePropertiesSource) {
                    LoadablePropertiesSource lps = (LoadablePropertiesSource) ps;
                    Properties p = lps.loadProperties();
                    prop.putAll(p);
                }
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

    @Override
    public Properties loadProperties(Predicate<String> filter) {
        Properties prop = new OrderedProperties();

        // use initial properties
        if (initialProperties != null) {
            for (String name: initialProperties.stringPropertyNames()) {
                if (filter.test(name)) {
                    prop.put(name, initialProperties.get(name));
                }
            }
        }

        if (!sources.isEmpty()) {
            // sources are ordered according to {@link org.apache.camel.support.OrderComparator} so
            // it is needed to iterate them in reverse order otherwise lower priority sources may
            // override properties from higher priority ones
            for (int i = sources.size(); i-- > 0;) {
                PropertiesSource ps = sources.get(i);
                if (ps instanceof LoadablePropertiesSource) {
                    LoadablePropertiesSource lps = (LoadablePropertiesSource) ps;
                    Properties p = lps.loadProperties(filter);
                    prop.putAll(p);
                }
            }
        }

        // use override properties
        if (overrideProperties != null) {
            for (String name: overrideProperties.stringPropertyNames()) {
                if (filter.test(name)) {
                    prop.put(name, overrideProperties.get(name));
                }
            }
        }

        return prop;
    }

    protected String parseUri(String uri, PropertiesLookup properties) {
        // enclose tokens if missing
        if (!uri.contains(PREFIX_TOKEN) && !uri.startsWith(PREFIX_TOKEN)) {
            uri = PREFIX_TOKEN + uri;
        }
        if (!uri.contains(SUFFIX_TOKEN) && !uri.endsWith(SUFFIX_TOKEN)) {
            uri = uri + SUFFIX_TOKEN;
        }

        LOG.trace("Parsing uri {}", uri);
        return propertiesParser.parseUri(uri, properties, defaultFallbackEnabled);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getLocations() {
        if (locations.isEmpty()) {
            return Collections.EMPTY_LIST;
        } else {
            return locations.stream().map(PropertiesLocation::toString).collect(Collectors.toList());
        }
    }

    /**
     * A list of locations to load properties.
     * This option will override any default locations and only use the locations from this option.
     */
    public void setLocations(List<PropertiesLocation> locations) {
        // reset locations
        locations = parseLocations(locations);
        this.locations = Collections.unmodifiableList(locations);

        // we need to re-create the property sources which may have already been created from locations
        this.sources.removeIf(s -> s instanceof LocationPropertiesSource);
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

    public void addLocation(PropertiesLocation location) {
        this.locations.add(location);
    }

    @Override
    public void addLocation(String location) {
        if (location != null) {
            List<PropertiesLocation> newLocations = new ArrayList<>();
            for (String loc : location.split(",")) {
                newLocations.add(new PropertiesLocation(loc));
            }
            List<PropertiesLocation> current = locations;
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
    @Override
    public void setLocation(String location) {
        this.location = location;
        if (location != null) {
            setLocations(location.split(","));
        }
    }

    public String getLocation() {
        return location;
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
    @Override
    public void setEncoding(String encoding) {
        this.encoding = encoding;
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
    @Override
    public void setIgnoreMissingLocation(boolean ignoreMissingLocation) {
        this.ignoreMissingLocation = ignoreMissingLocation;
    }

    /**
     * @return a list of properties which will be used before any locations are resolved (can't be null).
     */
    public Properties getInitialProperties() {
        if (initialProperties == null) {
            initialProperties = new Properties();
        }

        return initialProperties;
    }

    /**
     * Sets initial properties which will be used before any locations are resolved.
     */
    @Override
    public void setInitialProperties(Properties initialProperties) {
        this.initialProperties = initialProperties;
    }

    /**
     * @return a list of properties that take precedence and will use first, if a property exist (can't be null).
     */
    public Properties getOverrideProperties() {
        if (overrideProperties == null) {
            overrideProperties = new Properties();
        }

        return overrideProperties;
    }

    /**
     * Sets a special list of override properties that take precedence
     * and will use first, if a property exist.
     */
    @Override
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
     * Registers the {@link PropertiesFunction} as a function to this component.
     */
    public void addPropertiesFunction(PropertiesFunction function) {
        this.functions.put(function.getName(), function);
    }

    /**
     * Is there a {@link PropertiesFunction} with the given name?
     */
    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    @ManagedAttribute(description = "System properties mode")
    public int getSystemPropertiesMode() {
        return systemPropertiesMode;
    }

    /**
     * Sets the JVM system property mode (0 = never, 1 = fallback, 2 = override).
     *
     * The default mode (override) is to use system properties if present,
     * and override any existing properties.
     *
     * OS environment variable mode is checked before JVM system property mode
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
     * Sets the OS environment variables mode (0 = never, 1 = fallback, 2 = override).
     *
     * The default mode (override) is to use OS environment variables if present,
     * and override any existing properties.
     *
     * OS environment variable mode is checked before JVM system property mode
     *
     * @see #ENVIRONMENT_VARIABLES_MODE_NEVER
     * @see #ENVIRONMENT_VARIABLES_MODE_FALLBACK
     * @see #ENVIRONMENT_VARIABLES_MODE_OVERRIDE
     */
    public void setEnvironmentVariableMode(int environmentVariableMode) {
        this.environmentVariableMode = environmentVariableMode;
    }

    public boolean isAutoDiscoverPropertiesSources() {
        return autoDiscoverPropertiesSources;
    }

    /**
     * Whether to automatically discovery instances of {@link PropertiesSource} from registry and service factory.
     */
    public void setAutoDiscoverPropertiesSources(boolean autoDiscoverPropertiesSources) {
        this.autoDiscoverPropertiesSources = autoDiscoverPropertiesSources;
    }

    @Override
    public void addPropertiesSource(PropertiesSource propertiesSource) {
        if (propertiesSource instanceof CamelContextAware) {
            ((CamelContextAware) propertiesSource).setCamelContext(getCamelContext());
        }
        synchronized (lock) {
            sources.add(propertiesSource);
            if (!isNew()) {
                // if we have already initialized or started then we should also init the source
                ServiceHelper.initService(propertiesSource);
            }
            if (isStarted()) {
                ServiceHelper.startService(propertiesSource);
            }
        }
    }

    public List<PropertiesSource> getSources() {
        return sources;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        ObjectHelper.notNull(camelContext, "CamelContext", this);

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

        if (isAutoDiscoverPropertiesSources()) {
            // discover any 3rd party properties sources
            try {
                for (PropertiesSource source : getCamelContext().getRegistry().findByType(PropertiesSource.class)) {
                    addPropertiesSource(source);
                    LOG.info("PropertiesComponent added custom PropertiesSource (registry): {}", source);
                }

                FactoryFinder factoryFinder = getCamelContext().adapt(ExtendedCamelContext.class).getFactoryFinder("META-INF/services/org/apache/camel/");
                Class<?> type = factoryFinder.findClass("properties-source-factory").orElse(null);
                if (type != null) {
                    Object obj = getCamelContext().getInjector().newInstance(type, false);
                    if (obj instanceof PropertiesSource) {
                        PropertiesSource ps = (PropertiesSource) obj;
                        addPropertiesSource(ps);
                        LOG.info("PropertiesComponent added custom PropertiesSource (factory): {}", ps);
                    } else if (obj != null) {
                        LOG.warn("PropertiesComponent cannot add custom PropertiesSource as the type is not a org.apache.camel.component.properties.PropertiesSource but: " + type.getName());
                    }
                }
            } catch (Exception e) {
                LOG.debug("Error discovering and using custom PropertiesSource due to " + e.getMessage() + ". This exception is ignored", e);
            }
        }

        sources.sort(OrderedComparator.get());
        ServiceHelper.initService(sources);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(sources);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(sources);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(sources);
    }

    private void addPropertiesLocationsAsPropertiesSource(PropertiesLocation location) {
        if ("ref".equals(location.getResolver())) {
            addPropertiesSource(new RefPropertiesSource(this, location));
        } else if ("file".equals(location.getResolver())) {
            addPropertiesSource(new FilePropertiesSource(this, location));
        } else if ("classpath".equals(location.getResolver())) {
            addPropertiesSource(new ClasspathPropertiesSource(this, location));
        }
    }
    private List<PropertiesLocation> parseLocations(List<PropertiesLocation> locations) {
        List<PropertiesLocation> answer = new ArrayList<>();

        for (PropertiesLocation location : locations) {
            LOG.trace("Parsing location: {}", location);

            try {
                String path = FilePathResolver.resolvePath(location.getPath());
                LOG.debug("Parsed location: {}", path);
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
                    LOG.debug("Ignored missing location: {}", location);
                }
            }
        }

        // must return a not-null answer
        return answer;
    }

}
