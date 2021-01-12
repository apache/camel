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
package org.apache.camel.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckConfiguration;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.BaseService;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.SensitiveUtils;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.main.MainHelper.computeProperties;
import static org.apache.camel.main.MainHelper.loadEnvironmentVariablesAsProperties;
import static org.apache.camel.main.MainHelper.lookupPropertyFromSysOrEnv;
import static org.apache.camel.main.MainHelper.optionKey;
import static org.apache.camel.main.MainHelper.setPropertiesOnTarget;
import static org.apache.camel.main.MainHelper.validateOptionAndValue;
import static org.apache.camel.support.ObjectHelper.invokeMethod;
import static org.apache.camel.util.ReflectionHelper.findMethod;
import static org.apache.camel.util.StringHelper.matches;

/**
 * Base class for main implementations to allow bootstrapping Camel in standalone mode.
 */
public abstract class BaseMainSupport extends BaseService {
    public static final String DEFAULT_PROPERTY_PLACEHOLDER_LOCATION = "classpath:application.properties;optional=true";
    public static final String INITIAL_PROPERTIES_LOCATION = "camel.main.initial-properties-location";
    public static final String OVERRIDE_PROPERTIES_LOCATION = "camel.main.override-properties-location";
    public static final String PROPERTY_PLACEHOLDER_LOCATION = "camel.main.property-placeholder-location";

    private static final Logger LOG = LoggerFactory.getLogger(BaseMainSupport.class);

    protected volatile CamelContext camelContext;

    protected final List<MainListener> listeners = new ArrayList<>();
    protected MainConfigurationProperties mainConfigurationProperties = new MainConfigurationProperties();
    protected Properties wildcardProperties = new OrderedProperties();
    protected RoutesCollector routesCollector = new DefaultRoutesCollector();
    protected String propertyPlaceholderLocations;
    protected String defaultPropertyPlaceholderLocation = DEFAULT_PROPERTY_PLACEHOLDER_LOCATION;
    protected Properties initialProperties;
    protected Properties overrideProperties;

    protected BaseMainSupport() {
    }

    protected BaseMainSupport(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * To configure options on Camel Main.
     */
    public MainConfigurationProperties configure() {
        return mainConfigurationProperties;
    }

    public RoutesCollector getRoutesCollector() {
        return routesCollector;
    }

    /**
     * To use a custom {@link RoutesCollector}.
     */
    public void setRoutesCollector(RoutesCollector routesCollector) {
        this.routesCollector = routesCollector;
    }

    public String getPropertyPlaceholderLocations() {
        return propertyPlaceholderLocations;
    }

    /**
     * A list of locations to add for loading properties. You can use comma to separate multiple locations.
     */
    public void setPropertyPlaceholderLocations(String location) {
        this.propertyPlaceholderLocations = location;
    }

    public String getDefaultPropertyPlaceholderLocation() {
        return defaultPropertyPlaceholderLocation;
    }

    /**
     * Set the default location for application properties if no locations have been set. If the value is set to "false"
     * or empty, the default location is not taken into account.
     * <p/>
     * Default value is "classpath:application.properties;optional=true".
     */
    public void setDefaultPropertyPlaceholderLocation(String defaultPropertyPlaceholderLocation) {
        this.defaultPropertyPlaceholderLocation = defaultPropertyPlaceholderLocation;
    }

    @Deprecated
    public boolean isAutoConfigurationEnabled() {
        return mainConfigurationProperties.isAutoConfigurationEnabled();
    }

    /**
     * Whether auto-configuration of components/dataformats/languages is enabled or not. When enabled the configuration
     * parameters are loaded from the properties component and configured as defaults (similar to spring-boot
     * auto-configuration). You can prefix the parameters in the properties file with: -
     * camel.component.name.option1=value1 - camel.component.name.option2=value2 - camel.dataformat.name.option1=value1
     * - camel.dataformat.name.option2=value2 - camel.language.name.option1=value1 - camel.language.name.option2=value2
     * Where name is the name of the component, dataformat or language such as seda,direct,jaxb.
     * <p/>
     * The auto-configuration also works for any options on components that is a complex type (not standard Java type)
     * and there has been an explicit single bean instance registered to the Camel registry via the
     * {@link org.apache.camel.spi.Registry#bind(String, Object)} method or by using the
     * {@link org.apache.camel.BindToRegistry} annotation style.
     * <p/>
     * This option is default enabled.
     *
     * @deprecated use {@link #configure()}
     */
    @Deprecated
    public void setAutoConfigurationEnabled(boolean autoConfigurationEnabled) {
        mainConfigurationProperties.setAutoConfigurationEnabled(autoConfigurationEnabled);
    }

    public Properties getInitialProperties() {
        return initialProperties;
    }

    /**
     * Sets initial properties for the properties component, which will be used before any locations are resolved.
     */
    public void setInitialProperties(Properties initialProperties) {
        this.initialProperties = initialProperties;
    }

    /**
     * Sets initial properties for the properties component, which will be used before any locations are resolved.
     */
    public void setInitialProperties(Map<String, Object> initialProperties) {
        this.initialProperties = new OrderedProperties();
        this.initialProperties.putAll(initialProperties);
    }

    /**
     * Adds a property (initial) for the properties component, which will be used before any locations are resolved.
     *
     * @param key   the property key
     * @param value the property value
     * @see         #addInitialProperty(String, String)
     * @see         #addOverrideProperty(String, String)
     */
    public void addProperty(String key, String value) {
        addInitialProperty(key, value);
    }

    /**
     * Adds a initial property for the properties component, which will be used before any locations are resolved.
     *
     * @param key   the property key
     * @param value the property value
     */
    public void addInitialProperty(String key, String value) {
        if (initialProperties == null) {
            initialProperties = new OrderedProperties();
        }
        initialProperties.setProperty(key, value);
    }

    public Properties getOverrideProperties() {
        return overrideProperties;
    }

    /**
     * Sets a special list of override properties that take precedence and will use first, if a property exist.
     */
    public void setOverrideProperties(Properties overrideProperties) {
        this.overrideProperties = overrideProperties;
    }

    /**
     * Sets a special list of override properties that take precedence and will use first, if a property exist.
     */
    public void setOverrideProperties(Map<String, Object> initialProperties) {
        this.overrideProperties = new OrderedProperties();
        this.overrideProperties.putAll(initialProperties);
    }

    /**
     * Adds an override property that take precedence and will use first, if a property exist.
     *
     * @param key   the property key
     * @param value the property value
     */
    public void addOverrideProperty(String key, String value) {
        if (overrideProperties == null) {
            overrideProperties = new OrderedProperties();
        }
        overrideProperties.setProperty(key, value);
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    /**
     * Adds a {@link MainListener} to receive callbacks when the main is started or stopping
     *
     * @param listener the listener
     */
    public void addMainListener(MainListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes the {@link MainListener}
     *
     * @param listener the listener
     */
    public void removeMainListener(MainListener listener) {
        listeners.remove(listener);
    }

    protected void loadRouteBuilders(CamelContext camelContext) throws Exception {
        // lets use Camel's bean post processor on any existing route builder classes
        // so the instance has some support for dependency injection
        CamelBeanPostProcessor postProcessor = camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
        for (RoutesBuilder routeBuilder : mainConfigurationProperties.getRoutesBuilders()) {
            postProcessor.postProcessBeforeInitialization(routeBuilder, routeBuilder.getClass().getName());
            postProcessor.postProcessAfterInitialization(routeBuilder, routeBuilder.getClass().getName());
        }

        if (mainConfigurationProperties.getRoutesBuilderClasses() != null) {
            String[] routeClasses = mainConfigurationProperties.getRoutesBuilderClasses().split(",");
            for (String routeClass : routeClasses) {
                Class<RoutesBuilder> routeClazz = camelContext.getClassResolver().resolveClass(routeClass, RoutesBuilder.class);
                if (routeClazz == null) {
                    LOG.warn("Unable to resolve class: {}", routeClass);
                    continue;
                }

                // lets use Camel's injector so the class has some support for dependency injection
                RoutesBuilder builder = camelContext.getInjector().newInstance(routeClazz);

                mainConfigurationProperties.addRoutesBuilder(builder);
            }
        }

        if (mainConfigurationProperties.getPackageScanRouteBuilders() != null) {
            String[] pkgs = mainConfigurationProperties.getPackageScanRouteBuilders().split(",");
            Set<Class<?>> set = camelContext.adapt(ExtendedCamelContext.class)
                    .getPackageScanClassResolver()
                    .findImplementations(RoutesBuilder.class, pkgs);
            for (Class<?> routeClazz : set) {
                Object builder = camelContext.getInjector().newInstance(routeClazz);
                if (builder instanceof RoutesBuilder) {
                    mainConfigurationProperties.addRoutesBuilder((RoutesBuilder) builder);
                } else {
                    LOG.warn("Class {} is not a RouteBuilder class", routeClazz);
                }
            }
        }
    }

    protected void loadConfigurations(CamelContext camelContext) throws Exception {
        // lets use Camel's bean post processor on any existing configuration classes
        // so the instance has some support for dependency injection
        CamelBeanPostProcessor postProcessor = camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
        for (Object configuration : mainConfigurationProperties.getConfigurations()) {
            postProcessor.postProcessBeforeInitialization(configuration, configuration.getClass().getName());
            postProcessor.postProcessAfterInitialization(configuration, configuration.getClass().getName());
        }

        if (mainConfigurationProperties.getConfigurationClasses() != null) {
            String[] configClasses = mainConfigurationProperties.getConfigurationClasses().split(",");
            for (String configClass : configClasses) {
                Class<?> configClazz = camelContext.getClassResolver().resolveClass(configClass);
                // lets use Camel's injector so the class has some support for dependency injection
                Object config = camelContext.getInjector().newInstance(configClazz);
                mainConfigurationProperties.addConfiguration(config);
            }
        }

        for (Object config : mainConfigurationProperties.getConfigurations()) {
            // invoke configure method if exists
            Method method = findMethod(config.getClass(), "configure");
            if (method != null) {
                LOG.info("Calling configure method on configuration class: {}", config.getClass().getName());
                invokeMethod(method, config);
            } else {
                Object arg = camelContext;
                method = findMethod(config.getClass(), "configure", CamelContext.class);
                if (method == null) {
                    method = findMethod(config.getClass(), "configure", Main.class);
                    arg = this;
                }
                if (method != null) {
                    LOG.info("Calling configure method on configuration class: {}", config.getClass().getName());
                    invokeMethod(method, config, arg);
                }
            }
        }
    }

    protected void configurePropertiesService(CamelContext camelContext) throws Exception {
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        if (pc.getLocations().isEmpty()) {
            String locations = propertyPlaceholderLocations;
            if (locations == null) {
                locations
                        = lookupPropertyFromSysOrEnv(PROPERTY_PLACEHOLDER_LOCATION).orElse(defaultPropertyPlaceholderLocation);
            }
            if (!Objects.equals(locations, "false")) {
                pc.addLocation(locations);
                LOG.info("Using properties from: {}", locations);
            }
        }

        Properties ip = initialProperties;
        if (ip == null || ip.isEmpty()) {
            Optional<String> location = lookupPropertyFromSysOrEnv(INITIAL_PROPERTIES_LOCATION);
            if (location.isPresent()) {
                try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, location.get())) {
                    ip = new Properties();
                    ip.load(is);
                }
            }
        }
        if (ip != null) {
            pc.setInitialProperties(ip);
        }

        Properties op = overrideProperties;
        if (op == null || op.isEmpty()) {
            Optional<String> location = lookupPropertyFromSysOrEnv(OVERRIDE_PROPERTIES_LOCATION);
            if (location.isPresent()) {
                try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, location.get())) {
                    op = new Properties();
                    op.load(is);
                }
            }
        }
        if (op != null) {
            pc.setOverrideProperties(op);
        }
    }

    protected void configureLifecycle(CamelContext camelContext) throws Exception {
    }

    protected void autoconfigure(CamelContext camelContext) throws Exception {
        // gathers the properties (key=value) that was auto-configured
        final Map<String, String> autoConfiguredProperties = new LinkedHashMap<>();

        // need to eager allow to auto-configure properties component
        if (mainConfigurationProperties.isAutoConfigurationEnabled()) {
            autoConfigurationFailFast(camelContext, autoConfiguredProperties);
            autoConfigurationPropertiesComponent(camelContext, autoConfiguredProperties);
            autoConfigurationMainConfiguration(camelContext, mainConfigurationProperties, autoConfiguredProperties);
        }

        // configure from main configuration properties
        doConfigureCamelContextFromMainConfiguration(camelContext, mainConfigurationProperties, autoConfiguredProperties);

        // try to load configuration classes
        loadConfigurations(camelContext);

        if (mainConfigurationProperties.isAutoConfigurationEnabled()) {
            autoConfigurationFromProperties(camelContext, autoConfiguredProperties);
            autowireWildcardProperties(camelContext);
        }

        // tracing may be enabled by some other property (i.e. camel.context.tracer.exchange-formatter.show-headers)
        if (camelContext.isTracing() && !mainConfigurationProperties.isTracing()) {
            camelContext.setTracing(Boolean.FALSE);
        }

        // log summary of configurations
        if (mainConfigurationProperties.isAutoConfigurationLogSummary() && !autoConfiguredProperties.isEmpty()) {
            LOG.info("Auto-configuration summary:");
            autoConfiguredProperties.forEach((k, v) -> {
                if (SensitiveUtils.containsSensitive(k)) {
                    LOG.info("\t{}=xxxxxx", k);
                } else {
                    LOG.info("\t{}={}", k, v);
                }
            });
        }
    }

    protected void configureRoutes(CamelContext camelContext) throws Exception {
        // try to load the route builders
        loadRouteBuilders(camelContext);

        // then configure and add the routes
        RoutesConfigurer configurer = new RoutesConfigurer(routesCollector, mainConfigurationProperties.getRoutesBuilders());
        configurer.configureRoutes(camelContext, mainConfigurationProperties);
    }

    protected void postProcessCamelContext(CamelContext camelContext) throws Exception {
        // ensure camel is initialized
        camelContext.build();

        for (MainListener listener : listeners) {
            listener.beforeInitialize(this);
        }

        configurePropertiesService(camelContext);

        // allow to do configuration before its started
        for (MainListener listener : listeners) {
            listener.beforeConfigure(this);
        }

        autoconfigure(camelContext);
        configureLifecycle(camelContext);
        configureRoutes(camelContext);

        // allow to do configuration before its started
        for (MainListener listener : listeners) {
            listener.afterConfigure(this);
            listener.configure(camelContext);
        }
    }

    protected void autoConfigurationFailFast(CamelContext camelContext, Map<String, String> autoConfiguredProperties)
            throws Exception {
        // load properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."));
        LOG.debug("Properties from Camel properties component:");
        for (String key : prop.stringPropertyNames()) {
            LOG.debug("    {}={}", key, prop.getProperty(key));
        }

        // special for environment-variable-enabled as we need to know this early before we set all the other options
        Object envEnabled = prop.remove("camel.main.autoConfigurationEnvironmentVariablesEnabled");
        if (envEnabled == null) {
            envEnabled = prop.remove("camel.main.auto-configuration-environment-variables-enabled");
            if (envEnabled != null) {
                mainConfigurationProperties.setAutoConfigurationEnvironmentVariablesEnabled(
                        CamelContextHelper.parseBoolean(camelContext, envEnabled.toString()));
                autoConfiguredProperties.put("camel.main.auto-configuration-environment-variables-enabled",
                        envEnabled.toString());
            }
        }

        // load properties from ENV (override existing)
        Properties propENV = null;
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            propENV = loadEnvironmentVariablesAsProperties(new String[] { "camel.main." });
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
                LOG.debug("Properties from OS environment variables:");
                for (String key : propENV.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propENV.getProperty(key));
                }
            }
        }

        // special for fail-fast as we need to know this early before we set all the other options
        Object failFast = propENV != null ? propENV.remove("camel.main.autoconfigurationfailfast") : null;
        if (failFast != null) {
            mainConfigurationProperties
                    .setAutoConfigurationFailFast(CamelContextHelper.parseBoolean(camelContext, failFast.toString()));
        } else {
            failFast = prop.remove("camel.main.autoConfigurationFailFast");
            if (failFast == null) {
                failFast = prop.remove("camel.main.auto-configuration-fail-fast");
            }
            if (failFast != null) {
                mainConfigurationProperties
                        .setAutoConfigurationFailFast(CamelContextHelper.parseBoolean(camelContext, failFast.toString()));
                autoConfiguredProperties.put("camel.main.auto-configuration-fail-fast", failFast.toString());
            }
        }
    }

    /**
     * Configures CamelContext from the {@link MainConfigurationProperties} properties.
     */
    protected void doConfigureCamelContextFromMainConfiguration(
            CamelContext camelContext, MainConfigurationProperties config,
            Map<String, String> autoConfiguredProperties)
            throws Exception {
        if (config.getFileConfigurations() != null) {
            String[] locs = config.getFileConfigurations().split(",");
            for (String loc : locs) {
                String path = FileUtil.onlyPath(loc);
                if (path != null) {
                    String pattern = loc.length() > path.length() ? loc.substring(path.length() + 1) : null;
                    File[] files = new File(path).listFiles(f -> matches(pattern, f.getName()));
                    if (files != null) {
                        for (File file : files) {
                            Properties props = new Properties();
                            try (FileInputStream is = new FileInputStream(file)) {
                                props.load(is);
                            }
                            if (!props.isEmpty()) {
                                if (overrideProperties == null) {
                                    // setup override properties on properties component
                                    overrideProperties = new Properties();
                                    PropertiesComponent pc = camelContext.getPropertiesComponent();
                                    pc.setOverrideProperties(overrideProperties);
                                }
                                LOG.info("Loaded additional {} properties from file: {}", props.size(), file);
                                overrideProperties.putAll(props);
                            }
                        }
                    }
                }
            }
        }

        // configure the common/default options
        DefaultConfigurationConfigurer.configure(camelContext, config);
        // lookup and configure SPI beans
        DefaultConfigurationConfigurer.afterConfigure(camelContext);

        // now configure context/hystrix/resilience4j/rest with additional properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."));

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV = loadEnvironmentVariablesAsProperties(new String[] { "camel.component.properties." });
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
                LOG.debug("Properties from OS environment variables:");
                for (String key : propENV.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propENV.getProperty(key));
                }
            }
        }

        Map<String, Object> contextProperties = new LinkedHashMap<>();
        Map<String, Object> hystrixProperties = new LinkedHashMap<>();
        Map<String, Object> resilience4jProperties = new LinkedHashMap<>();
        Map<String, Object> faultToleranceProperties = new LinkedHashMap<>();
        Map<String, Object> restProperties = new LinkedHashMap<>();
        Map<String, Object> threadPoolProperties = new LinkedHashMap<>();
        Map<String, Object> healthProperties = new LinkedHashMap<>();
        Map<String, Object> lraProperties = new LinkedHashMap<>();
        Map<String, Object> routeTemplateProperties = new LinkedHashMap<>();
        Map<String, Object> beansProperties = new LinkedHashMap<>();
        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("camel.context.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(14);
                validateOptionAndValue(key, option, value);
                contextProperties.put(optionKey(option), value);
            } else if (key.startsWith("camel.hystrix.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(14);
                validateOptionAndValue(key, option, value);
                hystrixProperties.put(optionKey(option), value);
            } else if (key.startsWith("camel.resilience4j.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(19);
                validateOptionAndValue(key, option, value);
                resilience4jProperties.put(optionKey(option), value);
            } else if (key.startsWith("camel.faulttolerance.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(21);
                validateOptionAndValue(key, option, value);
                faultToleranceProperties.put(optionKey(option), value);
            } else if (key.startsWith("camel.rest.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(11);
                validateOptionAndValue(key, option, value);
                restProperties.put(optionKey(option), value);
            } else if (key.startsWith("camel.threadpool.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(17);
                validateOptionAndValue(key, option, value);
                threadPoolProperties.put(optionKey(option), value);
            } else if (key.startsWith("camel.health.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(13);
                validateOptionAndValue(key, option, value);
                healthProperties.put(optionKey(option), value);
            } else if (key.startsWith("camel.lra.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(10);
                validateOptionAndValue(key, option, value);
                lraProperties.put(optionKey(option), value);
            } else if (key.startsWith("camel.route-template")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(20);
                validateOptionAndValue(key, option, value);
                routeTemplateProperties.put(optionKey(option), value);
            } else if (key.startsWith("camel.beans.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(12);
                validateOptionAndValue(key, option, value);
                beansProperties.put(optionKey(option), value);
            }
        }

        // create beans first as they may be used later
        if (!beansProperties.isEmpty()) {
            LOG.debug("Creating and binding beans to registry from loaded properties: {}", beansProperties.size());
            bindBeansToRegistry(camelContext, beansProperties, "camel.beans.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }
        if (!contextProperties.isEmpty()) {
            LOG.debug("Auto-configuring CamelContext from loaded properties: {}", contextProperties.size());
            setPropertiesOnTarget(camelContext, camelContext, contextProperties, "camel.context.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }

        if (!restProperties.isEmpty()) {
            RestConfigurationProperties rest = mainConfigurationProperties.rest();
            LOG.debug("Auto-configuring Rest DSL from loaded properties: {}", restProperties.size());
            setPropertiesOnTarget(camelContext, rest, restProperties, "camel.rest.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
            camelContext.setRestConfiguration(rest);
        }

        if (!threadPoolProperties.isEmpty()) {
            LOG.debug("Auto-configuring Thread Pool from loaded properties: {}", threadPoolProperties.size());
            MainSupportModelConfigurer.setThreadPoolProperties(camelContext, mainConfigurationProperties, threadPoolProperties,
                    mainConfigurationProperties.isAutoConfigurationFailFast(), autoConfiguredProperties);
        }
        if (!healthProperties.isEmpty()) {
            LOG.debug("Auto-configuring HealthCheck from loaded properties: {}", healthProperties.size());
            setHealthCheckProperties(camelContext, healthProperties, mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }
        if (!routeTemplateProperties.isEmpty()) {
            LOG.debug("Auto-configuring Route templates from loaded properties: {}", routeTemplateProperties.size());
            setRouteTemplateProperties(camelContext, routeTemplateProperties,
                    mainConfigurationProperties.isAutoConfigurationFailFast(), autoConfiguredProperties);
        }
        if (!lraProperties.isEmpty()) {
            LOG.debug("Auto-configuring Saga LRA from loaded properties: {}", lraProperties.size());
            setLraCheckProperties(camelContext, lraProperties, mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }

        // configure which requires access to the model
        MainSupportModelConfigurer.configureModelCamelContext(camelContext, mainConfigurationProperties,
                autoConfiguredProperties, hystrixProperties, resilience4jProperties, faultToleranceProperties);

        // log which options was not set
        if (!beansProperties.isEmpty()) {
            beansProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.beans.{}={}", k, v);
            });
        }
        if (!contextProperties.isEmpty()) {
            contextProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.context.{}={}", k, v);
            });
        }
        if (!hystrixProperties.isEmpty()) {
            hystrixProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.hystrix.{}={}", k, v);
            });
        }
        if (!resilience4jProperties.isEmpty()) {
            resilience4jProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.resilience4j.{}={}", k, v);
            });
        }
        if (!faultToleranceProperties.isEmpty()) {
            faultToleranceProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.faulttolerance.{}={}", k, v);
            });
        }
        if (!restProperties.isEmpty()) {
            restProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.rest.{}={}", k, v);
            });
        }
        if (!threadPoolProperties.isEmpty()) {
            threadPoolProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.threadpool.{}={}", k, v);
            });
        }
        if (!healthProperties.isEmpty()) {
            healthProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.health.{}={}", k, v);
            });
        }
        if (!routeTemplateProperties.isEmpty()) {
            routeTemplateProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.routetemplate.{}={}", k, v);
            });
        }
        if (!lraProperties.isEmpty()) {
            lraProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.lra.{}={}", k, v);
            });
        }

        // and call after all properties are set
        DefaultConfigurationConfigurer.afterPropertiesSet(camelContext);
    }

    private void setRouteTemplateProperties(
            CamelContext camelContext, Map<String, Object> routeTemplateProperties,
            boolean failIfNotSet, Map<String, String> autoConfiguredProperties)
            throws Exception {

        // store the route template parameters as a source and register it on the camel context
        PropertiesRouteTemplateParametersSource source = new PropertiesRouteTemplateParametersSource();
        for (Map.Entry<String, Object> entry : routeTemplateProperties.entrySet()) {
            String id = StringHelper.between(entry.getKey(), "[", "]");
            String key = StringHelper.after(entry.getKey(), "].");
            source.addParameter(id, key, entry.getValue());
        }
        camelContext.getRegistry().bind("CamelMainRouteTemplateParametersSource", RouteTemplateParameterSource.class, source);

        // lets sort by keys
        Map<String, Object> sorted = new TreeMap<>(routeTemplateProperties);
        sorted.forEach((k, v) -> {
            autoConfiguredProperties.put("camel.route-template" + k, v.toString());
        });
        routeTemplateProperties.clear();
    }

    private void setHealthCheckProperties(
            CamelContext camelContext, Map<String, Object> healthCheckProperties,
            boolean failIfNotSet, Map<String, String> autoConfiguredProperties)
            throws Exception {

        HealthCheckRegistry hcr = camelContext.getExtension(HealthCheckRegistry.class);
        if (hcr == null) {
            LOG.warn("Cannot find HealthCheckRegistry from classpath. Add camel-health to classpath.");
            return;
        }

        HealthConfigurationProperties health = mainConfigurationProperties.health();

        // extract all config to know their parent ids so we can set the values afterwards
        Map<String, Object> hcConfig = PropertiesHelper.extractProperties(healthCheckProperties, "config", false);
        Map<String, HealthCheckConfigurationProperties> hcConfigs = new HashMap<>();
        // build set of configuration objects
        for (Map.Entry<String, Object> entry : hcConfig.entrySet()) {
            String parent = StringHelper.between(entry.getKey(), "[", "]");
            if (parent != null) {
                HealthCheckConfigurationProperties hcp = hcConfigs.get(parent);
                if (hcp == null) {
                    hcp = new HealthCheckConfigurationProperties();
                    hcConfigs.put(parent, hcp);
                }
            }
        }
        if (health.getConfig() != null) {
            health.getConfig().putAll(hcConfigs);
        } else {
            health.setConfig(hcConfigs);
        }

        setPropertiesOnTarget(camelContext, health, healthCheckProperties, "camel.health.",
                mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);

        if (health.getEnabled() != null) {
            hcr.setEnabled(health.getEnabled());
        }
        // context is enabled by default
        if (hcr.isEnabled() && (!health.getConfig().containsKey("context") || health.getContextEnabled() != null)) {
            HealthCheck hc = (HealthCheck) hcr.resolveById("context");
            if (hc != null) {
                if (health.getContextEnabled() != null) {
                    hc.getConfiguration().setEnabled(health.getContextEnabled());
                }
                hcr.register(hc);
            }
        }
        // routes is enabled by default
        if (hcr.isEnabled() && (!health.getConfig().containsKey("routes") || health.getRoutesEnabled() != null)) {
            HealthCheckRepository hc = hcr.getRepository("routes").orElse((HealthCheckRepository) hcr.resolveById("routes"));
            if (hc != null) {
                if (health.getRoutesEnabled() != null) {
                    hc.setEnabled(health.getRoutesEnabled());
                }
                hcr.register(hc);
            }
        }
        // registry is enabled by default
        if (hcr.isEnabled() && (!health.getConfig().containsKey("registry") || health.getRegistryEnabled() != null)) {
            hcr.getRepository("registry").ifPresent(h -> {
                if (health.getRegistryEnabled() != null) {
                    h.setEnabled(health.getRegistryEnabled());
                }
            });
        }

        // configure health checks configurations
        for (String id : health.getConfig().keySet()) {
            HealthCheckConfiguration hcc = health.getConfig().get(id);
            String parent = hcc.getParent();
            // lookup health check by id
            Object hc = hcr.getCheck(parent).orElse(null);
            if (hc == null) {
                hc = hcr.resolveById(parent);
                if (hc == null) {
                    LOG.warn("Cannot resolve HealthCheck with id: " + parent + " from classpath.");
                    continue;
                }
                hcr.register(hc);
                if (hc instanceof HealthCheck) {
                    ((HealthCheck) hc).getConfiguration().setParent(hcc.getParent());
                    ((HealthCheck) hc).getConfiguration().setEnabled(hcc.isEnabled());
                    ((HealthCheck) hc).getConfiguration().setFailureThreshold(hcc.getFailureThreshold());
                    ((HealthCheck) hc).getConfiguration().setInterval(hcc.getInterval());
                } else if (hc instanceof HealthCheckRepository) {
                    ((HealthCheckRepository) hc).setEnabled(hcc.isEnabled());
                    ((HealthCheckRepository) hc).addConfiguration(id, hcc);
                }
            }
        }
    }

    private void setLraCheckProperties(
            CamelContext camelContext, Map<String, Object> lraProperties,
            boolean failIfNotSet, Map<String, String> autoConfiguredProperties)
            throws Exception {

        Object obj = lraProperties.remove("enabled");
        if (obj != null) {
            autoConfiguredProperties.put("camel.lra.enabled", obj.toString());
        }
        boolean enabled = obj != null ? CamelContextHelper.parseBoolean(camelContext, obj.toString()) : true;
        if (enabled) {
            CamelSagaService css = resolveLraSagaService(camelContext);
            setPropertiesOnTarget(camelContext, css, lraProperties, "camel.lra.", failIfNotSet, true, autoConfiguredProperties);
        }
    }

    private static CamelSagaService resolveLraSagaService(CamelContext camelContext) throws Exception {
        // lookup in service registry first
        Set<CamelSagaService> set = camelContext.getRegistry().findByType(CamelSagaService.class);
        if (set.size() == 1) {
            return set.iterator().next();
        }
        CamelSagaService answer = camelContext.adapt(ExtendedCamelContext.class).getBootstrapFactoryFinder()
                .newInstance("lra-saga-service", CamelSagaService.class)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot find LRASagaService on classpath. "
                                                                + "Add camel-lra to classpath."));

        // add as service so its discover by saga eip
        camelContext.addService(answer, true, false);
        return answer;
    }

    private void bindBeansToRegistry(
            CamelContext camelContext, Map<String, Object> properties,
            String optionPrefix, boolean failIfNotSet, boolean ignoreCase,
            Map<String, String> autoConfiguredProperties)
            throws Exception {

        // make defensive copy as we mutate the map
        Set<String> keys = new LinkedHashSet<>(properties.keySet());
        // find names of beans
        final Set<String> beans
                = properties.keySet().stream().map(k -> StringHelper.before(k, ".", k)).collect(Collectors.toSet());
        // create beans first
        for (String key : keys) {
            if (key.indexOf('.') == -1) {
                String name = key;
                Object value = properties.remove(key);
                Object bean = PropertyBindingSupport.resolveBean(camelContext, name, value);
                if (bean == null) {
                    throw new IllegalArgumentException(
                            "Cannot create/resolve bean with name " + name + " from value: " + value);
                }
                // register bean
                LOG.info("Binding bean: {} (type: {}) to the registry", key, ObjectHelper.classCanonicalName(bean));
                camelContext.getRegistry().bind(name, bean);
            }
        }
        // then set properties per bean
        for (String name : beans) {
            Object bean = camelContext.getRegistry().lookupByName(name);
            if (bean == null) {
                throw new IllegalArgumentException(
                        "Cannot resolve bean with name " + name);
            }
            // configure all the properties on the bean at once (to ensure they are configured in right order)
            Map<String, Object> config = PropertiesHelper.extractProperties(properties, name + ".");
            setPropertiesOnTarget(camelContext, bean, config, optionPrefix + name + ".", failIfNotSet, ignoreCase,
                    autoConfiguredProperties);
        }
    }

    protected void autoConfigurationPropertiesComponent(CamelContext camelContext, Map<String, String> autoConfiguredProperties)
            throws Exception {
        // load properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."));

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV = loadEnvironmentVariablesAsProperties(new String[] { "camel.component.properties." });
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
            }
        }

        Map<String, Object> properties = new LinkedHashMap<>();

        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("camel.component.properties.")) {
                int dot = key.indexOf('.', 26);
                String option = dot == -1 ? "" : key.substring(dot + 1);
                String value = prop.getProperty(key, "");
                validateOptionAndValue(key, option, value);
                properties.put(optionKey(option), value);
            }
        }

        if (!properties.isEmpty()) {
            LOG.debug("Auto-configuring properties component from loaded properties: {}", properties.size());
            setPropertiesOnTarget(camelContext, camelContext.getPropertiesComponent(), properties,
                    "camel.component.properties.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }

        // log which options was not set
        if (!properties.isEmpty()) {
            properties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.component.properties.{}={} on object: {}", k, v,
                        camelContext.getPropertiesComponent());
            });
        }
    }

    protected void autoConfigurationMainConfiguration(
            CamelContext camelContext, MainConfigurationProperties config, Map<String, String> autoConfiguredProperties)
            throws Exception {
        // load properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."));

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV = loadEnvironmentVariablesAsProperties(new String[] { "camel.main." });
            propENV.remove(INITIAL_PROPERTIES_LOCATION.replace('-', '.'));
            propENV.remove(OVERRIDE_PROPERTIES_LOCATION.replace('-', '.'));
            propENV.remove(PROPERTY_PLACEHOLDER_LOCATION.replace('-', '.'));
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
            }
        }

        Map<String, Object> properties = new LinkedHashMap<>();

        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("camel.main.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(11);
                validateOptionAndValue(key, option, value);
                properties.put(optionKey(option), value);
            }
        }

        if (!properties.isEmpty()) {
            LOG.debug("Auto-configuring main from loaded properties: {}", properties.size());
            setPropertiesOnTarget(camelContext, config, properties, "camel.main.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }

        // log which options was not set
        if (!properties.isEmpty()) {
            properties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.main.{}={} on bean: {}", k, v, config);
            });
        }
    }

    protected void autoConfigurationFromProperties(CamelContext camelContext, Map<String, String> autoConfiguredProperties)
            throws Exception {
        // load optional META-INF/services/org/apache/camel/autowire.properties
        Properties prop = new OrderedProperties();
        try {
            InputStream is = camelContext.getClassResolver()
                    .loadResourceAsStream("/META-INF/services/org/apache/camel/autowire.properties");
            if (is != null) {
                prop.load(is);
                if (!prop.isEmpty()) {
                    LOG.info(
                            "Autowired enabled from classpath: META-INF/services/org/apache/camel/autowire.properties with {} properties",
                            prop.size());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Properties from classpath: META-INF/services/org/apache/camel/autowire.properties:");
                        for (String key : prop.stringPropertyNames()) {
                            LOG.debug("    {}={}", key, prop.getProperty(key));
                        }
                    }
                }
                IOHelper.close(is);
            }
        } catch (Throwable e) {
            // ignore as this file is optional
        }

        // load properties from properties component (override existing)
        Properties propPC = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."));
        prop.putAll(propPC);

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV = loadEnvironmentVariablesAsProperties(
                    new String[] { "camel.component.", "camel.dataformat.", "camel.language." });
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
            }
        }

        Map<PropertyOptionKey, Map<String, Object>> properties = new LinkedHashMap<>();

        // filter out wildcard properties
        for (String key : prop.stringPropertyNames()) {
            if (key.contains("*")) {
                wildcardProperties.put(key, prop.getProperty(key));
            }
        }
        // and remove wildcards
        for (String key : wildcardProperties.stringPropertyNames()) {
            prop.remove(key);
        }

        for (String key : prop.stringPropertyNames()) {
            computeProperties("camel.component.", key, prop, properties, name -> {
                // its an existing component name
                Component target = camelContext.getComponent(name);
                if (target == null) {
                    throw new IllegalArgumentException(
                            "Error configuring property: " + key + " because cannot find component with name " + name
                                                       + ". Make sure you have the component on the classpath");
                }
                return Collections.singleton(target);
            });
            computeProperties("camel.dataformat.", key, prop, properties, name -> {
                DataFormat target = camelContext.resolveDataFormat(name);
                if (target == null) {
                    throw new IllegalArgumentException(
                            "Error configuring property: " + key + " because cannot find dataformat with name " + name
                                                       + ". Make sure you have the dataformat on the classpath");
                }

                return Collections.singleton(target);
            });
            computeProperties("camel.language.", key, prop, properties, name -> {
                Language target;
                try {
                    target = camelContext.resolveLanguage(name);
                } catch (NoSuchLanguageException e) {
                    throw new IllegalArgumentException(
                            "Error configuring property: " + key + " because cannot find language with name " + name
                                                       + ". Make sure you have the language on the classpath");
                }

                return Collections.singleton(target);
            });
        }

        if (!properties.isEmpty()) {
            long total = properties.values().stream().mapToLong(Map::size).sum();
            LOG.debug("Auto-configuring {} components/dataformat/languages from loaded properties: {}", properties.size(),
                    total);
        }

        for (Map.Entry<PropertyOptionKey, Map<String, Object>> entry : properties.entrySet()) {
            setPropertiesOnTarget(
                    camelContext,
                    entry.getKey().getInstance(),
                    entry.getValue(),
                    entry.getKey().getOptionPrefix(),
                    mainConfigurationProperties.isAutoConfigurationFailFast(),
                    true,
                    autoConfiguredProperties);
        }

        // log which options was not set
        if (!properties.isEmpty()) {
            for (PropertyOptionKey pok : properties.keySet()) {
                Map<String, Object> values = properties.get(pok);
                values.forEach((k, v) -> {
                    String stringValue = v != null ? v.toString() : null;
                    LOG.warn("Property ({}={}) not auto-configured with name: {} on bean: {} with value: {}",
                            pok.getOptionPrefix() + "." + k, stringValue, k, pok.getInstance(), stringValue);
                });
            }
        }
    }

    protected void autowireWildcardProperties(CamelContext camelContext) {
        if (wildcardProperties.isEmpty()) {
            return;
        }

        // autowire any pre-existing components as they have been added before we are invoked
        for (String name : camelContext.getComponentNames()) {
            Component comp = camelContext.getComponent(name);
            doAutowireWildcardProperties(name, comp);
        }

        // and autowire any new components that may be added in the future
        camelContext.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onComponentAdd(String name, Component component) {
                doAutowireWildcardProperties(name, component);
            }
        });
    }

    protected void doAutowireWildcardProperties(String name, Component component) {
        Map<PropertyOptionKey, Map<String, Object>> properties = new LinkedHashMap<>();
        Map<String, String> autoConfiguredProperties = new LinkedHashMap<>();
        String match = ("camel.component." + name).toLowerCase(Locale.ENGLISH);

        for (String key : wildcardProperties.stringPropertyNames()) {
            String mKey = key.substring(0, key.indexOf('*')).toLowerCase(Locale.ENGLISH);
            if (match.startsWith(mKey)) {
                computeProperties("camel.component.", key, wildcardProperties, properties,
                        s -> Collections.singleton(component));
            }
        }

        try {
            for (Map.Entry<PropertyOptionKey, Map<String, Object>> entry : properties.entrySet()) {
                setPropertiesOnTarget(
                        camelContext,
                        entry.getKey().getInstance(),
                        entry.getValue(),
                        entry.getKey().getOptionPrefix(),
                        mainConfigurationProperties.isAutoConfigurationFailFast(),
                        true,
                        autoConfiguredProperties);
            }
            // log summary of configurations
            if (mainConfigurationProperties.isAutoConfigurationLogSummary() && !autoConfiguredProperties.isEmpty()) {
                LOG.info("Auto-configuration component {} summary:", name);
                autoConfiguredProperties.forEach((k, v) -> {
                    if (SensitiveUtils.containsSensitive(k)) {
                        LOG.info("\t{}=xxxxxx", k);
                    } else {
                        LOG.info("\t{}={}", k, v);
                    }
                });
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }

}
