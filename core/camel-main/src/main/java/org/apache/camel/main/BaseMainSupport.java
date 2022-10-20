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
import java.util.ArrayList;
import java.util.Collections;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Configuration;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.PropertiesLookupListener;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StartupStep;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.spi.AutowiredLifecycleStrategy;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.BaseService;
import org.apache.camel.support.startup.LoggingStartupStepRecorder;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.SensitiveUtils;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.vault.VaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.main.MainHelper.computeProperties;
import static org.apache.camel.main.MainHelper.optionKey;
import static org.apache.camel.main.MainHelper.setPropertiesOnTarget;
import static org.apache.camel.main.MainHelper.validateOptionAndValue;
import static org.apache.camel.util.LocationHelper.locationSummary;
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

    protected final List<MainListener> listeners = new ArrayList<>();
    protected volatile CamelContext camelContext;
    protected MainConfigurationProperties mainConfigurationProperties = new MainConfigurationProperties();
    protected OrderedLocationProperties wildcardProperties = new OrderedLocationProperties();
    protected RoutesCollector routesCollector = new DefaultRoutesCollector();
    protected String propertyPlaceholderLocations;
    protected String defaultPropertyPlaceholderLocation = DEFAULT_PROPERTY_PLACEHOLDER_LOCATION;
    protected Properties initialProperties;
    protected Properties overrideProperties;
    protected boolean standalone = true;
    protected final MainHelper helper;

    protected BaseMainSupport() {
        this.helper = new MainHelper();
    }

    protected BaseMainSupport(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.helper = new MainHelper();
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

    public Properties getInitialProperties() {
        if (initialProperties == null) {
            initialProperties = new OrderedProperties();
        }
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
     * Adds a property (initial) for the properties component, which will be used before any locations are resolved.
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

    protected void loadConfigurations(CamelContext camelContext) throws Exception {
        // auto-detect camel configurations via base package scanning
        String basePackage = camelContext.adapt(ExtendedCamelContext.class).getBasePackageScan();
        if (basePackage != null) {
            PackageScanClassResolver pscr = camelContext.adapt(ExtendedCamelContext.class).getPackageScanClassResolver();
            Set<Class<?>> found1 = pscr.findImplementations(CamelConfiguration.class, basePackage);
            Set<Class<?>> found2 = pscr.findAnnotated(Configuration.class, basePackage);
            Set<Class<?>> found = new LinkedHashSet<>();
            found.addAll(found1);
            found.addAll(found2);
            for (Class<?> clazz : found) {
                // lets use Camel's injector so the class has some support for dependency injection
                Object config = camelContext.getInjector().newInstance(clazz);
                if (config instanceof CamelConfiguration) {
                    LOG.debug("Discovered CamelConfiguration class: {}", clazz);
                    CamelConfiguration cc = (CamelConfiguration) config;
                    mainConfigurationProperties.addConfiguration(cc);
                }
            }
        }

        if (mainConfigurationProperties.getConfigurationClasses() != null) {
            String[] configClasses = mainConfigurationProperties.getConfigurationClasses().split(",");
            for (String configClass : configClasses) {
                Class<CamelConfiguration> configClazz
                        = camelContext.getClassResolver().resolveClass(configClass, CamelConfiguration.class);
                // skip main classes
                boolean mainClass = false;
                try {
                    configClazz.getDeclaredMethod("main", String[].class);
                    mainClass = true;
                } catch (NoSuchMethodException e) {
                    // ignore
                }
                if (!mainClass) {
                    // lets use Camel's injector so the class has some support for dependency injection
                    CamelConfiguration config = camelContext.getInjector().newInstance(configClazz);
                    mainConfigurationProperties.addConfiguration(config);
                }
            }
        }

        // lets use Camel's bean post processor on any existing configuration classes
        // so the instance has some support for dependency injection
        CamelBeanPostProcessor postProcessor = camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor();

        // discover configurations from the registry
        Set<CamelConfiguration> registryConfigurations = camelContext.getRegistry().findByType(CamelConfiguration.class);
        for (CamelConfiguration configuration : registryConfigurations) {
            postProcessor.postProcessBeforeInitialization(configuration, configuration.getClass().getName());
            postProcessor.postProcessAfterInitialization(configuration, configuration.getClass().getName());
        }

        // prepare the directly configured instances (from registry should have been post processed already)
        for (Object configuration : mainConfigurationProperties.getConfigurations()) {
            postProcessor.postProcessBeforeInitialization(configuration, configuration.getClass().getName());
            postProcessor.postProcessAfterInitialization(configuration, configuration.getClass().getName());
        }

        // invoke configure on configurations
        for (CamelConfiguration config : mainConfigurationProperties.getConfigurations()) {
            config.configure(camelContext);
        }
        // invoke configure on configurations that are from registry
        for (CamelConfiguration config : registryConfigurations) {
            config.configure(camelContext);
        }
    }

    protected void configurePropertiesService(CamelContext camelContext) throws Exception {
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        if (pc.getLocations().isEmpty()) {
            String locations = propertyPlaceholderLocations;
            if (locations == null) {
                locations
                        = MainHelper.lookupPropertyFromSysOrEnv(PROPERTY_PLACEHOLDER_LOCATION)
                                .orElse(defaultPropertyPlaceholderLocation);
            }
            if (locations != null) {
                locations = locations.trim();
            }
            if (!Objects.equals(locations, "false")) {
                pc.addLocation(locations);
                if (DEFAULT_PROPERTY_PLACEHOLDER_LOCATION.equals(locations)) {
                    LOG.debug("Using properties from: {}", locations);
                } else {
                    // if not default location then log at INFO
                    LOG.info("Using properties from: {}", locations);
                }
            }
        }

        Properties ip = initialProperties;
        if (ip == null || ip.isEmpty()) {
            Optional<String> location = MainHelper.lookupPropertyFromSysOrEnv(INITIAL_PROPERTIES_LOCATION);
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
            Optional<String> location = MainHelper.lookupPropertyFromSysOrEnv(OVERRIDE_PROPERTIES_LOCATION);
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

    /**
     * Configures security vaults such as AWS, Azure, Google and Hashicorp.
     */
    protected void configureVault(CamelContext camelContext) throws Exception {
        VaultConfiguration vc = camelContext.getVaultConfiguration();
        if (vc == null) {
            return;
        }

        if (vc.aws().isRefreshEnabled()) {
            Optional<Runnable> task = camelContext.adapt(ExtendedCamelContext.class)
                    .getPeriodTaskResolver().newInstance("aws-secret-refresh", Runnable.class);
            if (task.isPresent()) {
                long period = vc.aws().getRefreshPeriod();
                Runnable r = task.get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling: {} (period: {})", r, TimeUtils.printDuration(period, false));
                }
                if (camelContext.hasService(ContextReloadStrategy.class) == null) {
                    // refresh is enabled then we need to automatically enable context-reload as well
                    ContextReloadStrategy reloader = new DefaultContextReloadStrategy();
                    camelContext.addService(reloader);
                }
                PeriodTaskScheduler scheduler = getCamelContext().adapt(ExtendedCamelContext.class).getPeriodTaskScheduler();
                scheduler.schedulePeriodTask(r, period);
            }
        }

        if (vc.gcp().isRefreshEnabled()) {
            Optional<Runnable> task = camelContext.adapt(ExtendedCamelContext.class)
                    .getPeriodTaskResolver().newInstance("gcp-secret-refresh", Runnable.class);
            if (task.isPresent()) {
                long period = vc.gcp().getRefreshPeriod();
                Runnable r = task.get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling: {} (period: {})", r, TimeUtils.printDuration(period, false));
                }
                if (camelContext.hasService(ContextReloadStrategy.class) == null) {
                    // refresh is enabled then we need to automatically enable context-reload as well
                    ContextReloadStrategy reloader = new DefaultContextReloadStrategy();
                    camelContext.addService(reloader);
                }
                PeriodTaskScheduler scheduler = getCamelContext().adapt(ExtendedCamelContext.class).getPeriodTaskScheduler();
                scheduler.schedulePeriodTask(r, period);
            }
        }

        if (vc.azure().isRefreshEnabled()) {
            Optional<Runnable> task = camelContext.adapt(ExtendedCamelContext.class)
                    .getPeriodTaskResolver().newInstance("azure-secret-refresh", Runnable.class);
            if (task.isPresent()) {
                long period = vc.azure().getRefreshPeriod();
                Runnable r = task.get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling: {} (period: {})", r, TimeUtils.printDuration(period, false));
                }
                if (camelContext.hasService(ContextReloadStrategy.class) == null) {
                    // refresh is enabled then we need to automatically enable context-reload as well
                    ContextReloadStrategy reloader = new DefaultContextReloadStrategy();
                    camelContext.addService(reloader);
                }
                PeriodTaskScheduler scheduler = getCamelContext().adapt(ExtendedCamelContext.class).getPeriodTaskScheduler();
                scheduler.schedulePeriodTask(r, period);
            }
        }
    }

    protected void autoconfigure(CamelContext camelContext) throws Exception {
        // gathers the properties (key=value) that was auto-configured
        final OrderedLocationProperties autoConfiguredProperties = new OrderedLocationProperties();

        // need to eager allow to auto-configure properties component
        if (mainConfigurationProperties.isAutoConfigurationEnabled()) {
            autoConfigurationFailFast(camelContext, autoConfiguredProperties);
            autoConfigurationPropertiesComponent(camelContext, autoConfiguredProperties);

            autoConfigurationSingleOption(camelContext, autoConfiguredProperties, "camel.main.routesIncludePattern",
                    value -> {
                        mainConfigurationProperties.setRoutesIncludePattern(value);
                        return null;
                    });
            autoConfigurationSingleOption(camelContext, autoConfiguredProperties, "camel.main.routesCompileDirectory",
                    value -> {
                        mainConfigurationProperties.setRoutesCompileDirectory(value);
                        return null;
                    });
            autoConfigurationSingleOption(camelContext, autoConfiguredProperties, "camel.main.routesCompileLoadFirst",
                    value -> {
                        boolean bool = CamelContextHelper.parseBoolean(camelContext, value);
                        mainConfigurationProperties.setRoutesCompileLoadFirst(bool);
                        return null;
                    });

            // eager load properties from modeline by scanning DSL sources and gather properties for auto configuration
            if (camelContext.isModeline() || mainConfigurationProperties.isModeline()) {
                modelineRoutes(camelContext);
            }

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

        // log summary of configurations
        if (mainConfigurationProperties.isAutoConfigurationLogSummary() && !autoConfiguredProperties.isEmpty()) {
            boolean header = false;
            for (var entry : autoConfiguredProperties.entrySet()) {
                String k = entry.getKey().toString();
                Object v = entry.getValue();
                String loc = locationSummary(autoConfiguredProperties, k);

                // tone down logging noise for our own internal configurations
                boolean debug = loc.contains("[camel-main]");
                if (debug && !LOG.isDebugEnabled()) {
                    continue;
                }

                if (!header) {
                    LOG.info("Auto-configuration summary");
                    header = true;
                }

                if (SensitiveUtils.containsSensitive(k)) {
                    if (debug) {
                        LOG.debug("    {} {}=xxxxxx", loc, k);
                    } else {
                        LOG.info("    {} {}=xxxxxx", loc, k);
                    }
                } else {
                    if (debug) {
                        LOG.debug("    {} {}={}", loc, k, v);
                    } else {
                        LOG.info("    {} {}={}", loc, k, v);
                    }
                }
            }
        }

        // we are now done with the main helper during bootstrap
        helper.bootstrapDone();
    }

    protected void configureStartupRecorder(CamelContext camelContext) {
        // we need to load these configurations early as they control the startup recorder when using camel-jfr
        // and we want to start jfr recording as early as possible to also capture details during bootstrapping Camel

        // load properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."),
                MainHelper::optionKey);

        Object value = prop.remove("camel.main.startupRecorder");
        if (ObjectHelper.isNotEmpty(value)) {
            mainConfigurationProperties.setStartupRecorder(value.toString());
        }

        value = prop.remove("camel.main.startupRecorderRecording");
        if (ObjectHelper.isNotEmpty(value)) {
            mainConfigurationProperties.setStartupRecorderRecording("true".equalsIgnoreCase(value.toString()));
        }
        value = prop.remove("camel.main.startupRecorderProfile");
        if (ObjectHelper.isNotEmpty(value)) {
            mainConfigurationProperties.setStartupRecorderProfile(
                    CamelContextHelper.parseText(camelContext, value.toString()));
        }
        value = prop.remove("camel.main.startupRecorderDuration");
        if (ObjectHelper.isNotEmpty(value)) {
            mainConfigurationProperties.setStartupRecorderDuration(Long.parseLong(value.toString()));
        }
        value = prop.remove("camel.main.startupRecorderMaxDepth");
        if (ObjectHelper.isNotEmpty(value)) {
            mainConfigurationProperties.setStartupRecorderMaxDepth(Integer.parseInt(value.toString()));
        }

        if ("off".equals(mainConfigurationProperties.getStartupRecorder())
                || "false".equals(mainConfigurationProperties.getStartupRecorder())) {
            camelContext.adapt(ExtendedCamelContext.class).getStartupStepRecorder().setEnabled(false);
        } else if ("logging".equals(mainConfigurationProperties.getStartupRecorder())) {
            camelContext.adapt(ExtendedCamelContext.class).setStartupStepRecorder(new LoggingStartupStepRecorder());
        } else if ("jfr".equals(mainConfigurationProperties.getStartupRecorder())
                || "java-flight-recorder".equals(mainConfigurationProperties.getStartupRecorder())
                || mainConfigurationProperties.getStartupRecorder() == null) {
            // try to auto discover camel-jfr to use
            StartupStepRecorder fr = camelContext.adapt(ExtendedCamelContext.class).getBootstrapFactoryFinder()
                    .newInstance(StartupStepRecorder.FACTORY, StartupStepRecorder.class).orElse(null);
            if (fr != null) {
                LOG.debug("Discovered startup recorder: {} from classpath", fr);
                fr.setRecording(mainConfigurationProperties.isStartupRecorderRecording());
                fr.setStartupRecorderDuration(mainConfigurationProperties.getStartupRecorderDuration());
                fr.setRecordingProfile(mainConfigurationProperties.getStartupRecorderProfile());
                fr.setMaxDepth(mainConfigurationProperties.getStartupRecorderMaxDepth());
                camelContext.adapt(ExtendedCamelContext.class).setStartupStepRecorder(fr);
            }
        }
    }

    protected void configurePackageScan(CamelContext camelContext) {
        if (mainConfigurationProperties.isBasePackageScanEnabled()) {
            // only set the base package if enabled
            camelContext.adapt(ExtendedCamelContext.class).setBasePackageScan(mainConfigurationProperties.getBasePackageScan());
            if (mainConfigurationProperties.getBasePackageScan() != null) {
                LOG.info("Classpath scanning enabled from base package: {}", mainConfigurationProperties.getBasePackageScan());
            }
        }
    }

    protected void configureRoutesLoader(CamelContext camelContext) {
        // use main based routes loader
        camelContext.adapt(ExtendedCamelContext.class).setRoutesLoader(new MainRoutesLoader(mainConfigurationProperties));
    }

    protected void modelineRoutes(CamelContext camelContext) throws Exception {
        // then configure and add the routes
        RoutesConfigurer configurer = new RoutesConfigurer();

        if (mainConfigurationProperties.isRoutesCollectorEnabled()) {
            configurer.setRoutesCollector(routesCollector);
        }

        configurer.setBeanPostProcessor(camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor());
        configurer.setRoutesBuilders(mainConfigurationProperties.getRoutesBuilders());
        configurer.setRoutesBuilderClasses(mainConfigurationProperties.getRoutesBuilderClasses());
        if (mainConfigurationProperties.isBasePackageScanEnabled()) {
            // only set the base package if enabled
            configurer.setBasePackageScan(mainConfigurationProperties.getBasePackageScan());
        }
        configurer.setJavaRoutesExcludePattern(mainConfigurationProperties.getJavaRoutesExcludePattern());
        configurer.setJavaRoutesIncludePattern(mainConfigurationProperties.getJavaRoutesIncludePattern());
        configurer.setRoutesExcludePattern(mainConfigurationProperties.getRoutesExcludePattern());
        configurer.setRoutesIncludePattern(mainConfigurationProperties.getRoutesIncludePattern());

        configurer.configureModeline(camelContext);
    }

    protected void configureRoutes(CamelContext camelContext) throws Exception {
        // then configure and add the routes
        RoutesConfigurer configurer = new RoutesConfigurer();

        if (mainConfigurationProperties.isRoutesCollectorEnabled()) {
            configurer.setRoutesCollector(routesCollector);
        }

        configurer.setBeanPostProcessor(camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor());
        configurer.setRoutesBuilders(mainConfigurationProperties.getRoutesBuilders());
        configurer.setRoutesBuilderClasses(mainConfigurationProperties.getRoutesBuilderClasses());
        if (mainConfigurationProperties.isBasePackageScanEnabled()) {
            // only set the base package if enabled
            configurer.setBasePackageScan(mainConfigurationProperties.getBasePackageScan());
        }
        configurer.setJavaRoutesExcludePattern(mainConfigurationProperties.getJavaRoutesExcludePattern());
        configurer.setJavaRoutesIncludePattern(mainConfigurationProperties.getJavaRoutesIncludePattern());
        configurer.setRoutesExcludePattern(mainConfigurationProperties.getRoutesExcludePattern());
        configurer.setRoutesIncludePattern(mainConfigurationProperties.getRoutesIncludePattern());

        configurer.configureRoutes(camelContext);
    }

    protected void postProcessCamelContext(CamelContext camelContext) throws Exception {
        // gathers the properties (key=value) that was used as property placeholders during bootstrap
        final OrderedLocationProperties propertyPlaceholders = new OrderedLocationProperties();

        // use the main autowired lifecycle strategy instead of the default
        camelContext.getLifecycleStrategies().removeIf(s -> s instanceof AutowiredLifecycleStrategy);
        camelContext.addLifecycleStrategy(new MainAutowiredLifecycleStrategy(camelContext));

        // setup properties
        configurePropertiesService(camelContext);
        // register listener on properties component so we can capture them
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        pc.addPropertiesLookupListener(new PropertyPlaceholderListener(propertyPlaceholders));
        // setup startup recorder before building context
        configureStartupRecorder(camelContext);
        // setup package scan
        configurePackageScan(camelContext);
        // configure to use our main routes loader
        configureRoutesLoader(camelContext);

        // ensure camel context is build
        camelContext.build();

        for (MainListener listener : listeners) {
            listener.beforeInitialize(this);
        }

        // allow doing custom configuration before camel is started
        for (MainListener listener : listeners) {
            listener.beforeConfigure(this);
        }

        // we want to capture startup events for import tasks during main bootstrap
        StartupStepRecorder recorder = camelContext.adapt(ExtendedCamelContext.class).getStartupStepRecorder();
        StartupStep step;

        if (standalone) {
            step = recorder.beginStep(BaseMainSupport.class, "autoconfigure", "Auto Configure");
            autoconfigure(camelContext);
            recorder.endStep(step);
        }

        configureLifecycle(camelContext);

        configureVault(camelContext);

        if (standalone) {
            step = recorder.beginStep(BaseMainSupport.class, "configureRoutes", "Collect Routes");
            configureRoutes(camelContext);
            recorder.endStep(step);
        }

        // allow doing custom configuration before camel is started
        for (MainListener listener : listeners) {
            listener.afterConfigure(this);
            listener.configure(camelContext);
        }

        // we want to log the property placeholder summary after routes has been started,
        // but before camel context logs that it has been started, so we need to use an event listener
        if (standalone && mainConfigurationProperties.isAutoConfigurationLogSummary()) {
            camelContext.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
                @Override
                public boolean isEnabled(CamelEvent event) {
                    return event instanceof CamelEvent.CamelContextRoutesStartedEvent;
                }

                @Override
                public void notify(CamelEvent event) throws Exception {
                    // log summary of configurations
                    if (!propertyPlaceholders.isEmpty()) {
                        LOG.info("Property-placeholders summary");
                        for (var entry : propertyPlaceholders.entrySet()) {
                            String k = entry.getKey().toString();
                            Object v = entry.getValue();
                            Object dv = propertyPlaceholders.getDefaultValue(k);
                            // skip logging configurations that are using default-value
                            // or a kamelet that uses templateId as a parameter
                            boolean same = ObjectHelper.equal(v, dv);
                            boolean skip = "templateId".equals(k);
                            if (!same && !skip) {
                                String loc = locationSummary(propertyPlaceholders, k);
                                if (SensitiveUtils.containsSensitive(k)) {
                                    LOG.info("    {} {}=xxxxxx", loc, k);
                                } else {
                                    LOG.info("    {} {}={}", loc, k, v);
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    protected void autoConfigurationFailFast(CamelContext camelContext, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {
        // load properties
        OrderedLocationProperties prop = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."), MainHelper::optionKey);
        LOG.debug("Properties from Camel properties component:");
        for (String key : prop.stringPropertyNames()) {
            LOG.debug("    {}={}", key, prop.getProperty(key));
        }

        // special for environment-variable-enabled as we need to know this early before we set all the other options
        Object envEnabled = prop.remove("camel.main.autoConfigurationEnvironmentVariablesEnabled");
        if (ObjectHelper.isNotEmpty(envEnabled)) {
            mainConfigurationProperties.setAutoConfigurationEnvironmentVariablesEnabled(
                    CamelContextHelper.parseBoolean(camelContext, envEnabled.toString()));
            String loc = prop.getLocation("camel.main.autoConfigurationEnvironmentVariablesEnabled");
            autoConfiguredProperties.put(loc, "camel.main.autoConfigurationEnvironmentVariablesEnabled",
                    envEnabled.toString());
        }
        // special for system-properties-enabled as we need to know this early before we set all the other options
        Object jvmEnabled = prop.remove("camel.main.autoConfigurationSystemPropertiesEnabled");
        if (ObjectHelper.isNotEmpty(jvmEnabled)) {
            mainConfigurationProperties.setAutoConfigurationSystemPropertiesEnabled(
                    CamelContextHelper.parseBoolean(camelContext, jvmEnabled.toString()));
            String loc = prop.getLocation("camel.main.autoConfigurationSystemPropertiesEnabled");
            autoConfiguredProperties.put(loc, "camel.autoConfigurationSystemPropertiesEnabled",
                    jvmEnabled.toString());
        }

        // load properties from ENV (override existing)
        Properties propENV = null;
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            propENV = MainHelper.loadEnvironmentVariablesAsProperties(new String[] { "camel.main." });
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
                LOG.debug("Properties from OS environment variables:");
                for (String key : propENV.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propENV.getProperty(key));
                }
            }
        }
        // load properties from JVM (override existing)
        Properties propJVM = null;
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(new String[] { "camel.main." });
            if (!propJVM.isEmpty()) {
                prop.putAll(propJVM);
                LOG.debug("Properties from JVM system properties:");
                for (String key : propJVM.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propJVM.getProperty(key));
                }
            }
        }

        // special for fail-fast as we need to know this early before we set all the other options
        String loc = "ENV";
        Object failFast = propENV != null ? propENV.remove("camel.main.autoconfigurationfailfast") : null;
        if (ObjectHelper.isNotEmpty(propJVM)) {
            Object val = propJVM.remove("camel.main.autoconfigurationfailfast");
            if (ObjectHelper.isNotEmpty(val)) {
                loc = "SYS";
                failFast = val;
            }
        }
        if (ObjectHelper.isNotEmpty(failFast)) {
            mainConfigurationProperties
                    .setAutoConfigurationFailFast(CamelContextHelper.parseBoolean(camelContext, failFast.toString()));
            autoConfiguredProperties.put(loc, "camel.main.autoConfigurationFailFast", failFast.toString());
        } else {
            loc = prop.getLocation("camel.main.autoConfigurationFailFast");
            failFast = prop.remove("camel.main.autoConfigurationFailFast");
            if (ObjectHelper.isNotEmpty(failFast)) {
                mainConfigurationProperties
                        .setAutoConfigurationFailFast(CamelContextHelper.parseBoolean(camelContext, failFast.toString()));
                autoConfiguredProperties.put(loc, "camel.main.autoConfigurationFailFast", failFast.toString());
            }
        }
    }

    protected void autoConfigurationSingleOption(
            CamelContext camelContext, OrderedLocationProperties autoConfiguredProperties,
            String optionName, Function<String, Object> setter) {

        String lowerOptionName = optionName.toLowerCase(Locale.US);

        // load properties
        OrderedLocationProperties prop = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."), MainHelper::optionKey);
        LOG.debug("Properties from Camel properties component:");
        for (String key : prop.stringPropertyNames()) {
            LOG.debug("    {}={}", key, prop.getProperty(key));
        }
        // load properties from ENV (override existing)
        Properties propENV = null;
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            propENV = MainHelper.loadEnvironmentVariablesAsProperties(new String[] { "camel.main." });
            if (!propENV.isEmpty()) {
                prop.putAll("ENV", propENV);
                LOG.debug("Properties from OS environment variables:");
                for (String key : propENV.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propENV.getProperty(key));
                }
            }
        }
        // load properties from JVM (override existing)
        Properties propJVM = null;
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(new String[] { "camel.main." });
            if (!propJVM.isEmpty()) {
                prop.putAll("SYS", propJVM);
                LOG.debug("Properties from JVM system properties:");
                for (String key : propJVM.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propJVM.getProperty(key));
                }
            }
        }
        // SYS and ENV take priority (ENV are in lower-case keys)
        String loc = "ENV";
        Object value = propENV != null ? propENV.remove(lowerOptionName) : null;
        if (ObjectHelper.isNotEmpty(propJVM)) {
            Object val = propJVM.remove(optionName);
            if (ObjectHelper.isNotEmpty(val)) {
                // SYS override ENV
                loc = "SYS";
                value = val;
            }
        }
        if (ObjectHelper.isEmpty(value)) {
            // then try properties
            loc = prop.getLocation(optionName);
            value = prop.remove(optionName);
        }
        if (ObjectHelper.isEmpty(value)) {
            // fallback to initial properties
            value = getInitialProperties().getProperty(optionName);
            loc = "initial";
        }

        // set the option if we have a value
        if (ObjectHelper.isNotEmpty(value)) {
            String str = CamelContextHelper.parseText(camelContext, value.toString());
            setter.apply(str);
            autoConfiguredProperties.put(loc, optionName, value.toString());
        }
    }

    /**
     * Configures CamelContext from the {@link MainConfigurationProperties} properties.
     */
    protected void doConfigureCamelContextFromMainConfiguration(
            CamelContext camelContext, MainConfigurationProperties config,
            OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        if (ObjectHelper.isNotEmpty(config.getFileConfigurations())) {
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

        // now configure context/resilience4j/rest with additional properties
        OrderedLocationProperties prop = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."), MainHelper::optionKey);

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV
                    = MainHelper.loadEnvironmentVariablesAsProperties(new String[] { "camel.component.properties." });
            if (!propENV.isEmpty()) {
                prop.putAll("ENV", propENV);
                LOG.debug("Properties from OS environment variables:");
                for (String key : propENV.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propENV.getProperty(key));
                }
            }
        }
        // load properties from JVM (override existing)
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            Properties propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(new String[] { "camel.component.properties." });
            if (!propJVM.isEmpty()) {
                prop.putAll("SYS", propJVM);
                LOG.debug("Properties from JVM system properties:");
                for (String key : propJVM.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propJVM.getProperty(key));
                }
            }
        }

        OrderedLocationProperties contextProperties = new OrderedLocationProperties();
        OrderedLocationProperties resilience4jProperties = new OrderedLocationProperties();
        OrderedLocationProperties faultToleranceProperties = new OrderedLocationProperties();
        OrderedLocationProperties restProperties = new OrderedLocationProperties();
        OrderedLocationProperties vaultProperties = new OrderedLocationProperties();
        OrderedLocationProperties threadPoolProperties = new OrderedLocationProperties();
        OrderedLocationProperties healthProperties = new OrderedLocationProperties();
        OrderedLocationProperties lraProperties = new OrderedLocationProperties();
        OrderedLocationProperties routeTemplateProperties = new OrderedLocationProperties();
        OrderedLocationProperties beansProperties = new OrderedLocationProperties();
        OrderedLocationProperties devConsoleProperties = new OrderedLocationProperties();
        OrderedLocationProperties globalOptions = new OrderedLocationProperties();
        for (String key : prop.stringPropertyNames()) {
            String loc = prop.getLocation(key);
            if (key.startsWith("camel.context.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(14);
                validateOptionAndValue(key, option, value);
                contextProperties.put(loc, optionKey(option), value);
            } else if (key.startsWith("camel.resilience4j.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(19);
                validateOptionAndValue(key, option, value);
                resilience4jProperties.put(loc, optionKey(option), value);
            } else if (key.startsWith("camel.faulttolerance.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(21);
                validateOptionAndValue(key, option, value);
                faultToleranceProperties.put(loc, optionKey(option), value);
            } else if (key.startsWith("camel.rest.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(11);
                validateOptionAndValue(key, option, value);
                restProperties.put(loc, optionKey(option), value);
            } else if (key.startsWith("camel.vault.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(12);
                validateOptionAndValue(key, option, value);
                vaultProperties.put(loc, optionKey(option), value);
            } else if (key.startsWith("camel.threadpool.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(17);
                validateOptionAndValue(key, option, value);
                threadPoolProperties.put(loc, optionKey(option), value);
            } else if (key.startsWith("camel.health.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(13);
                validateOptionAndValue(key, option, value);
                healthProperties.put(loc, optionKey(option), value);
            } else if (key.startsWith("camel.lra.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(10);
                validateOptionAndValue(key, option, value);
                lraProperties.put(loc, optionKey(option), value);
            } else if (key.startsWith("camel.routeTemplate")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(19);
                validateOptionAndValue(key, option, value);
                routeTemplateProperties.put(loc, optionKey(option), value);
            } else if (key.startsWith("camel.devConsole.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(17);
                validateOptionAndValue(key, option, value);
                devConsoleProperties.put(loc, optionKey(option), value);
            } else if (key.startsWith("camel.beans.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(12);
                validateOptionAndValue(key, option, value);
                beansProperties.put(loc, optionKey(option), value);
            } else if (key.startsWith("camel.globalOptions.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(20);
                validateOptionAndValue(key, option, value);
                globalOptions.put(loc, optionKey(option), value);
            }
        }

        // global options first
        if (!globalOptions.isEmpty()) {
            for (var name : globalOptions.stringPropertyNames()) {
                Object value = globalOptions.getProperty(name);
                mainConfigurationProperties.addGlobalOption(name, value);
            }
        }
        // create beans first as they may be used later
        if (!beansProperties.isEmpty()) {
            LOG.debug("Creating and binding beans to registry from loaded properties: {}", beansProperties.size());
            bindBeansToRegistry(camelContext, beansProperties, "camel.beans.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(),
                    mainConfigurationProperties.isAutoConfigurationLogSummary(), true, autoConfiguredProperties);
        }
        if (!contextProperties.isEmpty()) {
            LOG.debug("Auto-configuring CamelContext from loaded properties: {}", contextProperties.size());
            setPropertiesOnTarget(camelContext, camelContext, contextProperties, "camel.context.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }

        if (!restProperties.isEmpty() || mainConfigurationProperties.hasRestConfiguration()) {
            RestConfigurationProperties rest = mainConfigurationProperties.rest();
            LOG.debug("Auto-configuring Rest DSL from loaded properties: {}", restProperties.size());
            setPropertiesOnTarget(camelContext, rest, restProperties, "camel.rest.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
            camelContext.setRestConfiguration(rest);
        }

        if (!vaultProperties.isEmpty() || mainConfigurationProperties.hasVaultConfiguration()) {
            LOG.debug("Auto-configuring Vault from loaded properties: {}", vaultProperties.size());
            setVaultProperties(camelContext, vaultProperties, mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }

        if (!threadPoolProperties.isEmpty() || mainConfigurationProperties.hasThreadPoolConfiguration()) {
            LOG.debug("Auto-configuring Thread Pool from loaded properties: {}", threadPoolProperties.size());
            MainSupportModelConfigurer.setThreadPoolProperties(camelContext, mainConfigurationProperties, threadPoolProperties,
                    autoConfiguredProperties);
        }
        // need to let camel-main setup health-check using its convention over configuration
        boolean hc = mainConfigurationProperties.health().getEnabled() != null; // health-check is enabled by default
        if (hc || !healthProperties.isEmpty() || mainConfigurationProperties.hasHealthCheckConfiguration()) {
            LOG.debug("Auto-configuring HealthCheck from loaded properties: {}", healthProperties.size());
            setHealthCheckProperties(camelContext, healthProperties,
                    autoConfiguredProperties);
        }
        if (!routeTemplateProperties.isEmpty()) {
            LOG.debug("Auto-configuring Route templates from loaded properties: {}", routeTemplateProperties.size());
            setRouteTemplateProperties(camelContext, routeTemplateProperties,
                    autoConfiguredProperties);
        }
        if (!lraProperties.isEmpty() || mainConfigurationProperties.hasLraConfiguration()) {
            LOG.debug("Auto-configuring Saga LRA from loaded properties: {}", lraProperties.size());
            setLraCheckProperties(camelContext, lraProperties, mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }
        if (!devConsoleProperties.isEmpty()) {
            LOG.debug("Auto-configuring Dev Console from loaded properties: {}", devConsoleProperties.size());
            setDevConsoleProperties(camelContext, devConsoleProperties,
                    mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }

        // configure which requires access to the model
        MainSupportModelConfigurer.configureModelCamelContext(camelContext, mainConfigurationProperties,
                autoConfiguredProperties, resilience4jProperties, faultToleranceProperties);

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
        if (!vaultProperties.isEmpty()) {
            vaultProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.vault.{}={}", k, v);
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
            CamelContext camelContext, OrderedLocationProperties routeTemplateProperties,
            OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        // store the route template parameters as a source and register it on the camel context
        PropertiesRouteTemplateParametersSource source = new PropertiesRouteTemplateParametersSource();
        for (Map.Entry<Object, Object> entry : routeTemplateProperties.entrySet()) {
            String key = entry.getKey().toString();
            String id = StringHelper.between(key, "[", "]");
            key = StringHelper.after(key, "].");
            source.addParameter(id, key, entry.getValue());
        }
        camelContext.getRegistry().bind("CamelMainRouteTemplateParametersSource", RouteTemplateParameterSource.class, source);

        // lets sort by keys
        Map<String, Object> sorted = new TreeMap<>(routeTemplateProperties.asMap());
        sorted.forEach((k, v) -> {
            String loc = routeTemplateProperties.getLocation(k);
            autoConfiguredProperties.put(loc, "camel.routeTemplate" + k, v.toString());
        });
        routeTemplateProperties.clear();
    }

    private void setHealthCheckProperties(
            CamelContext camelContext, OrderedLocationProperties healthCheckProperties,
            OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        HealthConfigurationProperties health = mainConfigurationProperties.health();

        setPropertiesOnTarget(camelContext, health, healthCheckProperties, "camel.health.",
                mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);

        if (health.getEnabled() != null && !health.getEnabled()) {
            // health-check is disabled
            return;
        }

        // auto-detect camel-health on classpath
        HealthCheckRegistry hcr = camelContext.getExtension(HealthCheckRegistry.class);
        if (hcr == null) {
            if (health.getEnabled() != null && health.getEnabled()) {
                LOG.warn("Cannot find HealthCheckRegistry from classpath. Add camel-health to classpath.");
            }
            return;
        }

        if (health.getEnabled() != null) {
            hcr.setEnabled(health.getEnabled());
        }
        if (health.getExcludePattern() != null) {
            hcr.setExcludePattern(health.getExcludePattern());
        }
        if (health.getExposureLevel() != null) {
            hcr.setExposureLevel(health.getExposureLevel());
        }
        if (health.getInitialState() != null) {
            hcr.setInitialState(camelContext.getTypeConverter().convertTo(HealthCheck.State.class, health.getInitialState()));
        }

        // context is enabled by default
        if (hcr.isEnabled()) {
            HealthCheck hc = (HealthCheck) hcr.resolveById("context");
            if (hc != null) {
                hcr.register(hc);
            }
        }
        // routes are enabled by default
        if (hcr.isEnabled()) {
            HealthCheckRepository hc = hcr.getRepository("routes").orElse((HealthCheckRepository) hcr.resolveById("routes"));
            if (hc != null) {
                if (health.getRoutesEnabled() != null) {
                    hc.setEnabled(health.getRoutesEnabled());
                }
                hcr.register(hc);
            }
        }
        // components are enabled by default
        if (hcr.isEnabled()) {
            HealthCheckRepository hc
                    = hcr.getRepository("components").orElse((HealthCheckRepository) hcr.resolveById("components"));
            if (hc != null) {
                if (health.getComponentsEnabled() != null) {
                    hc.setEnabled(health.getComponentsEnabled());
                }
                hcr.register(hc);
            }
        }
        // consumers are enabled by default
        if (hcr.isEnabled()) {
            HealthCheckRepository hc
                    = hcr.getRepository("consumers").orElse((HealthCheckRepository) hcr.resolveById("consumers"));
            if (hc != null) {
                if (health.getConsumersEnabled() != null) {
                    hc.setEnabled(health.getConsumersEnabled());
                }
                hcr.register(hc);
            }
        }
        // registry are enabled by default
        if (hcr.isEnabled()) {
            HealthCheckRepository hc
                    = hcr.getRepository("registry").orElse((HealthCheckRepository) hcr.resolveById("registry"));
            if (hc != null) {
                if (health.getRegistryEnabled() != null) {
                    hc.setEnabled(health.getRegistryEnabled());
                }
                hcr.register(hc);
            }
        }
    }

    private void setLraCheckProperties(
            CamelContext camelContext, OrderedLocationProperties lraProperties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        Object obj = lraProperties.remove("enabled");
        if (ObjectHelper.isNotEmpty(obj)) {
            String loc = lraProperties.getLocation("enabled");
            autoConfiguredProperties.put(loc, "camel.lra.enabled", obj.toString());
        }
        boolean enabled = obj != null ? CamelContextHelper.parseBoolean(camelContext, obj.toString()) : true;
        if (enabled) {
            CamelSagaService css = resolveLraSagaService(camelContext);
            setPropertiesOnTarget(camelContext, css, lraProperties, "camel.lra.", failIfNotSet, true, autoConfiguredProperties);
        }
    }

    private void setDevConsoleProperties(
            CamelContext camelContext, OrderedLocationProperties properties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        // make defensive copy as we mutate the map
        Set<String> keys = new LinkedHashSet(properties.keySet());
        // set properties per console
        for (String key : keys) {
            String name = StringHelper.before(key, ".");
            DevConsole console = camelContext.getExtension(DevConsoleRegistry.class).resolveById(name);
            if (console == null) {
                throw new IllegalArgumentException(
                        "Cannot resolve DevConsole with id: " + name);
            }
            // configure all the properties on the console at once (to ensure they are configured in right order)
            OrderedLocationProperties config = MainHelper.extractProperties(properties, name + ".");
            setPropertiesOnTarget(camelContext, console, config, "camel.devConsole." + name + ".", failIfNotSet, true,
                    autoConfiguredProperties);
        }
    }

    private void setVaultProperties(
            CamelContext camelContext, OrderedLocationProperties properties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        if (mainConfigurationProperties.hasVaultConfiguration()) {
            camelContext.setVaultConfiguration(mainConfigurationProperties.vault());
        }
        VaultConfiguration target = camelContext.getVaultConfiguration();

        // make defensive copy as we mutate the map
        Set<String> keys = new LinkedHashSet(properties.keySet());
        // set properties per different vault component
        for (String key : keys) {
            String name = StringHelper.before(key, ".");
            if ("aws".equalsIgnoreCase(name)) {
                target = target.aws();
            }
            if ("gcp".equalsIgnoreCase(name)) {
                target = target.gcp();
            }
            if ("azure".equalsIgnoreCase(name)) {
                target = target.azure();
            }
            if ("hashicorp".equalsIgnoreCase(name)) {
                target = target.hashicorp();
            }
            // configure all the properties on the vault at once (to ensure they are configured in right order)
            OrderedLocationProperties config = MainHelper.extractProperties(properties, name + ".");
            setPropertiesOnTarget(camelContext, target, config, "camel.vault." + name + ".", failIfNotSet, true,
                    autoConfiguredProperties);
        }
    }

    private void bindBeansToRegistry(
            CamelContext camelContext, OrderedLocationProperties properties,
            String optionPrefix, boolean failIfNotSet, boolean logSummary, boolean ignoreCase,
            OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        // make defensive copy as we mutate the map
        Set<String> keys = new LinkedHashSet(properties.keySet());
        // find names of beans (dot style)
        final Set<String> beansDot
                = properties.keySet().stream()
                        .map(k -> StringHelper.before(k.toString(), ".", k.toString()))
                        .filter(k -> k.indexOf('[') == -1)
                        .collect(Collectors.toSet());

        // find names of beans (map style)
        final Set<String> beansMap
                = properties.keySet().stream()
                        .map(k -> StringHelper.before(k.toString(), "[", k.toString()))
                        .filter(k -> k.indexOf('.') == -1)
                        .collect(Collectors.toSet());

        // then create beans first (beans with #class values etc)
        for (String key : keys) {
            if (key.indexOf('.') == -1 && key.indexOf('[') == -1) {
                String name = key;
                Object value = properties.remove(key);
                Object bean = PropertyBindingSupport.resolveBean(camelContext, value);
                if (bean == null) {
                    throw new IllegalArgumentException(
                            "Cannot create/resolve bean with name " + name + " from value: " + value);
                }
                // register bean
                if (logSummary) {
                    LOG.info("Binding bean: {} (type: {}) to the registry", key, ObjectHelper.classCanonicalName(bean));
                } else {
                    LOG.debug("Binding bean: {} (type: {}) to the registry", key, ObjectHelper.classCanonicalName(bean));
                }
                camelContext.getRegistry().bind(name, bean);
            }
        }
        // create map beans if none already exists
        for (String name : beansMap) {
            if (camelContext.getRegistry().lookupByName(name) == null) {
                // register bean as a map
                Map<String, Object> bean = new LinkedHashMap<>();
                if (logSummary) {
                    LOG.info("Binding bean: {} (type: {}) to the registry", name, ObjectHelper.classCanonicalName(bean));
                } else {
                    LOG.debug("Binding bean: {} (type: {}) to the registry", name, ObjectHelper.classCanonicalName(bean));
                }
                camelContext.getRegistry().bind(name, bean);
            }
        }

        // then set properties per bean (dot style)
        for (String name : beansDot) {
            Object bean = camelContext.getRegistry().lookupByName(name);
            if (bean == null) {
                throw new IllegalArgumentException(
                        "Cannot resolve bean with name " + name);
            }
            // configure all the properties on the bean at once (to ensure they are configured in right order)
            OrderedLocationProperties config = MainHelper.extractProperties(properties, name + ".");
            setPropertiesOnTarget(camelContext, bean, config, optionPrefix + name + ".", failIfNotSet, ignoreCase,
                    autoConfiguredProperties);
        }
        // then set properties per bean (map style)
        for (String name : beansMap) {
            Object bean = camelContext.getRegistry().lookupByName(name);
            if (bean == null) {
                throw new IllegalArgumentException(
                        "Cannot resolve bean with name " + name);
            }
            // configure all the properties on the bean at once (to ensure they are configured in right order)
            OrderedLocationProperties config = MainHelper.extractProperties(properties, name + "[", "]");
            setPropertiesOnTarget(camelContext, bean, config, optionPrefix + name + ".", failIfNotSet, ignoreCase,
                    autoConfiguredProperties);
        }
    }

    protected void autoConfigurationPropertiesComponent(
            CamelContext camelContext, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {
        // load properties
        OrderedLocationProperties prop = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."), MainHelper::optionKey);

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV
                    = MainHelper.loadEnvironmentVariablesAsProperties(new String[] { "camel.component.properties." });
            if (!propENV.isEmpty()) {
                prop.putAll("ENV", propENV);
            }
        }
        // load properties from JVM (override existing)
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            Properties propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(new String[] { "camel.component.properties." });
            if (!propJVM.isEmpty()) {
                prop.putAll("SYS", propJVM);
            }
        }

        OrderedLocationProperties properties = new OrderedLocationProperties();

        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("camel.component.properties.")) {
                int dot = key.indexOf('.', 26);
                String option = dot == -1 ? "" : key.substring(dot + 1);
                String value = prop.getProperty(key, "");
                validateOptionAndValue(key, option, value);
                String loc = prop.getLocation(key);
                properties.put(loc, optionKey(option), value);
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
            CamelContext camelContext, MainConfigurationProperties config, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {
        // load properties
        OrderedLocationProperties prop = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."), MainHelper::optionKey);

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV = MainHelper.loadEnvironmentVariablesAsProperties(new String[] { "camel.main." });
            // special handling of these so remove them
            // ENV variables cannot use dash so replace with dot
            propENV.remove(INITIAL_PROPERTIES_LOCATION.replace('-', '.'));
            propENV.remove(OVERRIDE_PROPERTIES_LOCATION.replace('-', '.'));
            propENV.remove(PROPERTY_PLACEHOLDER_LOCATION.replace('-', '.'));
            if (!propENV.isEmpty()) {
                prop.putAll("ENV", propENV);
            }
        }
        // load properties from JVM (override existing)
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            Properties propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(new String[] { "camel.main." });
            // special handling of these so remove them
            propJVM.remove(INITIAL_PROPERTIES_LOCATION);
            propJVM.remove(StringHelper.dashToCamelCase(INITIAL_PROPERTIES_LOCATION));
            propJVM.remove(OVERRIDE_PROPERTIES_LOCATION);
            propJVM.remove(StringHelper.dashToCamelCase(OVERRIDE_PROPERTIES_LOCATION));
            propJVM.remove(PROPERTY_PLACEHOLDER_LOCATION);
            propJVM.remove(StringHelper.dashToCamelCase(PROPERTY_PLACEHOLDER_LOCATION));
            if (!propJVM.isEmpty()) {
                prop.putAll("SYS", propJVM);
            }
        }

        OrderedLocationProperties properties = new OrderedLocationProperties();

        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("camel.main.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(11);
                validateOptionAndValue(key, option, value);
                String loc = prop.getLocation(key);
                properties.put(loc, optionKey(option), value);
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

    protected void autoConfigurationFromProperties(
            CamelContext camelContext, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {
        OrderedLocationProperties prop = new OrderedLocationProperties();

        // load properties from properties component (override existing)
        OrderedLocationProperties propPC = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."));
        prop.putAll(propPC);

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Map<String, String> env = MainHelper
                    .filterEnvVariables(new String[] { "camel.component.", "camel.dataformat.", "camel.language." });
            LOG.debug("Gathered {} ENV variables to configure components, dataformats, languages", env.size());

            // special configuration when using ENV variables as we need to extract the ENV variables
            // that are for the out of the box components first, and then afterwards for any 3rd party custom components
            Properties propENV = new OrderedProperties();
            helper.addComponentEnvVariables(env, propENV, false);
            helper.addDataFormatEnvVariables(env, propENV, false);
            helper.addLanguageEnvVariables(env, propENV, false);

            if (!env.isEmpty()) {
                LOG.debug("Remaining {} ENV variables to configure custom components, dataformats, languages", env.size());
                helper.addComponentEnvVariables(env, propENV, true);
                helper.addDataFormatEnvVariables(env, propENV, true);
                helper.addLanguageEnvVariables(env, propENV, true);
            }

            if (!propENV.isEmpty()) {
                prop.putAll("ENV", propENV);
            }
        }
        // load properties from JVM (override existing)
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            Properties propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(
                    new String[] { "camel.component.", "camel.dataformat.", "camel.language." });
            if (!propJVM.isEmpty()) {
                prop.putAll("SYS", propJVM);
            }
        }

        Map<PropertyOptionKey, OrderedLocationProperties> properties = new LinkedHashMap<>();

        // filter out wildcard properties
        for (String key : prop.stringPropertyNames()) {
            if (key.contains("*")) {
                String loc = prop.getLocation(key);
                wildcardProperties.put(loc, key, prop.getProperty(key));
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

        for (Map.Entry<PropertyOptionKey, OrderedLocationProperties> entry : properties.entrySet()) {
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
            for (Map.Entry<PropertyOptionKey, OrderedLocationProperties> entry : properties.entrySet()) {
                PropertyOptionKey pok = entry.getKey();
                OrderedLocationProperties values = entry.getValue();
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
        Map<PropertyOptionKey, OrderedLocationProperties> properties = new LinkedHashMap<>();
        OrderedLocationProperties autoConfiguredProperties = new OrderedLocationProperties();
        String match = ("camel.component." + name).toLowerCase(Locale.ENGLISH);

        for (String key : wildcardProperties.stringPropertyNames()) {
            String mKey = key.substring(0, key.indexOf('*')).toLowerCase(Locale.ENGLISH);
            if (match.startsWith(mKey)) {
                computeProperties("camel.component.", key, wildcardProperties, properties,
                        s -> Collections.singleton(component));
            }
        }

        try {
            for (Map.Entry<PropertyOptionKey, OrderedLocationProperties> entry : properties.entrySet()) {
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
                boolean header = false;
                for (var entry : autoConfiguredProperties.entrySet()) {
                    String k = entry.getKey().toString();
                    Object v = entry.getValue();
                    String loc = locationSummary(autoConfiguredProperties, k);

                    // tone down logging noise for our own internal configurations
                    boolean debug = loc.contains("[camel-main]");
                    if (debug && !LOG.isDebugEnabled()) {
                        continue;
                    }

                    if (!header) {
                        LOG.info("Auto-configuration component {} summary", name);
                        header = true;
                    }

                    if (SensitiveUtils.containsSensitive(k)) {
                        if (debug) {
                            LOG.debug("    {} {}=xxxxxx", loc, k);
                        } else {
                            LOG.info("    {} {}=xxxxxx", loc, k);
                        }
                    } else {
                        if (debug) {
                            LOG.debug("    {} {}={}", loc, k, v);
                        } else {
                            LOG.info("    {} {}={}", loc, k, v);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }

    private static CamelSagaService resolveLraSagaService(CamelContext camelContext) throws Exception {
        // lookup in service registry first
        CamelSagaService answer = camelContext.getRegistry().findSingleByType(CamelSagaService.class);
        if (answer == null) {
            answer = camelContext.adapt(ExtendedCamelContext.class).getBootstrapFactoryFinder()
                    .newInstance("lra-saga-service", CamelSagaService.class)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot find LRASagaService on classpath. Add camel-lra to classpath."));

            // add as service so its discover by saga eip
            camelContext.addService(answer, true, false);
        }
        return answer;
    }

    private static final class PropertyPlaceholderListener implements PropertiesLookupListener {

        private final OrderedLocationProperties olp;

        public PropertyPlaceholderListener(OrderedLocationProperties olp) {
            this.olp = olp;
        }

        @Override
        public void onLookup(String name, String value, String defaultValue, String source) {
            if (source == null) {
                source = "unknown";
            }
            olp.put(source, name, value, defaultValue);
        }
    }

}
